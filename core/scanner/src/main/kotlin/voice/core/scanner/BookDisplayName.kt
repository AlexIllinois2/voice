package voice.core.scanner

import voice.core.documentfile.CachedDocumentFile
import voice.core.logging.api.Logger

/**
 * The name a book is listed with. It is derived from the file or folder that was added as an
 * audiobook and is never overwritten by the metadata of the book's chapters.
 */
internal fun CachedDocumentFile.bookDisplayName(): String {
  val fileName = name
  if (fileName == null) {
    val fallback = uri.toString()
      .removePrefix("/storage/emulated/0/")
      .removePrefix("/storage/emulated/")
      .removePrefix("/storage/")
    Logger.w("Could not parse fileName from $this. Fallback to $fallback")
    return fallback
  }
  return if (isFile) fileName.substringBeforeLast(".") else fileName
}
