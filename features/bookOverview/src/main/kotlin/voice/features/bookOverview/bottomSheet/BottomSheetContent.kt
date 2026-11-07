package voice.features.bookOverview.bottomSheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

@Composable
internal fun BottomSheetContent(
  state: EditBookBottomSheetState,
  onItemClick: (BottomSheetItem) -> Unit,
) {
  Column {
    state.items.forEach { item ->
      ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable {
          onItemClick(item)
        },
        leadingContent = {
          Icon(
            imageVector = item.icon,
            contentDescription = stringResource(item.titleRes),
          )
        },
      ) {
        Text(text = stringResource(item.titleRes))
      }
    }
  }
}
