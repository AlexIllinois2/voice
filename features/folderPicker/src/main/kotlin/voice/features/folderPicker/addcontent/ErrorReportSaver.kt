package voice.features.folderPicker.addcontent

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.zacsweers.metro.Inject
import voice.core.logging.api.Logger
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Saves error reports as plain text files into the public Downloads folder so that the user can
 * pick them up directly from the file manager.
 */
@Inject
class ErrorReportSaver(private val context: Context) {

  fun save(text: String): String {
    val fileName = "voice-add-error-${FILE_NAME_TIME_FORMAT.format(Instant.now())}.txt"
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      saveViaMediaStore(fileName, text)
    } else {
      saveLegacy(fileName, text)
    }
  }

  private fun saveViaMediaStore(
    fileName: String,
    text: String,
  ): String {
    val values = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
      put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
      put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
      ?: throw IOException("MediaStore did not return an uri for $fileName")
    try {
      resolver.openOutputStream(uri)?.use { stream ->
        stream.write(text.toByteArray())
      } ?: throw IOException("Could not open an output stream for $uri")
    } catch (e: Exception) {
      resolver.delete(uri, null, null)
      throw e
    }
    return "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
  }

  private fun saveLegacy(
    fileName: String,
    text: String,
  ): String {
    @Suppress("DEPRECATION")
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    try {
      val file = File(downloads, fileName)
      file.writeText(text)
      return file.absolutePath
    } catch (e: Exception) {
      Logger.w(e, "Could not write $fileName to the public downloads folder")
    }
    // fall back to the app specific external storage, which is always writable
    val appDir = context.getExternalFilesDir(null) ?: context.filesDir
    val fallback = File(appDir, fileName)
    fallback.writeText(text)
    return fallback.absolutePath
  }

  private companion object {
    val FILE_NAME_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
      .withZone(ZoneId.systemDefault())
  }
}
