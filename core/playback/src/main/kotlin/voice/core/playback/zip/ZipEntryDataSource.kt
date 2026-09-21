package voice.core.playback.zip

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.runBlocking
import voice.core.zip.ZipPlaybackCache
import java.io.IOException

/**
 * Serves zip archive entries by keeping them fully extracted in memory.
 */
internal class ZipEntryDataSource(
  private val playbackCache: ZipPlaybackCache,
) : BaseDataSource(false) {

  private var bytes: ByteArray? = null
  private var uri: Uri? = null
  private var bytesRemaining = 0L
  private var position = 0L

  override fun open(dataSpec: DataSpec): Long {
    transferInitializing(dataSpec)
    val all = runBlocking {
      playbackCache.ensureExtracted(dataSpec.uri)
    }
    uri = dataSpec.uri
    position = dataSpec.position
    bytes = all
    bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
      all.size - dataSpec.position
    } else {
      dataSpec.length
    }
    if (bytesRemaining < 0) {
      throw IOException("DataSpec out of bounds: $dataSpec")
    }
    transferStarted(dataSpec)
    return bytesRemaining
  }

  override fun read(
    buffer: ByteArray,
    offset: Int,
    readLength: Int,
  ): Int {
    if (readLength == 0) return 0
    val bytes = checkNotNull(bytes) { "open() not called" }
    if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
    val toRead = minOf(readLength.toLong(), bytesRemaining).toInt()
    System.arraycopy(bytes, position.toInt(), buffer, offset, toRead)
    position += toRead
    bytesRemaining -= toRead
    bytesTransferred(toRead)
    return toRead
  }

  override fun getUri(): Uri? = uri

  override fun close() {
    uri = null
    bytes = null
    bytesRemaining = 0L
  }
}
