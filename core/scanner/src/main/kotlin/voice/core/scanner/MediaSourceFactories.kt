package voice.core.scanner

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory

/**
 * Creates a [DefaultMediaSourceFactory] that reads the media through [dataSourceFactory].
 *
 * A factory is created per call because retrieving metadata happens in parallel and the
 * default one would otherwise be shared. We use a custom [DefaultMediaSourceFactory] because
 * the default one for the retriever also extracts the covers.
 */
internal fun mediaSourceFactory(
  context: Context,
  dataSourceFactory: DataSource.Factory,
): DefaultMediaSourceFactory {
  return DefaultMediaSourceFactory(context, DefaultExtractorsFactory())
    .setDataSourceFactory(dataSourceFactory)
}
