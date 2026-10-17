package voice.core.data.repo

/**
 * Remembers the zips whose automatic parse has already been attempted.
 *
 * The content of a zip can not change once it has been created, so parsing it a second time
 * yields the same result. That is why every zip is parsed automatically at most once in its
 * lifetime, no matter whether the parse succeeded or failed, and even after the app has been
 * restarted. The state has to be persisted for the last part to hold, which is what this repo
 * is for.
 *
 * Parsing a book on the request of the user is not limited by this, they can reparse a book as
 * often as they like.
 */
public interface ZipParseAttemptRepo {

  /**
   * The zips that have already been parsed automatically, whether that succeeded or not.
   */
  public suspend fun attemptedZips(): Set<String>

  /**
   * Remembers that the automatic parse of [zipUris] has been attempted.
   */
  public suspend fun markAttempted(zipUris: Collection<String>)
}
