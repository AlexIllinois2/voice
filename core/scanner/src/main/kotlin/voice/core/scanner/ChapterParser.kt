package voice.core.scanner

import dev.zacsweers.metro.Inject
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.isAudioFile
import voice.core.data.repo.ChapterRepo
import voice.core.data.repo.getOrPut
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.nameWithoutExtension
import voice.core.zip.ZipArchiveProvider
import java.time.Instant

@Inject
internal class ChapterParser(
  private val chapterRepo: ChapterRepo,
  private val zipArchiveProvider: ZipArchiveProvider,
) {

  /**
   * Collects the audio files of [documentFile] and persists them as chapters without parsing
   * their duration or metadata. That keeps the initial scan fast: books show up in the library
   * right away and the durations are backfilled later by the
   * [ChapterEnricher][voice.core.scanner.ChapterEnricher].
   *
   * Chapters that are already known and unchanged are reused, so their parsed duration survives.
   */
  suspend fun listChapters(
    documentFile: CachedDocumentFile,
    onProgress: (ScanProgress) -> Unit,
  ): List<Chapter> {
    val audioFiles = mutableListOf<CachedDocumentFile>()
    collectAudioFiles(documentFile, audioFiles, onProgress)
    onProgress(ScanProgress(entryCount = audioFiles.size))

    return audioFiles
      .map { file ->
        val id = ChapterId(file.uri)
        chapterRepo.getOrPut(
          id = id,
          lastModified = Instant.ofEpochMilli(file.lastModified),
          fileSize = file.length,
        ) {
          Chapter(
            id = id,
            name = file.nameWithoutExtension(),
            duration = 0L,
            fileLastModified = Instant.ofEpochMilli(file.lastModified),
            markData = emptyList(),
            fileSize = file.length,
          )
        }
      }
      .filterNotNull()
      .sorted()
  }

  private suspend fun collectAudioFiles(
    file: CachedDocumentFile,
    result: MutableList<CachedDocumentFile>,
    onProgress: (ScanProgress) -> Unit,
  ) {
    if (file.isAudioFile()) {
      result += file
    } else if (file.isDirectory) {
      file.children
        .forEach {
          collectAudioFiles(it, result, onProgress)
        }
    } else {
      zipArchiveProvider.childrenIfZip(file) { name, copiedBytes, totalBytes ->
        onProgress(ScanProgress(archiveCopy = ScanProgress.ArchiveCopy(name, copiedBytes, totalBytes)))
      }
        ?.forEach {
          collectAudioFiles(it, result, onProgress)
        }
    }
  }
}
