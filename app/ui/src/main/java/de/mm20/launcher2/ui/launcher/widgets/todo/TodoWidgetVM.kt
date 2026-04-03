package de.mm20.launcher2.ui.launcher.widgets.todo

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.widgets.TodoWidget
import de.mm20.launcher2.widgets.TodoWidgetConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TodoWidgetVM : ViewModel(), KoinComponent {

    private val calendarRepository: CalendarRepository by inject()

    private val widgetConfig = MutableStateFlow(TodoWidgetConfig())

    val tasks = mutableStateOf<List<CalendarEvent>>(emptyList())
    val isGoogleSignedIn = mutableStateOf(false)
    val isAddingTask = mutableStateOf(false)

    private var loadJob: Job? = null

    fun updateWidget(widget: TodoWidget) {
        widgetConfig.value = widget.config
        loadTasks()
    }

    fun onResume() {
        isGoogleSignedIn.value = calendarRepository.isGoogleSignedIn()
        loadTasks()
    }

    private fun loadTasks() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val config = widgetConfig.value
            val endOfToday = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            calendarRepository.findMany(
                from = 0L,
                to = endOfToday,
                excludeCalendars = config.excludedCalendars,
            ).collectLatest { events ->
                val taskEvents = events.filter { it.isTask }
                val filtered = if (config.showCompleted) {
                    taskEvents
                } else {
                    taskEvents.filter { it.isCompleted != true }
                }
                tasks.value = filtered.sortedBy { it.endTime }
            }
        }
    }

    fun toggleTask(event: CalendarEvent) {
        val completed = event.isCompleted ?: return
        viewModelScope.launch {
            calendarRepository.completeTask(event, !completed)
            loadTasks()
        }
    }

    fun signInGoogle(context: Context) {
        val intent = android.content.Intent(context, de.mm20.launcher2.google.GoogleLoginActivity::class.java)
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
