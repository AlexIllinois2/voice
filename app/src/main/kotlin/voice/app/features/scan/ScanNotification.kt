package voice.app.features.scan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import voice.core.scanner.ScanProgress
import voice.core.strings.R as StringsR

/**
 * Builds the notification that reports the progress of the background scan.
 */
internal object ScanNotification {

  const val NOTIFICATION_ID = 2
  const val CHANNEL_ID = "scan_progress"

  fun build(
    context: Context,
    progress: ScanProgress?,
    contentIntent: PendingIntent?,
  ): Notification {
    createChannel(context)

    val indeterminate = progress == null || progress.entryCount == 0
    val max = progress?.entryCount ?: 0
    val current = progress?.entryIndex ?: 0
    val text = if (progress == null) {
      context.getString(StringsR.string.scan_notification_title)
    } else {
      val files = context.getString(StringsR.string.scan_notification_text, current, max)
      val entryName = progress.entryName
      if (entryName.isNullOrBlank()) files else "$files · $entryName"
    }

    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setContentTitle(context.getString(StringsR.string.scan_notification_title))
      .setContentText(text)
      .setSmallIcon(voice.core.playback.R.drawable.ic_notification)
      .setProgress(max, current, indeterminate)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(contentIntent)
      .build()
  }

  private fun createChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
      CHANNEL_ID,
      context.getString(StringsR.string.scan_notification_channel_name),
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      setSound(null, null)
      enableVibration(false)
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }
}
