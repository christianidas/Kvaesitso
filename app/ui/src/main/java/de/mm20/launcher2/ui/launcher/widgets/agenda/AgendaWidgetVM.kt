package de.mm20.launcher2.ui.launcher.widgets.agenda

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.calendar.providers.GoogleTasksCalendarEvent
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.widgets.AgendaWidget
import de.mm20.launcher2.widgets.AgendaWidgetConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Integer.min
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.max

class AgendaWidgetVM : ViewModel(), KoinComponent {

    private val calendarRepository: CalendarRepository by inject()
    private val favoritesService: FavoritesService by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val permissionsManager: PermissionsManager by inject()

    private val widgetConfig = MutableStateFlow(AgendaWidgetConfig())

    val calendarEvents = mutableStateOf<List<CalendarEvent>>(emptyList())
    val taskEvents = mutableStateOf<List<CalendarEvent>>(emptyList())
    val nextEvents = mutableStateOf<List<CalendarEvent>>(emptyList())
    val pinnedCalendarEvents =
        favoritesService.getFavorites(
            includeTypes = listOf("calendar", "tasks.org", "plugin.calendar"),
            minPinnedLevel = PinnedLevel.AutomaticallySorted,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val hiddenPastEvents = mutableStateOf(0)
    val hiddenRunningTasks = mutableStateOf(0)

    val selectedDate = mutableStateOf(LocalDate.now())
    var availableDates = (0L until 14L).map { LocalDate.now().plusDays(it) }
        private set

    val isGoogleSignedIn = mutableStateOf(false)
    val isAddingTask = mutableStateOf(false)

    private var showRunningPastDayEvents = false
    private var showRunningTasks = false

    private var upcomingEvents: List<CalendarEvent> = emptyList()
        set(value) {
            field = value
            updateEvents()
            updateFilteredTasks()
        }

    // Google Tasks specific state
    private var allGoogleTasks: List<CalendarEvent> = emptyList()
    private var googleTasksJob: Job? = null

    fun updateWidget(widget: AgendaWidget) {
        widgetConfig.value = widget.config
    }

    fun nextDay() {
        val dates = availableDates
        val date = selectedDate.value ?: return
        val currentIndex = dates.indexOf(date)
        val index = min(currentIndex + 1, dates.lastIndex)
        selectDate(dates[index])
    }

    fun previousDay() {
        val dates = availableDates
        val date = selectedDate.value ?: return
        val currentIndex = dates.indexOf(date)
        val index = max(currentIndex - 1, 0)
        selectDate(dates[index])
    }

    fun selectDate(date: LocalDate) {
        showRunningPastDayEvents = false
        showRunningTasks = false
        selectedDate.value = date
        updateEvents()
        updateFilteredTasks()
    }

    fun showAllEvents() {
        showRunningPastDayEvents = true
        updateEvents()
    }

    fun showAllTasks() {
        showRunningTasks = true
        updateEvents()
    }

    private fun updateEvents() {
        val date = selectedDate.value ?: return
        val now = System.currentTimeMillis()
        val offset = OffsetDateTime.now().offset
        val dayStart = max(now, date.atStartOfDay().toEpochSecond(offset) * 1000)
        val dayEnd = date.plusDays(1).atStartOfDay().toEpochSecond(offset) * 1000

        // Filter to non-task events only for the calendar events list
        var events = upcomingEvents.filter { !it.isTask }.filter {
            it.endTime >= dayStart && (it.startTime ?: 0L) < dayEnd
        }

        val startOfDay = date.atStartOfDay().toEpochSecond(offset) * 1000
        val startOfNextDay = date.atStartOfDay().plusDays(1).toEpochSecond(offset) * 1000

        if (!showRunningPastDayEvents) {
            val totalCount = events.size
            events = events.filter {
                (it.startTime != null && it.startTime!! >= startOfDay) ||
                        it.endTime < startOfNextDay
            }
            hiddenPastEvents.value = totalCount - events.size
        } else {
            hiddenPastEvents.value = 0
        }

        calendarEvents.value = events

        // Next events from all upcoming (both events and tasks)
        val allCurrentEvents = events
        val e = this.upcomingEvents
        if (allCurrentEvents.isEmpty() && e.isNotEmpty() && date == LocalDate.now()) {
            nextEvents.value = listOfNotNull(
                e.sortedBy { if (it.isTask) it.endTime else (it.startTime ?: 0L) }
                    .find { now < if (it.isTask) it.endTime else (it.startTime ?: 0L) }
            )
        } else {
            nextEvents.value = emptyList()
        }
    }

    private fun updateFilteredTasks() {
        val config = widgetConfig.value
        val date = selectedDate.value
        val today = LocalDate.now()

        // Filter tasks from the findMany results (tasks from all providers)
        val tasksFromCalendar = upcomingEvents.filter { it.isTask }
        // Also include Google Tasks from dedicated query
        val allTasks = (tasksFromCalendar + allGoogleTasks).distinctBy { it.key }

        val filtered = allTasks.filter { task ->
            val isUndated = task is GoogleTasksCalendarEvent && task.dueDate == null
            if (isUndated) return@filter date == today

            val taskDate = Instant.ofEpochMilli(task.endTime)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            if (date == today) {
                taskDate <= today
            } else {
                taskDate == date
            }
        }.let { list ->
            if (config.showCompletedTasks) list
            else list.filter { it.isCompleted != true }
        }

        taskEvents.value = filtered.sortedWith(
            compareBy<CalendarEvent> { it.isCompleted == true }
                .thenBy { (it as? GoogleTasksCalendarEvent)?.position ?: "" }
        )
    }

    suspend fun onActive() {
        isGoogleSignedIn.value = calendarRepository.isGoogleSignedIn()
        selectDate(LocalDate.now())
        loadGoogleTasks()
        widgetConfig.collectLatest { config ->
            calendarRepository.findMany(
                from = System.currentTimeMillis(),
                to = System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000L,
                excludeAllDayEvents = !config.allDayEvents,
                excludeCalendars = config.excludedCalendarIds,
            ).collectLatest { events ->
                searchableRepository.getKeys(
                    includeTypes = listOf("calendar", "tasks.org", "plugin.calendar"),
                    maxVisibility = VisibilityLevel.SearchOnly,
                    limit = 9999,
                ).collectLatest { hidden ->
                    upcomingEvents = events
                        .filter {
                            !hidden.contains(it.key) && !(!config.completedTasks && it.isCompleted == true)
                        }.sortedBy { it.startTime ?: it.endTime }
                }
            }
        }
    }

    private fun loadGoogleTasks() {
        googleTasksJob?.cancel()
        googleTasksJob = viewModelScope.launch {
            val config = widgetConfig.value
            val endOfRange = LocalDate.now().plusDays(14)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            calendarRepository.findGoogleTasks(
                from = 0L,
                to = endOfRange,
                excludeCalendars = config.excludedTaskCalendars,
            ).collectLatest { taskEvents ->
                allGoogleTasks = taskEvents
                updateFilteredTasks()
            }
        }
    }

    // Task mutation methods
    fun toggleTask(event: CalendarEvent) {
        val completed = event.isCompleted ?: return
        val newCompleted = !completed
        val config = widgetConfig.value
        taskEvents.value = taskEvents.value.map {
            if (it.key == event.key && it is GoogleTasksCalendarEvent) {
                it.copy(isCompleted = newCompleted)
            } else it
        }.let { updated ->
            if (!config.showCompletedTasks) updated.filter { it.isCompleted != true }
            else updated
        }.sortedWith(
            compareBy<CalendarEvent> { it.isCompleted == true }
                .thenBy { (it as? GoogleTasksCalendarEvent)?.position ?: "" }
        )
        viewModelScope.launch {
            calendarRepository.completeTask(event, newCompleted)
            loadGoogleTasks()
        }
    }

    fun postponeTask(event: CalendarEvent) {
        taskEvents.value = taskEvents.value.filter { it.key != event.key }
        val newDate = selectedDate.value.plusDays(1)
        viewModelScope.launch {
            calendarRepository.postponeTask(event, newDate)
            loadGoogleTasks()
        }
    }

    fun deleteTask(event: CalendarEvent) {
        taskEvents.value = taskEvents.value.filter { it.key != event.key }
        viewModelScope.launch {
            calendarRepository.deleteTask(event)
            loadGoogleTasks()
        }
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
            loadGoogleTasks()
        }
    }

    fun signInGoogle(context: Context) {
        val intent = Intent(context, de.mm20.launcher2.google.GoogleLoginActivity::class.java)
        context.startActivity(intent)
    }

    fun createEvent(context: Context) {
        val intent = Intent(Intent.ACTION_EDIT)
        intent.data = CalendarContract.Events.CONTENT_URI
        val zoneOffset = OffsetDateTime.now().offset
        val beginTime = selectedDate.value.atTime(12, 0).toInstant(zoneOffset).toEpochMilli()
        val endTime = selectedDate.value.atTime(13, 0).toInstant(zoneOffset).toEpochMilli()
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
        context.tryStartActivity(intent)
    }

    fun openCalendarApp(context: Context) {
        val startMillis = System.currentTimeMillis()
        val builder = CalendarContract.CONTENT_URI.buildUpon()
        builder.appendPath("time")
        ContentUris.appendId(builder, startMillis)
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(builder.build())
        context.tryStartActivity(intent)
    }

    fun requestCalendarPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Calendar)
    }
}
