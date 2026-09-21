package voice.features.folderPicker

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import voice.core.strings.R as StringsR
import voice.core.zip.ZipPasswordRequester
import voice.core.zip.ZipPasswordUiRequest

@Composable
fun ZipPasswordDialogHost(requester: ZipPasswordRequester) {
  val request by requester.state.collectAsState()
  request?.let {
    ZipPasswordDialog(
      request = it,
      onSubmit = requester::submit,
    )
  }
}

@Composable
private fun ZipPasswordDialog(
  request: ZipPasswordUiRequest,
  onSubmit: (String?) -> Unit,
) {
  var password by remember(request) { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = { onSubmit(null) },
    title = {
      Text(text = stringResource(StringsR.string.zip_password_title))
    },
    text = {
      OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(StringsR.string.zip_password_field)) },
        supportingText = {
          if (request.failedAttempt) {
            Text(text = stringResource(StringsR.string.zip_password_wrong))
          } else {
            Text(text = request.displayName)
          }
        },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(
        onClick = { onSubmit(password) },
      ) {
        Text(text = stringResource(StringsR.string.common_dialog_ok))
      }
    },
    dismissButton = {
      TextButton(
        onClick = { onSubmit(null) },
      ) {
        Text(text = stringResource(StringsR.string.zip_password_skip))
      }
    },
  )
}
