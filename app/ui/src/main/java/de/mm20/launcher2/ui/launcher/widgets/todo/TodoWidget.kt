package de.mm20.launcher2.ui.launcher.widgets.todo

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.TodoItem
import de.mm20.launcher2.widgets.TodoWidget
import de.mm20.launcher2.widgets.Widget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TodoWidget(
    widget: TodoWidget,
    onWidgetUpdate: (widget: Widget) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val viewModel: TodoWidgetVM =
        viewModel(key = "todo-widget-${widget.id}", factory = TodoWidgetVM.Factory)

    LaunchedEffect(widget) {
        viewModel.updateWidget(widget)
    }
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume()
        }
    }

    val allItems by viewModel.items
    val showCompleted = widget.config.showCompleted

    val visibleItems = remember(allItems, showCompleted) {
        val filtered = if (showCompleted) {
            val today = LocalDate.now()
            allItems.filter { item ->
                if (!item.completed) true
                else {
                    val completedAt = item.completedAt ?: return@filter false
                    val completedDate = Instant.parse(completedAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    completedDate == today
                }
            }
        } else {
            allItems.filter { !it.completed }
        }
        filtered.sortedBy { it.completed }
    }

    var showRecurrenceEditor by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 8.dp),
    ) {
        if (visibleItems.isEmpty()) {
            Text(
                text = stringResource(R.string.todo_widget_no_items),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        visibleItems.forEach { item ->
            TodoItemRow(
                item = item,
                onToggle = { viewModel.toggleItem(item.id) },
                onDelete = { viewModel.deleteItem(item.id) },
            )
        }

        // Inline add field
        var newItemText by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.add_24px),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 12.dp, end = 4.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (newItemText.isNotBlank()) {
                            viewModel.addItem(newItemText)
                            newItemText = ""
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    if (newItemText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.todo_widget_add_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    innerTextField()
                },
            )
            IconButton(
                onClick = { showRecurrenceEditor = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.repeat_24px),
                    contentDescription = stringResource(R.string.todo_widget_add_recurring),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    RecurrenceRuleEditorSheet(
        expanded = showRecurrenceEditor,
        rule = null,
        onSave = { newRule ->
            viewModel.addRecurrenceRule(newRule)
            showRecurrenceEditor = false
        },
        onDismiss = { showRecurrenceEditor = false },
    )
}

@Composable
private fun TodoItemRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.completed,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(40.dp),
        )

        Text(
            text = item.text,
            style = if (item.completed) {
                MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = TextDecoration.LineThrough,
                )
            } else {
                MaterialTheme.typography.bodyMedium
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(if (item.completed) 0.5f else 1f),
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.close_24px),
                contentDescription = stringResource(R.string.todo_widget_delete),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
