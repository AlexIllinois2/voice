package voice.core.transcode

import android.app.Application
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import voice.core.logging.api.Logger
import voice.core.zip.ZipPlaybackCache
import voice.core.zip.ZipUriCodec
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Converts audio files whose format can not be played by ExoPlayer (like WMA) to m4a.
 *
 * Conversion happens lazily when a file is first played and the result is cached permanently.
 */
@Inject
@SingleIn(AppScope::class)
class TranscodeManager(
  private val application: Application,
  private val zipPlaybackCache: ZipPlaybackCache,
) {

  private val transcoder = AudioTranscoder()
  private val locks = ConcurrentHashMap<String, Mutex>()
  private val recentlyUsed = ArrayDeque<String>()

  private val _state = MutableStateFlow<TranscodeState?>(null)

  /**
   * The state of the currently running conversion, or the last failure. Null when idle.
   */
  val state: StateFlow<TranscodeState?> = _state.asStateFlow()

  init {
    // ffmpeg-kit's libavcodec.so references __gxx_personality_v0, which is only resolvable
    // when libc++_shared.so is loaded before it, otherwise dlopen fails
    runCatching {
      System.loadLibrary("c++_shared")
    }.onFailure {
      Logger.w(it, "Could not preload libc++_shared.so, transcoding will most likely fail")
    }
  }

  fun isTranscodeNeeded(uri: Uri): Boolean {
    return extensionOf(uri) in TRANSCODE_EXTENSIONS
  }

  /**
   * Reads the duration of [uri] in milliseconds without transcoding it, or null when it can
   * not be determined.
   */
  suspend fun probeDurationMs(uri: Uri): Long? {
    if (!isTranscodeNeeded(uri)) return null
    val input = materializeSource(uri) ?: return null
    try {
      return transcoder.probeDurationMs(input)
    } finally {
      input.delete()
    }
  }

  /**
   * Clears the failure that is currently exposed through [state].
   */
  fun dismissError() {
    if (_state.value is TranscodeState.Failed) {
      _state.value = null
    }
  }

  /**
   * Returns a playable m4a [File] for [uri], converting it first when it has not been
   * converted yet. Concurrent calls for the same uri share a single conversion.
   */
  suspend fun ensureConverted(uri: Uri): File {
    return convert(uri, announce = true)
  }

  /**
   * Silently converts [uri] in the background so the upcoming chapter starts without
   * a waiting period. Does not publish progress to [state] and swallows errors, since
   * a real playback of [uri] will surface them with UI.
   */
  suspend fun preload(uri: Uri) {
    if (!isTranscodeNeeded(uri)) return
    runCatching {
      convert(uri, announce = false)
    }.onFailure {
      Logger.w(it, "Preloading $uri failed")
    }
  }

  private suspend fun convert(
    uri: Uri,
    announce: Boolean,
  ): File {
    val output = outputFileFor(uri)
    if (output.exists()) {
      markUsedAndCleanup(uri)
      return output
    }
    val lock = locks.computeIfAbsent(output.path) { Mutex() }
    lock.withLock {
      output.parentFile?.mkdirs()
      if (output.exists()) {
        markUsedAndCleanup(uri)
        return output
      }
      val fileName = fileNameOf(uri) ?: uri.toString()
      try {
        if (announce) {
          _state.value = TranscodeState.Running(
            fileName = fileName,
            step = TranscodeState.Step.ReadingSource,
            progressPercent = null,
          )
        }
        val input = checkNotNull(materializeSource(uri)) { "Could not read $uri" }
        try {
          if (announce) {
            _state.value = TranscodeState.Running(
              fileName = fileName,
              step = TranscodeState.Step.Transcoding,
              progressPercent = null,
            )
          }
          val part = File(output.parentFile, "${output.name}.part")
          transcoder.transcode(input, part) { percent ->
            if (announce) {
              _state.value = TranscodeState.Running(
                fileName = fileName,
                step = TranscodeState.Step.Transcoding,
                progressPercent = percent,
              )
            }
          }
          if (announce) {
            _state.value = TranscodeState.Running(
              fileName = fileName,
              step = TranscodeState.Step.Saving,
              progressPercent = null,
            )
          }
          if (part.renameTo(output)) {
            Logger.d("Transcoded $uri to ${output.path}")
          } else {
            Logger.w("Could not rename ${part.path} to ${output.path}, copying instead")
            part.copyTo(output, overwrite = true)
            part.delete()
          }
          if (announce) {
            _state.value = null
          }
          markUsedAndCleanup(uri)
          return output
        } finally {
          input.delete()
        }
      } catch (e: Exception) {
        if (announce) {
          Logger.w(e, "Transcoding $uri failed")
          _state.value = TranscodeState.Failed(
            fileName = fileName,
            message = e.message ?: e.toString(),
          )
        }
        throw e
      }
    }
  }

  /**
   * Marks [uri]'s converted file as recently used and deletes all transcoded files
   * that are not among the [KEEP_COUNT] most recently used ones, so the cache does
   * not grow indefinitely with every played chapter.
   */
  private fun markUsedAndCleanup(uri: Uri) {
    val keepHashes = synchronized(recentlyUsed) {
      val hash = hashOf(uri)
      recentlyUsed.remove(hash)
      recentlyUsed.addFirst(hash)
      while (recentlyUsed.size > KEEP_COUNT) {
        recentlyUsed.removeLast()
      }
      recentlyUsed.toSet()
    }
    val dir = File(application.cacheDir, TRANSCODED_DIR)
    for (file in dir.listFiles() ?: return) {
      if (file.name.endsWith(PART_SUFFIX)) {
        val outputCandidate = File(file.parentFile, file.name.removeSuffix(PART_SUFFIX))
        if (locks[outputCandidate.path]?.isLocked == true) continue
      } else if (file.nameWithoutExtension in keepHashes) {
        continue
      }
      file.delete()
    }
  }

  /**
   * Copies the bytes behind [uri] into a temporary input file for ffmpeg.
   */
  private suspend fun materializeSource(uri: Uri): File? = withContext(Dispatchers.IO) {
    val input = File.createTempFile("transcode-input-${hashOf(uri)}-", null, application.cacheDir)
    try {
      if (ZipUriCodec.isZipUri(uri)) {
        val bytes = zipPlaybackCache.ensureExtracted(uri)
        input.outputStream().use { output ->
          output.write(bytes)
        }
      } else {
        application.contentResolver.openInputStream(uri)?.use { stream ->
          stream.copyTo(input.outputStream())
        } ?: return@withContext null
      }
      input
    } catch (e: Exception) {
      input.delete()
      throw e
    }
  }

  private fun outputFileFor(uri: Uri): File {
    return File(application.cacheDir, "$TRANSCODED_DIR/${hashOf(uri)}.m4a")
  }

  private fun extensionOf(uri: Uri): String {
    return fileNameOf(uri)?.substringAfterLast('.')?.lowercase(Locale.US) ?: ""
  }

  private fun fileNameOf(uri: Uri): String? {
    return if (ZipUriCodec.isZipUri(uri)) {
      ZipUriCodec.entryName(uri)
    } else {
      uri.lastPathSegment
    }
  }

  private fun hashOf(uri: Uri): String {
    val digest = MessageDigest.getInstance("MD5").digest(uri.toString().toByteArray())
    return digest.joinToString(separator = "") { "%02x".format(it) }
  }

  private companion object {
    val TRANSCODE_EXTENSIONS = setOf("wma", "asf")
    const val TRANSCODED_DIR = "transcoded"
    const val PART_SUFFIX = ".part"

    /**
     * How many recently played chapters stay converted: the current one and the
     * preloaded next one. Everything older is deleted to keep the cache small.
     */
    const val KEEP_COUNT = 2
  }
}
