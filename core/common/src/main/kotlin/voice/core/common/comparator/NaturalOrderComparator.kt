package voice.core.common.comparator

import android.net.Uri

object NaturalOrderComparator {

  /**
   * The scheme used for entries inside of a zip archive. See `voice.core.zip.ZipUriCodec`.
   */
  private const val ZIP_SCHEME = "zip"

  val stringComparator: Comparator<String> = NaturalComparator

  val uriComparator = object : Comparator<Uri> {
    override fun compare(
      lhs: Uri,
      rhs: Uri,
    ): Int {
      val lhsSegments = lhs.comparableSegments()
      val rhsSegments = rhs.comparableSegments()

      val leftSize = lhsSegments.size
      val rightSize = rhsSegments.size

      // compare parents only and return if one differs
      var i = 0
      val toLeft = leftSize - 1
      val toRight = rightSize - 1
      while (i < toLeft && i < toRight) {
        val pl = lhsSegments[i]
        val pr = rhsSegments[i]
        if (pl != pr) {
          return stringComparator.compare(pl, pr)
        }
        i++
      }

      // if sizes are the same
      return if (leftSize == rightSize) {
        stringComparator.compare(lhsSegments.lastOrNull() ?: "", rhsSegments.lastOrNull() ?: "")
      } else {
        rightSize - leftSize
      }
    }
  }

  /**
   * The path segments to compare, percent decoded. Otherwise escaped names (chinese ones in
   * particular) would be compared by their escaped form, which does not sort naturally.
   */
  private fun Uri.comparableSegments(): List<String> {
    return if (scheme == ZIP_SCHEME) {
      // The first segment holds the uri of the archive and the following ones the entry names.
      // They are encoded as single segments, so they must not be split up any further.
      pathSegments.map(::decodeSegment)
    } else {
      pathSegments.flatMap { it.split("/").map(::decodeSegment) }
    }
  }

  private fun decodeSegment(segment: String): String {
    return try {
      Uri.decode(segment)
    } catch (e: IllegalArgumentException) {
      // not a valid escape sequence, compare it as is
      segment
    }
  }
}

fun Set<String>.sortedNaturally(): List<String> = sortedWith(NaturalOrderComparator.stringComparator)
