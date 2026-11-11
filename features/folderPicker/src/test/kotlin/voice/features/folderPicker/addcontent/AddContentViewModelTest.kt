package voice.features.folderPicker.addcontent

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.common.DispatcherProvider
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookRepository
import voice.core.logging.inapp.LogStore
import voice.core.scanner.MediaScanTrigger
import voice.features.folderPicker.book
import voice.features.folderPicker.folderPicker.FileTypeSelection
import voice.navigation.Destination
import voice.navigation.Destination.OnboardingCompletion
import voice.navigation.Navigator
import voice.navigation.Origin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AddContentViewModelTest {

  private val audiobookFolders: AudiobookFolders = mockk(relaxUnitFun = true)
  private val navigator: Navigator = mockk(relaxed = true)
  private val mediaScanTrigger: MediaScanTrigger = mockk(relaxUnitFun = true)
  private val logStore: LogStore = mockk {
    every { entries } returns MutableStateFlow(emptyList())
  }
  private val errorReportSaver: ErrorReportSaver = mockk(relaxed = true)
  private val completedScanJob = Job().apply { complete() }

  init {
    every { mediaScanTrigger.currentScanJob() } returns completedScanJob
  }

  @Test
  fun fileIsAddedAsSingleBook() = runTest {
    val uri = "file:///book.zip".toUri()
    val repo = repoWithBooks(book(id = BookId(uri)))
    val viewModel = createViewModel(Origin.Default, repo)

    viewModel.add(uri, FileTypeSelection.File)
    testScheduler.advanceUntilIdle()

    coVerify { audiobookFolders.add(uri, FolderType.SingleFile) }
    verify { mediaScanTrigger.scan(restartIfScanning = true) }
    verify { navigator.setRoot(Destination.BookOverview) }
    assertEquals(AddState.Idle, viewModel.addState)
  }

  @Test
  fun folderIsAddedAsSingleBook() = runTest {
    val uri = "file:///book".toUri()
    val repo = repoWithBooks(book(id = BookId(uri)))
    val viewModel = createViewModel(Origin.Onboarding, repo)

    viewModel.add(uri, FileTypeSelection.Folder)
    testScheduler.advanceUntilIdle()

    coVerify { audiobookFolders.add(uri, FolderType.SingleFolder) }
    verify { mediaScanTrigger.scan(restartIfScanning = true) }
    verify { navigator.goTo(OnboardingCompletion) }
  }

  @Test
  fun addFailsWhenScanCompletesWithoutTheBook() = runTest {
    val uri = "file:///book.zip".toUri()
    val repo = repoWithBooks()
    val viewModel = createViewModel(Origin.Default, repo)

    viewModel.add(uri, FileTypeSelection.File)
    testScheduler.advanceUntilIdle()

    val state = viewModel.addState
    assertTrue(state is AddState.Failed)
    assertTrue(state.detail.contains("file:///book.zip"))
    verify(exactly = 0) { navigator.setRoot(Destination.BookOverview) }
  }

  @Test
  fun dismissingTheErrorReturnsToBookOverview() = runTest {
    val uri = "file:///book.zip".toUri()
    val repo = repoWithBooks()
    val viewModel = createViewModel(Origin.Default, repo)

    viewModel.add(uri, FileTypeSelection.File)
    testScheduler.advanceUntilIdle()
    viewModel.dismissError()
    testScheduler.advanceUntilIdle()

    verify(exactly = 1) { navigator.setRoot(Destination.BookOverview) }
    assertEquals(AddState.Idle, viewModel.addState)
  }

  @Test
  fun addExceptionIsReportedAsFailure() = runTest {
    val uri = "file:///book.zip".toUri()
    coEvery { audiobookFolders.add(uri, FolderType.SingleFile) } throws SecurityException("no persistable grant")
    val repo = repoWithBooks()
    val viewModel = createViewModel(Origin.Default, repo)

    viewModel.add(uri, FileTypeSelection.File)
    testScheduler.advanceUntilIdle()

    val state = viewModel.addState
    assertTrue(state is AddState.Failed)
    assertTrue(state.detail.contains("SecurityException"))
  }

  private fun repoWithBooks(vararg books: Book): BookRepository {
    val booksFlow = MutableStateFlow(books.toList())
    val repo = mockk<BookRepository>()
    every { repo.flow() } returns booksFlow
    coEvery { repo.all() } returns booksFlow.value
    coEvery { repo.get(any()) } returns null
    books.forEach { current -> coEvery { repo.get(current.id) } returns current }
    return repo
  }

  private fun TestScope.createViewModel(
    origin: Origin,
    repo: BookRepository,
  ) = AddContentViewModel(
    audiobookFolders = audiobookFolders,
    navigator = navigator,
    mediaScanTrigger = mediaScanTrigger,
    bookRepository = repo,
    logStore = logStore,
    errorReportSaver = errorReportSaver,
    dispatcherProvider = DispatcherProvider(coroutineContext, coroutineContext, coroutineContext),
    origin = origin,
  )
}
