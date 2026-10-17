package voice.core.zip

import voice.core.documentfile.CachedDocumentFile
import java.util.Locale

/**
 * Whether [file] is a zip archive. An archive is listed as a folder of its entries, it never
 * counts as a single audio file.
 */
fun CachedDocumentFile.isZipArchive(): Boolean {
  return isFile && name?.lowercase(Locale.US)?.endsWith(".zip") == true
}
