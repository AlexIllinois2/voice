package voice.core.transcode

/**
 * The current state of the transcoding pipeline, exposed so the UI can show progress and
 * errors instead of leaving the user with an unresponsive looking player.
 */
sealed interface TranscodeState {

  enum class Step {
    ReadingSource,
    Transcoding,
    Saving,
  }

  data class Running(
    val fileName: String,
    val step: Step,
    /**
     * The progress of the current step in percent, or null when the step has no measurable
     * progress.
     */
    val progressPercent: Int?,
  ) : TranscodeState

  data class Failed(
    val fileName: String,
    val message: String,
  ) : TranscodeState

  companion object {
    val STEP_COUNT = Step.entries.size
  }
}
