package voice.core.playback.zip

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import voice.core.zip.ZipUriCodec

/**
 * Dispatches to a zip-aware [DataSource] for `zip://` uris and to the default one otherwise.
 */
internal class SchemeDispatchingDataSource(
  private val defaultFactory: DataSource.Factory,
  private val zipFactory: DataSource.Factory,
) : DataSource {

  private val listeners = mutableListOf<TransferListener>()
  private var delegate: DataSource? = null

  override fun addTransferListener(transferListener: TransferListener) {
    listeners.add(transferListener)
    delegate?.addTransferListener(transferListener)
  }

  override fun open(dataSpec: DataSpec): Long {
    val dataSource = if (ZipUriCodec.isZipUri(dataSpec.uri)) {
      zipFactory.createDataSource()
    } else {
      defaultFactory.createDataSource()
    }
    listeners.forEach(dataSource::addTransferListener)
    delegate = dataSource
    return dataSource.open(dataSpec)
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

internal class SchemeDispatchingDataSourceFactory(
  private val defaultFactory: DataSource.Factory,
  private val zipFactory: DataSource.Factory,
) : DataSource.Factory {

  override fun createDataSource(): DataSource {
    return SchemeDispatchingDataSource(defaultFactory, zipFactory)
  }
}
