/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.server

import app.altio.service.domain.settings.AppSettings
import app.altio.service.server.plugins.configureAuth
import app.altio.service.server.plugins.configureCallLogging
import app.altio.service.server.plugins.configureRouting
import app.altio.service.server.plugins.configureSerialization
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.io.IOException
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** Embedded Ktor HTTP server bound exclusively to the loopback interface (127.0.0.1). */
class AiHttpServer(private val deps: ServerDependencies) {
  private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? =
      null

  private val _port = MutableStateFlow<Int?>(null)
  private val _startedAt = MutableStateFlow<Instant?>(null)

  /** The port this server is listening on, or null if not running. */
  val port: StateFlow<Int?> = _port.asStateFlow()

  /** The instant the server last started, or null if not running. */
  val startedAtFlow: StateFlow<Instant?> = _startedAt.asStateFlow()

  /** The instant the server last started, or null if not running. */
  var startedAt: Instant? = null
    private set

  fun preflightStart(port: Int) {
    ensurePortAvailable(port)
  }

  fun start(port: Int) {
    if (server != null) {
      Timber.d("Server already running, ignoring start()")
      return
    }
    ensurePortAvailable(port)
    val now = Instant.now()
    Timber.i("HTTP server starting on port %d", port)
    val embedded =
        embeddedServer(CIO, host = LOOPBACK_HOST, port = port) {
          configureSerialization()
          configureAuth(deps.tokenRepository)
          configureCallLogging()
          configureRouting(deps, serverStartedAt = now)
        }
    try {
      embedded.start(wait = false)
    } catch (t: Throwable) {
      throw classifyEngineStartFailure(port, t)
    }
    server = embedded
    _port.value = port
    startedAt = now
    _startedAt.value = now
  }

  fun stop() {
    Timber.i("HTTP server stopped")
    server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
    server = null
    _port.value = null
    startedAt = null
    _startedAt.value = null
  }

  private fun ensurePortAvailable(port: Int) {
    if (port !in MIN_PORT..MAX_PORT) {
      throw AiHttpServerStartException(
          kind = AiHttpServerStartException.Kind.INVALID_PORT,
          attemptedPort = port,
          message = "Port $port is outside the valid range $MIN_PORT..$MAX_PORT",
      )
    }
    try {
      ServerSocket().use { socket ->
        socket.reuseAddress = false
        socket.bind(InetSocketAddress(LOOPBACK_HOST, port))
      }
    } catch (e: BindException) {
      val kind =
          if (e.hasAddressInUseSemantics()) {
            AiHttpServerStartException.Kind.PORT_IN_USE
          } else {
            AiHttpServerStartException.Kind.BIND_FAILED
          }
      throw AiHttpServerStartException(
          kind = kind,
          attemptedPort = port,
          message = "Failed preflight bind for port $port",
          cause = e,
      )
    } catch (e: IllegalArgumentException) {
      throw AiHttpServerStartException(
          kind = AiHttpServerStartException.Kind.INVALID_PORT,
          attemptedPort = port,
          message = "Port $port is invalid",
          cause = e,
      )
    } catch (e: IOException) {
      throw AiHttpServerStartException(
          kind = AiHttpServerStartException.Kind.BIND_FAILED,
          attemptedPort = port,
          message = "Failed preflight bind for port $port",
          cause = e,
      )
    }
  }

  private fun classifyEngineStartFailure(
      port: Int,
      throwable: Throwable,
  ): AiHttpServerStartException {
    val kind =
        when {
          throwable is BindException && throwable.hasAddressInUseSemantics() ->
              AiHttpServerStartException.Kind.PORT_IN_USE
          throwable is BindException || throwable is IOException ->
              AiHttpServerStartException.Kind.BIND_FAILED
          else -> AiHttpServerStartException.Kind.ENGINE_FAILED
        }
    return AiHttpServerStartException(
        kind = kind,
        attemptedPort = port,
        message = "HTTP server engine failed to start on port $port",
        cause = throwable,
    )
  }

  private fun Throwable.hasAddressInUseSemantics(): Boolean =
      sequenceOf(message, localizedMessage).filterNotNull().any { message ->
        val normalized = message.lowercase()
        normalized.contains("address already in use") ||
            normalized.contains("addrinuse") ||
            normalized.contains("eaddrinuse")
      }

  companion object {
    const val LOOPBACK_HOST = "127.0.0.1"
    private const val MIN_PORT = AppSettings.MIN_SERVER_PORT
    private const val MAX_PORT = AppSettings.MAX_SERVER_PORT
  }
}
