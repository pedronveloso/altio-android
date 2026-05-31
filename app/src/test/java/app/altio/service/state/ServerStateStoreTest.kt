/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.state

import java.time.Instant
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerStateStoreTest {

  @Test
  fun `serverRunning follows port not null`() = runTest {
    val portFlow = MutableStateFlow<Int?>(null)
    val startedAtFlow = MutableStateFlow<Instant?>(null)
    val sessionCountFlow = MutableStateFlow(0)
    val jobCountFlow = MutableStateFlow(0)
    val loadedModelIdFlow = MutableStateFlow<String?>(null)
    val startFailureFlow = MutableStateFlow<ServiceStartFailure?>(null)
    val latest =
        collectLatestServerState(
            portFlow = portFlow,
            startedAtFlow = startedAtFlow,
            sessionCountFlow = sessionCountFlow,
            jobCountFlow = jobCountFlow,
            loadedModelIdFlow = loadedModelIdFlow,
            startFailureFlow = startFailureFlow,
            tickerFlowFactory = { flowOf(Unit) },
        )

    advanceUntilIdle()
    assertFalse(latest.value.serverRunning)

    startedAtFlow.value = Instant.parse("2026-05-22T10:00:00Z")
    portFlow.value = 52731
    advanceUntilIdle()
    assertTrue(latest.value.serverRunning)

    portFlow.value = null
    startedAtFlow.value = null
    advanceUntilIdle()
    assertFalse(latest.value.serverRunning)
    latest.job.cancel()
  }

  @Test
  fun `uptime increments only while running`() = runTest {
    val portFlow = MutableStateFlow<Int?>(null)
    val startedAt = Instant.parse("2026-05-22T10:00:00Z")
    val startedAtFlow = MutableStateFlow<Instant?>(null)
    val sessionCountFlow = MutableStateFlow(0)
    val jobCountFlow = MutableStateFlow(0)
    val loadedModelIdFlow = MutableStateFlow<String?>(null)
    val startFailureFlow = MutableStateFlow<ServiceStartFailure?>(null)
    val tickFlow = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 8)
    var now = startedAt
    val latest =
        collectLatestServerState(
            portFlow = portFlow,
            startedAtFlow = startedAtFlow,
            sessionCountFlow = sessionCountFlow,
            jobCountFlow = jobCountFlow,
            loadedModelIdFlow = loadedModelIdFlow,
            startFailureFlow = startFailureFlow,
            nowProvider = { now },
            tickerFlowFactory = { tickFlow },
        )

    startedAtFlow.value = startedAt
    portFlow.value = 52731
    advanceUntilIdle()
    tickFlow.tryEmit(Unit)
    advanceUntilIdle()
    assertEquals(0L, latest.value.uptimeSeconds)

    now = startedAt.plusSeconds(5)
    tickFlow.tryEmit(Unit)
    advanceUntilIdle()
    assertEquals(5L, latest.value.uptimeSeconds)

    portFlow.value = null
    startedAtFlow.value = null
    advanceUntilIdle()
    assertEquals(0L, latest.value.uptimeSeconds)

    now = startedAt.plusSeconds(10)
    tickFlow.tryEmit(Unit)
    advanceUntilIdle()
    assertEquals(0L, latest.value.uptimeSeconds)
    latest.job.cancel()
  }

  @Test
  fun `session and job counts propagate correctly`() = runTest {
    val latest =
        collectLatestServerState(
            portFlow = MutableStateFlow(52731),
            startedAtFlow = MutableStateFlow(Instant.parse("2026-05-22T10:00:00Z")),
            sessionCountFlow = MutableStateFlow(0),
            jobCountFlow = MutableStateFlow(0),
            loadedModelIdFlow = MutableStateFlow<String?>(null),
            startFailureFlow = MutableStateFlow<ServiceStartFailure?>(null),
            tickerFlowFactory = { flowOf(Unit) },
        )

    latest.sessionCountFlow.value = 3
    latest.jobCountFlow.value = 7
    advanceUntilIdle()

    assertEquals(3, latest.value.activeSessionCount)
    assertEquals(7, latest.value.activeJobCount)
    latest.job.cancel()
  }

  @Test
  fun `loaded model id is reflected`() = runTest {
    val latest =
        collectLatestServerState(
            portFlow = MutableStateFlow(52731),
            startedAtFlow = MutableStateFlow(Instant.parse("2026-05-22T10:00:00Z")),
            sessionCountFlow = MutableStateFlow(0),
            jobCountFlow = MutableStateFlow(0),
            loadedModelIdFlow = MutableStateFlow<String?>(null),
            startFailureFlow = MutableStateFlow<ServiceStartFailure?>(null),
            tickerFlowFactory = { flowOf(Unit) },
        )

    latest.loadedModelIdFlow.value = "gemma-4-e2b-it"
    advanceUntilIdle()

    assertEquals("gemma-4-e2b-it", latest.value.loadedModelId)
    latest.job.cancel()
  }

  @Test
  fun `start failure is reflected and clears`() = runTest {
    val failureFlow = MutableStateFlow<ServiceStartFailure?>(null)
    val latest =
        collectLatestServerState(
            portFlow = MutableStateFlow<Int?>(null),
            startedAtFlow = MutableStateFlow<Instant?>(null),
            sessionCountFlow = MutableStateFlow(0),
            jobCountFlow = MutableStateFlow(0),
            loadedModelIdFlow = MutableStateFlow<String?>(null),
            startFailureFlow = failureFlow,
            tickerFlowFactory = { flowOf(Unit) },
        )

    val failure =
        ServiceStartFailure(
            code = "SVC001",
            title = "Port unavailable",
            description = "Port 52731 is already in use.",
            attemptedPort = 52731,
            occurredAt = Instant.parse("2026-05-27T10:00:00Z"),
            technicalDetail = "java.net.BindException: Address already in use",
        )
    failureFlow.value = failure
    advanceUntilIdle()
    assertEquals(failure, latest.value.startFailure)

    failureFlow.value = null
    advanceUntilIdle()
    assertEquals(null, latest.value.startFailure)
    latest.job.cancel()
  }
}

private data class LatestServerState(
    val valueFlow: MutableStateFlow<ServerState>,
    val sessionCountFlow: MutableStateFlow<Int>,
    val jobCountFlow: MutableStateFlow<Int>,
    val loadedModelIdFlow: MutableStateFlow<String?>,
    val job: Job,
) {
  val value: ServerState
    get() = valueFlow.value
}

private fun kotlinx.coroutines.CoroutineScope.collectLatestServerState(
    portFlow: MutableStateFlow<Int?>,
    startedAtFlow: MutableStateFlow<Instant?>,
    sessionCountFlow: MutableStateFlow<Int>,
    jobCountFlow: MutableStateFlow<Int>,
    loadedModelIdFlow: MutableStateFlow<String?>,
    startFailureFlow: MutableStateFlow<ServiceStartFailure?>,
    nowProvider: () -> Instant = Instant::now,
    tickerFlowFactory: () -> kotlinx.coroutines.flow.Flow<Unit>,
): LatestServerState {
  val latest = MutableStateFlow(ServerState())
  val job =
      launch(EmptyCoroutineContext) {
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
            .collect { latest.value = it }
      }
  return LatestServerState(
      valueFlow = latest,
      sessionCountFlow = sessionCountFlow,
      jobCountFlow = jobCountFlow,
      loadedModelIdFlow = loadedModelIdFlow,
      job = job,
  )
}
