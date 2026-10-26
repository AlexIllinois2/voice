package voice.core.playback.session

import androidx.media3.common.C
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.MarkData
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterRepo
import voice.core.playback.session.search.book
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class MediaItemProviderTest {

  private val chapterRepo = mockk<ChapterRepo>()
  private val contentRepo = mockk<BookContentRepo>()
  private val provider = MediaItemProvider(
    bookRepository = mockk<BookRepository>(),
    application = mockk(),
    chapterRepo = chapterRepo,
    contentRepo = contentRepo,
    imageFileProvider = mockk(),
    currentBookStoreId = mockk(),
  )

  @Test
  fun `plays the whole file while the duration is not parsed yet`() = runTest {
    val chapter = chapter(
      duration = 0L,
      MarkData(startMs = 0, name = "one"),
      MarkData(startMs = 12_000, name = "two"),
    )
    val book = book(listOf(chapter))
    coEvery { contentRepo.get(book.id) } returns book.content
    coEvery { chapterRepo.get(chapter.id) } returns chapter

    val mediaItem = provider.item(mediaItemId(book.id, chapter.id, markIndex = 0))!!

    val clipping = mediaItem.clippingConfiguration
    assertEquals(expected = 0L, actual = clipping.startPositionMs)
    assertEquals(expected = C.TIME_END_OF_SOURCE, actual = clipping.endPositionMs)
    assertEquals(expected = null, actual = mediaItem.mediaMetadata.durationMs)
  }

  @Test
  fun `clips to the mark once the duration is parsed`() = runTest {
    val chapter = chapter(
      duration = 20_000L,
      MarkData(startMs = 0, name = "one"),
      MarkData(startMs = 12_000, name = "two"),
    )
    val book = book(listOf(chapter))
    coEvery { contentRepo.get(book.id) } returns book.content
    coEvery { chapterRepo.get(chapter.id) } returns chapter

    val mediaItem = provider.item(mediaItemId(book.id, chapter.id, markIndex = 1))!!

    val clipping = mediaItem.clippingConfiguration
    assertEquals(expected = 12_000L, actual = clipping.startPositionMs)
    assertEquals(expected = 19_999L, actual = clipping.endPositionMs)
    assertEquals(expected = 7_999L, actual = mediaItem.mediaMetadata.durationMs)
  }

  private fun mediaItemId(
    bookId: BookId,
    chapterId: ChapterId,
    markIndex: Int,
  ): String {
    val mediaId = MediaId.ChapterMark(
      bookId = bookId,
      chapterId = chapterId,
      markIndex = markIndex,
      startMs = 0,
      endMs = 0,
    )
    return Json.encodeToString(MediaId.serializer(), mediaId)
  }

  private fun chapter(
    duration: Long,
    vararg marks: MarkData,
  ): Chapter {
    return Chapter(
      id = ChapterId(Uuid.random().toString()),
      name = "chapter",
      duration = duration,
      fileLastModified = Instant.EPOCH,
      markData = marks.toList(),
      fileSize = 0,
    )
  }
}
