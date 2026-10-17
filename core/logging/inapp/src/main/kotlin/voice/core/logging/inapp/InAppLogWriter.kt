package voice.core.logging.inapp

import dev.zacsweers.metro.Inject
import voice.core.logging.api.Logger
import voice.core.logging.api.LogWriter

/**
 * A [LogWriter] that keeps warnings and errors in the [LogStore] so that the user can inspect
 * them. Only warnings and errors are kept, everything below is just noise for diagnosing.
 */
@Inject
public class InAppLogWriter(private val logStore: LogStore) : LogWriter {

  override fun log(
    severity: Logger.Severity,
    message: String,
    throwable: Throwable?,
  ) {
    if (severity !in LOGGED_SEVERITIES) return
    logStore.add(
      LogEntry(
        severity = severity,
        timestamp = System.currentTimeMillis(),
        message = message,
      ),
    )
  }

  private companion object {
    val LOGGED_SEVERITIES = setOf(Logger.Severity.Warn, Logger.Severity.Error)
  }
}
