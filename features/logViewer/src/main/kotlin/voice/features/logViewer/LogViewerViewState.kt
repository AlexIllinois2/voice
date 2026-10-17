package voice.features.logViewer

import voice.core.logging.api.Logger

public data class LogViewerViewState(
  val entries: List<LogEntryViewState>,
) {

  public data class LogEntryViewState(
    val key: Int,
    val severity: Logger.Severity,
    val time: String,
    val message: String,
  )
}
