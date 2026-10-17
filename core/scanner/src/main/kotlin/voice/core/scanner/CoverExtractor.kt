package voice.core.scanner

import android.content.Context
import android.net.Uri
import androidx.media3.common.FileTypes
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.inspector.MetadataRetriever
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.await
import voice.core.logging.api.Logger
import voice.core.scanner.matroska.MatroskaCoverExtractor
import voice.core.zip.ZipArchiveProvider
import voice.core.zip.ZipCancelledException
import voice.core.zip.ZipUriCodec
import java.io.File

@Inject
internal class CoverExtractor(
  private val context: Context,
  private val matroskaCoverExtractor: MatroskaCoverExtractor,
  private val zipArchiveProvider: ZipArchiveProvider,
) {

  suspend fun extractCover(
    input: Uri,
    outputFile: File,
  ): Boolean {
    val fileType = FileTypes.inferFileTypeFromUri(input)
    val extension = (input.path ?: "").substringAfterLast(delimiter = ".", missingDelimiterValue = "").lowercase()
    if (fileType == FileTypes.MATROSKA || extension == "mka" || extension == "mkv") {
      return matroskaCoverExtractor.extract(input, outputFile)
    }

    val trackGroups = retrieveMetadata(input)
      ?: return false

    repeat(trackGroups.length) { trackGroupIndex ->
      val trackGroup = trackGroups[trackGroupIndex]
      repeat(trackGroup.length) { formatIndex ->
        val format = trackGroup.getFormat(formatIndex)
        val metadata = format.metadata
        if (metadata != null) {
          repeat(metadata.length()) { metadataIndex ->
            when (val entry = metadata.get(metadataIndex)) {
              is ApicFrame -> {
                Logger.w("Found image frame in ${trackGroup.type}")
                outputFile.outputStream().use { output ->
                  output.write(entry.pictureData)
                }
                return true
              }
              is PictureFrame -> {
                Logger.w("Found image frame in ${trackGroup.type}")
                outputFile.outputStream().use { output ->
                  output.write(entry.pictureData)
                }
                return true
              }
              else -> {
                Logger.v("Unknown metadata entry: $entry")
              }
            }
          }
        }
      }
    }
    return false
  }

  private suspend fun retrieveMetadata(uri: Uri): TrackGroupArray? {
    return try {
      if (ZipUriCodec.isZipUri(uri)) {
        // the default media source factory does not know the zip scheme, so the entry is
        // streamed out of the archive instead
        zipArchiveProvider.withEntryDataSource(uri) { dataSourceFactory ->
          retrieveTrackGroups(uri, dataSourceFactory)
        }
      } else {
        retrieveTrackGroups(uri, dataSourceFactory = null)
      }
    } catch (e: ZipCancelledException) {
      // the password prompt got cancelled, so don't ask a second time
      null
    } catch (e: Exception) {
      if (e is CancellationException) currentCoroutineContext().ensureActive()
      Logger.w(e, "Error retrieving metadata")
      null
    }
  }

  private suspend fun retrieveTrackGroups(
    uri: Uri,
    dataSourceFactory: DataSource.Factory?,
  ): TrackGroupArray {
    val builder = MetadataRetriever.Builder(context, MediaItem.fromUri(uri))
    if (dataSourceFactory != null) {
      builder.setMediaSourceFactory(mediaSourceFactory(context = context, dataSourceFactory = dataSourceFactory))
    }
    return builder
      .build()
      .retrieveTrackGroups()
      .await()
  }
}
