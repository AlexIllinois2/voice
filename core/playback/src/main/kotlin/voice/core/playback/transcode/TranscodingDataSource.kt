package voice.core.playback.transcode

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.runBlocking
import voice.core.playback.CurrentMediaUriHolder
import voice.core.transcode.TranscodeManager

/**
 * Serves uris whose audio format is not supported by ExoPlayer (like WMA) from a transcoded
 * file, and everything else from the fallback [DataSource].
 *
 * The conversion runs while the player is in the buffering state, which may take a while for
 * the first play of a file.
 */
internal class TranscodingDataSource(
  private val transcodeManager: TranscodeManager,
  private val fallbackFactory: DataSource.Factory,
  private val currentMediaUriHolder: CurrentMediaUriHolder,
) : DataSource {

  private val listeners = mutableListOf<TransferListener>()
  private var delegate: DataSource? = null

  override fun addTransferListener(transferListener: TransferListener) {
    listeners.add(transferListener)
    delegate?.addTransferListener(transferListener)
  }

  override fun open(dataSpec: DataSpec): Long {
    val uri = dataSpec.uri
    val dataSource: DataSource
    val effectiveSpec: DataSpec
    if (transcodeManager.isTranscodeNeeded(uri)) {
      // When buffering an item different from the one currently playing, this is the
      // automatic chapter transition, so the conversion runs in auto mode (non
      // intrusive UI). Null falls back to manual, the safe default for first playback.
      val auto = currentMediaUriHolder.uri?.let { it != uri } ?: false
      val converted = runBlocking {
        transcodeManager.ensureConverted(uri, auto = auto)
      }
      dataSource = FileDataSource()
      effectiveSpec = dataSpec.buildUpon()
        .setUri(Uri.fromFile(converted))
        .build()
    } else {
      dataSource = fallbackFactory.createDataSource()
      effectiveSpec = dataSpec
    }
    listeners.forEach(dataSource::addTransferListener)
    delegate = dataSource
    return dataSource.open(effectiveSpec)
  }

  override fun read(
    buffer: ByteArray,
    offset: Int,
    readLength: Int,
  ): Int {
    return checkNotNull(delegate) { "open() not called" }.read(buffer, offset, readLength)
  }

  override fun getUri(): Uri? = delegate?.uri

  override fun getResponseHeaders(): Map<String, List<String>> {
    return delegate?.responseHeaders ?: emptyMap()
  }

  override fun close() {
    delegate?.close()
    delegate = null
  }
}

internal class TranscodingDataSourceFactory(
  private val fallbackFactory: DataSource.Factory,
  private val transcodeManager: TranscodeManager,
  private val currentMediaUriHolder: CurrentMediaUriHolder,
) : DataSource.Factory {

  override fun createDataSource(): DataSource {
    return TranscodingDataSource(transcodeManager, fallbackFactory, currentMediaUriHolder)
  }
}
