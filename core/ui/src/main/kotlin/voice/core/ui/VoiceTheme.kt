package voice.core.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import voice.core.data.ThemeColorScheme
import voice.core.data.ThemeMode
import java.util.concurrent.ConcurrentHashMap

val VoiceBlue = Color(0xFF003b7f)
private val VoiceSecondary = Color(0xFF5E6F95)

// The expressive color scheme is computed from dozens of HCT colors. Doing that inside the
// first composition kept the main thread busy for a noticeable part of the cold start, so the
// scheme is prepared on a background thread and only read here.
private val voiceColorSchemes = ConcurrentHashMap<Boolean, ColorScheme>()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoiceTheme(
  themeMode: ThemeMode = ThemeMode.FollowSystem,
  themeColorScheme: ThemeColorScheme = ThemeColorScheme.VoiceBlue,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    ThemeMode.FollowSystem -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
  }
  val themedContent = remember(content) {
    movableContentOf {
      content()
    }
  }
  if (themeColorScheme == ThemeColorScheme.Dynamic && Build.VERSION.SDK_INT >= 31) {
    MaterialExpressiveTheme(
      colorScheme = systemDynamicColorScheme(darkTheme),
    ) {
      themedContent()
    }
  } else {
    MaterialExpressiveTheme(
      colorScheme = remember(darkTheme) { voiceColorScheme(darkTheme) },
    ) {
      themedContent()
    }
  }
}

/** Computes the voice color scheme ahead of the first composition. Safe to call on any thread. */
fun preloadVoiceColorScheme(isDark: Boolean) {
  voiceColorScheme(isDark)
}

@IgnorableReturnValue
private fun voiceColorScheme(isDark: Boolean): ColorScheme {
  return voiceColorSchemes.getOrPut(isDark) {
    dynamicColorScheme(
      seedColor = VoiceBlue,
      isDark = isDark,
      primary = VoiceBlue,
      secondary = VoiceSecondary,
      style = PaletteStyle.Expressive,
      specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
  }
}

@RequiresApi(31)
@Composable
private fun systemDynamicColorScheme(darkTheme: Boolean): ColorScheme {
  return if (darkTheme) {
    dynamicDarkColorScheme(LocalContext.current)
  } else {
    dynamicLightColorScheme(LocalContext.current)
  }
}
