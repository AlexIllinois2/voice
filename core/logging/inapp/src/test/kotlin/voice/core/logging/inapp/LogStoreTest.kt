package voice.core.logging.inapp

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogStoreTest {

  @Test
  fun `new entries are appended`() = runTest {
    val store = LogStore()

    store.add(entry(message = "first"))
    store.add(entry(message = "second"))

    assertEquals(
      expected = listOf("first", "second"),
      actual = store.entries.value.map { it.message },
    )
  }

  @Test
  fun `entries are capped`() = runTest {
    val store = LogStore()
    val count = 250

    repeat(count) { index ->
      store.add(entry(message = "entry $index"))
    }

    val entries = store.entries.value
    assertEquals(expected = 200, actual = entries.size)
    assertEquals(expected = "entry ${count - 200}", actual = entries.first().message)
    assertEquals(expected = "entry ${count - 1}", actual = entries.last().message)
  }

  @Test
  fun `entries are exposed as a flow`() = runTest {
    val store = LogStore()
    assertTrue(store.entries.value.isEmpty())

    store.add(entry(message = "appears in the flow"))

    assertEquals(expected = 1, actual = store.entries.value.size)
  }

  private fun entry(message: String): LogEntry {
    return LogEntry(
      severity = voice.core.logging.api.Logger.Severity.Warn,
      timestamp = System.currentTimeMillis(),
      message = message,
    )
  }
}
