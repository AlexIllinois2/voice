package voice.features.folderPicker.addcontent

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.common.DispatcherProvider
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.scanner.MediaScanTrigger
import voice.navigation.Destination
import voice.navigation.Destination.OnboardingCompletion
import voice.navigation.Navigator
import voice.navigation.Origin
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
class AddContentViewModelTest {

  private val audiobookFolders: AudiobookFolders = mockk(relaxUnitFun = true)
  private val navigator: Navigator = mockk(relaxUnitFun = true)
  private val mediaScanTrigger: MediaScanTrigger = mockk(relaxUnitFun = true)

  @Test
  fun fileIsAddedAsSingleBook() = runTest {
    val viewModel = createViewModel(Origin.Default)
    val uri = "file:///book.zip".toUri()

    viewModel.add(uri, FileTypeSelection.File)
    testScheduler.advanceUntilIdle()

    coVerify { audiobookFolders.add(uri, FolderType.SingleFile) }
    verify { mediaScanTrigger.scan(restartIfScanning = true) }
    verify { navigator.setRoot(Destination.BookOverview) }
  }

  @Test
  fun folderIsAddedAsSingleBook() = runTest {
    val viewModel = createViewModel(Origin.Onboarding)
    val uri = "file:///book".toUri()

    viewModel.add(uri, FileTypeSelection.Folder)
    testScheduler.advanceUntilIdle()

    coVerify { audiobookFolders.add(uri, FolderType.SingleFolder) }
    verify { mediaScanTrigger.scan(restartIfScanning = true) }
    verify { navigator.goTo(OnboardingCompletion) }
  }

  private fun TestScope.createViewModel(origin: Origin) = AddContentViewModel(
    audiobookFolders = audiobookFolders,
    navigator = navigator,
    mediaScanTrigger = mediaScanTrigger,
    dispatcherProvider = DispatcherProvider(coroutineContext, coroutineContext, coroutineContext),
    origin = origin,
  )
}
