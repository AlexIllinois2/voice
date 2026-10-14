package voice.core.data.repo

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.internals.dao.ChapterDao
import voice.core.data.runForMaxSqlVariableNumber
import java.util.concurrent.ConcurrentHashMap

@ContributesBinding(AppScope::class)
public class ChapterRepoImpl(private val dao: ChapterDao) : ChapterRepo {

  // chapters are parsed in parallel, so the cache has to be thread safe. A concurrent map
  // can not hold null values, hence the holder.
  private val cache = ConcurrentHashMap<ChapterId, Holder>()

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
  }

  private class Holder(val chapter: Chapter?)
}
