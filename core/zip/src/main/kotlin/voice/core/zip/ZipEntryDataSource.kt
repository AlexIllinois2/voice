package voice.core.zip

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InputStream

/**
 * Serves a single zip entry by decompressing only the part that actually gets read.
 *
 * The alternative (extracting the whole entry into memory first) is what makes scanning books that
 * consist of many archives take minutes: for reading the duration of an mp3 only a few kilobytes
 * are needed, yet the whole file would be inflated.
 */
internal class ZipEntryDataSource(
  private val uri: Uri,
  private val archive: ZipArchive,
  private val entryPath: String,
) : DataSource {

  private var inputStream: InputStream? = null
  private var buffered: ByteArray? = null
  private var bufferPosition = 0
  private var bytesRemaining = 0L

  override fun addTransferListener(transferListener: TransferListener) = Unit

  override fun open(dataSpec: DataSpec): Long {
    close()
    val size = archive.sizeOf(entryPath)
    if (size < 0) {
      // The central directory does not know the uncompressed size, so the entry has to be
      // buffered in order to be able to report a length at all.
      val bytes = archive.openStream(entryPath).use { it.readBytes() }
      buffered = bytes
      bytesRemaining = resolveLength(available = bytes.size.toLong(), dataSpec = dataSpec)
      bufferPosition = dataSpec.position.toInt()
      return bytesRemaining
    }

    val stream = archive.openStream(entryPath)
    try {
      if (dataSpec.position > 0) {
        skip(stream, dataSpec.position)
      }
      bytesRemaining = resolveLength(available = size, dataSpec = dataSpec)
    } catch (e: IOException) {
      stream.close()
      throw e
    }
    inputStream = stream
    return bytesRemaining
  }

  override fun read(
    buffer: ByteArray,
    offset: Int,
    readLength: Int,
  ): Int {
    if (readLength == 0) return 0
    if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
    val toRead = minOf(readLength.toLong(), bytesRemaining).toInt()

    val bytes = buffered
    val read = if (bytes != null) {
      val available = bytes.size - bufferPosition
      if (available <= 0) {
        return C.RESULT_END_OF_INPUT
      }
      val count = minOf(toRead, available)
      System.arraycopy(bytes, bufferPosition, buffer, offset, count)
      bufferPosition += count
      count
    } else {
      checkNotNull(inputStream) { "open() has not been called" }.read(buffer, offset, toRead)
    }
    if (read < 0) return C.RESULT_END_OF_INPUT
    bytesRemaining -= read
    return read
  }

  override fun getUri(): Uri? = uri

  override fun close() {
    try {
      inputStream?.close()
    } catch (_: IOException) {
    }
    inputStream = null
    buffered = null
    bufferPosition = 0
    bytesRemaining = 0L
  }

  private fun resolveLength(
    available: Long,
    dataSpec: DataSpec,
  ): Long {
    val position = dataSpec.position
    if (position > available) {
      throw IOException("Position $position is out of bounds for $uri")
    }
    return if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
      available - position
    } else {
      dataSpec.length
    }
  }

  private fun skip(
    stream: InputStream,
    bytes: Long,
  ) {
    var remaining = bytes
    val skipBuffer = ByteArray(SKIP_BUFFER_SIZE)
    while (remaining > 0) {
      val toRead = minOf(remaining, SKIP_BUFFER_SIZE.toLong()).toInt()
      val read = stream.read(skipBuffer, 0, toRead)
      if (read < 0) {
        throw IOException("Unexpected end of $uri while skipping to $bytes")
      }
      remaining -= read
    }
  }

  private companion object {
    const val SKIP_BUFFER_SIZE = 64 * 1024
  }
}
