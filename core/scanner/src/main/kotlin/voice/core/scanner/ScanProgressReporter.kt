package voice.core.scanner

/**
 * Reports the progress of the background scan outside of the app, e.g. as a notification.
 *
 * The parsing of the chapter durations can take a long time and keeps running while the user
 * uses the library, so it is reported to the system as well.
 */
public interface ScanProgressReporter {

  /**
   * Reports the current [progress] of the background scan.
   */
  public fun report(progress: ScanProgress)

  /**
   * The background scan is done, so the progress can be removed again.
   */
  public fun dismiss()
}
