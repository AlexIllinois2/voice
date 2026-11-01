package voice.core.ui

import android.app.Application
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import coil.Coil
import coil.ImageLoader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import voice.core.data.ThemeMode
import voice.core.data.store.ThemeModeStore
import voice.core.initializer.AppInitializer

@ContributesIntoSet(AppScope::class)
class UIAppStartInitializer(
  @ThemeModeStore
  private val themeModeStore: DataStore<ThemeMode>,
  private val scope: CoroutineScope,
) : AppInitializer {

  override fun onAppStart(application: Application) {
    Coil.setImageLoader(
      ImageLoader.Builder(application)
        .addLastModifiedToFileCacheKey(false)
        .build(),
    )
    themeModeStore.data
      .distinctUntilChanged()
      .onEach { themeMode ->
        val nightMode = when (themeMode) {
          ThemeMode.FollowSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
          ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
          ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        scope.launch(Dispatchers.Default) {
          preloadVoiceColorScheme(isDark(application, themeMode))
        }
      }
      .launchIn(scope)
  }

  private fun isDark(application: Application, themeMode: ThemeMode): Boolean {
    return when (themeMode) {
      ThemeMode.FollowSystem -> {
        val uiMode = application.resources.configuration.uiMode
        uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
      }
      ThemeMode.Light -> false
      ThemeMode.Dark -> true
    }
  }
}
