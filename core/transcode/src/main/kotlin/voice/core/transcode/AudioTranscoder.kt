package voice.core.transcode

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import voice.core.logging.api.Logger
import java.io.File
import kotlin.coroutines.resume

class TranscodeException(message: String) : Exception(message)

/**
 * Thin suspend wrapper around ffmpeg-kit.
 */
internal class AudioTranscoder {

  /**
   * Transcodes [input] to AAC in an m4a container, writing the result to [output].
   *
   * [onProgress] is invoked with the progress in percent while transcoding, or null when the
   * duration of the input is unknown.
   */
  suspend fun transcode(
    input: File,
    output: File,
    onProgress: (Int?) -> Unit,
  ) = withContext(Dispatchers.IO) {
    val totalMs = FFprobeKit.getMediaInformation(input.absolutePath)
      ?.mediaInformation
      ?.duration
      ?.toDoubleOrNull()
      ?.let { (it * 1000).toLong() }
      ?.takeIf { it > 0 }
    // the input timestamps of these broken asf files reset again and again, so the progress
    // is derived from the size of the output file instead of from statistics.time
    val expectedOutputBytes = totalMs?.let { it * TRANSCODE_BIT_RATE_BYTES_PER_MS }

    val session = suspendCancellableCoroutine { continuation ->
      FFmpegKit.executeWithArgumentsAsync(
        arrayOf(
          "-y",
          "-i",
          input.absolutePath,
          "-vn",
          "-c:a",
          "aac",
          "-b:a",
          TRANSCODE_BIT_RATE,
          // the part extension is meaningless to ffmpeg, so the m4a muxer is chosen explicitly
          "-f",
          "ipod",
          output.absolutePath,
        ),
        { session -> continuation.resume(session) },
        null,
        { statistics ->
          val expected = expectedOutputBytes
          if (expected != null && statistics.size > 0) {
            onProgress(((statistics.size * 100) / expected).toInt().coerceIn(0, 99))
          } else {
            onProgress(null)
          }
        },
      )
    }
    if (!ReturnCode.isSuccess(session.returnCode)) {
      val logs = session.allLogsAsString?.takeLast(MAX_LOG_LENGTH) ?: ""
      Logger.w("Transcoding ${input.name} failed with ${session.returnCode}: $logs")
      throw TranscodeException("Transcoding ${input.name} failed with return code ${session.returnCode}")
    }
    onProgress(100)
  }

  /**
   * Reads the duration of [file] in milliseconds, or null when it can not be probed.
   */
  suspend fun probeDurationMs(file: File): Long? = withContext(Dispatchers.IO) {
    val session = FFprobeKit.getMediaInformation(file.absolutePath)
    session.mediaInformation?.duration?.toDoubleOrNull()?.let { (it * 1000).toLong() }
  }

  private companion object {
    const val TRANSCODE_BIT_RATE = "96k"
    const val TRANSCODE_BIT_RATE_BYTES_PER_MS = 12L
    const val MAX_LOG_LENGTH = 2000
  }
}
