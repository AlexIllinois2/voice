package voice.features.bookOverview.reparseBook

import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.scanner.MediaScanTrigger
import voice.core.scanner.isZipBook
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope

/**
 * Offers to reparse a book that lives inside a zip archive. The app parses such a book
 * automatically only once, so reparse is the only way to try again.
 */
@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(BookOverviewScope::class)
class ReparseBookViewModel(
  private val repo: BookRepository,
  private val mediaScanTrigger: MediaScanTrigger,
) : BottomSheetItemViewModel {

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    val book = repo.get(bookId) ?: return emptyList()
    return if (book.isZipBook()) {
      listOf(BottomSheetItem.Reparse)
    } else {
      emptyList()
    }
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    if (item != BottomSheetItem.Reparse) return
    mediaScanTrigger.reparseBook(bookId)
  }
}
