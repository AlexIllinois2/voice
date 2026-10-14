package voice.core.zip

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.datasource.DataSource
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

    /**
     * How many callers currently read from this archive. Archives that are in use are never closed.
     */
    var useCount: Int = 0

    /**
     * Set once the archive got dropped from [openArchives]. It is closed as soon as [useCount] hits zero.
     */
    var evicted: Boolean = false

    private var closed = false

    fun close() {
      if (closed) return
      closed = true
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
      withArchive(file.uri) { it.root.children }
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
      withArchive(zipUri) { it.find(entryPath) }
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
    withArchive(zipUri) { archive ->
      archive.openStream(entryPath).use { input ->
        input.readBytes()
      }
    }
  }

  /**
   * Runs [block] with a [DataSource.Factory] that streams the entry at [uri] straight out of the
   * archive. Compared to [readEntryBytes] only the bytes that are actually consumed get
   * decompressed, which makes scanning a book that is split over many archives a lot faster.
   */
  suspend fun <T> withEntryDataSource(
    uri: Uri,
    block: suspend (DataSource.Factory) -> T,
  ): T {
    val zipUri = requireNotNull(ZipUriCodec.zipUri(uri)) { "Not a zip uri: $uri" }
    val entryPath = requireNotNull(ZipUriCodec.entryPath(uri)) { "Not a zip entry uri: $uri" }
    return withArchive(zipUri) { archive ->
      block(DataSource.Factory { ZipEntryDataSource(uri, archive, entryPath) })
    }
  }

  /**
   * Runs [block] with the archive at [zipUri]. The archive is guaranteed to stay open (and is
   * never closed by [evict]) for the whole duration of [block].
   */
  private suspend fun <T> withArchive(
    zipUri: Uri,
    block: suspend (ZipArchive) -> T,
  ): T {
    val key = zipUri.toString()
    val opened = mutex.withLock {
      openArchives[key]?.also { it.useCount++ }
    } ?: openAndRegister(zipUri, key)
    return try {
      block(opened.archive)
    } finally {
      release(opened)
    }
  }

  private suspend fun openAndRegister(
    zipUri: Uri,
    key: String,
  ): OpenedArchive {
    val opened = openArchive(zipUri)
    return mutex.withLock {
      val existing = openArchives[key]
      if (existing != null) {
        existing.useCount++
        opened.close()
        existing
      } else {
        opened.useCount = 1
        openArchives[key] = opened
        evict()
        opened
      }
    }
  }

  private suspend fun release(opened: OpenedArchive) {
    mutex.withLock {
      opened.useCount--
      if (opened.useCount == 0 && opened.evicted) {
        opened.close()
      }
    }
  }

  // must not suspend: it runs while the mutex is held
  private fun evict() {
    while (openArchives.size > MAX_OPEN_ARCHIVES) {
      // the map is access ordered, so the first entry without readers is the least recently used one
      val eldest = openArchives.entries.firstOrNull { it.value.useCount == 0 } ?: break
      openArchives.remove(eldest.key)
      eldest.value.evicted = true
      try {
        eldest.value.close()
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
    // books are often split over several archives, so keep a couple more around to avoid
    // reopening (and re-reading the central directory of) the same archive over and over
    const val MAX_OPEN_ARCHIVES = 8
  }
}
