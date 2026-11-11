package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import voice.core.transcode.TranscodeState
import voice.core.strings.R as StringsR

/**
 * A modal overlay that shows the progress of the currently running transcode, so the waiting
 * player does not look broken.
 */
@Composable
internal fun TranscodeOverlay(
  state: TranscodeState.Running,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6F))
      .padding(24.dp)
      .fillMaxWidth(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(StringsR.string.transcode_running_title, state.fileName),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1F),
          )
          Spacer(modifier = Modifier.width(16.dp))
          CircularProgressIndicator()
        }
        Spacer(modifier = Modifier.height(16.dp))
        val progress = state.progressPercent
        if (progress == null) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
          LinearProgressIndicator(
            progress = { progress / 100F },
            modifier = Modifier.fillMaxWidth(),
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
        TranscodeSteps(state.step)
      }
    }
  }
}

@Composable
private fun TranscodeSteps(currentStep: TranscodeState.Step) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    TranscodeState.Step.entries.forEachIndexed { index, step ->
      val isCurrent = step == currentStep
      val isDone = step.ordinal < currentStep.ordinal
      val alpha = if (isCurrent || isDone) 1F else 0.5F
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (isCurrent) {
          CircularProgressIndicator(
            modifier = Modifier
              .width(20.dp)
              .height(20.dp),
            strokeWidth = 2.dp,
          )
        } else {
          Text(
            text = "✓",
            color = if (isDone) {
              MaterialTheme.colorScheme.primary
            } else {
              Color.Transparent
            },
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        val text = stringResource(StringsR.string.transcode_running_step, index + 1, TranscodeState.STEP_COUNT, step.displayText())
        Text(
          text = text,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
      }
    }
  }
}

@Composable
private fun TranscodeState.Step.displayText(): String {
  return when (this) {
    TranscodeState.Step.ReadingSource -> stringResource(StringsR.string.transcode_step_reading_source)
    TranscodeState.Step.Transcoding -> stringResource(StringsR.string.transcode_step_transcoding)
    TranscodeState.Step.Saving -> stringResource(StringsR.string.transcode_step_saving)
  }
}

/**
 * A dialog that explains in detail why a transcode has failed.
 */
@Composable
internal fun TranscodeErrorDialog(
  state: TranscodeState.Failed,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.transcode_failed_confirm))
      }
    },
    title = {
      Text(stringResource(StringsR.string.transcode_failed_title))
    },
    text = {
      Column {
        Text(state.fileName, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(state.message)
      }
    },
  )
}
