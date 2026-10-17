package voice.core.scanner

import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.MarkData
import voice.core.data.repo.ChapterRepo
import voice.core.data.repo.ZipParseAttemptRepo
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.FileBasedDocumentFile
import voice.core.zip.ZipArchiveProvider
import voice.core.zip.ZipUriCodec
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ChapterEnricherTest {

  @get:Rule
  val testFolder = TemporaryFolder()

  // stands in for the database, so the enricher reads back what it has written
  private val storedChapters = mutableMapOf<ChapterId, Chapter>()

  // the enricher parses files in parallel, so progress may be emitted from several threads
  private val progress: MutableList<ScanProgress> = java.util.Collections.synchronizedList(mutableListOf())

  private val mediaAnalyzer = mockk<MediaAnalyzer>()
  private val chapterRepo = mockk<ChapterRepo>()
  private val bookParser = mockk<BookParser>()
  private val fileFactory = mockk<CachedDocumentFileFactory>()
  private val zipArchiveProvider = mockk<ZipArchiveProvider>()

  // stands in for the persisted store, so a new enricher sees the zips of the previous one
  private val zipParseAttempts = mutableSetOf<String>()
  private val zipParseAttemptRepo = object : ZipParseAttemptRepo {
    override suspend fun attemptedZips(): Set<String> = zipParseAttempts.toSet()

    override suspend fun markAttempted(zipUris: Collection<String>) {
      zipParseAttempts += zipUris
    }
  }

  private val enricher = newEnricher()

  @Before
  fun setUp() {
    coEvery { chapterRepo.get(any()) } coAnswers { storedChapters[firstArg()] }
    coEvery { chapterRepo.put(any()) } coAnswers {
      val chapter: Chapter = firstArg()
      storedChapters[chapter.id] = chapter
    }
    coEvery { bookParser.fillMetadata(any(), any()) } just Runs
    coEvery { mediaAnalyzer.analyze(any()) } returns metadata()
    every { fileFactory.create(any()) } answers {
      val uri: Uri = firstArg()
      FileBasedDocumentFile(uri.toFile())
    }
  }

  @Test
  fun backfillsDurationsAndNames() = runTest {
    val book = book("Book 1", chapterDurations = listOf(0L, 0L))

    enricher.enrich(listOf(book)) { progress += it }

    coVerify(exactly = 2) { mediaAnalyzer.analyze(any()).let { } }
    coVerify(exactly = 2) { chapterRepo.put(match { it.duration == DURATION_MS && it.name == "Parsed Title" }) }
    assertEquals(
      expected = listOf(DURATION_MS, DURATION_MS),
      actual = book.chapters.map { storedChapters.getValue(it.id).duration },
    )
    assertEquals(expected = 2, actual = progress.size)
    assertTrue(progress.all { it.phase == ScanProgress.Phase.Enriching })
    assertEquals(expected = 2, actual = progress.last().entryCount)
    assertEquals(expected = listOf(1, 2), actual = progress.map { it.entryIndex }.sorted())
  }

  @Test
  fun fillsBookMetadataForFirstChapterOfEachBook() = runTest {
    val book1 = book("Book 1", chapterDurations = listOf(0L, 0L))
    val book2 = book("Book 2", chapterDurations = listOf(0L, 0L))

    enricher.enrich(listOf(book1, book2)) { }

    coVerify(exactly = 1) { bookParser.fillMetadata(book1.id, any()) }
    coVerify(exactly = 1) { bookParser.fillMetadata(book2.id, any()) }
  }

  @Test
  fun processesMoreChaptersThanFitInOneBatch() = runTest {
    // the chapters are analyzed in batches, so this spans several of them
    val book1 = book("Book 1", chapterDurations = List(5) { 0L })
    val book2 = book("Book 2", chapterDurations = List(5) { 0L })

    enricher.enrich(listOf(book1, book2)) { progress += it }

    coVerify(exactly = 10) { mediaAnalyzer.analyze(any()).let { } }
    coVerify(exactly = 10) { chapterRepo.put(any()) }
    assertEquals(expected = (1..10).toList(), actual = progress.map { it.entryIndex }.sorted())
    assertTrue(progress.all { it.entryCount == 10 })
  }

  @Test
  fun skipsChaptersThatAlreadyHaveADuration() = runTest {
    val book = book("Book 1", chapterDurations = listOf(5000L, 0L))

    enricher.enrich(listOf(book)) { progress += it }

    // only the chapter that is still missing its duration is parsed
    coVerify(exactly = 1) { mediaAnalyzer.analyze(any()).let { } }
    coVerify(exactly = 0) { bookParser.fillMetadata(any(), any()) }
    assertEquals(expected = 5000L, actual = storedChapters.getValue(book.chapters[0].id).duration)
    assertEquals(expected = DURATION_MS, actual = storedChapters.getValue(book.chapters[1].id).duration)
    assertEquals(expected = 1, actual = progress.size)
  }

  @Test
  fun skipsChaptersThePlayerAlreadyResolved() = runTest {
    val book = book("Book 1", chapterDurations = listOf(0L))
    storedChapters[book.chapters.single().id] = book.chapters.single().copy(duration = 9999L)

    enricher.enrich(listOf(book)) { }

    // the player may have resolved the duration while the chapter was pending
    coVerify(exactly = 0) { mediaAnalyzer.analyze(any()).let { } }
    coVerify(exactly = 0) { chapterRepo.put(any()) }
    assertEquals(expected = 9999L, actual = storedChapters.getValue(book.chapters.single().id).duration)
  }

  @Test
  fun keepsChapterWhenAnalysisFails() = runTest {
    coEvery { mediaAnalyzer.analyze(any()) } returns null
    val book = book("Book 1", chapterDurations = listOf(0L))

    enricher.enrich(listOf(book)) { progress += it }

    coVerify(exactly = 1) { mediaAnalyzer.analyze(any()).let { } }
    coVerify(exactly = 0) { chapterRepo.put(any()) }
    assertEquals(expected = 0L, actual = storedChapters.getValue(book.chapters.single().id).duration)
    // the progress still advances, so the bar keeps moving
    assertEquals(expected = 1, actual = progress.size)
  }

  @Test
  fun pendingCountCountsChaptersWithoutDuration() = runTest {
    val book1 = book("Book 1", chapterDurations = listOf(0L, 1234L))
    val book2 = book("Book 2", chapterDurations = listOf(0L, 0L))

    assertEquals(expected = 3, actual = enricher.pendingCount(listOf(book1, book2)))

    enricher.enrich(listOf(book1, book2)) { }

    // the durations are written to the repo, so once the books are read back from it, as the
    // scanner does on every run, no chapter is missing its duration anymore
    assertEquals(
      expected = 0,
      actual = enricher.pendingCount(
        listOf(
          book1.copy(chapters = book1.chapters.map { storedChapters.getValue(it.id) }),
          book2.copy(chapters = book2.chapters.map { storedChapters.getValue(it.id) }),
        ),
      ),
    )
  }

  @Test
  fun givesUpOnChaptersThatCannotBeParsed() = runTest {
    coEvery { mediaAnalyzer.analyze(any()) } returns null
    val book = book("Book 1", chapterDurations = listOf(0L, 0L))

    // parsing is retried a couple of times, but not on every single scan
    repeat(3) {
      enricher.enrich(listOf(book)) { }
    }
    coVerify(exactly = 6) { mediaAnalyzer.analyze(any()).let { } }
    assertEquals(expected = 0, actual = enricher.pendingCount(listOf(book)))

    enricher.enrich(listOf(book)) { }
    coVerify(exactly = 6) { mediaAnalyzer.analyze(any()).let { } }

    // an explicit restart tries the failed chapters again
    enricher.resetFailures()
    assertEquals(expected = 2, actual = enricher.pendingCount(listOf(book)))
  }

  @Test
  fun resolvesZipEntriesThroughTheArchiveProvider() = runTest {
    val zipUri = File(testFolder.root, "book.zip").toUri()
    val entryUri = ZipUriCodec.encode(zipUri, "Chapter 1.mp3")
    val entryFile = FileBasedDocumentFile(File(testFolder.root, "Chapter 1.mp3"))
    coEvery { zipArchiveProvider.entryFile(entryUri) } returns entryFile
    val chapter = chapter(id = ChapterId(entryUri), name = "Chapter 1", duration = 0L)

    enricher.enrich(listOf(bookOf(BookId(zipUri), listOf(chapter)))) { }

    coVerify { mediaAnalyzer.analyze(entryFile).let { } }
    coVerify { chapterRepo.put(match { it.id == chapter.id && it.duration == DURATION_MS }) }
    // entries inside of a zip are not read through the document file factory
    coVerify(exactly = 0) { fileFactory.create(any()).let { } }
  }

  @Test
  fun `a zip is parsed automatically only once`() = runTest {
    val book = zipBook("Book 1", chapterDurations = listOf(0L, 0L))

    enricher.enrich(listOf(book)) { }
    coVerify(exactly = 2) { mediaAnalyzer.analyze(any()).let { } }

    // scanning again while the library is used must not parse the same zip a second time
    enricher.enrich(listOf(book)) { }
    coVerify(exactly = 2) { mediaAnalyzer.analyze(any()).let { } }
    assertEquals(expected = 0, actual = enricher.pendingCount(listOf(book)))
  }

  @Test
  fun `a zip that failed to parse is not retried automatically`() = runTest {
    coEvery { mediaAnalyzer.analyze(any()) } returns null
    val book = zipBook("Book 1", chapterDurations = listOf(0L))

    enricher.enrich(listOf(book)) { }
    coVerify(exactly = 1) { mediaAnalyzer.analyze(any()).let { } }

    // it does not matter whether the parse succeeded, the zip is not parsed again on its own
    enricher.enrich(listOf(book)) { }
    coVerify(exactly = 1) { mediaAnalyzer.analyze(any()).let { } }
    assertEquals(expected = 0, actual = enricher.pendingCount(listOf(book)))
  }

  @Test
  fun `a failed zip can be retried manually`() = runTest {
    coEvery { mediaAnalyzer.analyze(any()) } returns null
    val book = zipBook("Book 1", chapterDurations = listOf(0L))

    enricher.enrich(listOf(book)) { }

    // the user asks for another try
    coEvery { mediaAnalyzer.analyze(any()) } returns metadata()
    enricher.enrich(listOf(book), mode = ChapterEnricher.Mode.Retry) { }

    coVerify(exactly = 2) { mediaAnalyzer.analyze(any()).let { } }
    assertEquals(
      expected = listOf(DURATION_MS),
      actual = book.chapters.map { storedChapters.getValue(it.id).duration },
    )
  }

  @Test
  fun `reparse parses chapters that already have a duration`() = runTest {
    val book = zipBook("Book 1", chapterDurations = listOf(0L, 0L))

    enricher.enrich(listOf(book)) { }
    coVerify(exactly = 2) { mediaAnalyzer.analyze(any()).let { } }

    enricher.enrich(listOf(book), mode = ChapterEnricher.Mode.Reparse) { }
    coVerify(exactly = 4) { mediaAnalyzer.analyze(any()).let { } }
  }

  @Test
  fun `a zip that failed to parse is not parsed again after a restart`() = runTest {
    coEvery { mediaAnalyzer.analyze(any()) } returns null
    val book = zipBook("Book 1", chapterDurations = listOf(0L))

    enricher.enrich(listOf(book)) { }
    coVerify(exactly = 1) { mediaAnalyzer.analyze(any()).let { } }

    // the app is restarted, which creates a fresh enricher, but the zip stays remembered
    val restarted = newEnricher()
    assertEquals(expected = 0, actual = restarted.pendingCount(listOf(book)))
    restarted.enrich(listOf(book)) { }
    coVerify(exactly = 1) { mediaAnalyzer.analyze(any()).let { } }

    // only the user can ask for another attempt
    restarted.enrich(listOf(book), mode = ChapterEnricher.Mode.Retry) { }
    coVerify(exactly = 2) { mediaAnalyzer.analyze(any()).let { } }
  }

  @Test
  fun `resetFailures keeps the zips that were parsed automatically`() = runTest {
    // the zip could not be parsed, so the chapter keeps missing its duration
    coEvery { mediaAnalyzer.analyze(any()) } returns null
    val book = zipBook("Book 1", chapterDurations = listOf(0L))

    enricher.enrich(listOf(book)) { }
    coVerify(exactly = 1) { mediaAnalyzer.analyze(any()).let { } }

    // a restart forgets the failures of regular files, but a zip does not change either
    enricher.resetFailures()
    enricher.enrich(listOf(book)) { }
    coVerify(exactly = 1) { mediaAnalyzer.analyze(any()).let { } }
    assertEquals(expected = 0, actual = enricher.pendingCount(listOf(book)))
  }

  @Test
  fun `chapters outside of a zip are retried after a failure`() = runTest {
    // regular files are only gated by their failure count, unlike chapters in a zip
    coEvery { mediaAnalyzer.analyze(any()) } returns null
    val book = book("Book 1", chapterDurations = listOf(0L))

    enricher.enrich(listOf(book)) { }
    enricher.enrich(listOf(book)) { }

    coVerify(exactly = 2) { mediaAnalyzer.analyze(any()).let { } }
  }

  private fun newEnricher(): ChapterEnricher {
    return ChapterEnricher(
      mediaAnalyzer = mediaAnalyzer,
      chapterRepo = chapterRepo,
      bookParser = bookParser,
      fileFactory = fileFactory,
      zipArchiveProvider = zipArchiveProvider,
      zipParseAttemptRepo = zipParseAttemptRepo,
    )
  }

  private fun book(
    name: String,
    chapterDurations: List<Long>,
  ): Book {
    val bookFolder = File(testFolder.root, name).apply { mkdirs() }
    val chapters = chapterDurations.mapIndexed { index, duration ->
      chapter(
        id = ChapterId(File(bookFolder, "${index + 1}.mp3").toUri()),
        name = "${index + 1}",
        duration = duration,
      )
    }
    return bookOf(BookId(bookFolder.toUri()), chapters)
  }

  private fun zipBook(
    name: String,
    chapterDurations: List<Long>,
  ): Book {
    val zipUri = File(testFolder.root, "$name.zip").apply { createNewFile() }.toUri()
    val chapters = chapterDurations.mapIndexed { index, duration ->
      val entryUri = ZipUriCodec.encode(zipUri, "Chapter ${index + 1}.mp3")
      val entryFile = FileBasedDocumentFile(File(testFolder.root, "Chapter ${index + 1}.mp3"))
      coEvery { zipArchiveProvider.entryFile(entryUri) } returns entryFile
      chapter(id = ChapterId(entryUri), name = "${index + 1}", duration = duration)
    }
    return bookOf(BookId(zipUri), chapters)
  }

  private fun bookOf(
    bookId: BookId,
    chapters: List<Chapter>,
  ): Book {
    chapters.forEach { storedChapters[it.id] = it }
    return Book(
      content = BookContent(
        id = bookId,
        playbackSpeed = 1F,
        skipSilence = false,
        isActive = true,
        lastPlayedAt = Instant.EPOCH,
        author = null,
        name = bookId.value,
        addedAt = Instant.EPOCH,
        chapters = chapters.map { it.id },
        currentChapter = chapters.first().id,
        positionInChapter = 0L,
        cover = null,
        gain = 0F,
        genre = null,
        narrator = null,
        series = null,
        part = null,
      ),
      chapters = chapters,
    )
  }

  private fun chapter(
    id: ChapterId,
    name: String,
    duration: Long,
  ): Chapter {
    return Chapter(
      id = id,
      name = name,
      duration = duration,
      fileLastModified = Instant.EPOCH,
      markData = emptyList(),
      fileSize = 0L,
    )
  }

  private fun metadata(): Metadata {
    return Metadata(
      duration = DURATION_MS,
      artist = "Parsed Artist",
      album = "Parsed Album",
      title = "Parsed Title",
      fileName = "file.mp3",
      chapters = listOf(MarkData(startMs = 0L, name = "Intro")),
      genre = null,
      narrator = null,
      series = null,
      part = null,
    )
  }

  private companion object {
    const val DURATION_MS = 4242L
  }
}
