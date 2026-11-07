package voice.features.playbackScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface ChapterSelectionProvider {

  @Provides
  @IntoSet
  fun chapterSelectionNavEntryProvider(): NavEntryProvider<*> =
    NavEntryProvider<Destination.ChapterSelection> { key ->
      NavEntry(key) {
        ChapterSelectionScreen(bookId = key.bookId)
      }
    }
}

@Composable
fun ChapterSelectionScreen(bookId: BookId) {
  val viewModel = retain(bookId.value) {
    rootGraphAs<BookPlayGraph>()
      .bookPlayViewModelFactory
      .create(bookId)
  }
  val viewState = viewModel.chapterListViewState() ?: return
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = viewState.title) },
        navigationIcon = {
          IconButton(onClick = viewModel::onCloseClick) {
            Icon(
              imageVector = VoiceIcons.ArrowBack,
              contentDescription = stringResource(StringsR.string.common_action_close),
            )
          }
        },
      )
    },
  ) { contentPadding ->
    val selectedIndex = viewState.items.indexOfFirst { it.active }
    // -1 because we want to show the previous chapter on the screen
    val initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0)
    LazyColumn(
      state = rememberLazyListState(initialFirstVisibleItemIndex = initialFirstVisibleItemIndex),
      contentPadding = contentPadding,
      content = {
        items(viewState.items, key = { it.number }) { chapter ->
          val description = stringResource(StringsR.string.playback_chapter_current_content_description)
          ListItem(
            colors = ListItemDefaults.colors(
              containerColor = if (chapter.active) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                Color.Transparent
              },
            ),
            modifier = Modifier
              .fillMaxWidth()
              .padding(3.dp)
              .clip(shape = RoundedCornerShape(12.dp))
              .semantics {
                selected = chapter.active
                if (chapter.active) contentDescription = description
              }
              .clickable { viewModel.onChapterClick(number = chapter.number) },
            leadingContent = {
              Text(text = chapter.number.toString())
            },
            trailingContent = {
              Text(text = chapter.time)
            },
          ) {
            Text(text = chapter.name)
          }
        }
      },
    )
  }
}
