package voice.core.zip

import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

data class ZipPasswordUiRequest(
  val zipUri: Uri,
  val displayName: String,
  val failedAttempt: Boolean,
)

@Inject
@SingleIn(AppScope::class)
class ZipPasswordRequester {

  private val mutex = Mutex()
  private val stateFlow = MutableStateFlow<ZipPasswordUiRequest?>(null)
  val state: StateFlow<ZipPasswordUiRequest?> = stateFlow.asStateFlow()

  private var continuation: ((String?) -> Unit)? = null

  suspend fun request(
    zipUri: Uri,
    displayName: String,
    failedAttempt: Boolean,
  ): String? = mutex.withLock {
    // Without a UI collector, we cannot ask for a password.
    if (stateFlow.subscriptionCount.value < 1) {
      return null
    }
    suspendCancellableCoroutine { cont ->
      continuation = { password ->
        continuation = null
        stateFlow.value = null
        cont.resume(password)
      }
      stateFlow.value = ZipPasswordUiRequest(zipUri, displayName, failedAttempt)
      cont.invokeOnCancellation {
        continuation = null
        stateFlow.value = null
      }
    }
  }

  fun submit(password: String?) {
    continuation?.invoke(password)
  }
}
