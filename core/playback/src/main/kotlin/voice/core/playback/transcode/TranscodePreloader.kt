package voice.core.playback.transcode

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import voice.core.data.repo.BookRepository
import voice.core.data.toUri
import voice.core.playback.di.PlaybackScope
import voice.core.playback.session.MediaId
import voice.core.playback.session.toMediaIdOrNull
import voice.core.transcode.TranscodeManager

/**
 * Converts the next chapter in the background as soon as the current one has loaded,
 * so chapter transitions happen without a transcoding wait.
 */
@Inject
@SingleIn(PlaybackScope::class)
class TranscodePreloader(
  private val transcodeManager: TranscodeManager,
  private val bookRepository: BookRepository,
) : Player.Listener {

  private lateinit var player: Player
  private val scope = MainScope()

  private var lastPreloadedUri: String? = null

  fun attachTo(player: Player) {
    this.player = player
    player.addListener(this)
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    if (isPlaying) {
      preloadNext()
    }
  }

  override fun onMediaItemTransition(
    mediaItem: MediaItem?,
    reason: Int,
  ) {
    preloadNext()
  }

  private fun preloadNext() {
    if (!this::player.isInitialized) return
    val mediaId = player.currentMediaItem?.mediaId?.toMediaIdOrNull() as? MediaId.Chapter ?: return
    scope.launch {
      val book = bookRepository.get(mediaId.bookId) ?: return@launch
      val index = book.chapters.indexOfFirst { it.id == mediaId.chapterId }
      if (index < 0 || index >= book.chapters.size - 1) return@launch
      val uri = book.chapters[index + 1].id.toUri()
      if (uri.toString() == lastPreloadedUri) return@launch
      if (!transcodeManager.isTranscodeNeeded(uri)) return@launch
      lastPreloadedUri = uri.toString()
      transcodeManager.preload(uri)
    }
  }
}
