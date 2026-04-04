package de.mm20.launcher2.calendar

import android.content.Context
import de.mm20.launcher2.calendar.providers.AndroidCalendarProvider
import de.mm20.launcher2.calendar.providers.CalendarList
import de.mm20.launcher2.calendar.providers.CalendarProvider
import de.mm20.launcher2.calendar.providers.GoogleTasksCalendarEvent
import de.mm20.launcher2.calendar.providers.GoogleTasksProvider
import de.mm20.launcher2.calendar.providers.PluginCalendarProvider
import de.mm20.launcher2.calendar.providers.TasksCalendarProvider
import de.mm20.launcher2.google.GoogleApiHelper
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.time.Duration.Companion.days

interface CalendarRepository : SearchableRepository<CalendarEvent> {
    fun findMany(
        from: Long = System.currentTimeMillis(),
        to: Long = from + 14 * 24 * 60 * 60 * 1000L,
        excludeCalendars: List<String> = emptyList(),
        excludeAllDayEvents: Boolean = false,
    ): Flow<ImmutableList<CalendarEvent>>

    fun findGoogleTasks(
        from: Long = 0L,
        to: Long = System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000L,
        excludeCalendars: List<String> = emptyList(),
    ): Flow<ImmutableList<CalendarEvent>>

    fun getCalendars(providerId: String? = null): Flow<List<CalendarList>>

    suspend fun completeTask(event: CalendarEvent, completed: Boolean)
    suspend fun createGoogleTask(title: String)
    suspend fun postponeTask(event: CalendarEvent, toDate: java.time.LocalDate)
    suspend fun deleteTask(event: CalendarEvent)
    suspend fun moveTask(event: CalendarEvent, previousEvent: CalendarEvent?)
    fun isGoogleSignedIn(): Boolean
}

