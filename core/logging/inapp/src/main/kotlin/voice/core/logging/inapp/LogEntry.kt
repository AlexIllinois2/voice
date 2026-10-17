package voice.core.logging.inapp

import voice.core.logging.api.Logger

/**
 * A single log entry that is kept in memory to be displayed in the log screen.
 */
public data class LogEntry(
  val severity: Logger.Severity,
  val timestamp: Long,
  val message: String,
)
