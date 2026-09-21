package voice.core.zip

import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.core.logging.api.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps fully extracted zip entries in memory ("ramfs" semantics) for playback.
 */
@Inject
@SingleIn(AppScope::class)
class ZipPlaybackCache(private val provider: ZipArchiveProvider) {

  private val mutex = Mutex()
  private val cache = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
  private val locks = ConcurrentHashMap<String, Mutex>()
  private val extractTimings = HashMap<String, MutableList<Long>>() // zipUri -> millis

  private val pinned = mutableSetOf<String>()

  suspend fun ensureExtracted(uri: Uri): ByteArray {
    val key = uri.toString()
    mutex.withLock {
      cache[key]
    }?.let {
      return it
    }
    val lock = locks.computeIfAbsent(key) { Mutex() }
    lock.withLock {
      mutex.withLock {
        cache[key]
      }?.let {
        return it
      }
      val bytes = extract(key, uri)
      mutex.withLock {
        cache[key] = bytes
        evict()
      }
      return bytes
    }
  }

  /**
   * Extracts the entry without throwing, e.g. for prefetching.
   */
  suspend fun prefetch(uri: Uri) {
    try {
      val bytes = ensureExtracted(uri)
      Logger.d("Prefetched $uri (${bytes.size} bytes)")
    } catch (e: Exception) {
      Logger.w(e, "Failed to prefetch $uri")
    }
  }

  fun setPinned(keys: Set<String>) {
    synchronized(pinned) {
      pinned.clear()
      pinned += keys
    }
  }

  /**
   * Estimates how long it takes to extract an entry of [nextEntrySize] bytes
   * from the zip at [zipUri]. Uses the measured average when available.
   */
  fun estimateExtractDuration(zipUri: Uri, nextEntrySize: Long): Duration {
    val timings = synchronized(extractTimings) { extractTimings[zipUri.toString()] }
    val avg = timings
      ?.takeIf { it.isNotEmpty() }
      ?.average()
      ?.let { (it / 1000.0).seconds }
    return avg ?: estimatedFromSize(nextEntrySize)
  }

  private fun estimatedFromSize(size: Long): Duration {
    // assume ~30 MB/s decompression throughput
    return ((size / (30.0 * 1024 * 1024)).seconds).coerceAtLeast(1.seconds)
  }

  private suspend fun extract(
    key: String,
    uri: Uri,
  ): ByteArray {
    val start = System.currentTimeMillis()
    val bytes = provider.readEntryBytes(uri)
    val took = System.currentTimeMillis() - start
    ZipUriCodec.zipUri(uri)?.let { zipUri ->
      synchronized(extractTimings) {
        extractTimings.getOrPut(zipUri.toString()) { mutableListOf() }.add(took)
      }
    }
    Logger.d("Extracted $key (${bytes.size} bytes) in $took ms")
    return bytes
  }

  private suspend fun evict() {
    val pinnedSnapshot = synchronized(pinned) { pinned.toSet() }
    while (cache.size > MAX_ENTRIES) {
      val eldest = cache.entries.firstOrNull { it.key !in pinnedSnapshot } ?: break
      cache.remove(eldest.key)
    }
  }

  private companion object {
    const val MAX_ENTRIES = 8
  }
}
