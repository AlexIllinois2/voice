package voice.core.data.repo

import kotlinx.coroutines.test.runTest
import voice.core.data.repo.internals.MemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZipParseAttemptRepoTest {

  private val store = MemoryDataStore<Set<String>>(emptySet())
  private val repo = ZipParseAttemptRepoImpl(store)

  @Test
  fun `remembers the zips that were parsed automatically`() = runTest {
    assertTrue(repo.attemptedZips().isEmpty())

    repo.markAttempted(listOf("content://book1.zip", "content://book2.zip"))
    repo.markAttempted(listOf("content://book2.zip", "content://book3.zip"))

    // a new repo stands in for the next app start, the zips are read back from the store
    assertEquals(
      expected = setOf("content://book1.zip", "content://book2.zip", "content://book3.zip"),
      actual = ZipParseAttemptRepoImpl(store).attemptedZips(),
    )
  }

  @Test
  fun `marking an empty collection keeps the store as it is`() = runTest {
    repo.markAttempted(listOf("content://book1.zip"))

    repo.markAttempted(emptyList())

    assertEquals(expected = setOf("content://book1.zip"), actual = repo.attemptedZips())
  }
}
