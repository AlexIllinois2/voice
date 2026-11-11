package voice.features.folderPicker.addcontent

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.BookId
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookRepository
import voice.core.logging.api.Logger
import voice.core.logging.inapp.LogStore
import voice.core.scanner.MediaScanTrigger
import voice.features.folderPicker.folderPicker.FileTypeSelection
import voice.navigation.Destination
import voice.navigation.Destination.OnboardingCompletion
import voice.navigation.Navigator
import voice.navigation.Origin
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal sealed interface AddState {
  data object Idle : AddState
  data object Adding : AddState
  data class Failed(val detail: String) : AddState
}

@AssistedInject
class AddContentViewModel(
  private val audiobookFolders: AudiobookFolders,
  private val navigator: Navigator,
  private val mediaScanTrigger: MediaScanTrigger,
  private val bookRepository: BookRepository,
  private val logStore: LogStore,
  private val errorReportSaver: ErrorReportSaver,
  dispatcherProvider: DispatcherProvider,
  @Assisted
  private val origin: Origin,
) {

  private val scope = MainScope(dispatcherProvider)

  internal var addState: AddState by mutableStateOf(AddState.Idle)
    private set

  internal var savedPath: String? by mutableStateOf(null)
    private set

  internal var saveError: String? by mutableStateOf(null)
    private set

  internal fun add(
    uri: Uri,
    type: FileTypeSelection,
  ) {
    if (addState is AddState.Adding) return
    scope.launch {
      addState = AddState.Adding
      savedPath = null
      saveError = null
      val logCountBefore = logStore.entries.value.size
      val folderType = when (type) {
        FileTypeSelection.File -> FolderType.SingleFile
        FileTypeSelection.Folder -> FolderType.SingleFolder
      }
      val scanJob = try {
        // Both a zip file and a folder are added as a single book.
        audiobookFolders.add(uri, folderType)
        mediaScanTrigger.scan(restartIfScanning = true)
        // scan() starts the job synchronously, so it is available right away.
        mediaScanTrigger.currentScanJob()
      } catch (e: Exception) {
        addState = AddState.Failed(errorDetail(uri, folderType, logCountBefore, "Adding the folder failed", e))
        return@launch
      }
      val bookId = BookId(uri)
      val appeared = try {
        awaitBookAppearance(scanJob, bookId)
      } catch (_: TimeoutCancellationException) {
        false
      }
      if (appeared) {
        addState = AddState.Idle
        when (origin) {
          Origin.Default -> navigator.setRoot(Destination.BookOverview)
          Origin.Onboarding -> navigator.goTo(OnboardingCompletion)
        }
      } else {
        addState = AddState.Failed(
          errorDetail(
            uri = uri,
            folderType = folderType,
            logCountBefore = logCountBefore,
            reason = "The scan finished, but the book never showed up in the library",
            throwable = null,
          ),
        )
      }
    }
  }

  /**
   * The library list is the single source of truth: the add only succeeded once the book shows up
   * in [BookRepository.flow]. The scan completing without the book means the add failed.
   */
  private suspend fun awaitBookAppearance(
    scanJob: Job?,
    bookId: BookId,
  ): Boolean {
    return withTimeout(ADD_TIMEOUT) {
      coroutineScope {
        val bookAppeared = async {
          bookRepository.flow().first { books -> books.any { it.id == bookId } }
          true
        }
        val scanCompleted = async {
          scanJob?.join()
          bookRepository.get(bookId) != null
        }
        select {
          bookAppeared.onAwait { it }
          scanCompleted.onAwait { it }
        }
      }
    }
  }

  internal fun dismissError() {
    addState = AddState.Idle
    when (origin) {
      Origin.Default -> navigator.setRoot(Destination.BookOverview)
      Origin.Onboarding -> navigator.goBack()
    }
  }

  internal fun saveErrorReport(detail: String) {
    scope.launch {
      try {
        savedPath = withContext(Dispatchers.IO) { errorReportSaver.save(detail) }
      } catch (e: Exception) {
        Logger.e(e, "Could not save the error report")
        saveError = e.message ?: e.toString()
      }
    }
  }

  private fun errorDetail(
    uri: Uri,
    folderType: FolderType,
    logCountBefore: Int,
    reason: String,
    throwable: Throwable?,
  ): String {
    return buildString {
      appendLine("time: ${DETAIL_TIME_FORMAT.format(Instant.now())}")
      appendLine("uri: $uri")
      appendLine("type: $folderType")
      appendLine("reason: $reason")
      throwable?.let {
        appendLine()
        appendLine(android.util.Log.getStackTraceString(it))
      }
      val logs = logStore.entries.value.drop(logCountBefore)
      if (logs.isNotEmpty()) {
        appendLine()
        appendLine("recent logs:")
        logs.forEach { entry ->
          appendLine("  ${LOG_TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestamp))} ${entry.severity}: ${entry.message}")
        }
      }
    }
  }

  internal fun back() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(origin: Origin): AddContentViewModel
  }

  private companion object {
    // Large zips are copied during the scan, so the timeout has to be generous.
    val ADD_TIMEOUT: Duration = 5.minutes
    val DETAIL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      .withZone(ZoneId.systemDefault())
    val LOG_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
      .withZone(ZoneId.systemDefault())
  }
}
