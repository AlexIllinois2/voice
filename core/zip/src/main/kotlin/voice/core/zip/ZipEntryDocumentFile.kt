package voice.core.zip

import android.net.Uri
import voice.core.documentfile.CachedDocumentFile

/**
 * A [CachedDocumentFile] backed by a zip archive entry.
 */
internal class ZipEntryDocumentFile(
  override val uri: Uri,
  override val name: String?,
  override val isDirectory: Boolean,
  override val length: Long,
  override val lastModified: Long,
  children: () -> List<CachedDocumentFile>,
) : CachedDocumentFile {

  override val isFile: Boolean = !isDirectory

  override val children: List<CachedDocumentFile> by lazy(children)

  override fun toString(): String = "ZipEntryDocumentFile($uri)"
}
