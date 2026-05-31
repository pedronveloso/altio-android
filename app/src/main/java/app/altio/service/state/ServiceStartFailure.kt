/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.state

import app.altio.service.server.AiHttpServerStartException
import java.io.IOException
import java.net.BindException
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServiceStartFailure(
    val code: String,
    val title: String,
    val description: String,
    val attemptedPort: Int?,
    val occurredAt: Instant,
    val technicalDetail: String,
)

interface ServiceStartFailureStore {
  val failure: StateFlow<ServiceStartFailure?>

  fun setFailure(failure: ServiceStartFailure?)

  fun clear() = setFailure(null)
}

class InMemoryServiceStartFailureStore : ServiceStartFailureStore {
  private val failureState = MutableStateFlow<ServiceStartFailure?>(null)

  override val failure: StateFlow<ServiceStartFailure?> = failureState.asStateFlow()

  override fun setFailure(failure: ServiceStartFailure?) {
    failureState.value = failure
  }
}

fun classifyServerStartFailure(
    throwable: Throwable,
    attemptedPort: Int?,
    occurredAt: Instant = Instant.now(),
): ServiceStartFailure {
  val code =
      when {
        throwable is AiHttpServerStartException ->
            when (throwable.kind) {
              AiHttpServerStartException.Kind.PORT_IN_USE -> "SVC001"
              AiHttpServerStartException.Kind.INVALID_PORT -> "SVC002"
              AiHttpServerStartException.Kind.BIND_FAILED -> "SVC003"
              AiHttpServerStartException.Kind.ENGINE_FAILED -> "SVC004"
            }
        throwable is IllegalArgumentException -> "SVC002"
        throwable is BindException && throwable.hasAddressInUseSemantics() -> "SVC001"
        throwable is BindException || throwable is IOException -> "SVC003"
        else -> "SVC999"
      }
  return serviceStartFailure(
      code = code,
      attemptedPort = attemptedPort ?: (throwable as? AiHttpServerStartException)?.attemptedPort,
      occurredAt = occurredAt,
      throwable = throwable,
  )
}

fun serviceLaunchBlockedFailure(
    throwable: Throwable,
    occurredAt: Instant = Instant.now(),
): ServiceStartFailure =
    serviceStartFailure(
        code = "SVC005",
        attemptedPort = null,
        occurredAt = occurredAt,
        throwable = throwable,
    )

fun serviceNotificationFailure(
    throwable: Throwable,
    occurredAt: Instant = Instant.now(),
): ServiceStartFailure =
    serviceStartFailure(
        code = "SVC006",
        attemptedPort = null,
        occurredAt = occurredAt,
        throwable = throwable,
    )

fun serviceConfigurationFailure(
    throwable: Throwable,
    occurredAt: Instant = Instant.now(),
): ServiceStartFailure =
    serviceStartFailure(
        code = "SVC007",
        attemptedPort = null,
        occurredAt = occurredAt,
        throwable = throwable,
    )

fun unexpectedServiceStartFailure(
    throwable: Throwable,
    attemptedPort: Int? = null,
    occurredAt: Instant = Instant.now(),
): ServiceStartFailure =
    serviceStartFailure(
        code = "SVC999",
        attemptedPort = attemptedPort,
        occurredAt = occurredAt,
        throwable = throwable,
    )

private fun serviceStartFailure(
    code: String,
    attemptedPort: Int?,
    occurredAt: Instant,
    throwable: Throwable,
): ServiceStartFailure {
  val title =
      when (code) {
        "SVC001" -> "Port unavailable"
        "SVC002" -> "Invalid port"
        "SVC003" -> "Loopback bind failed"
        "SVC004" -> "HTTP server start failed"
        "SVC005" -> "Service launch blocked"
        "SVC006" -> "Foreground setup failed"
        "SVC007" -> "Configuration unavailable"
        else -> "Service start failed"
      }
  val description =
      when (code) {
        "SVC001" ->
            "Port ${attemptedPort ?: "configured"} is already in use. Change the port in Settings or stop the other process."
        "SVC002" ->
            "Port ${attemptedPort ?: "configured"} is invalid. Choose a port from 1 to 65535 in Settings."
        "SVC003" ->
            "The service could not bind to 127.0.0.1${attemptedPort?.let { ":$it" }.orEmpty()}. Restart the service or choose another port."
        "SVC004" ->
            "The HTTP server failed after the port check passed. See debug logs for the matching $code entry."
        "SVC005" ->
            "Android blocked the foreground service launch. Open the app and start the service again."
        "SVC006" ->
            "Android could not create the foreground service notification. Check notification permissions and try again."
        "SVC007" ->
            "The service could not read its startup configuration. Open Settings and try again."
        else ->
            "The service failed to start unexpectedly. See debug logs for the matching $code entry."
      }
  return ServiceStartFailure(
      code = code,
      title = title,
      description = description,
      attemptedPort = attemptedPort,
      occurredAt = occurredAt,
      technicalDetail = throwable.technicalDetail(),
  )
}

private fun Throwable.technicalDetail(): String {
  val className = this::class.java.name
  return message?.let { "$className: $it" } ?: className
}

private fun Throwable.hasAddressInUseSemantics(): Boolean =
    sequenceOf(message, localizedMessage).filterNotNull().any { message ->
      val normalized = message.lowercase()
      normalized.contains("address already in use") ||
          normalized.contains("addrinuse") ||
          normalized.contains("eaddrinuse")
    }
