/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SessionManagementScreenTest {

  @Test
  fun sessionManagement_showsSessionAndJobMetadata() = runComposeUiTest {
    setContent {
      SessionManagementScreen(
          state =
              SessionManagementState(
                  sessions =
                      listOf(
                          ManagedSessionItem(
                              sessionId = "session-1234567890",
                              appName = "Demo App",
                              modelId = "gemma-4-e2b-it",
                              messageCount = 4,
                              createdAt = Instant.now(),
                              lastActiveAt = Instant.now(),
                          )
                      ),
                  activeJobs =
                      listOf(
                          ManagedJobItem(
                              jobId = "job-1234567890",
                              sessionId = "session-1234567890",
                              appName = "Demo App",
                              type = JobType.GENERATE,
                              status = JobStatus.RUNNING,
                              createdAt = Instant.now(),
                          )
                      ),
              ),
          onCancelSession = {},
          onCancelJob = {},
      )
    }

    onNodeWithText("Sessions & Jobs").assertIsDisplayed()
    onNodeWithText("Demo App").assertIsDisplayed()
    onNodeWithText("Cancel Session").assertIsDisplayed()
    onNodeWithText("Cancel Job").assertIsDisplayed()
  }

  @Test
  fun sessionManagement_hidesJobActionsWhenNoActiveJobsRemain() = runComposeUiTest {
    setContent {
      SessionManagementScreen(
          state =
              SessionManagementState(
                  sessions = emptyList(),
                  activeJobs = emptyList(),
              ),
          onCancelSession = {},
          onCancelJob = {},
      )
    }

    onNodeWithText("No queued or running jobs need attention.").assertIsDisplayed()
    onAllNodesWithText("Cancel Job").assertCountEquals(0)
  }
}
