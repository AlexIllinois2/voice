package voice.core.scanner

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.isAudioFile
import voice.core.data.repo.ChapterRepo
import voice.core.data.repo.getOrPut
import voice.core.documentfile.CachedDocumentFile
import voice.core.zip.ZipArchiveProvider
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal data class ChapterParseResult(
  val chapters: List<Chapter>,
  val firstChapterMetadata: Metadata?,
)

@Inject
internal class ChapterParser(
  private val chapterRepo: ChapterRepo,
  private val mediaAnalyzer: MediaAnalyzer,
  private val zipArchiveProvider: ZipArchiveProvider,
) {

  suspend fun parse(documentFile: CachedDocumentFile): ChapterParseResult {
    val audioFiles = mutableListOf<CachedDocumentFile>()
    collectAudioFiles(documentFile, audioFiles)

    val analyzedMetadata = ConcurrentHashMap<ChapterId, Metadata>()
    val chapters = coroutineScope {
      audioFiles
        .map { file ->
          // reading the metadata is by far the most expensive part, so it happens in parallel
          async(analysisDispatcher) { parseChapter(file, analyzedMetadata) }
        }
        .awaitAll()
        .filterNotNull()
    }

    val sorted = chapters.sorted()
    return ChapterParseResult(
      chapters = sorted,
      firstChapterMetadata = sorted.firstOrNull()?.let { analyzedMetadata[it.id] },
    )
  }

  private suspend fun parseChapter(
    file: CachedDocumentFile,
    analyzedMetadata: MutableMap<ChapterId, Metadata>,
  ): Chapter? {
    val id = ChapterId(file.uri)
    return chapterRepo.getOrPut(
      id = id,
      lastModified = Instant.ofEpochMilli(file.lastModified),
      fileSize = file.length,
    ) {
      val metaData = mediaAnalyzer.analyze(file) ?: return@getOrPut null
      analyzedMetadata[id] = metaData
      Chapter(
        id = id,
        duration = metaData.duration,
        fileLastModified = Instant.ofEpochMilli(file.lastModified),
        name = metaData.title ?: metaData.fileName,
        markData = metaData.chapters,
        fileSize = file.length,
      )
    }
  }

  private suspend fun collectAudioFiles(
    file: CachedDocumentFile,
    result: MutableList<CachedDocumentFile>,
  ) {
    if (file.isAudioFile()) {
      result += file
    } else if (file.isDirectory) {
      file.children
        .forEach {
          collectAudioFiles(it, result)
        }
    } else {
      zipArchiveProvider.childrenIfZip(file)
        ?.forEach {
          collectAudioFiles(it, result)
        }
    }
  }

  private companion object {
    val analysisDispatcher = Dispatchers.IO.limitedParallelism(4)
  }
}
