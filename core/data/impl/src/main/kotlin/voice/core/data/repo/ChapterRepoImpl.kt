package voice.core.data.repo

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.internals.dao.ChapterDao
import voice.core.data.runForMaxSqlVariableNumber
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@ContributesBinding(AppScope::class)
public class ChapterRepoImpl(private val dao: ChapterDao) : ChapterRepo {

  // chapters are parsed in parallel, so the cache has to be thread safe. A concurrent map
  // can not hold null values, hence the holder.
  private val cache = ConcurrentHashMap<ChapterId, Holder>()
  private val updateCounter = MutableStateFlow(0L)

  /**
   * Emits whenever a chapter is written. Book flows combine with this so that backfilled
   * durations show up without requiring a change to the book itself. The updates are sampled
   * to keep the re-mapping of the books at a sane rate while they are being enriched, but the
   * first emission is immediate so that book flows don't wait for the first sample tick.
   */
  internal val updates: Flow<Long> = updateCounter
    .sample(UPDATE_SAMPLE_PERIOD)
    .onStart { emit(0L) }

  override suspend fun get(id: ChapterId): Chapter? {
    // a missing chapter is cached as well, so the holder is checked instead of its content
    val cached = cache[id]
    if (cached != null) return cached.chapter
    val chapter = dao.chapter(id)
    cache[id] = Holder(chapter)
    return chapter
  }

  internal suspend fun warmup(ids: List<ChapterId>) {
    val missing = ids.filter { !cache.containsKey(it) }
    missing
      .runForMaxSqlVariableNumber {
        dao.chapters(it)
      }
      .forEach { cache[it.id] = Holder(it) }
  }

  override suspend fun put(chapter: Chapter) {
    dao.insert(chapter)
    cache[chapter.id] = Holder(chapter)
    updateCounter.update { it + 1 }
  }

  private class Holder(val chapter: Chapter?)

  private companion object {
    val UPDATE_SAMPLE_PERIOD = 1.seconds
  }
}
