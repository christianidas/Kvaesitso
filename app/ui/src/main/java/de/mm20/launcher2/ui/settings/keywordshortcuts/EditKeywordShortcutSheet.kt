package de.mm20.launcher2.ui.settings.keywordshortcuts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.builders.KeywordShortcutBuilder
import de.mm20.launcher2.searchactions.builders.KeywordShortcutBuilder.ActionType
import de.mm20.launcher2.ui.component.DismissableBottomSheet

@Composable
fun EditKeywordShortcutSheet(
    expanded: Boolean,
    initial: KeywordShortcutBuilder?,
    onSave: (KeywordShortcutBuilder) -> Unit,
    onDismiss: () -> Unit,
) {
    DismissableBottomSheet(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        var keyword by remember(initial) { mutableStateOf(initial?.keyword ?: "") }
        var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
        var actionType by remember(initial) { mutableStateOf(initial?.actionType ?: ActionType.Sms) }
        var phoneNumber by remember(initial) { mutableStateOf(initial?.phoneNumber ?: "") }
        var messageBody by remember(initial) { mutableStateOf(initial?.messageBody ?: "") }
        var silentSms by remember(initial) { mutableStateOf(initial?.silentSms ?: false) }
        var url by remember(initial) { mutableStateOf(initial?.url ?: "") }
        var httpMethod by remember(initial) { mutableStateOf(initial?.httpMethod ?: "POST") }
        var httpBody by remember(initial) { mutableStateOf(initial?.httpBody ?: "") }
        var httpHeadersText by remember(initial) {
            mutableStateOf(
                initial?.httpHeaders?.entries?.joinToString("\n") { "${it.key}: ${it.value}" } ?: ""
            )
        }

        val isValid = keyword.isNotBlank() && label.isNotBlank() && when (actionType) {
            ActionType.Sms -> phoneNumber.isNotBlank() && messageBody.isNotBlank()
            ActionType.OpenUrl -> url.isNotBlank()
            ActionType.HttpRequest -> url.isNotBlank()
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = if (initial == null) "New Keyword Shortcut" else "Edit Keyword Shortcut",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("Keyword") },
                placeholder = { Text("e.g. omw") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                placeholder = { Text("e.g. Text Mom - On my way") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Action Type",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ActionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = actionType == type,
                        onClick = { actionType = type },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ActionType.entries.size,
                        ),
                    ) {
                        Text(
                            when (type) {
                                ActionType.Sms -> "SMS"
                                ActionType.OpenUrl -> "URL"
                                ActionType.HttpRequest -> "HTTP"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(actionType == ActionType.Sms) {
                Column {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone number") },
                        placeholder = { Text("+1 555 123 4567") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = messageBody,
                        onValueChange = { messageBody = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Send silently",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = if (silentSms) "Send immediately without opening SMS app"
                                else "Open SMS app with message pre-filled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = silentSms,
                            onCheckedChange = { silentSms = it },
                        )
                    }
                }
            }

            AnimatedVisibility(actionType == ActionType.OpenUrl) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(actionType == ActionType.HttpRequest) {
                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        placeholder = { Text("https://ha.local/api/services/...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("GET", "POST", "PUT").forEachIndexed { index, method ->
                            SegmentedButton(
                                selected = httpMethod == method,
                                onClick = { httpMethod = method },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = 3,
                                ),
                            ) {
                                Text(method)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = httpBody,
                        onValueChange = { httpBody = it },
                        label = { Text("Body (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = httpHeadersText,
                        onValueChange = { httpHeadersText = it },
                        label = { Text("Headers (optional, one per line)") },
                        placeholder = { Text("Authorization: Bearer token\nContent-Type: application/json") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val headers = httpHeadersText
                        .lines()
                        .filter { it.contains(":") }
                        .associate {
                            val (k, v) = it.split(":", limit = 2)
                            k.trim() to v.trim()
                        }
                        .takeIf { it.isNotEmpty() }

                    val defaultIcon = when (actionType) {
                        ActionType.Sms -> SearchActionIcon.Message
                        ActionType.OpenUrl -> SearchActionIcon.Website
                        ActionType.HttpRequest -> SearchActionIcon.Website
                    }

                    onSave(
                        KeywordShortcutBuilder(
                            label = label.trim(),
                            keyword = keyword.trim(),
                            actionType = actionType,
                            phoneNumber = phoneNumber.takeIf { actionType == ActionType.Sms },
                            messageBody = messageBody.takeIf { actionType == ActionType.Sms },
                            silentSms = silentSms && actionType == ActionType.Sms,
                            url = url.takeIf { actionType != ActionType.Sms },
                            httpMethod = httpMethod.takeIf { actionType == ActionType.HttpRequest },
                            httpBody = httpBody.takeIf { actionType == ActionType.HttpRequest && httpBody.isNotBlank() },
                            httpHeaders = headers?.takeIf { actionType == ActionType.HttpRequest },
                            icon = initial?.icon ?: defaultIcon,
                            iconColor = initial?.iconColor ?: 0,
                            customIcon = initial?.customIcon,
                        )
                    )
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (initial == null) "Create" else "Save")
            }
        }
    }
}
