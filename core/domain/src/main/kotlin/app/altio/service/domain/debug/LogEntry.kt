/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.domain.debug

data class LogEntry(
    val timestamp: Long, // System.currentTimeMillis()
    val priority: Int, // android.util.Log constants (VERBOSE=2, DEBUG=3, INFO=4, WARN=5, ERROR=6)
    val tag: String?,
    val message: String,
)
