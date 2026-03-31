package de.mm20.launcher2.ui.launcher.widgets.todo

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.services.widgets.WidgetsService
import de.mm20.launcher2.widgets.IntervalType
import de.mm20.launcher2.widgets.RecurrenceRule
import de.mm20.launcher2.widgets.TodoItem
import de.mm20.launcher2.widgets.TodoWidget
import de.mm20.launcher2.widgets.TodoWidgetConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class TodoWidgetVM(
    private val widgetsService: WidgetsService,
) : ViewModel() {
    private val widget = MutableStateFlow<TodoWidget?>(null)

    val items = mutableStateOf<List<TodoItem>>(emptyList())
    val recurrenceRules = mutableStateOf<List<RecurrenceRule>>(emptyList())

    fun updateWidget(widget: TodoWidget) {
        this.widget.value = widget
        recurrenceRules.value = widget.config.recurrenceRules
        materializeRecurringItems(widget)
    }

    fun onResume() {
        val w = widget.value ?: return
        materializeRecurringItems(w)
    }

    private fun materializeRecurringItems(widget: TodoWidget) {
        val now = ZonedDateTime.now()
        val config = widget.config
        var updatedItems = config.items.toMutableList()
        var updatedRules = config.recurrenceRules.toMutableList()
        var changed = false

        // Prune old completed items
        val cutoff = now.minusHours(config.autoDeleteCompletedAfterHours.toLong())
        val cutoffInstant = cutoff.toInstant().toString()
        val beforeSize = updatedItems.size
        updatedItems.removeAll { item ->
            val completedAt = item.completedAt
            item.completed && completedAt != null && completedAt < cutoffInstant
        }
        if (updatedItems.size != beforeSize) changed = true

        // Materialize recurring items
        for (i in updatedRules.indices) {
            val rule = updatedRules[i]
            var lastMaterialized = rule.lastMaterialized?.let { LocalDate.parse(it) }
            var materialized = 0

            while (materialized < 3) {
                val nextDue = computeNextDue(rule, lastMaterialized, now.zone) ?: break
                if (nextDue.isAfter(now)) break

                val newItem = TodoItem(
                    id = UUID.randomUUID().toString(),
                    text = rule.templateText,
                    createdAt = Instant.now().toString(),
                    recurrenceRuleId = rule.id,
                    dueAt = nextDue.toInstant().toString(),
                )
                updatedItems.add(newItem)
                lastMaterialized = nextDue.toLocalDate()
                materialized++
                changed = true
            }

            if (materialized > 0) {
                updatedRules[i] = rule.copy(lastMaterialized = lastMaterialized.toString())
            }
        }

        if (changed) {
            val newConfig = config.copy(
                items = updatedItems,
                recurrenceRules = updatedRules,
            )
            val newWidget = widget.copy(config = newConfig)
            this.widget.value = newWidget
            recurrenceRules.value = updatedRules
            widgetsService.updateWidget(newWidget)
        }

        items.value = updatedItems
    }

    private fun computeNextDue(
        rule: RecurrenceRule,
        lastMaterialized: LocalDate?,
        zone: ZoneId,
    ): ZonedDateTime? {
        val time = rule.timeOfDay?.let { LocalTime.parse(it) } ?: LocalTime.MIDNIGHT

        if (lastMaterialized == null) {
            // First materialization: compute the first occurrence at or after today
            val today = LocalDate.now(zone)
            return when (rule.intervalType) {
                IntervalType.DAILY -> today.atTime(time).atZone(zone)
                IntervalType.WEEKLY -> {
                    val days = rule.daysOfWeek ?: return null
                    val nextDay = days.map { DayOfWeek.of(it) }
                        .map { dow ->
                            val candidate = today.with(TemporalAdjusters.nextOrSame(dow))
                            candidate
                        }
                        .minOrNull() ?: return null
                    nextDay.atTime(time).atZone(zone)
                }
                IntervalType.MONTHLY -> {
                    val dom = rule.dayOfMonth ?: return null
                    val clampedDom = dom.coerceAtMost(today.lengthOfMonth())
                    val candidate = today.withDayOfMonth(clampedDom)
                    if (candidate.isBefore(today)) {
                        val nextMonth = today.plusMonths(1)
                        nextMonth.withDayOfMonth(dom.coerceAtMost(nextMonth.lengthOfMonth()))
                            .atTime(time).atZone(zone)
                    } else {
                        candidate.atTime(time).atZone(zone)
                    }
                }
            }
        }

        return when (rule.intervalType) {
            IntervalType.DAILY -> {
                lastMaterialized.plusDays(rule.interval.toLong()).atTime(time).atZone(zone)
            }
            IntervalType.WEEKLY -> {
                val days = rule.daysOfWeek ?: return null
                val daysOfWeek = days.map { DayOfWeek.of(it) }.sorted()

                // Find the next day of week after lastMaterialized
                var candidate: LocalDate? = null
                for (dow in daysOfWeek) {
                    val next = lastMaterialized.with(TemporalAdjusters.next(dow))
                    // If within the same week (or interval weeks ahead)
                    if (candidate == null || next.isBefore(candidate)) {
                        candidate = next
                    }
                }
                candidate?.atTime(time)?.atZone(zone)
            }
            IntervalType.MONTHLY -> {
                val dom = rule.dayOfMonth ?: return null
                val nextMonth = lastMaterialized.plusMonths(rule.interval.toLong())
                val clampedDom = dom.coerceAtMost(nextMonth.lengthOfMonth())
                nextMonth.withDayOfMonth(clampedDom).atTime(time).atZone(zone)
            }
        }
    }

    fun addItem(text: String) {
        if (text.isBlank()) return
        val w = widget.value ?: return
        val newItem = TodoItem(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            createdAt = Instant.now().toString(),
        )
        val newConfig = w.config.copy(items = w.config.items + newItem)
        val newWidget = w.copy(config = newConfig)
        widget.value = newWidget
        items.value = newConfig.items
        widgetsService.updateWidget(newWidget)
    }

    fun toggleItem(itemId: String) {
        val w = widget.value ?: return
        val newItems = w.config.items.map { item ->
            if (item.id == itemId) {
                if (item.completed) {
                    item.copy(completed = false, completedAt = null)
                } else {
                    item.copy(completed = true, completedAt = Instant.now().toString())
                }
            } else item
        }
        val newConfig = w.config.copy(items = newItems)
        val newWidget = w.copy(config = newConfig)
        widget.value = newWidget
        items.value = newItems
        widgetsService.updateWidget(newWidget)
    }

    fun deleteItem(itemId: String) {
        val w = widget.value ?: return
        val newItems = w.config.items.filter { it.id != itemId }
        val newConfig = w.config.copy(items = newItems)
        val newWidget = w.copy(config = newConfig)
        widget.value = newWidget
        items.value = newItems
        widgetsService.updateWidget(newWidget)
    }

    fun addRecurrenceRule(rule: RecurrenceRule) {
        val w = widget.value ?: return
        val newRules = w.config.recurrenceRules + rule
        val newConfig = w.config.copy(recurrenceRules = newRules)
        val newWidget = w.copy(config = newConfig)
        widget.value = newWidget
        recurrenceRules.value = newRules
        // Materialize immediately for the new rule
        materializeRecurringItems(newWidget)
    }

    fun updateRecurrenceRule(rule: RecurrenceRule) {
        val w = widget.value ?: return
        val newRules = w.config.recurrenceRules.map { if (it.id == rule.id) rule else it }
        val newConfig = w.config.copy(recurrenceRules = newRules)
        val newWidget = w.copy(config = newConfig)
        widget.value = newWidget
        recurrenceRules.value = newRules
        widgetsService.updateWidget(newWidget)
    }

    fun deleteRecurrenceRule(ruleId: String) {
        val w = widget.value ?: return
        val newRules = w.config.recurrenceRules.filter { it.id != ruleId }
        // Also remove any items linked to this rule
        val newItems = w.config.items.filter { it.recurrenceRuleId != ruleId }
        val newConfig = w.config.copy(recurrenceRules = newRules, items = newItems)
        val newWidget = w.copy(config = newConfig)
        widget.value = newWidget
        items.value = newItems
        recurrenceRules.value = newRules
        widgetsService.updateWidget(newWidget)
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                TodoWidgetVM(get())
            }
        }
    }
}
