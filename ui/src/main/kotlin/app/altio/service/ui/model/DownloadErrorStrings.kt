/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.model

import app.altio.service.domain.model.DownloadFailureReason

internal fun DownloadFailureReason.toUiMessage(): String =
    when (this) {
      DownloadFailureReason.MISSING_MODEL_ID -> "Internal error: missing model identifier."
      DownloadFailureReason.UNKNOWN_MODEL -> "Model not found in the catalog."
      DownloadFailureReason.NETWORK_ERROR ->
          "Network connection failed after several attempts. Check your connection and retry."
      DownloadFailureReason.CHECKSUM_MISMATCH ->
          "Downloaded file is corrupted. Retrying will re-download from scratch."
      DownloadFailureReason.FILE_SYSTEM_ERROR ->
          "Downloaded file could not be stored locally. Free up space and retry."
    }
