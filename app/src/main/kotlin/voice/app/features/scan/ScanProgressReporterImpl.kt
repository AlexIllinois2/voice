package voice.app.features.scan

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import voice.core.logging.api.Logger
import voice.core.playback.notification.MainActivityIntentProvider
import voice.core.scanner.ScanProgress
import voice.core.scanner.ScanProgressReporter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reports the progress of the background scan as a notification.
 */
@ContributesBinding(AppScope::class)
@Inject
class ScanProgressReporterImpl(
  private val context: Context,
  private val mainActivityIntentProvider: MainActivityIntentProvider,
) : ScanProgressReporter {

  private val notificationManager: NotificationManager =
    context.getSystemService(NotificationManager::class.java)
  private val serviceStarted = AtomicBoolean(false)

  override fun report(progress: ScanProgress) {
    val notification = ScanNotification.build(
      context = context,
      progress = progress,
      contentIntent = mainActivityIntentProvider.toCurrentBook(),
    )
    startServiceIfNeeded()
    notificationManager.notify(ScanNotification.NOTIFICATION_ID, notification)
  }

  override fun dismiss() {
    notificationManager.cancel(ScanNotification.NOTIFICATION_ID)
    if (serviceStarted.compareAndSet(true, false)) {
      runCatching {
        context.stopService(Intent(context, ScanService::class.java))
      }
        .onFailure { error -> Logger.w(error, "Failed to stop the scan service") }
    }
  }

  /**
   * Starting a foreground service is not allowed while the app is in the background, in which case
   * the progress is only reported as a plain notification.
   */
  private fun startServiceIfNeeded() {
    if (!serviceStarted.compareAndSet(false, true)) return
    runCatching {
      ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java))
    }
      .onFailure { error ->
        serviceStarted.set(false)
        Logger.w(error, "Failed to start the scan service")
      }
  }
}
