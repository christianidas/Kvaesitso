package de.mm20.launcher2.ui.launcher.widgets.todo

import android.content.Context
import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.ULocale
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.widgets.TodoWidget
import de.mm20.launcher2.widgets.Widget
import java.sql.Date
import java.time.LocalDate
import java.time.ZoneId

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
    val selectedDate by viewModel.selectedDate

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        // Header row with date navigation
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = { viewModel.previousDay() }) {
                Icon(
                    painter = painterResource(R.drawable.chevron_backward_24px),
                    contentDescription = null,
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                var showDropdown by remember { mutableStateOf(false) }
                TextButton(onClick = { showDropdown = true }) {
                    Text(
                        text = formatDay(context, selectedDate),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        painterResource(R.drawable.arrow_drop_down_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenuPopup(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                ) {
                    val availableDates = viewModel.availableDates
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShapes(),
                    ) {
                        for ((i, date) in availableDates.withIndex()) {
                            DropdownMenuItem(
                                shape = if (availableDates.size == 1) MenuDefaults.standaloneItemShape
                                else when (i) {
                                    0 -> MenuDefaults.leadingItemShape
                                    availableDates.lastIndex -> MenuDefaults.trailingItemShape
                                    else -> MenuDefaults.middleItemShape
                                },
                                text = { Text(formatDay(context, date)) },
                                onClick = {
                                    viewModel.selectDate(date)
                                    showDropdown = false
                                },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = { viewModel.nextDay() }) {
                Icon(
                    painter = painterResource(R.drawable.chevron_forward_24px),
                    contentDescription = null,
                )
            }
            IconButton(onClick = { viewModel.startAddTask() }) {
                Icon(
                    painter = painterResource(R.drawable.add_24px),
                    contentDescription = stringResource(R.string.todo_widget_create_task),
                )
            }
            IconButton(onClick = { viewModel.openTasksApp(context) }) {
                Icon(
                    painter = painterResource(R.drawable.open_in_new_24px),
                    contentDescription = stringResource(R.string.todo_widget_open_tasks),
                )
            }
        }

        AnimatedContent(
            targetState = Pair(selectedDate, tasks),
            transitionSpec = {
                when {
                    initialState.first == targetState.first -> fadeIn() togetherWith fadeOut()
                    initialState.first < targetState.first -> {
                        fadeIn() + slideIn {
                            IntOffset((it.width * 0.25f).toInt(), 0)
                        } togetherWith fadeOut() + slideOut {
                            IntOffset((it.width * -0.25f).toInt(), 0)
                        }
                    }
                    else -> {
                        fadeIn() + slideIn {
                            IntOffset((it.width * -0.25f).toInt(), 0)
                        } togetherWith fadeOut() + slideOut {
                            IntOffset((it.width * 0.25f).toInt(), 0)
                        }
                    }
                }
            },
        ) { (_, currentTasks) ->
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                if (!isGoogleSignedIn) {
                    MissingPermissionBanner(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        text = stringResource(R.string.todo_widget_sign_in_google),
                        onClick = { viewModel.signInGoogle(context) },
                    )
                }

                if (isAddingTask) {
                    AddTaskRow(
                        onSubmit = { title -> viewModel.submitNewTask(title) },
                        onCancel = { viewModel.cancelAddTask() },
                    )
                }

                if (currentTasks.isEmpty() && isGoogleSignedIn) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.small
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.todo_widget_no_items),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else if (currentTasks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.small
                            ),
                    ) {
                        currentTasks.forEachIndexed { index, task ->
                            if (index != 0) {
                                HorizontalDivider()
                            }
                            SwipeableTaskRow(
                                task = task,
                                onToggle = { viewModel.toggleTask(task) },
                                onOpen = { task.launch(context, null) },
                                onPostpone = { viewModel.postponeTask(task) },
                                onDelete = { viewModel.deleteTask(task) },
                            )
                        }
                    }
                }
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

    BackHandler { onCancel() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.small
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onCancel() },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.close_24px),
                contentDescription = stringResource(R.string.close),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskRow(
    task: CalendarEvent,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onPostpone: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onPostpone()
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            val iconColor = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> R.drawable.chevron_forward_24px
                SwipeToDismissBoxValue.EndToStart -> R.drawable.delete_24px
                else -> R.drawable.chevron_forward_24px
            }
            val label = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> stringResource(R.string.todo_widget_postpone)
                SwipeToDismissBoxValue.EndToStart -> stringResource(R.string.todo_widget_delete_task)
                else -> ""
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 16.dp),
                contentAlignment = alignment,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = iconColor,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = label,
                        tint = iconColor,
                    )
                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = iconColor,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
    ) {
        TaskRow(
            task = task,
            onToggle = onToggle,
            onOpen = onOpen,
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
            .background(MaterialTheme.colorScheme.surface)
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

private fun formatDay(context: Context, day: LocalDate): String {
    val today = LocalDate.now()
    return when {
        today == day -> context.getString(R.string.date_today)
        today.plusDays(1) == day -> context.getString(R.string.date_tomorrow)
        else -> DateFormat.getInstanceForSkeleton(
            Calendar.getInstance(ULocale.getDefault()),
            "E MMMM d",
            ULocale.getDefault(),
        ).format(Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    }
}
