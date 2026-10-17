package voice.core.scanner

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AdtsDurationReaderTest {

  @Test
  fun `constant bitrate frames are counted`() = runTest {
    // 100 frames of 1024 samples at 44100 Hz
    val bytes = adtsFrames(frames = 100, frameLength = 200, sampleRateIndex = 4)

    val duration = readDuration(bytes)

    assertEquals(
      expected = 100 * 1024 * 1_000_000L / 44100,
      actual = duration,
    )
  }

  @Test
  fun `variable bitrate frames are counted`() = runTest {
    // frames of different sizes; only the count matters for the duration
    val bytes = adtsFrames(frameLengths = listOf(300, 120, 1000, 250), sampleRateIndex = 4)

    val duration = readDuration(bytes)

    assertEquals(
      expected = 4 * 1024 * 1_000_000L / 44100,
      actual = duration,
    )
  }

  @Test
  fun `multiple raw data blocks are counted`() = runTest {
    // two raw data blocks per frame double the samples per frame
    val bytes = adtsFrames(frames = 10, frameLength = 200, sampleRateIndex = 7, rawDataBlocks = 1)

    val duration = readDuration(bytes)

    assertEquals(
      expected = 10 * 2048 * 1_000_000L / 22050,
      actual = duration,
    )
  }

  @Test
  fun `an id3 tag in front is skipped`() = runTest {
    val id3 = ByteArray(128).also { bytes ->
      "ID3".encodeToByteArray().copyInto(bytes)
    }
    val bytes = id3 + adtsFrames(frames = 10, frameLength = 200, sampleRateIndex = 4)

    val duration = readDuration(bytes)

    assertEquals(
      expected = 10 * 1024 * 1_000_000L / 44100,
      actual = duration,
    )
  }

  @Test
  fun `garbage has no duration`() = runTest {
    val bytes = ByteArray(2048) { 0x55 }

    assertNull(readDuration(bytes))
  }

  @Test
  fun `an empty stream has no duration`() = runTest {
    assertNull(
      AdtsDurationReader.readDuration(
        uri = "test://stream".toUri(),
        dataSourceFactory = emptyDataSourceFactory(),
      )?.inWholeMicroseconds,
    )
  }

  private suspend fun readDuration(bytes: ByteArray): Long? {
    return AdtsDurationReader.readDuration(
      uri = "test://stream".toUri(),
      dataSourceFactory = { ByteArrayDataSource(bytes) },
    )?.inWholeMicroseconds
  }

  // ByteArrayDataSource does not accept an empty array, so an empty stream is faked
  private fun emptyDataSourceFactory(): DataSource.Factory {
    return DataSource.Factory {
      object : DataSource {
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec) = 0L
        override fun read(buffer: ByteArray, offset: Int, length: Int) = C.RESULT_END_OF_INPUT
        override fun getUri(): Uri = Uri.EMPTY
        override fun close() = Unit
      }
    }
  }

  private fun adtsFrames(
    frames: Int,
    frameLength: Int,
    sampleRateIndex: Int,
    rawDataBlocks: Int = 0,
  ): ByteArray {
    return adtsFrames(
      frameLengths = List(frames) { frameLength },
      sampleRateIndex = sampleRateIndex,
      rawDataBlocks = rawDataBlocks,
    )
  }

  private fun adtsFrames(
    frameLengths: List<Int>,
    sampleRateIndex: Int,
    rawDataBlocks: Int = 0,
  ): ByteArray {
    val stream = ByteArrayOutputStream()
    frameLengths.forEach { frameLength ->
      val frame = ByteArray(frameLength)
      // sync word, MPEG-4, layer 00, no CRC
      frame[0] = 0xFF.toByte()
      frame[1] = 0xF1.toByte()
      // profile AAC-LC (01), sampling frequency index, single channel
      frame[2] = ((0b01 shl 6) or (sampleRateIndex shl 2) or 0b0).toByte()
      // channel config low bits, frame length high bits
      frame[3] = ((0b01 shl 6) or ((frameLength shr 11) and 0x3)).toByte()
      frame[4] = ((frameLength shr 3) and 0xFF).toByte()
      // frame length low bits, buffer fullness high bits
      frame[5] = (((frameLength and 0x7) shl 5) or 0b00011).toByte()
      // buffer fullness low bits, raw data blocks
      frame[6] = (rawDataBlocks and 0x3).toByte()
      stream.write(frame)
    }
    return stream.toByteArray()
  }
}
