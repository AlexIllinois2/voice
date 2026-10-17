package voice.core.scanner

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookRepository
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.logging.api.Logger
import kotlin.time.measureTime

@SingleIn(AppScope::class)
@Inject
public class MediaScanTrigger
internal constructor(
  private val audiobookFolders: AudiobookFolders,
  private val scanner: MediaScanner,
  private val coverScanner: CoverScanner,
  private val chapterEnricher: ChapterEnricher,
  private val bookRepo: BookRepository,
  private val documentFileFactory: CachedDocumentFileFactory,
  private val progressReporter: ScanProgressReporter,
) {

  /**
   * Whether the files of the library are currently being listed.
   */
  public val scannerActive: Flow<Boolean>
    field = MutableStateFlow(false)

  /**
   * Whether the durations of the listed chapters are currently being parsed. This happens in the
   * background and does not block the library, so it is reported separately.
   */
  public val enrichActive: Flow<Boolean>
    field = MutableStateFlow(false)

  /**
   * Detailed progress of the ongoing scan, or `null` while no scan is running.
   */
  public val scanProgress: Flow<ScanProgress?>
    field = MutableStateFlow(null)

  private val scope = CoroutineScope(Dispatchers.IO)
  private var scanningJob: Job? = null
  private var enrichJob: Job? = null

  public fun scan(restartIfScanning: Boolean = false) {
    Logger.i("scanForFiles with restartIfScanning=$restartIfScanning")
    if (scanningJob?.isActive == true && !restartIfScanning) {
      return
    }
    if (restartIfScanning) {
      chapterEnricher.resetFailures()
    }
    val oldJob = scanningJob
    scanningJob = scope.launch {
      scannerActive.value = true
      oldJob?.cancelAndJoin()

      val books = try {
        measureTime {
          val folders: Map<FolderType, List<CachedDocumentFile>> = audiobookFolders.all()
            .first()
            .mapValues { (_, documentFilesWithUri) ->
              documentFilesWithUri.map {
                documentFileFactory.create(it.documentFile.uri)
              }
            }
          // A rescan of a library that is already known does not report any progress, because
          // there is nothing to show. Only a new book is reported, so the indicator stays quiet
          // when the library is merely checked for added or removed files.
          scanner.scan(folders) { progress ->
            scanProgress.value = progress
          }
        }.also {
          Logger.i("scan took $it")
        }
        bookRepo.all().also {
          coverScanner.scan(it)
        }
      } finally {
        scannerActive.value = false
      }

      // The enricher runs in its own job: listing the files again must not cancel it and the
      // library stays usable while the durations are parsed.
      if (!enrich(books)) {
        clearProgress()
      }
    }
  }

  /**
   * Starts parsing the durations that are still missing in a separate job. Returns whether a job
   * has been started.
   */
  private suspend fun enrich(books: List<Book>): Boolean {
    if (enrichJob?.isActive == true) return true
    if (chapterEnricher.pendingCount(books) == 0) {
      Logger.i("enrich skipped, no chapter is missing its duration")
      return false
    }
    enrichJob = scope.launch {
      enrichActive.value = true
      try {
        measureTime {
          chapterEnricher.enrich(books) { progress ->
            scanProgress.value = progress
            progressReporter.report(progress)
          }
        }.also {
          Logger.i("enrich took $it")
        }
      } finally {
        enrichActive.value = false
        clearProgress()
      }
    }
    return true
  }

  /**
   * Parses all chapters of the book with [bookId] again, even the ones that already have a
   * duration. Triggered manually by the user, who can reparse a book as often as they like.
   */
  public fun reparseBook(bookId: BookId) {
    manuallyParse(bookId, ChapterEnricher.Mode.Reparse)
  }

  /**
   * Retries parsing the chapters of the book with [bookId] that are still missing their duration.
   * Triggered manually by the user after automatic parsing has failed.
   */
  public fun retryBookParse(bookId: BookId) {
    manuallyParse(bookId, ChapterEnricher.Mode.Retry)
  }

  private fun manuallyParse(
    bookId: BookId,
    mode: ChapterEnricher.Mode,
  ) {
    Logger.i("Manually parsing $bookId")
    scope.launch {
      val book = bookRepo.get(bookId) ?: return@launch
      enrichActive.value = true
      try {
        measureTime {
          chapterEnricher.enrich(
            books = listOf(book),
            mode = mode,
          ) { progress ->
            scanProgress.value = progress
            progressReporter.report(progress)
          }
        }.also {
          Logger.i("Manually parsing $bookId took $it")
        }
      } finally {
        enrichActive.value = false
        clearProgress()
      }
    }
  }

  private fun clearProgress() {
    scanProgress.value = null
    progressReporter.dismiss()
  }
}
