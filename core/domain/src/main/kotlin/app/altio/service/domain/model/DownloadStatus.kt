/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.model

enum class DownloadStatus {
  QUEUED,
  DOWNLOADING,
  PAUSED,
  VERIFYING,
  SUCCESS,
  FAILED,
  CANCELLED,
}
