package voice.core.playback.zip

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import voice.core.data.repo.BookRepository
import voice.core.data.toUri
import voice.core.playback.di.PlaybackScope
import voice.core.playback.session.MediaId
import voice.core.playback.session.toMediaIdOrNull
import voice.core.zip.ZipPlaybackCache
import voice.core.zip.ZipUriCodec
import kotlin.time.Duration.Companion.seconds

/**
 * Preloads the upcoming zip chapter into memory when the remaining play time
 * is below the estimated extraction time + 10s.
 */
@Inject
@SingleIn(PlaybackScope::class)
class ZipPreloader(
  private val playbackCache: ZipPlaybackCache,
  private val bookRepository: BookRepository,
) : Player.Listener {

  private lateinit var player: Player
  private val scope = MainScope()

  private var pollingJob: Job? = null

  fun attachTo(player: Player) {
    this.player = player
    player.addListener(this)
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    if (isPlaying) {
      startPolling()
    } else {
      pollingJob?.cancel()
      pollingJob = null
    }
  }

  override fun onMediaItemTransition(
    mediaItem: MediaItem?,
    reason: Int,
  ) {
    scope.launch {
      updatePinned()
    }
  }

  private fun startPolling() {
    if (pollingJob?.isActive == true) return
    pollingJob = scope.launch {
      while (isActive) {
        preloadWhenAlmostDone()
        delay(1.seconds)
      }
    }
  }

  private suspend fun preloadWhenAlmostDone() {
    val mediaId = currentChapterId() ?: return
    val book = bookRepository.get(mediaId.bookId) ?: return
    val index = book.chapters.indexOfFirst { it.id == mediaId.chapterId }
    if (index < 0 || index >= book.chapters.size - 1) return
    val next = book.chapters[index + 1]
    val uri = next.id.toUri()
    if (!ZipUriCodec.isZipUri(uri)) return
    val playerDuration = player.duration
    if (playerDuration <= 0) return
    val remaining = playerDuration - player.currentPosition
    val zipUri = ZipUriCodec.zipUri(uri) ?: return
    val threshold = playbackCache.estimateExtractDuration(zipUri, next.fileSize) + SAFETY_MARGIN
    if (remaining <= threshold.inWholeMilliseconds) {
      playbackCache.prefetch(uri)
    }
  }

  private suspend fun updatePinned() {
    val mediaId = currentChapterId() ?: return
    val book = bookRepository.get(mediaId.bookId) ?: return
    val index = book.chapters.indexOfFirst { it.id == mediaId.chapterId }
    if (index < 0) return
    val pinned = book.chapters
      .drop(index)
      .take(1)
      .map { it.id.toUri().toString() }
      .toSet()
    playbackCache.setPinned(pinned)
  }

  private fun currentChapterId(): MediaId.Chapter? {
    if (!this::player.isInitialized) return null
    val mediaItem = player.currentMediaItem ?: return null
    return mediaItem.mediaId.toMediaIdOrNull() as? MediaId.Chapter
  }

  private companion object {
    val SAFETY_MARGIN = 10.seconds
  }
}