internal class CalendarRepositoryImpl(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val pluginRepository: PluginRepository,
    private val settings: CalendarSearchSettings,
) : CalendarRepository {

    private val googleApiHelper = GoogleApiHelper(context)

    override fun search(query: String, allowNetwork: Boolean): Flow<ImmutableList<CalendarEvent>> {
        if (query.isBlank() || query.length < 2) {
            return flow {
                emit(persistentListOf())
            }
        }

        val hasCalendarPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        val hasTasksPermission = permissionsManager.hasPermission(PermissionGroup.Tasks)
        val providerIds = settings.providers
        val excludedCalendars = settings.excludedCalendars

        return combineTransform(
            hasCalendarPermission,
            hasTasksPermission,
            providerIds,
            excludedCalendars
        ) { calPerm, taskPerm, providerIds, excludedCalendars ->
            val providers = providerIds.mapNotNull {
                when (it) {
                    "local" -> if (calPerm) AndroidCalendarProvider(context) else null
                    "tasks.org" -> if (taskPerm) TasksCalendarProvider(context) else null
                    else -> PluginCalendarProvider(context, it)
                }
            }

            val now = System.currentTimeMillis()
            emitAll(
                queryCalendarEvents(
                    query = query,
                    excludeAllDayEvents = false,
                    excludeCalendars = excludedCalendars.toList(),
                    providers = providers,
                    intervalStart = now,
                    intervalEnd = now + 730.days.inWholeMilliseconds,
                    allowNetwork = allowNetwork,
                )
            )
        }
    }

    override fun findMany(
        from: Long,
        to: Long,
        excludeCalendars: List<String>,
        excludeAllDayEvents: Boolean,
    ): Flow<ImmutableList<CalendarEvent>> {
        val hasCalendarPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        val hasTasksPermission = permissionsManager.hasPermission(PermissionGroup.Tasks)
        val plugins = pluginRepository.findMany(
            type = PluginType.Calendar,
            enabled = true,
        )
        return combineTransform(hasCalendarPermission, hasTasksPermission, plugins) { calPerm, taskPerm, plugins ->
            val providers = buildList {
                if (calPerm) add(AndroidCalendarProvider(context))
                if (taskPerm) add(TasksCalendarProvider(context))
                addAll(plugins.map { PluginCalendarProvider(context, it.authority) })
            }

            emitAll(
                queryCalendarEvents(
                    query = null,
                    intervalStart = from,
                    intervalEnd = to,
                    excludeAllDayEvents = excludeAllDayEvents,
                    excludeCalendars = excludeCalendars,
                    providers = providers,
                    allowNetwork = false,
                ).debounce(500)
            )
        }
    }

    override fun findGoogleTasks(
        from: Long,
        to: Long,
        excludeCalendars: List<String>,
    ): Flow<ImmutableList<CalendarEvent>> {
        if (!googleApiHelper.isSignedIn()) {
            return flow { emit(persistentListOf()) }
        }
        val provider = GoogleTasksProvider(context, googleApiHelper)
        return flow {
            val excludedIds = excludeCalendars.mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2 && parts[0] == provider.namespace) parts[1] else null
            }
            val tasks = provider.search(
                query = null,
                from = from,
                to = to,
                excludedCalendars = excludedIds,
                excludeAllDayEvents = false,
                allowNetwork = false,
            )
            emit(tasks.toPersistentList())
        }
    }

    private fun queryCalendarEvents(
        query: String?,
        intervalStart: Long,
        intervalEnd: Long,
        excludeAllDayEvents: Boolean = false,
        excludeCalendars: List<String> = emptyList(),
        allowNetwork: Boolean = false,
        providers: List<CalendarProvider>,
    ): Flow<ImmutableList<CalendarEvent>> = flow {
        supervisorScope {
            val result = MutableStateFlow(persistentListOf<CalendarEvent>())

            for (provider in providers) {
                launch {
                    val r = provider.search(
                        query,
                        from = intervalStart,
                        to = intervalEnd,
                        excludedCalendars = excludeCalendars.mapNotNull {
                            val (namespace, id) = it.split(":")
                            if (namespace == provider.namespace) id else null
                        },
                        excludeAllDayEvents = excludeAllDayEvents,
                        allowNetwork = allowNetwork,
                    )
                    result.update {
                        (it + r).toPersistentList()
                    }
                }
            }
            emitAll(result)
        }
    }

    override fun getCalendars(providerId: String?): Flow<List<CalendarList>> {
        val hasCalendarPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        val hasTaskPermission = permissionsManager.hasPermission(PermissionGroup.Tasks)

        val providers: Flow<List<CalendarProvider>> = if (providerId != null) {
            when (providerId) {
                "local" -> hasCalendarPermission.map {
                    if (it) listOf(AndroidCalendarProvider(context)) else emptyList()
                }
                "tasks.org" -> hasTaskPermission.map { if (it) listOf(TasksCalendarProvider(context)) else emptyList() }
                "google.tasks" -> hasCalendarPermission.map {
                    if (googleApiHelper.isSignedIn()) listOf(GoogleTasksProvider(context, googleApiHelper)) else emptyList()
                }
                else -> pluginRepository.get(providerId).map {
                    if (it?.enabled == true) listOf(PluginCalendarProvider(context, providerId)) else emptyList()
                }
            }
        } else {
            val plugins = pluginRepository.findMany(
                type = PluginType.Calendar,
                enabled = true,
            )
            combine(
                hasCalendarPermission,
                hasTaskPermission,
                plugins
            ) { calPerm, tasksPerm, plugins ->
                buildList {
                    if (calPerm) add(AndroidCalendarProvider(context))
                    if (tasksPerm) add(TasksCalendarProvider(context))
                    if (googleApiHelper.isSignedIn()) add(GoogleTasksProvider(context, googleApiHelper))
                    addAll(plugins.map { PluginCalendarProvider(context, it.authority) })
                }
            }
        }

        return providers.transform { providers ->
            supervisorScope {
                val result = MutableStateFlow(emptyList<CalendarList>())
                for (provider in providers) {
                    launch {
                        val r = provider.getCalendarLists()
                        result.update { it + r }
                    }
                }
                emitAll(result)
            }
        }
    }

    override suspend fun createGoogleTask(title: String) {
        GoogleTasksProvider(context, googleApiHelper).createTask("@default", title, null)
    }

    override suspend fun completeTask(event: CalendarEvent, completed: Boolean) {
        if (event is GoogleTasksCalendarEvent) {
            GoogleTasksProvider(context, googleApiHelper).completeTask(event.taskListId, event.taskId, completed)
        }
    }

    override suspend fun postponeTask(event: CalendarEvent, toDate: java.time.LocalDate) {
        if (event is GoogleTasksCalendarEvent) {
            GoogleTasksProvider(context, googleApiHelper).updateTaskDue(event.taskListId, event.taskId, toDate)
        }
    }

    override suspend fun deleteTask(event: CalendarEvent) {
        if (event is GoogleTasksCalendarEvent) {
            GoogleTasksProvider(context, googleApiHelper).deleteTask(event.taskListId, event.taskId)
        }
    }

    override suspend fun moveTask(event: CalendarEvent, previousEvent: CalendarEvent?) {
        if (event is GoogleTasksCalendarEvent) {
            val previousTaskId = (previousEvent as? GoogleTasksCalendarEvent)?.taskId
            GoogleTasksProvider(context, googleApiHelper).moveTask(event.taskListId, event.taskId, previousTaskId)
        }
    }

    override fun isGoogleSignedIn(): Boolean = googleApiHelper.isSignedIn()
}
