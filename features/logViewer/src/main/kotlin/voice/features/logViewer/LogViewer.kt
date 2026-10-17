package voice.features.logViewer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.rootGraphAs
import voice.core.logging.api.Logger
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FILE_NAME_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
  .withZone(ZoneId.systemDefault())

@Composable
private fun LogViewer(
  viewState: LogViewerViewState,
  viewModel: LogViewerViewModel,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  val snackbarHostState = remember { SnackbarHostState() }
  val savedMessage = stringResource(StringsR.string.logs_saved)
  val failedMessage = stringResource(StringsR.string.logs_save_failed)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val saveLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/plain"),
  ) { uri ->
    if (uri != null) {
      scope.launch {
        val saved = withContext(Dispatchers.IO) {
          runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
              stream.write(viewModel.logText().toByteArray())
            } ?: error("Could not open an output stream for $uri")
          }.isSuccess
        }
        snackbarHostState.showSnackbar(
          message = if (saved) savedMessage else failedMessage,
        )
      }
    }
  }
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState)
    },
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
          Text(stringResource(StringsR.string.logs_title))
        },
        navigationIcon = {
          IconButton(onClick = viewModel::close) {
            Icon(
              imageVector = VoiceIcons.Close,
              contentDescription = stringResource(StringsR.string.common_action_close),
            )
          }
        },
        actions = {
          if (viewState.entries.isNotEmpty()) {
            IconButton(
              onClick = {
                saveLauncher.launch("voice-parse-log-${FILE_NAME_TIME_FORMAT.format(Instant.now())}.txt")
              },
            ) {
              Icon(
                imageVector = VoiceIcons.Download,
                contentDescription = stringResource(StringsR.string.logs_save),
              )
            }
          }
        },
      )
    },
  ) { contentPadding ->
    if (viewState.entries.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(contentPadding),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(StringsR.string.logs_empty),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      SelectionContainer {
        LazyColumn(contentPadding = contentPadding) {
          items(viewState.entries, key = { it.key }) { entry ->
            LogEntryRow(entry)
          }
        }
      }
    }
  }
}

@Composable
private fun LogEntryRow(entry: LogViewerViewState.LogEntryViewState) {
  val isError = entry.severity == Logger.Severity.Error
  ListItem(
    supportingContent = {
      Text(
        text = entry.message,
        style = MaterialTheme.typography.bodySmall,
      )
    },
  ) {
    Text(
      text = "${stringResource(severityTextRes(entry))} · ${entry.time}",
      style = MaterialTheme.typography.labelLarge,
      color = if (isError) {
        MaterialTheme.colorScheme.error
      } else {
        MaterialTheme.colorScheme.tertiary
      },
    )
  }
}
@Composable
private fun severityTextRes(entry: LogViewerViewState.LogEntryViewState): Int {
  return if (entry.severity == Logger.Severity.Error) {
    StringsR.string.logs_severity_error
  } else {
    StringsR.string.logs_severity_warning
  }
}

@ContributesTo(AppScope::class)
interface LogViewerGraph {
  val logViewerViewModel: LogViewerViewModel
}

@ContributesTo(AppScope::class)
interface LogViewerProvider {

  @Provides
  @IntoSet
  fun logViewerNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.LogViewer> { key ->
    NavEntry(key) {
      LogViewer()
    }
  }
}

@Composable
fun LogViewer() {
  val viewModel = retain<LogViewerViewModel> { rootGraphAs<LogViewerGraph>().logViewerViewModel }
  LogViewer(viewModel.viewState(), viewModel)
}
