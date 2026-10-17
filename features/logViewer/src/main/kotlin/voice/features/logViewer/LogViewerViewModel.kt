package voice.features.logViewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metro.Inject
import voice.core.logging.api.Logger
import voice.core.logging.inapp.LogEntry
import voice.core.logging.inapp.LogStore
import voice.navigation.Navigator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Inject
public class LogViewerViewModel(
  private val logStore: LogStore,
  private val navigator: Navigator,
) {

  @Composable
  public fun viewState(): LogViewerViewState {
    val entries by logStore.entries.collectAsState()
    return LogViewerViewState(
      // the newest entry is the most interesting one when the user opens the screen
      entries = entries.asReversed().mapIndexed { index, entry ->
        entry.toViewState(key = index)
      },
    )
  }

  public fun close() {
    navigator.goBack()
  }

  /**
   * The current log entries as plain text, oldest first, ready to be saved or shared.
   */
  public fun logText(): String {
    return logStore.entries.value.joinToString(separator = "\n\n") { entry ->
      "${TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestamp))} ${entry.severity}: ${entry.message}"
    }
  }

  private fun LogEntry.toViewState(key: Int): LogViewerViewState.LogEntryViewState {
    return LogViewerViewState.LogEntryViewState(
      key = key,
      severity = severity,
      time = TIME_FORMAT.format(Instant.ofEpochMilli(timestamp)),
      message = message,
    )
  }

  private companion object {
    val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
      .withZone(ZoneId.systemDefault())
  }
}
