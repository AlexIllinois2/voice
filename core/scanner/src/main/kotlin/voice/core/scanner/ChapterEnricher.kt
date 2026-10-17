package voice.core.scanner

import android.net.Uri
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.ChapterRepo
import voice.core.data.repo.ZipParseAttemptRepo
import voice.core.data.toUri
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.logging.api.Logger
import voice.core.zip.ZipArchiveProvider
import voice.core.zip.ZipUriCodec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Parses the duration and metadata of chapters that were listed without being analyzed.
 *
 * The initial scan only lists the files of a book so that it shows up in the library right away.
 * This enricher picks up the chapters that are still missing their duration and parses them in
 * the background, backfilling whatever it finds.
 */
@Inject
internal class ChapterEnricher(
  private val mediaAnalyzer: MediaAnalyzer,
  private val chapterRepo: ChapterRepo,
  private val bookParser: BookParser,
  private val fileFactory: CachedDocumentFileFactory,
  private val zipArchiveProvider: ZipArchiveProvider,
  private val zipParseAttemptRepo: ZipParseAttemptRepo,
) {

  /**
   * How often parsing a chapter has failed. A chapter that can not be parsed at all (a corrupt
   * file, or a zip whose password is unknown) is skipped after [MAX_ATTEMPTS], otherwise every
   * scan would parse it again and never come to an end.
   */
  private val failedAttempts = ConcurrentHashMap<ChapterId, Int>()

  /**
   * How the chapters of [books] are parsed.
   */
  enum class Mode {
    /**
     * Parses the chapters that are still missing their duration and have not failed too often.
     * A zip is parsed automatically only once in its lifetime.
     */
    Auto,

    /**
     * Parses the chapters that are still missing their duration, even if automatic parsing has
     * already given up on them. Triggered manually by the user.
     */
    Retry,

    /**
     * Parses all chapters again, even the ones that already have a duration. Triggered manually
     * by the user.
     */
    Reparse,
  }

  /**
   * The number of chapters of [books] that are still missing their duration and can be parsed.
   */
  suspend fun pendingCount(books: List<Book>): Int {
    return chaptersToParse(books, mode = Mode.Auto).sumOf { it.size }
  }

  /**
   * Forgets the failures of the previous runs, so chapters that could not be parsed are tried
   * again on the next run. The zips that were parsed automatically are kept: a zip can not
   * change, so parsing it once more would not yield a different result either.
   */
  fun resetFailures() {
    failedAttempts.clear()
  }

  suspend fun enrich(
    books: List<Book>,
    mode: Mode = Mode.Auto,
    onProgress: (ScanProgress) -> Unit,
  ) {
    val pending = chaptersToParse(books, mode = mode)
    if (pending.isEmpty()) return

    // the attempt is remembered before anything is parsed: a zip is parsed automatically only
    // once in its lifetime, even when the app dies halfway through or the parse fails
    if (mode == Mode.Auto) {
      zipParseAttemptRepo.markAttempted(pending.flatten().mapNotNull { zipUriOf(it.chapter) })
    }

    // interleaving the books makes every book get its first chapters parsed early, so names and
    // durations show up for all of them instead of only for the first one
    val interleaved = interleave(pending)
    val total = interleaved.size
    val enriched = AtomicInteger()
    interleaved.chunked(BATCH_SIZE).forEach { batch ->
      coroutineScope {
        batch
          .map { pendingChapter ->
            async(analysisDispatcher) {
              if (enrichChapter(pendingChapter, force = mode != Mode.Auto)) {
                failedAttempts.remove(pendingChapter.chapter.id)
              } else {
                logFailedAttempt(pendingChapter)
              }
              val done = enriched.incrementAndGet()
              onProgress(
                ScanProgress(
                  phase = ScanProgress.Phase.Enriching,
                  entryName = pendingChapter.chapter.name,
                  entryIndex = done,
                  entryCount = total,
                ),
              )
            }
          }
          .awaitAll()
      }
    }
  }

  private fun logFailedAttempt(pendingChapter: PendingChapter) {
    val uri = pendingChapter.chapter.id.toUri()
    val attempts = failedAttempts.merge(pendingChapter.chapter.id, 1) { attempts, _ -> attempts + 1 }
      ?: MAX_ATTEMPTS
    if (attempts >= MAX_ATTEMPTS) {
      Logger.w("Giving up on '${pendingChapter.chapter.name}' ($uri) after $attempts failed attempts")
    } else {
      Logger.w("Failed to parse '${pendingChapter.chapter.name}' ($uri) ($attempts/$MAX_ATTEMPTS)")
    }
  }

  private suspend fun enrichChapter(
    pending: PendingChapter,
    force: Boolean,
  ): Boolean {
    val chapter = chapterRepo.get(pending.chapter.id) ?: return false
    // the player may have already resolved the duration while this chapter was pending
    if (chapter.duration > 0L && !force) return true

    val file = documentFileFor(pending.chapter.id.toUri())
    if (file == null) {
      Logger.w("Could not resolve the document file for ${pending.chapter.id.toUri()}")
      return false
    }
    val metadata = mediaAnalyzer.analyze(file) ?: return false
    chapterRepo.put(
      chapter.copy(
        name = metadata.title ?: chapter.name,
        duration = metadata.duration,
        markData = metadata.chapters,
      ),
    )
    if (pending.isFirstChapter) {
      bookParser.fillMetadata(pending.bookId, metadata)
    }
    return true
  }

  private suspend fun documentFileFor(uri: Uri): CachedDocumentFile? {
    return if (ZipUriCodec.isZipUri(uri)) {
      zipArchiveProvider.entryFile(uri)
    } else {
      fileFactory.create(uri)
    }
  }

  private suspend fun chaptersToParse(
    books: List<Book>,
    mode: Mode,
  ): List<List<PendingChapter>> {
    // the persisted zips are only consulted for the automatic mode, the user is not limited by
    // them when they ask for a parse
    val autoParsedZips = if (mode == Mode.Auto) {
      zipParseAttemptRepo.attemptedZips()
    } else {
      emptySet()
    }
    return books
      .map { book ->
        book.chapters.mapIndexedNotNull { index, chapter ->
          if (shouldParse(chapter, mode, autoParsedZips)) {
            PendingChapter(bookId = book.id, chapter = chapter, isFirstChapter = index == 0)
          } else {
            null
          }
        }
      }
      .filter { it.isNotEmpty() }
  }

  private fun shouldParse(
    chapter: Chapter,
    mode: Mode,
    autoParsedZips: Set<String>,
  ): Boolean {
    return when (mode) {
      Mode.Auto -> {
        chapter.duration <= 0L &&
          (failedAttempts[chapter.id] ?: 0) < MAX_ATTEMPTS &&
          zipNotAutoParsed(chapter, autoParsedZips)
      }
      Mode.Retry -> chapter.duration <= 0L
      Mode.Reparse -> true
    }
  }

  /**
   * Whether the zip that contains [chapter] has not been parsed automatically yet. Chapters
   * that do not live inside of a zip are only gated by their failure count.
   */
  private fun zipNotAutoParsed(
    chapter: Chapter,
    autoParsedZips: Set<String>,
  ): Boolean {
    val zipUri = zipUriOf(chapter) ?: return true
    return zipUri !in autoParsedZips
  }

  private fun zipUriOf(chapter: Chapter): String? {
    val uri = chapter.id.toUri()
    if (!ZipUriCodec.isZipUri(uri)) return null
    return ZipUriCodec.zipUri(uri)?.toString()
  }

  private fun interleave(pendingByBook: List<List<PendingChapter>>): List<PendingChapter> {
    val result = mutableListOf<PendingChapter>()
    val iterators = pendingByBook.map { it.iterator() }
    var anyRemaining = true
    while (anyRemaining) {
      anyRemaining = false
      iterators.forEach { iterator ->
        if (iterator.hasNext()) {
          result += iterator.next()
          anyRemaining = true
        }
      }
    }
    return result
  }

  private data class PendingChapter(
    val bookId: BookId,
    val chapter: Chapter,
    val isFirstChapter: Boolean,
  )

  private companion object {
    // reading the metadata is by far the most expensive part, so it happens in parallel
    val analysisDispatcher = Dispatchers.IO.limitedParallelism(4)
    const val BATCH_SIZE = 8
    const val MAX_ATTEMPTS = 3
  }
}
