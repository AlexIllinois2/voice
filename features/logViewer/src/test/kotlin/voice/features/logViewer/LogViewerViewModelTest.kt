package voice.features.logViewer

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import voice.core.logging.api.Logger
import voice.core.logging.inapp.LogEntry
import voice.core.logging.inapp.LogStore
import voice.navigation.Navigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogViewerViewModelTest {

  private val scope = TestScope()
  private val logStore = LogStore()
  private val navigator = mockk<Navigator> {
    every { goBack() } just Runs
  }
  private val viewModel = LogViewerViewModel(
    logStore = logStore,
    navigator = navigator,
  )

  @Test
  fun `view state shows the newest entry first`() = scope.runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      assertEquals(expected = emptyList(), actual = awaitItem().entries.map { it.message })

      logStore.add(entry(message = "first"))
      assertEquals(expected = listOf("first"), actual = awaitItem().entries.map { it.message })

      logStore.add(entry(message = "second"))
      assertEquals(
        expected = listOf("second", "first"),
        actual = awaitItem().entries.map { it.message },
      )
    }
  }

  @Test
  fun `view state exposes severity and formatted time`() = scope.runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      assertEquals(expected = emptyList(), actual = awaitItem().entries.map { it.severity })

      logStore.add(LogEntry(Logger.Severity.Error, 1700000000000, "boom"))
      val state = awaitItem()
      assertEquals(
        expected = listOf(Logger.Severity.Error),
        actual = state.entries.map { it.severity },
      )
      assertEquals(expected = 1, actual = state.entries.size)
      // formatted as HH:mm:ss
      assertEquals(expected = 8, actual = state.entries.single().time.length)
    }
  }

  @Test
  fun `close navigates back`() {
    viewModel.close()

    verify { navigator.goBack() }
  }

  @Test
  fun `log text contains all entries`() {
    logStore.add(LogEntry(Logger.Severity.Error, 1700000000000, "boom"))
    logStore.add(LogEntry(Logger.Severity.Warn, 1700000000001, "warned"))

    val text = viewModel.logText()

    assertTrue(text.contains("Error: boom"))
    assertTrue(text.contains("Warn: warned"))
  }

  private fun entry(message: String): LogEntry {
    return LogEntry(
      severity = Logger.Severity.Warn,
      timestamp = System.currentTimeMillis(),
      message = message,
    )
  }
}
