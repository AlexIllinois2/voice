package voice.core.logging.inapp

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import voice.core.initializer.AppInitializer
import voice.core.logging.api.Logger

@ContributesIntoSet(AppScope::class)
public class InAppLogWriterInitializer(private val writer: InAppLogWriter) : AppInitializer {

  override fun onAppStart(application: Application) {
    Logger.install(writer)
  }
}
