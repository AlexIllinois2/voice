package voice.core.zip

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import voice.core.documentfile.CachedDocumentFile
import voice.core.logging.api.Logger
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Locale

class ZipCancelledException : Exception("Zip password request was cancelled")

/**
 * Provides access to zip archives as if they were regular folders.
 */
@Inject
@SingleIn(AppScope::class)
class ZipArchiveProvider(
  private val context: Application,
  private val passwordsStore: ZipPasswordsStore,
  private val passwordRequester: ZipPasswordRequester,
) {

  private class OpenedArchive(
    val archive: ZipArchive,
    private val resolution: Resolution,
  ) {
    fun close() {
      try {
        archive.zipFile.close()
      } catch (_: Exception) {
      }
      resolution.release()
    }
  }

  private val mutex = Mutex()
  private val openArchives = LinkedHashMap<String, OpenedArchive>(8, 0.75f, true)

  fun isZipFile(file: CachedDocumentFile): Boolean {
    return file.isFile && file.name?.lowercase(Locale.US)?.endsWith(".zip") == true
  }

  /**
   * Returns the children of [file] if it is a zip archive, or `null` if it is not.
   */
  suspend fun childrenIfZip(file: CachedDocumentFile): List<CachedDocumentFile>? {
    if (!isZipFile(file)) return null
    return try {
      open(file.uri).root.children
    } catch (e: ZipCancelledException) {
      emptyList()
    } catch (e: Exception) {
      Logger.w(e, "Failed to open zip ${file.uri}")
      emptyList()
    }
  }

  /**
   * Resolves a `zip://` chapter uri to the document file of the contained entry.
   */
  suspend fun entryFile(uri: Uri): CachedDocumentFile? {
    if (!ZipUriCodec.isZipUri(uri)) return null
    val zipUri = ZipUriCodec.zipUri(uri) ?: return null
    val entryPath = ZipUriCodec.entryPath(uri) ?: return null
    return try {
      open(zipUri).find(entryPath)
    } catch (e: Exception) {
      Logger.w(e, "Failed to resolve zip entry $uri")
      null
    }
  }

  /**
   * Opens the entry at [uri] (must be a `zip://` uri) and reads it fully into memory.
   */
  suspend fun readEntryBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val zipUri = requireNotNull(ZipUriCodec.zipUri(uri))
    val entryPath = requireNotNull(ZipUriCodec.entryPath(uri))
    val archive = open(zipUri)
    archive.openStream(entryPath).use { input ->
      input.readBytes()
    }
  }

  internal suspend fun open(zipUri: Uri): ZipArchive {
    val key = zipUri.toString()
    mutex.withLock {
      openArchives[key]?.let { return it.archive }
    }
    val opened = openArchive(zipUri)
    return mutex.withLock {
      val existing = openArchives[key]
      if (existing != null) {
        opened.close()
        existing.archive
      } else {
        openArchives[key] = opened
        evict()
        opened.archive
      }
    }
  }

  private suspend fun evict() {
    while (openArchives.size > MAX_OPEN_ARCHIVES) {
      val eldest = openArchives.entries.iterator()
      val opened = eldest.next().value
      eldest.remove()
      try {
        opened.close()
      } catch (e: Exception) {
        Logger.w(e, "Failed to close evicted zip archive")
      }
    }
  }

  private class Resolution(
    val file: File,
    private val pfd: ParcelFileDescriptor?,
    private val tempCopy: File?,
  ) {
    fun release() {
      try {
        pfd?.close()
      } catch (_: Exception) {
      }
      tempCopy?.delete()
    }
  }

  private suspend fun openArchive(zipUri: Uri): OpenedArchive = withContext(Dispatchers.IO) {
    val resolution = resolve(zipUri)
    try {
      val zipFile = openZipFile(resolution.file, zipUri)
      val archive = ZipArchive(
        zipUri = zipUri.toString(),
        zipFile = zipFile,
      )
      OpenedArchive(archive, resolution)
    } catch (e: Exception) {
      resolution.release()
      throw e
    }
  }

  private suspend fun openZipFile(
    file: File,
    zipUri: Uri,
  ): ZipFile {
    val zipFile = ZipFile(file).apply { charset = Charsets.UTF_8 }
    val headers = runCatching { zipFile.fileHeaders }.getOrElse {
      Logger.w(it, "Failed to read zip headers with UTF-8, retrying with GBK: $zipUri")
      zipFile.charset = Charset.forName("GBK")
      zipFile.fileHeaders
    }
    if (headers.any { it.fileName.contains('\uFFFD') }) {
      zipFile.charset = Charset.forName("GBK")
    }

    if (!zipFile.isEncrypted) {
      return zipFile
    }

    val displayName = file.name
    var failedAttempt = false
    while (true) {
      val saved: String? = if (failedAttempt) null else try {
        passwordsStore.password(zipUri.toString())
      } catch (e: Exception) {
        Logger.w(e, "Failed to read saved zip password")
        null
      }
      val password = saved
        ?: passwordRequester.request(zipUri, displayName, failedAttempt)
        ?: throw ZipCancelledException()
      zipFile.setPassword(password.toCharArray())
      if (verifyPassword(zipFile)) {
        if (saved == null || failedAttempt) {
          try {
            passwordsStore.store(zipUri.toString(), password)
          } catch (e: Exception) {
            Logger.w(e, "Failed to store zip password")
          }
        }
        return zipFile
      }
      failedAttempt = true
    }
  }

  private fun verifyPassword(zipFile: ZipFile): Boolean {
    return try {
      val header = zipFile.fileHeaders.firstOrNull { !it.isDirectory }
        ?: return true
      zipFile.getInputStream(header).use { input ->
        input.read(ByteArray(64))
      }
      true
    } catch (e: ZipException) {
      if (e.type != ZipException.Type.WRONG_PASSWORD) {
        Logger.w(e, "Zip password verification failed")
      }
      false
    } catch (e: Exception) {
      Logger.w(e, "Zip password verification failed")
      false
    }
  }

  private fun resolve(zipUri: Uri): Resolution {
    return when (zipUri.scheme) {
      "file" -> Resolution(File(zipUri.path ?: throw IllegalArgumentException("Invalid file uri $zipUri")), null, null)
      "content" -> resolveContent(zipUri)
      else -> throw IllegalArgumentException("Unsupported zip uri scheme: $zipUri")
    }
  }

  private fun resolveContent(zipUri: Uri): Resolution {
    val pfd = context.contentResolver.openFileDescriptor(zipUri, "r")
      ?: throw IllegalArgumentException("Cannot open $zipUri")
    // Trick used by pdfium: many content providers are backed by real files,
    // which can be accessed through /proc/self/fd.
    try {
      val fdPath = File("/proc/self/fd/${pfd.fd}")
      val target = runCatching { fdPath.canonicalFile }.getOrNull()
      if (target != null && target.isFile && target.canRead() && target.length() > 0) {
        return Resolution(target, pfd, null)
      }
    } catch (e: Exception) {
      Logger.w(e, "Failed to resolve fd for $zipUri")
    }
    // Fallback: copy the content into the cache dir.
    return try {
      val hash = MessageDigest.getInstance("SHA-1")
        .digest(zipUri.toString().encodeToByteArray())
        .joinToString("") { "%02x".format(it) }
      val copy = File(File(context.cacheDir, "zip_access"), hash)
      copy.parentFile?.mkdirs()
      if (!copy.exists() || copy.length() == 0L) {
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
          copy.outputStream().use { output ->
            input.copyTo(output)
          }
        }
      } else {
        pfd.close()
      }
      Resolution(copy, null, copy)
    } catch (e: Exception) {
      try {
        pfd.close()
      } catch (_: Exception) {
      }
      throw e
    }
  }

  private companion object {
    const val MAX_OPEN_ARCHIVES = 3
  }
}
