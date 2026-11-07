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
import voice.navigation.Destination.SelectFolderType
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
    when (type) {
      FileTypeSelection.File -> {
        scope.launch {
          audiobookFolders.add(uri, FolderType.SingleFile)
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
      FileTypeSelection.Folder -> {
        navigator.goTo(
          SelectFolderType(
            uri = uri,
            origin = origin,
          ),
        )
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
