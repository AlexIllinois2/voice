package voice.features.folderPicker.addcontent

import android.net.Uri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.scanner.MediaScanTrigger
import voice.features.folderPicker.folderPicker.FileTypeSelection
import voice.navigation.Destination
import voice.navigation.Destination.OnboardingCompletion
import voice.navigation.Navigator
import voice.navigation.Origin

@AssistedInject
class AddContentViewModel(
  private val audiobookFolders: AudiobookFolders,
  private val navigator: Navigator,
  private val mediaScanTrigger: MediaScanTrigger,
  dispatcherProvider: DispatcherProvider,
  @Assisted
  private val origin: Origin,
) {

  private val scope = MainScope(dispatcherProvider)

  internal fun add(
    uri: Uri,
    type: FileTypeSelection,
  ) {
    scope.launch {
      // Both a zip file and a folder are added as a single book.
      val folderType = when (type) {
        FileTypeSelection.File -> FolderType.SingleFile
        FileTypeSelection.Folder -> FolderType.SingleFolder
      }
      audiobookFolders.add(uri, folderType)
      mediaScanTrigger.scan(restartIfScanning = true)
      when (origin) {
        Origin.Default -> {
          navigator.setRoot(Destination.BookOverview)
        }
        Origin.Onboarding -> {
          navigator.goTo(OnboardingCompletion)
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
}
