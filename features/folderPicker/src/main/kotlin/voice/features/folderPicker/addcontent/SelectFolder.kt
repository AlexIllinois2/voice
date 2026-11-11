package voice.features.folderPicker.addcontent

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import voice.features.folderPicker.R
import voice.features.folderPicker.folderPicker.FileTypeSelection
import voice.navigation.Origin
import voice.core.strings.R as StringsR

@Composable
internal fun SelectFolder(
  onBack: () -> Unit,
  onAdd: (FileTypeSelection, Uri) -> Unit,
  origin: Origin,
  addState: AddState,
  savedPath: String?,
  saveError: String?,
  onDismissError: () -> Unit,
  onSaveError: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      SelectFolderAppBar(onBack)
    },
    content = { contentPadding ->
      val adding = addState is AddState.Adding
      Column(Modifier.padding(contentPadding)) {
        if (shouldShowImage()) {
          Image(
            modifier = Modifier
              .weight(1F)
              .heightIn(max = 400.dp)
              .padding(top = 32.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
              .align(Alignment.CenterHorizontally),
            painter = painterResource(id = R.drawable.folder_type_artwork),
            contentDescription = null,
          )
        }

        Column(Modifier.weight(2F)) {
          Spacer(modifier = Modifier.size(16.dp))

          Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(
              when (origin) {
                Origin.Default -> StringsR.string.folder_add_title_default
                Origin.Onboarding -> StringsR.string.folder_add_title_onboarding
              },
            ),
            style = MaterialTheme.typography.displayMedium,
          )
          Spacer(modifier = Modifier.size(4.dp))
          Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(StringsR.string.folder_add_type_subtitle),
            style = MaterialTheme.typography.bodyLarge,
          )
          Spacer(modifier = Modifier.size(24.dp))
          if (adding) {
            Text(
              modifier = Modifier.padding(horizontal = 24.dp),
              text = stringResource(StringsR.string.add_book_adding),
              style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.size(8.dp))
            LinearProgressIndicator(
              modifier = Modifier
                .padding(horizontal = 24.dp)
                .size(width = 200.dp, height = 4.dp),
            )
          }
          SelectFolderButtonRow(onAdd, enabled = !adding)
        }
      }
    },
  )
  if (addState is AddState.Failed) {
    ErrorDialog(
      detail = addState.detail,
      savedPath = savedPath,
      saveError = saveError,
      onDismiss = onDismissError,
      onSave = onSaveError,
    )
  }
}

@Composable
private fun ErrorDialog(
  detail: String,
  savedPath: String?,
  saveError: String?,
  onDismiss: () -> Unit,
  onSave: (String) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(stringResource(StringsR.string.add_book_error_title))
    },
    text = {
      Column(
        Modifier
          .heightIn(max = 360.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = detail,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
        )
        if (savedPath != null) {
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = stringResource(StringsR.string.add_book_error_saved, savedPath),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        if (saveError != null) {
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = stringResource(StringsR.string.add_book_error_save_failed, saveError),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onSave(detail) }) {
        Text(stringResource(StringsR.string.add_book_error_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.common_action_close))
      }
    },
  )
}

@Composable
private fun shouldShowImage(): Boolean {
  val localWindowInfo = LocalWindowInfo.current
  val thresholdPx = with(LocalDensity.current) { 600.dp.toPx() }
  return localWindowInfo.containerSize.height > thresholdPx
}

@Composable
@Preview
private fun SelectFolderPreview() {
  SelectFolder(
    onBack = {},
    onAdd = { _, _ -> },
    origin = Origin.Default,
    addState = AddState.Idle,
    savedPath = null,
    saveError = null,
    onDismissError = {},
    onSaveError = {},
  )
}
