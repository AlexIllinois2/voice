package voice.core.scanner

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

/**
 * Determines the duration of an ADTS (AAC) stream by counting its frames.
 *
 * media3 only knows the duration of an ADTS stream when constant bitrate seeking is explicitly
 * enabled and the stream actually is constant bitrate, so it reports no duration for the variable
 * bitrate AAC files that audiobooks are usually encoded with. Counting the frames is exact for
 * both: every frame of an AAC-LC stream carries 1024 samples (2048 when the sampling frequency
 * index signals implicit SBR), and the frame length in the header leads to the next one, so the
 * stream can be parsed with a single sequential pass.
 */
internal object AdtsDurationReader {

  suspend fun readDuration(uri: Uri, dataSourceFactory: DataSource.Factory): Duration? {
    val dataSource = dataSourceFactory.createDataSource()
    return try {
      dataSource.open(DataSpec(uri))
      withContext(Dispatchers.IO) {
        parse(dataSource)
      }
    } catch (e: Exception) {
      null
    } finally {
      dataSource.close()
    }
  }

  private suspend fun parse(dataSource: DataSource): Duration? {
    val window = Window(dataSource)
    var frames = 0L
    var samplesPerFrame = 0
    var sampleRate = 0
    while (window.findSync()) {
      val header = window.peekHeader() ?: run {
        window.advance(count = 1)
        continue
      }
      if (sampleRate == 0) {
        sampleRate = header.sampleRate
        samplesPerFrame = header.samplesPerFrame
      }
      frames++
      window.advance(count = header.frameLength)
    }
    if (frames == 0L || sampleRate <= 0 || samplesPerFrame <= 0) return null
    return (frames * samplesPerFrame * 1_000_000L / sampleRate).microseconds
  }

  private class Header(
    val frameLength: Int,
    val sampleRate: Int,
    val samplesPerFrame: Int,
  )

  /**
   * A sliding window over a [DataSource] that allows peeking a few bytes ahead and advancing over
   * bytes that have been inspected.
   */
  private class Window(private val dataSource: DataSource) {

    private val buffer = ByteArray(WINDOW_SIZE)
    private var start = 0
    private var end = 0
    private var exhausted = false

    /** Moves the cursor forward by [count] bytes, stopping at the end of the stream. */
    fun advance(count: Int) {
      var remaining = count
      while (remaining > 0) {
        val available = end - start
        if (available == 0) {
          if (!fill()) return
          continue
        }
        val consumed = minOf(available, remaining)
        start += consumed
        remaining -= consumed
      }
    }

    /**
     * Advances the cursor to the next ADTS sync word. Returns `false` if the stream ended before
     * one was found.
     */
    fun findSync(): Boolean {
      var offset = 0
      while (true) {
        val first = peek(offset)
        if (first == -1) return false
        if (first == 0xFF) {
          val second = peek(offset + 1)
          if (second == -1) return false
          if (second and 0xF0 == 0xF0) {
            advance(offset)
            return true
          }
        }
        offset++
      }
    }

    /** Parses the ADTS header at the cursor, or returns `null` if it is not a valid one. */
    fun peekHeader(): Header? {
      val first = peek(0)
      val second = peek(1)
      val third = peek(2)
      val fourth = peek(3)
      val fifth = peek(4)
      val sixth = peek(5)
      val seventh = peek(6)
      if (listOf(first, second, third, fourth, fifth, sixth, seventh).any { it == -1 }) {
        return null
      }
      if (first != 0xFF || second and 0xF0 != 0xF0) return null
      // layer 00 is required for ADTS; anything else is a different (MPEG audio) format
      if ((second shr 1) and 0x3 != 0) return null
      val protectionAbsent = second and 0x1
      val sampleRateIndex = (third shr 2) and 0xF
      if (sampleRateIndex > SAMPLE_RATES.size - 1) return null
      val channelConfig = ((third and 0x1) shl 2) or ((fourth shr 6) and 0x3)
      if (channelConfig == 0) return null
      val frameLength = ((fourth and 0x3) shl 11) or ((fifth and 0xFF) shl 3) or ((sixth shr 5) and 0x7)
      val rawDataBlocks = seventh and 0x3
      val headerLength = if (protectionAbsent == 1) 7 else 9
      if (frameLength < headerLength + 1) return null
      val samplesPerFrame = when (sampleRateIndex) {
        // an index of 13, 14 or 15 signals implicit SBR, which outputs twice the samples per frame
        13, 14, 15 -> 2048
        else -> 1024
      } * (rawDataBlocks + 1)
      return Header(
        frameLength = frameLength,
        sampleRate = SAMPLE_RATES[sampleRateIndex],
        samplesPerFrame = samplesPerFrame,
      )
    }

    /** The byte [offset] bytes ahead of the cursor, or -1 if the stream has ended. */
    private fun peek(offset: Int): Int {
      while (start + offset >= end) {
        if (!fill()) return -1
      }
      return buffer[start + offset].toInt() and 0xFF
    }

    private fun fill(): Boolean {
      if (exhausted) return false
      if (start > 0) {
        System.arraycopy(buffer, start, buffer, 0, end - start)
        end -= start
        start = 0
      }
      val read = dataSource.read(buffer, end, buffer.size - end)
      if (read == C.RESULT_END_OF_INPUT) {
        exhausted = true
        return false
      }
      end += read
      return read > 0
    }

    private companion object {
      const val WINDOW_SIZE = 64 * 1024
    }
  }

  private val SAMPLE_RATES = intArrayOf(
    96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350,
  )
}
