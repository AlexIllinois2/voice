package voice.core.scanner

import dev.zacsweers.metro.Inject
import voice.core.common.comparator.NaturalOrderComparator
import voice.core.data.BookId
import voice.core.data.folders.FolderType
import voice.core.data.isAudioFile
import voice.core.data.repo.BookContentRepo
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.walk
import voice.core.logging.api.Logger

@Inject
internal class MediaScanner(
  private val contentRepo: BookContentRepo,
  private val chapterParser: ChapterParser,
  private val bookParser: BookParser,
  private val deviceHasPermissionBug: DeviceHasStoragePermissionBug,
) {

  suspend fun scan(
    folders: Map<FolderType, List<CachedDocumentFile>>,
    onProgress: (ScanProgress) -> Unit,
  ) {
    val files = folders.flatMap { (folderType, files) ->
      when (folderType) {
        FolderType.SingleFile, FolderType.SingleFolder -> {
          files
        }
        FolderType.Root -> {
          files.flatMap { file ->
            file.children
          }
        }
        FolderType.Author -> {
          files.flatMap { folder ->
            folder.children.flatMap { author ->
              if (author.isFile) {
                listOf(author)
              } else {
                author.children
              }
            }
          }
        }
      }
    }

    contentRepo.setAllInactiveExcept(files.map { BookId(it.uri) })

    val probeFile = folders.values.flatten().findProbeFile()
    if (probeFile != null) {
      if (deviceHasPermissionBug.checkForBugAndSet(probeFile)) {
        Logger.w("Device has permission bug, aborting scan! Probed $probeFile")
        return
      }
    }

    files
      // sorting by name keeps the order stable. Counting the audio files instead would mean
      // walking the whole tree of every book up front, which is expensive for big libraries.
      .sortedWith(compareBy(NaturalOrderComparator.stringComparator) { it.name ?: it.uri.toString() })
      .forEachIndexed { index, file ->
        scan(file, bookIndex = index + 1, bookCount = files.size, onProgress = onProgress)
      }
  }

  private suspend fun scan(
    file: CachedDocumentFile,
    bookIndex: Int,
    bookCount: Int,
    onProgress: (ScanProgress) -> Unit,
  ) {
    val bookName = file.bookDisplayName()
    // A book that is already known is only listed again to pick up files that were added or
    // removed, which usually finds nothing at all. Reporting that as progress would flash a
    // progress indicator on every rescan, so only a new book is announced.
    val isNew = contentRepo.get(BookId(file.uri)) == null
    if (isNew) {
      onProgress(ScanProgress(bookName = bookName, bookIndex = bookIndex, bookCount = bookCount))
    }
    // listing the files is cheap; the durations are parsed in the background afterwards
    val chapters = chapterParser.listChapters(file) { progress ->
      if (isNew) {
        onProgress(progress.copy(bookName = bookName, bookIndex = bookIndex, bookCount = bookCount))
      }
    }
    if (chapters.isEmpty()) return

    val content = bookParser.parseAndStore(chapters, file)

    val chapterIds = chapters.map { it.id }
    val currentChapterGone = content.currentChapter !in chapterIds
    val currentChapter = if (currentChapterGone) chapterIds.first() else content.currentChapter
    val positionInChapter = if (currentChapterGone) 0 else content.positionInChapter
    val updated = content.copy(
      chapters = chapterIds,
      currentChapter = currentChapter,
      positionInChapter = positionInChapter,
      isActive = true,
    )
    if (content != updated) {
      validateIntegrity(updated, chapters)
      contentRepo.put(updated)
    }
  }

  private fun List<CachedDocumentFile>.findProbeFile(): CachedDocumentFile? {
    return asSequence().flatMap { it.walk() }
      .firstOrNull { child ->
        child.isAudioFile() && child.uri.authority == "com.android.externalstorage.documents"
      }
  }
}
