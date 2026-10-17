package voice.core.scanner

import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookContentRepoImpl
import voice.core.data.repo.BookRepositoryImpl
import voice.core.data.repo.ChapterRepoImpl
import voice.core.data.repo.internals.AppDb
import voice.core.data.toUri
import voice.core.documentfile.FileBasedDocumentFactory
import voice.core.documentfile.FileBasedDocumentFile
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MediaScannerTest {

  @Test
  fun singleFileDeletion() = test {
    val audiobookFolder = folder("audiobooks")

    val book1 = File(audiobookFolder, "book1")
    val book1Chapters = listOf(
      audioFile(book1, "1.mp3"),
      audioFile(book1, "2.mp3"),
      audioFile(book1, "10.mp3"),
    )

    scan(FolderType.Root, audiobookFolder)

    book1Chapters.first().delete()

    scan(FolderType.Root, audiobookFolder)

    assertBookContents(
      BookContentView(
        id = book1,
        chapters = book1Chapters.drop(1),
      ),
    )
  }

  @Test
  fun metadataPreservedOnDeletion() = test {
    val audiobookFolder = folder("audiobooks")

    val book1 = File(audiobookFolder, "book1")
    val book1Id = BookId(book1.toUri())
    val book1Chapters = listOf(
      audioFile(book1, "1.mp3"),
      audioFile(book1, "2.mp3"),
      audioFile(book1, "10.mp3"),
    )

    scan(FolderType.Root, audiobookFolder)

    val contentWithPositionAtLastChapter =
      bookContentRepo.get(BookId(book1.toUri()))!!.copy(currentChapter = ChapterId(book1Chapters.last().toUri()))
    bookContentRepo.put(contentWithPositionAtLastChapter)

    book1Chapters.forEach { it.toUri().toFile().delete() }

    scan(FolderType.Root, audiobookFolder)

    audioFile(book1, "1.mp3")
    audioFile(book1, "2.mp3")
    audioFile(book1, "10.mp3")

    assertEquals(expected = contentWithPositionAtLastChapter, actual = bookContentRepo.get(book1Id))
  }

  @Test
  fun multipleRoots() = test {
    val audiobookFolder1 = folder("audiobooks1")

    val topFileBook = audioFile(parent = audiobookFolder1, "test.mp3")

    val book1 = File(audiobookFolder1, "book1")
    val book1Chapters = listOf(
      audioFile(book1, "1.mp3"),
      audioFile(book1, "2.mp3"),
      audioFile(book1, "10.mp3"),
    )

    val audiobookFolder2 = folder("audiobooks1")

    val book2 = File(audiobookFolder2, "book2")
    val book2Chapters = listOf(audioFile(book2, "1.mp3"))

    scan(FolderType.Root, audiobookFolder1, audiobookFolder2)

    assertBookContents(
      BookContentView(topFileBook, chapters = listOf(topFileBook)),
      BookContentView(book1, chapters = book1Chapters),
      BookContentView(book2, chapters = book2Chapters),
    )
  }

  @Test
  fun scanRoot() = test {
    val audiobookFolder = folder("audiobooks1")

    val topFileBook = audioFile(parent = audiobookFolder, "test.mp3")

    val book1 = File(audiobookFolder, "book1")
    val book1Chapters = listOf(
      audioFile(book1, "1.mp3"),
      audioFile(book1, "2.mp3"),
      audioFile(book1, "10.mp3"),
    )

    val book2 = File(audiobookFolder, "book2")
    val book2Chapters = listOf(
      audioFile(book2, "1.mp3"),
      audioFile(book2, "2.mp3"),
      audioFile(book2, "10.mp3"),
    )

    scan(FolderType.Root, audiobookFolder)

    assertBookContents(
      BookContentView(topFileBook, chapters = listOf(topFileBook)),
      BookContentView(book1, chapters = book1Chapters),
      BookContentView(book2, chapters = book2Chapters),
    )
  }

  @Test
  fun scanSingleFile() = test {
    val book = audioFile(parent = folder("audiobooks1"), "test.mp3")
    scan(FolderType.SingleFile, book)
    assertBookContents(
      BookContentView(book, chapters = listOf(book)),
    )
  }

  @Test
  fun scanSingleFolder() = test {
    val folder = folder("book")
    val book = audioFile(parent = folder, "test.mp3")
    scan(FolderType.SingleFolder, folder)
    assertBookContents(
      BookContentView(folder, chapters = listOf(book)),
    )
  }

  @Test
  fun newBookIsListedWithoutAnalyzingChapters() = test {
    val folder = folder("book")
    audioFile(parent = folder, "1.mp3")
    audioFile(parent = folder, "2.mp3")

    scan(FolderType.SingleFolder, folder)

    // the initial scan only lists the files; the durations are backfilled in the background
    val book = bookRepo.all().single()
    assertEquals(expected = listOf(0L, 0L), actual = book.chapters.map { it.duration })
    assertEquals(expected = "book", actual = book.content.name)
    assertEquals(
      expected = listOf(ScanProgress.Phase.Scanning),
      actual = scanProgress.map { it.phase }.distinct(),
    )
  }

  @Test
  fun scanAuthor() = test {
    val audioBooks = folder("audiobooks")

    val book1 = audioFile(parent = audioBooks, "test.mp3")

    val book2 = audioFile(parent = audioBooks, "author1/test.mp3")

    val book3 = File(audioBooks, "author1/book1")
    val book3Chapter1 = audioFile(parent = book3, "c1.mp3")
    val book3Chapter2 = audioFile(parent = book3, "c2.mp3")

    val book4 = File(audioBooks, "author1/book2")
    val book4Chapter1 = audioFile(book4, "a.mp3")

    scan(FolderType.Author, audioBooks)
    assertBookContents(
      BookContentView(book1, chapters = listOf(book1)),
      BookContentView(book2, chapters = listOf(book2)),
      BookContentView(book3, chapters = listOf(book3Chapter1, book3Chapter2)),
      BookContentView(book4, chapters = listOf(book4Chapter1)),
    )
  }

  @Test
  fun scanAuthorParsesEachBookOnce() = test {
    val audioBooks = folder("audiobooks")

    audioFile(parent = audioBooks, "test.mp3")
    audioFile(parent = audioBooks, "author1/test.mp3")
    audioFile(parent = File(audioBooks, "author1/book1"), "c1.mp3")
    audioFile(parent = File(audioBooks, "author1/book1"), "c2.mp3")
    audioFile(parent = File(audioBooks, "author1/book2"), "a.mp3")

    scan(FolderType.Author, audioBooks)

    assertEquals(expected = 4, actual = scannedBooks.size)
  }

  @Test
  fun scanReportsProgress() = test {
    val audiobookFolder = folder("audiobooks")

    val book1 = File(audiobookFolder, "book1")
    val book1Chapters = listOf(
      audioFile(book1, "1.mp3"),
      audioFile(book1, "2.mp3"),
      audioFile(book1, "10.mp3"),
    )

    val book2 = File(audiobookFolder, "book2")
    audioFile(book2, "1.mp3")

    scan(FolderType.Root, audiobookFolder)

    val book1Progress = scanProgress.filter { it.bookName == "book1" }
    assertEquals(expected = 1, actual = book1Progress.first().bookIndex)
    assertEquals(expected = 2, actual = book1Progress.first().bookCount)
    // the files are only listed during the scan, so the last progress just knows the total count
    assertEquals(expected = book1Chapters.size, actual = book1Progress.last().entryCount)
    assertEquals(expected = 0, actual = book1Progress.last().entryIndex)

    val book2Progress = scanProgress.filter { it.bookName == "book2" }
    assertEquals(expected = 2, actual = book2Progress.first().bookIndex)
    assertEquals(expected = 2, actual = book2Progress.first().bookCount)
    assertEquals(expected = 1, actual = book2Progress.last().entryCount)
    assertEquals(expected = 0, actual = book2Progress.last().entryIndex)
  }

  @Test
  fun rescanOfKnownLibraryReportsNoProgress() = test {
    val folder = folder("book")
    audioFile(parent = folder, "1.mp3")
    audioFile(parent = folder, "2.mp3")
    scan(FolderType.SingleFolder, folder)
    scanProgress.clear()

    // returning to the library scans again to pick up added or removed files, but when there is
    // nothing to report the scan stays quiet instead of flashing a progress indicator
    scan(FolderType.SingleFolder, folder)

    assertEquals(expected = emptyList(), actual = scanProgress)
    assertEquals(expected = 2, actual = bookRepo.all().single().chapters.size)
  }

  private fun test(test: suspend TestEnvironment.() -> Unit) {
    runTest {
      TestEnvironment().use { test(it) }
    }
  }

  private class TestEnvironment : Closeable {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java)
      .allowMainThreadQueries()
      .build()
    val bookContentRepo = BookContentRepoImpl(db.bookContentDao())
    private val chapterRepo = ChapterRepoImpl(db.chapterDao())
    private val scannedRepo = ScannedBooksRecordingRepo(bookContentRepo)
    val scannedBooks: List<BookId> get() = scannedRepo.scannedBooks
    private val scanner = MediaScanner(
      contentRepo = scannedRepo,
      chapterParser = ChapterParser(
        chapterRepo = chapterRepo,
        zipArchiveProvider = mockk(),
      ),
      bookParser = BookParser(
        contentRepo = bookContentRepo,
        fileFactory = FileBasedDocumentFactory,
      ),
      deviceHasPermissionBug = mockk(),
    )

    val bookRepo = BookRepositoryImpl(chapterRepo, bookContentRepo)

    // the enricher parses files in parallel, so progress may be emitted from several threads
    val scanProgress: MutableList<ScanProgress> = java.util.Collections.synchronizedList(mutableListOf<ScanProgress>())

    private val root: File = Files.createTempDirectory(this::class.java.canonicalName!!).toFile()

    suspend fun scan(
      type: FolderType = FolderType.Root,
      vararg roots: File,
    ) {
      scanner.scan(mapOf(type to roots.map(::FileBasedDocumentFile))) { progress ->
        scanProgress += progress
      }
    }

    @IgnorableReturnValue
    fun audioFile(
      parent: File,
      name: String,
    ): File {
      check(name.endsWith(".mp3"))
      return File(parent, name)
        .also {
          it.parentFile?.mkdirs()
          check(it.createNewFile())
        }
    }

    fun folder(name: String): File {
      return File(root, name)
        .also { it.mkdirs() }
    }

    suspend fun assertBookContents(vararg expected: BookContentView) {
      bookRepo.all()
        .map {
          BookContentView(
            id = it.id.toUri().toFile(),
            chapters = it.content.chapters.map { chapter ->
              chapter.toUri().toFile()
            },
          )
        }
        .let { actual ->
          assertEquals(
            expected = expected.sortedBy { it.id },
            actual = actual.sortedBy { it.id },
          )
        }
    }

    override fun close() {
      root.delete()
    }
  }

  data class BookContentView(
    val id: File,
    val chapters: List<File>,
  )

  private class ScannedBooksRecordingRepo(private val delegate: BookContentRepo) : BookContentRepo by delegate {

    val scannedBooks = mutableListOf<BookId>()

    override suspend fun setAllInactiveExcept(ids: List<BookId>) {
      scannedBooks += ids
      delegate.setAllInactiveExcept(ids)
    }
  }
}
