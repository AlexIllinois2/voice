package voice.core.scanner

/**
 * Detailed progress of an ongoing media scan.
 *
 * [bookIndex] and [bookCount] describe the book that is currently being parsed. [entryIndex] and
 * [entryCount] describe the audio files inside of it — for zips these are the entries of the
 * archive. While the entries are still being collected (e.g. while a zip archive is opened),
 * [entryCount] is 0. [archiveCopy] is set while an archive is copied into the cache because its
 * entries cannot be read in place.
 *
 * The book fields default to an empty context so that entry level producers (the chapter parser)
 * can omit them; the scanner fills them in before the progress reaches the UI.
 */
public data class ScanProgress(
  val bookName: String = "",
  val bookIndex: Int = 0,
  val bookCount: Int = 0,
  val entryName: String? = null,
  val entryIndex: Int = 0,
  val entryCount: Int = 0,
  val archiveCopy: ArchiveCopy? = null,
  val phase: Phase = Phase.Scanning,
) {

  /**
   * A zip archive that is currently copied into the cache.
   */
  public data class ArchiveCopy(
    val name: String,
    val copiedBytes: Long,
    val totalBytes: Long,
  )

  /**
   * Whether files are still being listed, or chapter durations are backfilled in the background.
   */
  public enum class Phase {
    Scanning,
    Enriching,
  }

  /**
   * The overall scan progress between 0 and 1.
   */
  public val fraction: Float
    get() {
      val bookFraction = if (bookCount > 0) (bookIndex - 1).toFloat() / bookCount else 0F
      val entryFraction = when {
        archiveCopy != null && archiveCopy.totalBytes > 0 ->
          (archiveCopy.copiedBytes.toFloat() / archiveCopy.totalBytes).coerceIn(0F, 1F)
        entryCount > 0 -> entryIndex.toFloat() / entryCount
        else -> 0F
      }
      return (bookFraction + entryFraction / bookCount.coerceAtLeast(1)).coerceIn(0F, 1F)
    }
}
