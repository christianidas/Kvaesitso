package de.mm20.launcher2.ui.launcher.widgets.todo

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.calendar.providers.GoogleTasksCalendarEvent
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.widgets.TodoWidget
import de.mm20.launcher2.widgets.TodoWidgetConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.max
import kotlin.math.min

class TodoWidgetVM : ViewModel(), KoinComponent {

    private val calendarRepository: CalendarRepository by inject()

    private val widgetConfig = MutableStateFlow(TodoWidgetConfig())

    val tasks = mutableStateOf<List<CalendarEvent>>(emptyList())
    val isGoogleSignedIn = mutableStateOf(false)
    val isAddingTask = mutableStateOf(false)

    val selectedDate = mutableStateOf(LocalDate.now())
    var availableDates = listOf(LocalDate.now())
        private set

    private var allTasks: List<CalendarEvent> = emptyList()
    private var loadJob: Job? = null

    fun updateWidget(widget: TodoWidget) {
        widgetConfig.value = widget.config
        loadTasks()
    }

    fun onResume() {
        isGoogleSignedIn.value = calendarRepository.isGoogleSignedIn()
        selectedDate.value = LocalDate.now()
        loadTasks()
    }

    fun nextDay() {
        val dates = availableDates
        val currentIndex = dates.indexOf(selectedDate.value)
        val index = min(currentIndex + 1, dates.lastIndex)
        selectDate(dates[index])
    }

    fun previousDay() {
        val dates = availableDates
        val currentIndex = dates.indexOf(selectedDate.value)
        val index = max(currentIndex - 1, 0)
        selectDate(dates[index])
    }

    fun selectDate(date: LocalDate) {
        if (availableDates.contains(date)) {
            selectedDate.value = date
            updateFilteredTasks()
        }
    }

    private fun updateFilteredTasks() {
        val config = widgetConfig.value
        val date = selectedDate.value
        val today = LocalDate.now()

        val filtered = allTasks.filter { task ->
            val taskDate = task.endTime.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            if (date == today) {
                // Today shows tasks due today or overdue
                taskDate <= today
            } else {
                taskDate == date
            }
        }.let { list ->
            if (config.showCompleted) list
            else list.filter { it.isCompleted != true }
        }.sortedWith(
            compareBy<CalendarEvent> { it.isCompleted == true }.thenBy { it.endTime }
        )

        tasks.value = filtered
    }

    private fun loadTasks() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val config = widgetConfig.value
            val endOfRange = LocalDate.now().plusDays(14)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            calendarRepository.findMany(
                from = 0L,
                to = endOfRange,
                excludeCalendars = config.excludedCalendars,
            ).collectLatest { events ->
                val taskEvents = events.filter { it.isTask }
                allTasks = taskEvents

                // Compute available dates from task due dates
                val today = LocalDate.now()
                val dates = taskEvents.mapNotNull { task ->
                    val taskDate = Instant.ofEpochMilli(task.endTime)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    // Overdue tasks collapse into "today"
                    if (taskDate < today) today else taskDate
                }.union(listOf(today))
                    .distinct()
                    .sorted()
                availableDates = dates.toList()

                // Keep selected date if still valid, otherwise reset to today
                val current = selectedDate.value
                if (!availableDates.contains(current)) {
                    selectedDate.value = today
                }

                updateFilteredTasks()
            }
        }
    }

    fun toggleTask(event: CalendarEvent) {
        val completed = event.isCompleted ?: return
        val newCompleted = !completed
        // Optimistic UI update
        val config = widgetConfig.value
        tasks.value = tasks.value.map {
            if (it.key == event.key && it is GoogleTasksCalendarEvent) {
                it.copy(isCompleted = newCompleted)
            } else it
        }.let { updated ->
            if (!config.showCompleted) updated.filter { it.isCompleted != true }
            else updated
        }.sortedWith(
            compareBy<CalendarEvent> { it.isCompleted == true }.thenBy { it.endTime }
        )
        // Sync with API in background
        viewModelScope.launch {
            calendarRepository.completeTask(event, newCompleted)
            loadTasks()
        }
    }

    fun signInGoogle(context: Context) {
        val intent = android.content.Intent(context, de.mm20.launcher2.google.GoogleLoginActivity::class.java)
        context.startActivity(intent)
    }

    fun openTasksApp(context: Context) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setData(android.net.Uri.parse("https://tasks.google.com/"))
            .setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun startAddTask() {
        isAddingTask.value = true
    }

    fun cancelAddTask() {
        isAddingTask.value = false
    }

    fun submitNewTask(title: String) {
        if (title.isBlank()) {
            isAddingTask.value = false
            return
        }
        isAddingTask.value = false
        viewModelScope.launch {
            calendarRepository.createGoogleTask(title)
            loadTasks()
        }
    }
}
