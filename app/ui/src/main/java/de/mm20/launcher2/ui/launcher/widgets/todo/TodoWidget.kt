package de.mm20.launcher2.ui.launcher.widgets.todo

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.widgets.TodoWidget
import de.mm20.launcher2.widgets.Widget

@Composable
fun TodoWidget(
    widget: TodoWidget,
    onWidgetUpdate: (widget: Widget) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val viewModel: TodoWidgetVM = viewModel(key = "todo-widget-${widget.id}")

    LaunchedEffect(widget) {
        viewModel.updateWidget(widget)
    }
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume()
        }
    }

    val tasks by viewModel.tasks
    val isGoogleSignedIn by viewModel.isGoogleSignedIn
    val isAddingTask by viewModel.isAddingTask

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 8.dp),
    ) {
        if (!isGoogleSignedIn) {
            MissingPermissionBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .padding(horizontal = 12.dp),
                text = stringResource(R.string.todo_widget_sign_in_google),
                onClick = { viewModel.signInGoogle(context) },
            )
        }

        if (tasks.isEmpty() && isGoogleSignedIn) {
            Text(
                text = stringResource(R.string.todo_widget_no_items),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        tasks.forEach { task ->
            TaskRow(
                task = task,
                onToggle = { viewModel.toggleTask(task) },
                onOpen = { task.launch(context, null) },
            )
        }

        if (isAddingTask) {
            AddTaskRow(
                onSubmit = { title -> viewModel.submitNewTask(title) },
                onCancel = { viewModel.cancelAddTask() },
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.startAddTask() }
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
                Text(
                    text = stringResource(R.string.todo_widget_add_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AddTaskRow(
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

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
        TextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = stringResource(R.string.todo_widget_add_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onSubmit(title.trim()) }
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun TaskRow(
    task: CalendarEvent,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val completed = task.isCompleted == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = completed,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(40.dp),
        )

        Text(
            text = task.label,
            style = if (completed) {
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
                .alpha(if (completed) 0.5f else 1f),
        )
    }
}
