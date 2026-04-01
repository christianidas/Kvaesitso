package de.mm20.launcher2.ui.launcher.widgets.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.widgets.IntervalType
import de.mm20.launcher2.widgets.RecurrenceRule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@Composable
fun RecurrenceRuleEditorSheet(
    expanded: Boolean,
    rule: RecurrenceRule?,
    onSave: (RecurrenceRule) -> Unit,
    onDismiss: () -> Unit,
) {
    DismissableBottomSheet(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        var templateText by rememberSaveable(rule) { mutableStateOf(rule?.templateText ?: "") }
        var intervalType by rememberSaveable(rule) {
            mutableStateOf(rule?.intervalType ?: IntervalType.WEEKLY)
        }
        var interval by rememberSaveable(rule) { mutableStateOf(rule?.interval ?: 1) }
        var daysOfWeek by rememberSaveable(rule) {
            mutableStateOf(rule?.daysOfWeek ?: listOf(7))
        }
        var dayOfMonth by rememberSaveable(rule) { mutableStateOf(rule?.dayOfMonth ?: 1) }
        var timeOfDay by rememberSaveable(rule) { mutableStateOf(rule?.timeOfDay ?: "") }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            OutlinedTextField(
                value = templateText,
                onValueChange = { templateText = it },
                label = { Text(stringResource(R.string.todo_widget_task_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IntervalType.entries.forEach { type ->
                    val selected = intervalType == type
                    FilledTonalButton(
                        onClick = { intervalType = type },
                        modifier = Modifier.weight(1f),
                        colors = if (selected) ButtonDefaults.filledTonalButtonColors()
                        else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(
                            text = when (type) {
                                IntervalType.DAILY -> stringResource(R.string.todo_widget_interval_daily)
                                IntervalType.WEEKLY -> stringResource(R.string.todo_widget_interval_weekly)
                                IntervalType.MONTHLY -> stringResource(R.string.todo_widget_interval_monthly)
                            },
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (intervalType == IntervalType.WEEKLY) {
                Text(
                    text = stringResource(R.string.todo_widget_interval_weekly),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DayOfWeek.entries.forEach { dow ->
                        val dayValue = dow.value
                        val selected = daysOfWeek.contains(dayValue)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                daysOfWeek = if (selected) {
                                    daysOfWeek.filter { it != dayValue }.ifEmpty { listOf(dayValue) }
                                } else {
                                    daysOfWeek + dayValue
                                }
                            },
                            label = {
                                Text(
                                    dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (intervalType == IntervalType.MONTHLY) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.todo_widget_day_of_month),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { if (dayOfMonth > 1) dayOfMonth-- }) {
                        Icon(painterResource(R.drawable.keyboard_arrow_down_24px), null)
                    }
                    Text(
                        text = dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { if (dayOfMonth < 31) dayOfMonth++ }) {
                        Icon(painterResource(R.drawable.keyboard_arrow_up_24px), null)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = timeOfDay,
                onValueChange = { timeOfDay = it },
                label = { Text(stringResource(R.string.todo_widget_time_of_day)) },
                placeholder = { Text("HH:mm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(24.dp))

            FilledTonalButton(
                onClick = {
                    if (templateText.isBlank()) return@FilledTonalButton
                    val parsedTime = timeOfDay.takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) }
                        ?.let {
                            val parts = it.split(":")
                            String.format("%02d:%02d", parts[0].toInt(), parts[1].toInt())
                        }
                    val newRule = RecurrenceRule(
                        id = rule?.id ?: UUID.randomUUID().toString(),
                        templateText = templateText.trim(),
                        intervalType = intervalType,
                        interval = interval.coerceAtLeast(1),
                        daysOfWeek = if (intervalType == IntervalType.WEEKLY) daysOfWeek else null,
                        dayOfMonth = if (intervalType == IntervalType.MONTHLY) dayOfMonth else null,
                        timeOfDay = parsedTime,
                        lastMaterialized = rule?.lastMaterialized,
                    )
                    onSave(newRule)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = templateText.isNotBlank(),
            ) {
                Text(stringResource(R.string.todo_widget_save))
            }
        }
    }
}

fun formatRecurrenceSchedule(rule: RecurrenceRule): String {
    val parts = mutableListOf<String>()
    when (rule.intervalType) {
        IntervalType.DAILY -> {
            if (rule.interval == 1) parts.add("Daily")
            else parts.add("Every ${rule.interval} days")
        }
        IntervalType.WEEKLY -> {
            val dayNames = rule.daysOfWeek?.map { dayValue ->
                DayOfWeek.of(dayValue).getDisplayName(
                    TextStyle.SHORT,
                    Locale.getDefault()
                )
            }?.joinToString(", ") ?: ""
            if (rule.interval == 1) parts.add("Weekly on $dayNames")
            else parts.add("Every ${rule.interval} weeks on $dayNames")
        }
        IntervalType.MONTHLY -> {
            val dom = rule.dayOfMonth ?: 1
            if (rule.interval == 1) parts.add("Monthly on day $dom")
            else parts.add("Every ${rule.interval} months on day $dom")
        }
    }
    if (rule.timeOfDay != null) {
        parts.add("at ${rule.timeOfDay}")
    }
    return parts.joinToString(" ")
}
