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
    /**
     * True when the conversion was triggered by the automatic chapter transition buffering, so
     * the previous chapter keeps playing and the UI may only show a non intrusive hint. False
     * when the user actively waits for this file, so the UI may show a blocking overlay.
     */
    val auto: Boolean,
  ) : TranscodeState

  data class Failed(
    val fileName: String,
    val message: String,
  ) : TranscodeState

  companion object {
    val STEP_COUNT = Step.entries.size
  }
}
