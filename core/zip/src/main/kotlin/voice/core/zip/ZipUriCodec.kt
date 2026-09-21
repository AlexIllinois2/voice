package voice.core.zip

import android.net.Uri
import androidx.core.net.toUri

object ZipUriCodec {

  const val SCHEME = "zip"

  fun isZipUri(uri: Uri): Boolean = uri.scheme == SCHEME

  fun encode(zipUri: Uri, entryPath: String): Uri {
    val encodedZip = Uri.encode(zipUri.toString())
    val encodedEntry = entryPath.split("/")
      .filter { it.isNotEmpty() }
      .joinToString("/") { Uri.encode(it) }
    // The triple slash keeps the encoded zip uri as the first path segment
    // instead of the authority.
    return "$SCHEME:///$encodedZip/$encodedEntry".toUri()
  }

  fun zipUri(entryUri: Uri): Uri? {
    val first = entryUri.pathSegments.firstOrNull() ?: return null
    return first.toUri()
  }

  fun entryPath(entryUri: Uri): String? {
    val segments = entryUri.pathSegments
    if (segments.size < 2) return null
    return segments.drop(1).joinToString("/")
  }

  fun entryName(entryUri: Uri): String? {
    return entryUri.pathSegments.lastOrNull()
  }
}
