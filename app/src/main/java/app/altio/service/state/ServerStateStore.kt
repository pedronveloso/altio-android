/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.state

import app.altio.service.data.runtime.RuntimeEngineHolder
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.session.SessionRepository
import app.altio.service.server.AiHttpServer
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class ServerState(
    val serverRunning: Boolean = false,
    val port: Int? = null,
    val startedAt: Instant? = null,
    val uptimeSeconds: Long = 0L,
    val activeSessionCount: Int = 0,
    val activeJobCount: Int = 0,
    val loadedModelId: String? = null,
    val startFailure: ServiceStartFailure? = null,
)

interface ServerStateStore {
  val state: StateFlow<ServerState>
}

@OptIn(ExperimentalCoroutinesApi::class)
class ServerStateStoreImpl
internal constructor(
    private val portFlow: StateFlow<Int?>,
    startedAtFlow: StateFlow<Instant?>,
    sessionCountFlow: Flow<Int>,
    jobCountFlow: Flow<Int>,
    loadedModelIdFlow: StateFlow<String?>,
    startFailureFlow: StateFlow<ServiceStartFailure?>,
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
    nowProvider: () -> Instant = Instant::now,
    tickerFlowFactory: () -> Flow<Unit> = ::defaultTickerFlow,
) : ServerStateStore {

  constructor(
      server: AiHttpServer,
      sessionRepository: SessionRepository,
      jobRepository: JobRepository,
      engineHolder: RuntimeEngineHolder,
      serviceStartFailureStore: ServiceStartFailureStore,
      scope: CoroutineScope,
      started: SharingStarted = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      nowProvider: () -> Instant = Instant::now,
      tickerFlowFactory: () -> Flow<Unit> = ::defaultTickerFlow,
  ) : this(
      portFlow = server.port,
      startedAtFlow = server.startedAtFlow,
      sessionCountFlow = sessionRepository.observeSessionCount(),
      jobCountFlow = jobRepository.observeActiveJobCount(),
      loadedModelIdFlow = engineHolder.loadedModelIdFlow,
      startFailureFlow = serviceStartFailureStore.failure,
      scope = scope,
      started = started,
      nowProvider = nowProvider,
      tickerFlowFactory = tickerFlowFactory,
  )

  override val state: StateFlow<ServerState> =
      buildServerStateFlow(
              portFlow = portFlow,
              startedAtFlow = startedAtFlow,
              sessionCountFlow = sessionCountFlow,
              jobCountFlow = jobCountFlow,
              loadedModelIdFlow = loadedModelIdFlow,
              startFailureFlow = startFailureFlow,
              nowProvider = nowProvider,
              tickerFlowFactory = tickerFlowFactory,
          )
          .stateIn(
              scope = scope,
              started = started,
              initialValue = ServerState(),
          )
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun buildServerStateFlow(
    portFlow: Flow<Int?>,
    startedAtFlow: Flow<Instant?>,
    sessionCountFlow: Flow<Int>,
    jobCountFlow: Flow<Int>,
    loadedModelIdFlow: Flow<String?>,
    startFailureFlow: Flow<ServiceStartFailure?>,
    nowProvider: () -> Instant = Instant::now,
    tickerFlowFactory: () -> Flow<Unit> = ::defaultTickerFlow,
): Flow<ServerState> {
  val runningSinceFlow =
      combine(portFlow, startedAtFlow) { port, startedAt -> if (port != null) startedAt else null }
          .distinctUntilChanged()

  val uptimeSecondsFlow =
      runningSinceFlow
          .flatMapLatest { startedAt ->
            if (startedAt == null) {
              flowOf(0L)
            } else {
              tickerFlowFactory().map {
                Duration.between(startedAt, nowProvider()).seconds.coerceAtLeast(0L)
              }
            }
          }
          .onStart { emit(0L) }

  return combine(
      combine(
          portFlow,
          startedAtFlow,
          uptimeSecondsFlow,
          sessionCountFlow,
          jobCountFlow,
      ) { port, startedAt, uptimeSeconds, activeSessionCount, activeJobCount ->
        PartialServerState(
            port = port,
            startedAt = startedAt,
            uptimeSeconds = uptimeSeconds,
            activeSessionCount = activeSessionCount,
            activeJobCount = activeJobCount,
        )
      },
      loadedModelIdFlow,
      startFailureFlow,
  ) { partial, loadedModelId, startFailure ->
    ServerState(
        serverRunning = partial.port != null,
        port = partial.port,
        startedAt = partial.startedAt,
        uptimeSeconds = partial.uptimeSeconds,
        activeSessionCount = partial.activeSessionCount,
        activeJobCount = partial.activeJobCount,
        loadedModelId = loadedModelId,
        startFailure = startFailure,
    )
  }
}

private data class PartialServerState(
    val port: Int?,
    val startedAt: Instant?,
    val uptimeSeconds: Long,
    val activeSessionCount: Int,
    val activeJobCount: Int,
)

private fun defaultTickerFlow(): Flow<Unit> = flow {
  emit(Unit)
  while (true) {
    delay(1_000)
    emit(Unit)
  }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
