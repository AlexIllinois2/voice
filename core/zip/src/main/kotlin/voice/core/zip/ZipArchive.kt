package voice.core.zip

import android.net.Uri
import androidx.core.net.toUri
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import voice.core.common.comparator.NaturalOrderComparator
import voice.core.documentfile.CachedDocumentFile
import java.io.InputStream

/**
 * An opened zip archive with an eagerly built entry tree.
 */
internal class ZipArchive(
  val zipUri: String,
  internal val zipFile: ZipFile,
) {

  private class Entry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val length: Long,
    val lastModified: Long,
    val childPaths: MutableList<String> = mutableListOf(),
  )

  private val entries = HashMap<String, Entry>()
  private val rootChildPaths = mutableListOf<String>()

  val root: CachedDocumentFile

  init {
    buildTree()
    root = documentFileFor("")
  }

  fun find(entryPath: String): CachedDocumentFile? {
    val normalized = entryPath.normalizeEntryPath()
    return if (normalized.isEmpty()) root else entries[normalized]?.let { documentFileFor(it.path) }
  }

  fun openStream(entryPath: String): InputStream {
    return zipFile.getInputStream(headerFor(entryPath))
  }

  fun headerFor(entryPath: String): FileHeader {
    val normalized = entryPath.normalizeEntryPath()
    return zipFile.getFileHeader(normalized)
      ?: zipFile.fileHeaders.firstOrNull { it.fileName.normalizeEntryPath() == normalized }
      ?: throw IllegalArgumentException("No entry for $normalized in $zipUri")
  }

  private fun buildTree() {
    for (header in zipFile.fileHeaders) {
      val path = header.fileName.normalizeEntryPath()
      if (path.isEmpty()) continue
      if (header.isDirectory) {
        ensureDir(path)
        addChildToParent(path, path)
      } else {
        entries[path] = Entry(
          path = path,
          name = path.lastSegment(),
          isDirectory = false,
          length = header.uncompressedSize,
          lastModified = header.lastModifiedTime,
        )
        addChildToParent(path, path)
      }
    }
  }

  private fun addChildToParent(
    entryPath: String,
    childPath: String,
  ) {
    val parent = entryPath.parentPath()
    if (parent == null) {
      rootChildPaths += childPath
    } else {
      ensureDir(parent)
      entries.getValue(parent).childPaths += childPath
    }
  }

  private fun ensureDir(dirPath: String) {
    if (dirPath in entries) return
    entries[dirPath] = Entry(
      path = dirPath,
      name = dirPath.lastSegment(),
      isDirectory = true,
      length = 0L,
      lastModified = 0L,
    )
  }

  private fun documentFileFor(path: String): CachedDocumentFile {
    val entry = entries[path]
    return ZipEntryDocumentFile(
      uri = uriFor(path),
      name = entry?.name,
      isDirectory = entry == null || entry.isDirectory,
      length = entry?.length ?: 0L,
      lastModified = entry?.lastModified ?: 0L,
      children = {
        val childPaths = entry?.childPaths ?: rootChildPaths
        childPaths
          .mapNotNull { entries[it] }
          .sortedWith(compareBy(NaturalOrderComparator.stringComparator) { it.name })
          .map { documentFileFor(it.path) }
      },
    )
  }

  private fun uriFor(entryPath: String): Uri {
    return if (entryPath.isEmpty()) {
      zipUri.toAndroidUri()
    } else {
      ZipUriCodec.encode(zipUri.toAndroidUri(), entryPath)
    }
  }

  private fun String.normalizeEntryPath(): String = replace("\\", "/").trimEnd('/')

  private fun String.parentPath(): String? {
    val index = lastIndexOf('/')
    return if (index <= 0) null else substring(0, index)
  }

  private fun String.lastSegment(): String = substringAfterLast('/')

  private fun String.toAndroidUri(): Uri = toUri()
}
