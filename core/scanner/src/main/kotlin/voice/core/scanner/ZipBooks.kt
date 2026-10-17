package voice.core.scanner

import voice.core.data.Book
import voice.core.data.toUri
import voice.core.zip.ZipUriCodec

/**
 * Whether the chapters of this book live inside a zip archive.
 */
public fun Book.isZipBook(): Boolean {
  return chapters.any { chapter ->
    ZipUriCodec.isZipUri(chapter.id.toUri())
  }
}
