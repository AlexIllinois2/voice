package voice.features.bookOverview.views.topbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import voice.core.data.BookId
import voice.features.bookOverview.search.BookSearchContent
import voice.features.bookOverview.search.BookSearchViewState

@Suppress("DEPRECATION")
@Composable
internal fun ColumnScope.BookOverviewSearchBar(
  horizontalPadding: Dp,
  onQueryChange: (String) -> Unit,
  onActiveChange: (Boolean) -> Unit,
  onBookFolderClick: () -> Unit,
  onSettingsClick: () -> Unit,
  onSearchBookClick: (BookId) -> Unit,
  searchActive: Boolean,
  showAddBookHint: Boolean,
  showFolderPickerIcon: Boolean,
  searchViewState: BookSearchViewState,
) {
  // The search field is only focusable while the search is active. Otherwise a focus change
  // (e.g. focus restored after returning to this screen) expands the search and the field
  // keeps eating focus, which made it impossible to leave the search again.
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(searchActive) {
    // a tap on the collapsed bar opens the search, but the disabled input field does not
    // receive focus, so it is requested explicitly to show the keyboard
    if (searchActive) {
      focusRequester.requestFocus()
    }
  }
  SearchBar(
    inputField = {
      SearchBarDefaults.InputField(
        query = if (searchActive) {
          searchViewState.query
        } else {
          ""
        },
        onQueryChange = onQueryChange,
        onSearch = onQueryChange,
        expanded = searchActive,
        onExpandedChange = onActiveChange,
        modifier = Modifier.focusRequester(focusRequester),
        enabled = searchActive,
        leadingIcon = {
          TopBarLeadingIcon(
            searchActive = searchActive,
            onActiveChange = onActiveChange,
          )
        },
        trailingIcon = {
          TopBarTrailingIcon(
            searchActive = searchActive,
            showAddBookHint = showAddBookHint,
            showFolderPickerIcon = showFolderPickerIcon,
            onBookFolderClick = onBookFolderClick,
            onSettingsClick = onSettingsClick,
          )
        },
      )
    },
    expanded = searchActive,
    onExpandedChange = onActiveChange,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = horizontalPadding)
      .then(
        if (searchActive) {
          Modifier
        } else {
          Modifier.clickable { onActiveChange(true) }
        },
      ),
    content = {
      BookSearchContent(
        viewState = searchViewState,
        contentPadding = PaddingValues(),
        onQueryChange = onQueryChange,
        onBookClick = onSearchBookClick,
      )
    },
  )
}
