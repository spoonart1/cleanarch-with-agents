package io.github.spoonart1.cleanarchwithagent.checklists.impl.list

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun CreateChecklistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New checklist") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.testTag(TestTags.CHECKLIST_TITLE_FIELD),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                // A blank title would create an unnamed checklist the user
                // cannot tell apart from any other.
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag(TestTags.CONFIRM_CREATE),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
