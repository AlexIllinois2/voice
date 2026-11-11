package voice.core.playback

import android.net.Uri
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.playback.di.PlaybackScope

/**
 * The uri of the media item the player is currently on, updated on every media item
 * transition. Data sources compare it against the uri they are opened for to tell
 * apart user initiated loading (the current item itself) from automatic chapter
 * transition buffering (a different, upcoming item).
 */
@Inject
@SingleIn(PlaybackScope::class)
class CurrentMediaUriHolder {

  var uri: Uri? = null
}
