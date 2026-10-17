package voice.core.scanner

import dev.zacsweers.metro.Inject
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.getOrPut
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.zip.isZipArchive
import java.time.Instant

@Inject
internal class BookParser(
  private val contentRepo: BookContentRepo,
  private val fileFactory: CachedDocumentFileFactory,
) {

  /**
   * Stores the book for [file] with the already listed [chapters]. The metadata of the book is
   * filled in later by [fillMetadata] once the first chapter has been parsed.
   */
  suspend fun parseAndStore(
    chapters: List<Chapter>,
    file: CachedDocumentFile,
  ): BookContent {
    val id = BookId(file.uri)
    val known = contentRepo.get(id)
    if (known != null) {
      return repairArchiveName(known, chapters, file)
    }
    return contentRepo.getOrPut(id) {
      parse(
        chapters = chapters,
        id = id,
        analyzed = null,
        file = file,
      )
    }
  }

  /**
   * Restores the name of a zip book that an earlier version named after the first chapter of
   * the archive. Books that were renamed by the user are left alone.
   */
  private suspend fun repairArchiveName(
    content: BookContent,
    chapters: List<Chapter>,
    file: CachedDocumentFile,
  ): BookContent {
    val name = file.bookDisplayName()
    if (!file.isZipArchive() || content.name == name) return content
    val firstChapterName = chapters.firstOrNull()?.name ?: return content
    if (content.name != firstChapterName) return content
    val repaired = content.copy(name = name)
    contentRepo.put(repaired)
    return repaired
  }

  /**
   * Fills in the metadata parsed from the first chapter of a book. The name of the book is
   * never touched: it always stays the name of the file or folder it was added with. Books that
   * were renamed by the user are skipped entirely.
   */
  suspend fun fillMetadata(bookId: BookId, metadata: Metadata) {
    val content = contentRepo.get(bookId) ?: return
    val bookFile = fileFactory.create(bookId.toUri())
    if (content.name != bookFile.bookDisplayName()) return
    val updated = content.copy(
      author = metadata.artist ?: content.author,
      genre = metadata.genre ?: content.genre,
      narrator = metadata.narrator ?: content.narrator,
      series = metadata.series ?: content.series,
      part = metadata.part ?: content.part,
    )
    if (updated != content) {
      contentRepo.put(updated)
    }
  }

  fun parse(
    chapters: List<Chapter>,
    id: BookId,
    analyzed: Metadata?,
    file: CachedDocumentFile,
  ): BookContent {
    return BookContent(
      id = id,
      isActive = true,
      addedAt = Instant.now(),
      author = analyzed?.artist,
      lastPlayedAt = Instant.EPOCH,
      name = file.bookDisplayName(),
      playbackSpeed = 1F,
      skipSilence = false,
      chapters = chapters.map { it.id },
      positionInChapter = 0L,
      currentChapter = chapters.first().id,
      cover = null,
      gain = 0F,
      genre = analyzed?.genre,
      narrator = analyzed?.narrator,
      series = analyzed?.series,
      part = analyzed?.part,
    ).also {
      validateIntegrity(it, chapters)
    }
  }
}

internal fun validateIntegrity(
  content: BookContent,
  chapters: List<Chapter>,
) {
  // the init block performs integrity validation
  @Suppress("RETURN_VALUE_NOT_USED")
  Book(content, chapters)
}
