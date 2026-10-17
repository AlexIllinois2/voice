package voice.core.scanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.repo.ChapterRepoImpl
import voice.core.documentfile.FileBasedDocumentFile
import voice.core.zip.ZipArchiveProvider
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ChapterParserTest {

  private val testFolder = TemporaryFolder()

  @Rule
  fun testFolder() = testFolder

  @Before
  fun setUp() {
    testFolder.create()
  }

  @Test
  fun parserSorts() = runTest {
    val audiobook = testFolder.newFolder("audiobook")
    testFolder.newFile("audiobook/Chapter 1.mp3")
    testFolder.newFile("audiobook/Chapter 2.mp3")
    testFolder.newFile("audiobook/Chapter 20.mp3")
    testFolder.newFile("audiobook/Chapter 3.mp3")
    testFolder.newFile("audiobook/Chapter 30.mp3")

    val chapterParser = chapterParser()

    assertEquals(
      expected = listOf(
        "Chapter 1",
        "Chapter 2",
        "Chapter 3",
        "Chapter 20",
        "Chapter 30",
      ),
      actual = chapterParser.listChapters(FileBasedDocumentFile(audiobook)) { }
        .map { it.name },
    )
  }

  @Test
  fun parserReportsEntryCountWithoutAnalyzing() = runTest {
    val audiobook = testFolder.newFolder("audiobook")
    testFolder.newFile("audiobook/Chapter 1.mp3")
    testFolder.newFile("audiobook/Chapter 2.mp3")
    testFolder.newFile("audiobook/Chapter 3.mp3")

    val chapterParser = chapterParser()

    // the parser lists files in parallel, so progress may be emitted from several threads
    val progress: MutableList<ScanProgress> = java.util.Collections.synchronizedList(mutableListOf())
    val chapters = chapterParser.listChapters(FileBasedDocumentFile(audiobook)) { progress += it }

    assertEquals(expected = 3, actual = chapters.size)
    assertEquals(expected = listOf(0L, 0L, 0L), actual = chapters.map { it.duration })
    // listing only reports the total count; the durations are backfilled later
    assertEquals(expected = 1, actual = progress.size)
    assertEquals(expected = 3, actual = progress.single().entryCount)
    assertEquals(expected = 0, actual = progress.single().entryIndex)
    assertEquals(expected = ScanProgress.Phase.Scanning, actual = progress.single().phase)
  }

  private fun chapterParser(): ChapterParser {
    return ChapterParser(
      chapterRepo = ChapterRepoImpl(
        mockk {
          coEvery {
            chapter(any())
          } returns null
          coEvery {
            insert(any())
          } just Runs
        },
      ),
      zipArchiveProvider = mockk(),
    )
  }
}
