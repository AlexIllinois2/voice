package voice.core.scanner

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookContentRepo
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.FileBasedDocumentFile
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class BookParserTest {

  @get:Rule
  val testFolder = TemporaryFolder()

  private val contentRepo = mockk<BookContentRepo>()
  private val fileFactory = mockk<CachedDocumentFileFactory>()
  private val parser = BookParser(
    contentRepo = contentRepo,
    fileFactory = fileFactory,
  )

  init {
    coEvery { contentRepo.get(any()) } returns null
    coEvery { contentRepo.put(any()) } returns Unit
  }

  @Test
  fun folderBookUsesFolderNameWhenAlbumMissing() {
    val bookFolder = testFolder.newFolder("My Audiobook")
    val chapters = listOf(
      chapter(File(bookFolder, "1.mp3").apply { createNewFile() }),
      chapter(File(bookFolder, "2.mp3").apply { createNewFile() }),
    )

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFolder.toUri()),
      analyzed = metadata(album = null, title = "First Chapter Title"),
      file = FileBasedDocumentFile(bookFolder),
    )

    assertEquals(expected = "My Audiobook", actual = content.name)
  }

  @Test
  fun folderBookWithSingleChapterStillUsesFolderName() {
    val bookFolder = testFolder.newFolder("Harry Potter 3")
    val chapters = listOf(chapter(File(bookFolder, "track01.mp3").apply { createNewFile() }))

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFolder.toUri()),
      analyzed = metadata(album = null, title = "Track Title"),
      file = FileBasedDocumentFile(bookFolder),
    )

    assertEquals(expected = "Harry Potter 3", actual = content.name)
  }

  @Test
  fun singleFileBookUsesFileName() {
    val bookFile = testFolder.newFile("book.mp3")
    val chapters = listOf(chapter(bookFile))

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFile.toUri()),
      analyzed = metadata(album = null, title = "The Title"),
      file = FileBasedDocumentFile(bookFile),
    )

    assertEquals(expected = "book", actual = content.name)
  }

  @Test
  fun albumDoesNotNameTheBook() {
    val bookFolder = testFolder.newFolder("Folder Name")
    val chapters = listOf(
      chapter(File(bookFolder, "1.mp3").apply { createNewFile() }),
      chapter(File(bookFolder, "2.mp3").apply { createNewFile() }),
    )

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFolder.toUri()),
      analyzed = metadata(album = "Album Name", title = "First Chapter Title"),
      file = FileBasedDocumentFile(bookFolder),
    )

    assertEquals(expected = "Folder Name", actual = content.name)
  }

  @Test
  fun missingMetadataFallsBackToFolderName() {
    val bookFolder = testFolder.newFolder("Fallback Folder")
    val chapters = listOf(
      chapter(File(bookFolder, "1.mp3").apply { createNewFile() }),
      chapter(File(bookFolder, "2.mp3").apply { createNewFile() }),
    )

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFolder.toUri()),
      analyzed = null,
      file = FileBasedDocumentFile(bookFolder),
    )

    assertEquals(expected = "Fallback Folder", actual = content.name)
  }

  @Test
  fun fillMetadataUpdatesMetadataWithoutRenaming() = runTest {
    val bookFolder = testFolder.newFolder("My Audiobook")
    val bookId = BookId(bookFolder.toUri())
    every { fileFactory.create(bookId.toUri()) } returns FileBasedDocumentFile(bookFolder)
    coEvery { contentRepo.get(bookId) } returns content(bookId, name = "My Audiobook")

    parser.fillMetadata(bookId, metadata(album = "Album Name", title = "Chapter Title", artist = "Author"))

    coVerify {
      contentRepo.put(
        match {
          it.name == "My Audiobook" && it.author == "Author"
        },
      )
    }
  }

  @Test
  fun fillMetadataDoesNotNameSingleFileBookAfterChapterTitle() = runTest {
    val bookFile = testFolder.newFile("book.mp3")
    val bookId = BookId(bookFile.toUri())
    every { fileFactory.create(bookId.toUri()) } returns FileBasedDocumentFile(bookFile)
    coEvery { contentRepo.get(bookId) } returns content(bookId, name = "book")

    parser.fillMetadata(bookId, metadata(album = null, title = "The Title"))

    coVerify(exactly = 0) { contentRepo.put(any()) }
  }

  @Test
  fun fillMetadataKeepsFolderNameWhenOnlyTitleIsKnown() = runTest {
    val bookFolder = testFolder.newFolder("My Audiobook")
    val bookId = BookId(bookFolder.toUri())
    every { fileFactory.create(bookId.toUri()) } returns FileBasedDocumentFile(bookFolder)
    coEvery { contentRepo.get(bookId) } returns content(bookId, name = "My Audiobook")

    parser.fillMetadata(bookId, metadata(album = null, title = "Chapter Title"))

    coVerify(exactly = 0) { contentRepo.put(any()) }
  }

  @Test
  fun fillMetadataKeepsRenamedBook() = runTest {
    val bookFolder = testFolder.newFolder("My Audiobook")
    val bookId = BookId(bookFolder.toUri())
    every { fileFactory.create(bookId.toUri()) } returns FileBasedDocumentFile(bookFolder)
    coEvery { contentRepo.get(bookId) } returns content(bookId, name = "My Custom Name")

    parser.fillMetadata(bookId, metadata(album = "Album Name", title = "Chapter Title", artist = "Author"))

    coVerify(exactly = 0) { contentRepo.put(any()) }
  }

  @Test
  fun zipBookUsesArchiveNameInsteadOfFirstChapterTitle() {
    val bookFile = testFolder.newFile("老友记音频.zip")
    val chapters = listOf(chapter(bookFile))

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFile.toUri()),
      analyzed = metadata(album = null, title = "S01E01"),
      file = FileBasedDocumentFile(bookFile),
    )

    assertEquals(expected = "老友记音频", actual = content.name)
  }

  @Test
  fun zipBookIgnoresAlbum() {
    val bookFile = testFolder.newFile("老友记音频.zip")
    val chapters = listOf(chapter(bookFile))

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFile.toUri()),
      analyzed = metadata(album = "老友记", title = "S01E01"),
      file = FileBasedDocumentFile(bookFile),
    )

    assertEquals(expected = "老友记音频", actual = content.name)
  }

  @Test
  fun fillMetadataKeepsArchiveNameForZipBooks() = runTest {
    val bookFile = testFolder.newFile("老友记音频.zip")
    val bookId = BookId(bookFile.toUri())
    every { fileFactory.create(bookId.toUri()) } returns FileBasedDocumentFile(bookFile)
    coEvery { contentRepo.get(bookId) } returns content(bookId, name = "老友记音频")

    parser.fillMetadata(bookId, metadata(album = null, title = "S01E01"))

    coVerify(exactly = 0) { contentRepo.put(any()) }
  }

  @Test
  fun fillMetadataKeepsRenamedZipBook() = runTest {
    val bookFile = testFolder.newFile("老友记音频.zip")
    val bookId = BookId(bookFile.toUri())
    every { fileFactory.create(bookId.toUri()) } returns FileBasedDocumentFile(bookFile)
    coEvery { contentRepo.get(bookId) } returns content(bookId, name = "Friends")

    parser.fillMetadata(bookId, metadata(album = null, title = "S01E01"))

    coVerify(exactly = 0) { contentRepo.put(any()) }
  }

  @Test
  fun parseAndStoreNamesNewZipBookAfterTheArchive() = runTest {
    val bookFile = testFolder.newFile("老友记音频.zip")
    val bookId = BookId(bookFile.toUri())
    val chapters = listOf(chapter(bookFile, name = "S01E01"))

    val content = parser.parseAndStore(chapters, FileBasedDocumentFile(bookFile))

    assertEquals(expected = "老友记音频", actual = content.name)
    assertEquals(expected = bookId, actual = content.id)
  }

  @Test
  fun parseAndStoreRepairsZipBookNamedAfterItsFirstChapter() = runTest {
    val bookFile = testFolder.newFile("老友记音频.zip")
    val bookId = BookId(bookFile.toUri())
    coEvery { contentRepo.get(bookId) } returns content(bookId, name = "S01E01")
    val chapters = listOf(chapter(bookFile, name = "S01E01"))

    val content = parser.parseAndStore(chapters, FileBasedDocumentFile(bookFile))

    assertEquals(expected = "老友记音频", actual = content.name)
    coVerify { contentRepo.put(match { it.name == "老友记音频" }) }
  }

  @Test
  fun parseAndStoreKeepsRenamedZipBook() = runTest {
    val bookFile = testFolder.newFile("老友记音频.zip")
    val bookId = BookId(bookFile.toUri())
    coEvery { contentRepo.get(bookId) } returns content(bookId, name = "Friends")
    val chapters = listOf(chapter(bookFile, name = "S01E01"))

    val content = parser.parseAndStore(chapters, FileBasedDocumentFile(bookFile))

    assertEquals(expected = "Friends", actual = content.name)
    coVerify(exactly = 0) { contentRepo.put(any()) }
  }

  private fun chapter(
    file: File,
    name: String = "Chapter",
  ): Chapter = Chapter(
    id = ChapterId(file.toUri()),
    name = name,
    duration = 1000L,
    fileLastModified = Instant.EPOCH,
    markData = emptyList(),
    fileSize = 0,
  )

  private fun metadata(
    album: String?,
    title: String?,
    artist: String? = null,
  ): Metadata = Metadata(
    duration = 1000L,
    artist = artist,
    album = album,
    title = title,
    fileName = "file",
    chapters = emptyList(),
    genre = null,
    narrator = null,
    series = null,
    part = null,
  )

  private fun content(id: BookId, name: String): BookContent {
    val chapterId = ChapterId("chapter")
    return BookContent(
      id = id,
      playbackSpeed = 1F,
      skipSilence = false,
      isActive = true,
      lastPlayedAt = Instant.EPOCH,
      author = null,
      name = name,
      addedAt = Instant.EPOCH,
      chapters = listOf(chapterId),
      currentChapter = chapterId,
      positionInChapter = 0L,
      cover = null,
      gain = 0F,
      genre = null,
      narrator = null,
      series = null,
      part = null,
    )
  }
}
