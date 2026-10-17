package voice.app.features.scan

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import voice.core.logging.api.Logger

/**
 * Keeps the process alive while the durations of the chapters are parsed in the background and
 * holds the notification that reports the progress.
 *
 * The service itself does no work; the progress is reported by [ScanProgressReporterImpl].
 */
class ScanService : Service() {

  override fun onCreate() {
    super.onCreate()
    val notification = ScanNotification.build(
      context = this,
      progress = null,
      contentIntent = null,
    )
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          ScanNotification.NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
      } else {
        startForeground(ScanNotification.NOTIFICATION_ID, notification)
      }
    }
      .onFailure { error ->
        Logger.w(error, "Failed to start the scan service in the foreground")
        stopSelf()
      }
  }

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    super.onDestroy()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
  }
}
