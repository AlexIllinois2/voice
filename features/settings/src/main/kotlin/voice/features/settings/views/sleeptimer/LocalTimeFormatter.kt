package voice.features.settings.views.sleeptimer

import android.content.res.Configuration
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun rememberLocalTimeFormatter(): DateTimeFormatter {
  val context = LocalContext.current
  val locale = primaryLocale(LocalConfiguration.current)
  return remember(context, locale) {
    val is24HourFormat = DateFormat.is24HourFormat(context)
    localTimeFormatter(is24HourFormat = is24HourFormat, locale = locale)
  }
}

@Suppress("DEPRECATION")
private fun primaryLocale(configuration: Configuration): Locale =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    configuration.locales.get(0) ?: configuration.locale
  } else {
    configuration.locale
  }

internal fun localTimeFormatter(
  is24HourFormat: Boolean,
  locale: Locale,
): DateTimeFormatter {
  val pattern = if (is24HourFormat) "HH:mm" else "hh:mm a"
  return DateTimeFormatter.ofPattern(pattern, locale)
}
