package voice.core.scanner

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.FileTypes
import androidx.media3.common.MediaItem
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.inspector.MetadataRetriever
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.await
import voice.core.data.MarkData
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.nameWithoutExtension
import voice.core.logging.api.Logger
import voice.core.scanner.matroska.MatroskaMetaDataExtractor
import voice.core.scanner.matroska.MatroskaParseException
import voice.core.scanner.mp4.Mp4ChapterExtractor
import voice.core.zip.ZipArchiveProvider
import voice.core.zip.ZipCancelledException
import voice.core.zip.ZipUriCodec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

@Inject
internal class MediaAnalyzer(
  private val context: Context,
  private val mp4ChapterExtractor: Mp4ChapterExtractor,
  private val matroskaExtractorFactory: MatroskaMetaDataExtractor.Factory,
  private val zipArchiveProvider: ZipArchiveProvider,
) {

  suspend fun analyze(file: CachedDocumentFile): Metadata? {
    if (!ZipUriCodec.isZipUri(file.uri)) {
      return analyzeWith(file, dataSourceFactory = null)
    }

    // Formats that keep their metadata at the end of the file (mp4 and matroska) are read
    // randomly. Streaming them would inflate the entry again for every single seek, so they are
    // buffered as a whole instead.
    if (extensionOf(file) in RANDOM_ACCESS_EXTENSIONS) {
      val bytes = runCatching { zipArchiveProvider.readEntryBytes(file.uri) }
        .onFailure { error -> logOrRethrow(error, "Failed to read zip entry ${file.uri}") }
        .getOrNull() ?: return null
      return analyzeWith(file, DataSource.Factory { ByteArrayDataSource(bytes) }, zipBytes = bytes)
    }

    // Streaming the entry instead of inflating it into memory first keeps scanning fast: for the
    // duration and the metadata of a file only a small part of it is needed.
    val streamed = runCatching {
      zipArchiveProvider.withEntryDataSource(file.uri) { dataSourceFactory ->
        analyzeWith(file, dataSourceFactory)
      }
    }
      .onFailure { error -> logOrRethrow(error, "Failed to stream zip entry ${file.uri}") }
    streamed.getOrNull()?.let { return it }
    // the password prompt got cancelled, so don't ask a second time
    if (streamed.exceptionOrNull() is ZipCancelledException) return null

    val bytes = runCatching { zipArchiveProvider.readEntryBytes(file.uri) }
      .onFailure { error -> logOrRethrow(error, "Failed to read zip entry ${file.uri}") }
      .getOrNull() ?: return null
    return analyzeWith(file, DataSource.Factory { ByteArrayDataSource(bytes) }, zipBytes = bytes)
  }

  private fun extensionOf(file: CachedDocumentFile): String {
    return (file.name ?: "")
      .substringAfterLast(delimiter = ".", missingDelimiterValue = "")
      .lowercase()
  }

  private fun logOrRethrow(
    error: Throwable,
    message: String,
  ) {
    if (error is CancellationException) throw error
    Logger.w(error, message)
  }

  private suspend fun analyzeWith(
    file: CachedDocumentFile,
    dataSourceFactory: DataSource.Factory?,
    zipBytes: ByteArray? = null,
  ): Metadata? {
    val builder = Metadata.Builder(file.nameWithoutExtension())
    var duration = retrieveDuration(file.uri, dataSourceFactory)
    if (duration == null || duration <= Duration.ZERO) {
      // media3 only knows the duration of a constant bitrate AAC stream, so the frames of
      // variable bitrate files (which audiobooks usually are) have to be counted ourselves
      duration = adtsDurationUs(file.uri, dataSourceFactory)
    }
    if (duration == null) {
      Logger.w("Could not read the duration of ${file.uri}")
      return null
    }
    if (duration <= Duration.ZERO) {
      Logger.w("Duration is zero or negative for file: ${file.uri}")
      return null
    }

    val trackGroups = retrieveMetadata(file.uri, dataSourceFactory)
    if (trackGroups == null) {
      Logger.w("Could not read the metadata of ${file.uri}")
      return null
    }

    repeat(trackGroups.length) { trackGroupsIndex ->
      val trackGroup = trackGroups[trackGroupsIndex]
      if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
        repeat(trackGroup.length) { formatIndex ->
          val format = trackGroup.getFormat(formatIndex)
          format.metadata?.let { metadata ->
            repeat(metadata.length()) { metadataIndex ->
              when (val entry = metadata.get(metadataIndex)) {
                is TextInformationFrame -> visitText(entry, builder)
                is ChapterFrame -> visitChapter(entry, builder)
                is VorbisComment -> visitVorbis(entry, builder)
                is MdtaMetadataEntry -> visitMdta(entry, builder)
                else -> Logger.d("Unknown metadata entry: $entry")
              }
            }
          }
        }
      }
    }

    val fileType = FileTypes.inferFileTypeFromUri(file.uri)
    val extension = extensionOf(file)
    if (fileType == FileTypes.MP4 || extension == "mp4" || extension == "m4a" || extension == "m4b") {
      parseMp4Chapters(file, builder, dataSourceFactory ?: DefaultDataSource.Factory(context))
    }
    if (fileType == FileTypes.MATROSKA || extension == "mka" || extension == "mkv") {
      // matroska needs random access, so entries inside of a zip have to be buffered
      val bytes = zipBytes ?: if (ZipUriCodec.isZipUri(file.uri)) {
        runCatching { zipArchiveProvider.readEntryBytes(file.uri) }
          .onFailure { Logger.w(it, "Failed to read zip entry ${file.uri}") }
          .getOrNull()
      } else {
        null
      }
      parseMatroskaMetaData(file, builder, bytes)
    }

    return builder.build(duration)
  }

  private fun parseMatroskaMetaData(
    file: CachedDocumentFile,
    builder: Metadata.Builder,
    zipBytes: ByteArray?,
  ) {
    try {
      val extractor = if (zipBytes != null) {
        matroskaExtractorFactory.create(zipBytes)
      } else {
        matroskaExtractorFactory.create(file.uri)
      }
      extractor.use {
        val mediaInfo = it.readMediaInfo()
        builder.chapters.addAll(mediaInfo.chapters)
        builder.artist = builder.artist ?: mediaInfo.artist
        builder.album = builder.album ?: mediaInfo.album
        builder.title = builder.title ?: mediaInfo.title
      }
    } catch (e: MatroskaParseException) {
      Logger.w(e, "Error parsing Matroska metadata")
    }
  }

  private suspend fun parseMp4Chapters(
    file: CachedDocumentFile,
    builder: Metadata.Builder,
    dataSourceFactory: DataSource.Factory,
  ) {
    val chapters = mp4ChapterExtractor.extractChapters(file.uri, dataSourceFactory)
    builder.chapters += chapters
  }

  private fun visitMdta(
    entry: MdtaMetadataEntry,
    builder: Metadata.Builder,
  ) {
    when (entry.key) {
      "com.apple.quicktime.title" -> {
        builder.title = entry.value.toString(Charsets.UTF_8)
      }
      "com.apple.quicktime.artist" -> {
        builder.artist = entry.value.toString(Charsets.UTF_8)
      }
      "com.apple.quicktime.album" -> {
        builder.album = entry.value.toString(Charsets.UTF_8)
      }
    }
  }

  private fun visitVorbis(
    entry: VorbisComment,
    builder: Metadata.Builder,
  ) {
    val key = entry.key
    val value = entry.value
    when {
      key == "ARTIST" -> builder.artist = value
      key == "ALBUM" -> builder.album = value
      key == "TITLE" -> builder.title = value
      key.startsWith("CHAPTER") -> {
        val withoutPrefix = key.removePrefix("CHAPTER")
        val isName = withoutPrefix.endsWith("NAME")
        if (isName) {
          val index = withoutPrefix.removeSuffix("NAME").toIntOrNull()
          if (index != null) {
            builder.vorbisChapterNames[index] = value
          }
        } else {
          val index = withoutPrefix.toIntOrNull()
          if (index != null) {
            val duration = parseVorbisDuration(value)
            if (duration != null) {
              builder.vorbisChapterStarts[index] = duration.inWholeMilliseconds
            }
          }
        }
      }
      else -> Logger.d("Unknown comment name: ${entry.key}, value: $value")
    }
  }

  private fun visitChapter(
    entry: ChapterFrame,
    builder: Metadata.Builder,
  ) {
    repeat(entry.subFrameCount) { subFrameIndex ->
      val subFrame = entry.getSubFrame(subFrameIndex)
      if (subFrame is TextInformationFrame) {
        builder.chapters.add(MarkData(startMs = entry.startTimeMs.toLong(), name = subFrame.values.first()))
      }
    }
  }

  private fun visitText(
    entry: TextInformationFrame,
    builder: Metadata.Builder,
  ) {
    val value = entry.values.first()
    when (entry.id) {
      "TIT2" -> builder.title = value
      "TPE1" -> builder.artist = value
      "TALB" -> builder.album = value
      "TCON" -> builder.genre = value
      "TCOM" -> builder.narrator = value
      "TXXX" -> when (entry.description) {
        "MVNM" -> builder.series = value
        "MVIN" -> builder.part = if (builder.part.isNullOrBlank()) value else builder.part
        "TXXX:PART" -> builder.part = value
        "TXXX:NARRATOR" -> builder.narrator = if (builder.narrator.isNullOrBlank()) value else builder.narrator
        else -> Logger.v("Unknown TXXX frame description:  ${entry.description}, value: $value")
      }
      "TRCK", "TYER", "TSSE" -> {}
      else -> Logger.v("Unknown frame ID: ${entry.id}, value: $value")
    }
  }

  private suspend fun retrieveMetadata(
    uri: Uri,
    dataSourceFactory: DataSource.Factory? = null,
  ): TrackGroupArray? {
    return try {
      MetadataRetriever.Builder(context, MediaItem.fromUri(uri))
        .setMediaSourceFactory(mediaSourceFor(dataSourceFactory))
        .build()
        .use {
          it.retrieveTrackGroups().await()
        }
    } catch (e: Exception) {
      if (e is CancellationException) currentCoroutineContext().ensureActive()
      Logger.w(e, "Error retrieving metadata")
      null
    }
  }

  private suspend fun adtsDurationUs(
    uri: Uri,
    dataSourceFactory: DataSource.Factory?,
  ): Duration? {
    val factory = dataSourceFactory ?: DefaultDataSource.Factory(context)
    return AdtsDurationReader.readDuration(uri, factory)
  }

  private suspend fun retrieveDuration(
    uri: Uri,
    dataSourceFactory: DataSource.Factory? = null,
  ): Duration? {
    return try {
      MetadataRetriever.Builder(context, MediaItem.fromUri(uri))
        .setMediaSourceFactory(mediaSourceFor(dataSourceFactory))
        .build()
        .use {
          it.retrieveDurationUs().await().microseconds
        }
    } catch (e: Exception) {
      if (e is CancellationException) currentCoroutineContext().ensureActive()
      Logger.w(e, "Error retrieving duration")
      null
    }
  }

  private fun mediaSourceFor(dataSourceFactory: DataSource.Factory?): DefaultMediaSourceFactory {
    return if (dataSourceFactory == null) {
      DefaultMediaSourceFactory(context, DefaultExtractorsFactory())
    } else {
      mediaSourceFactory(context = context, dataSourceFactory = dataSourceFactory)
    }
  }

  private companion object {
    // the mp4 family and matroska keep their metadata at the end of the file, so they are read
    // randomly and cannot be streamed out of a zip entry efficiently
    val RANDOM_ACCESS_EXTENSIONS = setOf("mp4", "m4a", "m4b", "mka", "mkv")
  }
}
