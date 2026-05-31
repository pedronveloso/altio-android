/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.model

enum class DownloadFailureReason {
  /** Worker input data was missing the model ID key. */
  MISSING_MODEL_ID,

  /** Model ID was not found in the local catalog. */
  UNKNOWN_MODEL,

  /** Network failure persisted after all retry attempts were exhausted. */
  NETWORK_ERROR,

  /** Downloaded file SHA-256 did not match the catalog entry. */
  CHECKSUM_MISMATCH,

  /** Download succeeded but the local file could not be finalized on disk. */
  FILE_SYSTEM_ERROR,
}
