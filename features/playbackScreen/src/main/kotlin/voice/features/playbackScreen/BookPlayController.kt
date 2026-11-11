package voice.features.playbackScreen

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.transcode.TranscodeState
import voice.features.playbackScreen.view.BookPlayView
import voice.features.playbackScreen.view.TranscodeErrorDialog
import voice.features.playbackScreen.view.TranscodeOverlay
import voice.features.sleepTimer.SleepTimerDialog
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@Composable
fun BookPlayScreen(
  bookId: BookId,
  modifier: Modifier = Modifier,
) {
  val viewModel = retain(bookId.value) {
    rootGraphAs<BookPlayGraph>()
      .bookPlayViewModelFactory
      .create(bookId)
  }
  val snackbarHostState = remember { SnackbarHostState() }
  val dialogState = viewModel.dialogState.value
  val transcodeState = viewModel.transcodeState()
  val viewState = viewModel.viewState()
  if (viewState == null && transcodeState == null) return
  val bookmarkAddedMessage = stringResource(StringsR.string.bookmark_added_snackbar)
  val batteryOptimizationMessage = stringResource(StringsR.string.playback_battery_optimization_rationale)
  val batteryOptimizationAction = stringResource(StringsR.string.playback_battery_optimization_action)
  val transcodeRunningTemplate = stringResource(StringsR.string.transcode_running_title)
  val autoTranscodeRunning = (transcodeState as? TranscodeState.Running)?.takeIf { it.auto }
  LaunchedEffect(autoTranscodeRunning?.fileName) {
    if (autoTranscodeRunning != null) {
      // The previous chapter keeps playing during an automatic chapter transition, so the
      // conversion is only announced non intrusively and disappears once it is done.
      snackbarHostState.showSnackbar(
        message = transcodeRunningTemplate.format(autoTranscodeRunning.fileName),
        duration = SnackbarDuration.Indefinite,
      )
    } else {
      snackbarHostState.currentSnackbarData?.dismiss()
    }
  }
  LaunchedEffect(viewModel) {
    viewModel.viewEffects.collect { viewEffect ->
      when (viewEffect) {
        BookPlayViewEffect.BookmarkAdded -> {
          snackbarHostState.showSnackbar(message = bookmarkAddedMessage)
        }
        BookPlayViewEffect.RequestIgnoreBatteryOptimization -> {
          val result = snackbarHostState.showSnackbar(
            message = batteryOptimizationMessage,
            duration = SnackbarDuration.Long,
            actionLabel = batteryOptimizationAction,
          )
          if (result == SnackbarResult.ActionPerformed) {
            viewModel.onBatteryOptimizationRequested()
          }
        }
      }
    }
  }
  Box(modifier = modifier.fillMaxSize()) {
    viewState?.let { nonNullViewState ->
      BookPlayView(
        nonNullViewState,
        bookId = bookId,
        onPlayClick = viewModel::playPause,
        onFastForwardClick = viewModel::fastForward,
        onRewindClick = viewModel::rewind,
        onSeek = viewModel::seekTo,
        onBookmarkClick = viewModel::onBookmarkClick,
        onBookmarkLongClick = viewModel::onBookmarkLongClick,
        onSleepTimerClick = viewModel::toggleSleepTimer,
        onVolumeBoostClick = viewModel::onVolumeGainIconClick,
        onSpeedChangeClick = viewModel::onPlaybackSpeedIconClick,
        onCloseClick = viewModel::onCloseClick,
        onSkipToNext = viewModel::next,
        onSkipToPrevious = viewModel::previous,
        onCurrentChapterClick = viewModel::onCurrentChapterClick,
        useLandscapeLayout = LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE,
        snackbarHostState = snackbarHostState,
      )
    }
    (transcodeState as? TranscodeState.Running)?.let { running ->
      if (!running.auto) {
        TranscodeOverlay(state = running)
      }
    }
  }
  (transcodeState as? TranscodeState.Failed)?.let { failed ->
    TranscodeErrorDialog(state = failed, onDismiss = viewModel::dismissTranscodeError)
  }
  when (val dialog = dialogState) {
    null -> Unit
    is BookPlayDialogViewState.SpeedDialog -> {
      SpeedDialog(dialog, viewModel)
    }
    is BookPlayDialogViewState.VolumeGainDialog -> {
      VolumeGainDialog(dialog, viewModel)
    }
    is BookPlayDialogViewState.SleepTimer -> {
      SleepTimerDialog(
        viewState = dialog.viewState,
        onDismiss = viewModel::dismissDialog,
        onIncrementSleepTime = viewModel::incrementSleepTime,
        onDecrementSleepTime = viewModel::decrementSleepTime,
        onAcceptSleepTime = viewModel::onAcceptSleepTime,
        onAcceptSleepAtEndOfChapter = viewModel::onAcceptSleepAtEndOfChapter,
      )
    }
  }
}

@ContributesTo(AppScope::class)
interface BookPlayGraph {
  val bookPlayViewModelFactory: BookPlayViewModel.Factory
}

@ContributesTo(AppScope::class)
interface BookPlayProvider {

  @Provides
  @IntoSet
  fun bookPlayNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Playback> { key ->
    NavEntry(key) {
      BookPlayScreen(bookId = key.bookId)
    }
  }
}
