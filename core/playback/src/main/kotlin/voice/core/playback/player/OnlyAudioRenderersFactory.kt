package voice.core.playback.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import voice.core.data.store.HiFiModeStore

@Inject
class OnlyAudioRenderersFactory(
  context: Context,
  @HiFiModeStore
  hiFiModeStore: DataStore<Boolean>,
) : DefaultRenderersFactory(context) {

  init {
    // Float output keeps high-resolution PCM sources at their original bit depth
    // instead of truncating them to 16 bit. It's only applied for such sources,
    // and it disables the built-in audio processing (silence skipping, speed
    // adjustment) which then falls back to the audio output for speed changes.
    setEnableAudioFloatOutput(runBlocking { hiFiModeStore.data.first() })
  }

  override fun buildVideoRenderers(
    context: Context,
    extensionRendererMode: Int,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
    allowedVideoJoiningTimeMs: Long,
    out: ArrayList<Renderer>,
  ) {
  }

  override fun buildTextRenderers(
    context: Context,
    output: TextOutput,
    outputLooper: Looper,
    extensionRendererMode: Int,
    out: ArrayList<Renderer>,
  ) {
  }

  override fun buildMetadataRenderers(
    context: Context,
    output: MetadataOutput,
    outputLooper: Looper,
    extensionRendererMode: Int,
    out: ArrayList<Renderer>,
  ) {
  }

  override fun buildCameraMotionRenderers(
    context: Context,
    extensionRendererMode: Int,
    out: ArrayList<Renderer>,
  ) {
  }

  override fun buildMiscellaneousRenderers(
    context: Context,
    eventHandler: Handler,
    extensionRendererMode: Int,
    out: ArrayList<Renderer>,
  ) {
  }
}
