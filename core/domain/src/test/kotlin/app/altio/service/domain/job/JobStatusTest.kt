/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.job

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JobStatusTest {

  // ─── isCancellable ────────────────────────────────────────────────────────

  @Test
  fun `QUEUED is cancellable`() {
    assertTrue(JobStatus.QUEUED.isCancellable())
  }

  @Test
  fun `RUNNING is cancellable`() {
    assertTrue(JobStatus.RUNNING.isCancellable())
  }

  @Test
  fun `COMPLETED is not cancellable`() {
    assertFalse(JobStatus.COMPLETED.isCancellable())
  }

  @Test
  fun `FAILED is not cancellable`() {
    assertFalse(JobStatus.FAILED.isCancellable())
  }

  @Test
  fun `CANCELLED is not cancellable`() {
    assertFalse(JobStatus.CANCELLED.isCancellable())
  }

  // ─── isTerminal ───────────────────────────────────────────────────────────

  @Test
  fun `COMPLETED is terminal`() {
    assertTrue(JobStatus.COMPLETED.isTerminal())
  }

  @Test
  fun `FAILED is terminal`() {
    assertTrue(JobStatus.FAILED.isTerminal())
  }

  @Test
  fun `CANCELLED is terminal`() {
    assertTrue(JobStatus.CANCELLED.isTerminal())
  }

  @Test
  fun `QUEUED is not terminal`() {
    assertFalse(JobStatus.QUEUED.isTerminal())
  }

  @Test
  fun `RUNNING is not terminal`() {
    assertFalse(JobStatus.RUNNING.isTerminal())
  }

  // ─── invariants ──────────────────────────────────────────────────────────

  @Test
  fun `terminal statuses are never cancellable`() {
    val terminalButCancellable = JobStatus.entries.filter { it.isTerminal() && it.isCancellable() }
    assertTrue(
        terminalButCancellable.isEmpty(),
        "Terminal statuses must not be cancellable: $terminalButCancellable",
    )
  }

  @Test
  fun `every status is either active or terminal`() {
    // No status should be in an unknown limbo where neither flag is set
    val neither = JobStatus.entries.filter { !it.isTerminal() && !it.isCancellable() }
    assertTrue(neither.isEmpty(), "All non-terminal statuses must be cancellable: $neither")
  }
}
