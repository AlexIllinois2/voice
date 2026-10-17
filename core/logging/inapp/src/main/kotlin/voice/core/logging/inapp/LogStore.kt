package voice.core.logging.inapp

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Keeps the most recent log entries in memory so that they can be inspected from within the app.
 *
 * Logs are not persisted, so only the entries of the current app session are available.
 */
@SingleIn(AppScope::class)
@Inject
public class LogStore {

  private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

  public val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

  public fun add(entry: LogEntry) {
    _entries.update { entries ->
      (entries + entry).takeLast(MAX_ENTRIES)
    }
  }

  private companion object {
    // 200 entries are plenty to diagnose a failing scan and keep the memory footprint low
    const val MAX_ENTRIES = 200
  }
}
