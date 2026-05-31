/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.model

enum class ModelStatus {
  NOT_DOWNLOADED,
  DOWNLOADING,
  PAUSED,
  VERIFYING,
  READY,
  LOADING,
  LOADED,
}
