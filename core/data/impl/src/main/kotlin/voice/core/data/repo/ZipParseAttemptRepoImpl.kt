package voice.core.data.repo

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.store.ZipParseAttemptStore
import voice.core.logging.api.Logger
import java.io.IOException

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class ZipParseAttemptRepoImpl(
  @ZipParseAttemptStore private val store: DataStore<Set<String>>,
) : ZipParseAttemptRepo {

  override suspend fun attemptedZips(): Set<String> {
    return try {
      store.data.first()
    } catch (e: IOException) {
      // a broken file must not keep the library from being scanned, the affected zips are
      // parsed automatically once more instead
      Logger.w(e, "Could not read the zips that were parsed automatically")
      emptySet()
    }
  }

  override suspend fun markAttempted(zipUris: Collection<String>) {
    if (zipUris.isEmpty()) return
    try {
      store.updateData { attempted -> attempted + zipUris }
    } catch (e: IOException) {
      Logger.w(e, "Could not remember the automatically parsed zips $zipUris")
    }
  }
}
