package voice.features.bookOverview.views.topbar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import voice.core.data.BookId
import voice.core.scanner.ScanProgress
import voice.core.strings.R as StringsR
import voice.core.ui.VoiceTheme
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.overview.BookOverviewViewState
import voice.features.bookOverview.search.BookSearchViewState
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun BookOverviewTopBar(
  viewState: BookOverviewViewState,
  onBookFolderClick: () -> Unit,
  onSettingsClick: () -> Unit,
  onActiveChange: (Boolean) -> Unit,
  onQueryChange: (String) -> Unit,
  onSearchBookClick: (BookId) -> Unit,
) {
  Column {
    val horizontalPadding by animateDpAsState(
      targetValue = if (viewState.searchActive) 0.dp else 16.dp,
      label = "horizontalPadding",
    )
    BookOverviewSearchBar(
      horizontalPadding = horizontalPadding,
      onQueryChange = onQueryChange,
      onActiveChange = onActiveChange,
      onBookFolderClick = onBookFolderClick,
      onSettingsClick = onSettingsClick,
      onSearchBookClick = onSearchBookClick,
      searchActive = viewState.searchActive,
      showAddBookHint = viewState.showAddBookHint,
      showFolderPickerIcon = viewState.showFolderPickerIcon,
      searchViewState = viewState.searchViewState,
    )
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(viewState.isLoading) {
      if (viewState.isLoading) {
        delay(3.seconds)
      }
      showLoading = viewState.isLoading
    }
    if (showLoading) {
      val scanProgress = viewState.scanProgress
      if (scanProgress != null) {
        LinearProgressIndicator(
          progress = { scanProgress.fraction },
          modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        )
        Text(
          text = scanProgressDetail(scanProgress),
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 4.dp),
        )
      } else {
        LinearProgressIndicator(
          Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun scanProgressDetail(progress: ScanProgress): String {
  val archiveCopy = progress.archiveCopy
  if (archiveCopy != null) {
    val share = if (archiveCopy.totalBytes > 0) {
      "${(archiveCopy.copiedBytes * 100 / archiveCopy.totalBytes).toInt()}%"
    } else {
      "${archiveCopy.copiedBytes / BYTES_PER_MB} MB"
    }
    return stringResource(StringsR.string.scan_progress_copying, archiveCopy.name, share)
  }
  if (progress.phase == ScanProgress.Phase.Enriching) {
    val durations = stringResource(
      StringsR.string.scan_progress_durations,
      progress.entryIndex,
      progress.entryCount,
    )
    val entryName = progress.entryName
    return if (entryName != null) {
      "$durations · $entryName"
    } else {
      durations
    }
  }
  val book = stringResource(
    StringsR.string.scan_progress_book,
    progress.bookName,
    progress.bookIndex,
    progress.bookCount,
  )
  if (progress.entryCount == 0) {
    return "$book · ${stringResource(StringsR.string.scan_progress_preparing)}"
  }
  val entries = stringResource(
    StringsR.string.scan_progress_entries,
    progress.entryIndex,
    progress.entryCount,
  )
  val entryName = progress.entryName
  return if (entryName != null) {
    "$book · $entries · $entryName"
  } else {
    "$book · $entries"
  }
}

private const val BYTES_PER_MB = 1024 * 1024

@Composable
@Preview
private fun BookOverviewTopBarPreview() {
  VoiceTheme {
    BookOverviewTopBar(
      viewState = BookOverviewViewState(
        books = emptyMap(),
        layoutMode = BookOverviewLayoutMode.List,
        playButtonState = BookOverviewViewState.PlayButtonState.Paused,
        showAddBookHint = true,
        showSearchIcon = true,
        isLoading = true,
        scanProgress = null,
        searchActive = true,
        searchViewState = BookSearchViewState.EmptySearch(
          suggestedAuthors = listOf(),
          recentQueries = listOf(),
          query = "",
        ),
        showStoragePermissionBugCard = false,
        showFolderPickerIcon = true,
        dialog = null,
      ),
      onBookFolderClick = {},
      onSettingsClick = {},
      onActiveChange = {},
      onQueryChange = {},
      onSearchBookClick = {},
    )
  }
}
