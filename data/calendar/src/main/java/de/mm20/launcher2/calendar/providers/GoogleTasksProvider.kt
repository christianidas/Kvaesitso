package de.mm20.launcher2.calendar.providers

import android.content.Context
import android.util.Log
import de.mm20.launcher2.google.GoogleApiHelper
import de.mm20.launcher2.search.CalendarEvent
import java.time.LocalDate
import java.time.ZoneId
import de.mm20.launcher2.search.calendar.CalendarListType
import de.mm20.launcher2.serialization.Json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonNull
import java.time.Instant

@Serializable
internal data class GoogleTaskListItem(val id: String, val title: String)

@Serializable
internal data class GoogleTaskListsResponse(val items: List<GoogleTaskListItem>? = null)

@Serializable
internal data class GoogleTaskItem(
    val id: String,
    val title: String? = null,
    val notes: String? = null,
    val status: String? = null,
    val due: String? = null,
)

@Serializable
internal data class GoogleTasksResponse(val items: List<GoogleTaskItem>? = null)

internal class GoogleTasksProvider(
    private val context: Context,
    private val googleApiHelper: GoogleApiHelper,
) : CalendarProvider {

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json.Lenient)
            }
        }
    }

    /**
     * Execute an authenticated request with automatic token refresh on 401.
     * Returns null if the user hasn't granted consent yet.
     */
    private suspend fun <T> withAuth(block: suspend (token: String) -> T): T? {
        val token = googleApiHelper.getAccessTokenOrNull() ?: return null
        val result = block(token)
        if (result is HttpResponse && result.status == HttpStatusCode.Unauthorized) {
            googleApiHelper.invalidateToken(token)
            val newToken = googleApiHelper.getAccessTokenOrNull() ?: return null
            @Suppress("UNCHECKED_CAST")
            return block(newToken) as T
        }
        return result
    }

    override suspend fun search(
        query: String?,
        from: Long,
        to: Long,
        excludedCalendars: List<String>,
        excludeAllDayEvents: Boolean,
        allowNetwork: Boolean,
    ): List<CalendarEvent> {
        return withContext(Dispatchers.IO) {
            try {
                withAuth { token ->
                    val lists = fetchTaskLists(token).filter { list ->
                        "$namespace:${list.id}" !in excludedCalendars
                    }
                    val today = LocalDate.now()
                    lists.flatMap { taskList ->
                        fetchTasks(token, taskList.id, taskList.title).filter { task ->
                            if (query != null && !task.label.contains(query, ignoreCase = true)) return@filter false
                            if (task.dueDate == null) return@filter true
                            // Compare as local dates — Google returns due as midnight UTC
                            // but "due April 3" means April 3 in the user's timezone
                            task.dueDate <= today
                        }
                    }
                } ?: emptyList()
            } catch (e: Exception) {
                Log.e("GoogleTasksProvider", "Error fetching tasks", e)
                emptyList()
            }
        }
    }

    override suspend fun getCalendarLists(): List<CalendarList> {
        return withContext(Dispatchers.IO) {
            try {
                withAuth { token ->
                    fetchTaskLists(token).map { list ->
                        CalendarList(
                            id = "$namespace:${list.id}",
                            name = list.title,
                            color = 0,
                            types = listOf(CalendarListType.Tasks),
                            providerId = namespace,
                            owner = googleApiHelper.getAccountEmail(),
                        )
                    }
                } ?: emptyList()
            } catch (e: Exception) {
                Log.e("GoogleTasksProvider", "Error fetching task lists", e)
                emptyList()
            }
        }
    }

    private suspend fun fetchTaskLists(token: String): List<GoogleTaskListItem> {
        val response = httpClient.get("$BASE_URL/users/@me/lists") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (!response.status.isSuccess()) return emptyList()
        return response.body<GoogleTaskListsResponse>().items ?: emptyList()
    }

    private suspend fun fetchTasks(token: String, listId: String, listTitle: String): List<GoogleTasksCalendarEvent> {
        val response = httpClient.get("$BASE_URL/lists/$listId/tasks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("showCompleted", "true")
            parameter("showHidden", "true")
        }
        if (!response.status.isSuccess()) return emptyList()
        val items = response.body<GoogleTasksResponse>().items ?: return emptyList()
        return items.mapNotNull { item ->
            val title = item.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Google Tasks returns due as midnight UTC — parse as a date, not a timestamp
            val dueDate = item.due?.let {
                try { Instant.parse(it).atZone(ZoneId.of("UTC")).toLocalDate() } catch (e: Exception) { null }
            }
            val dueMillis = dueDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            GoogleTasksCalendarEvent(
                label = title,
                taskId = item.id,
                taskListId = listId,
                dueDate = dueDate,
                color = null,
                startTime = null,
                endTime = dueMillis ?: (System.currentTimeMillis() + UNDATED_TASK_OFFSET_MS),
                allDay = true,
                isCompleted = item.status == "completed",
                description = item.notes,
                calendarName = listTitle,
            )
        }
    }

    suspend fun completeTask(taskListId: String, taskId: String, completed: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                withAuth { token ->
                    val body = buildJsonObject {
                        put("status", if (completed) "completed" else "needsAction")
                        if (!completed) put("completed", JsonNull)
                    }
                    httpClient.patch("$BASE_URL/lists/$taskListId/tasks/$taskId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleTasksProvider", "Error completing task", e)
            }
        }
    }

    suspend fun createTask(listId: String, title: String, dueDate: Long?) {
        withContext(Dispatchers.IO) {
            try {
                withAuth { token ->
                    val body = buildJsonObject {
                        put("title", title)
                        if (dueDate != null) {
                            put("due", Instant.ofEpochMilli(dueDate).toString())
                        }
                    }
                    httpClient.post("$BASE_URL/lists/$listId/tasks") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleTasksProvider", "Error creating task", e)
            }
        }
    }

    suspend fun getTask(listId: String, taskId: String): GoogleTasksCalendarEvent? {
        return withContext(Dispatchers.IO) {
            try {
                withAuth { token ->
                    val response = httpClient.get("$BASE_URL/lists/$listId/tasks/$taskId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    if (!response.status.isSuccess()) return@withAuth null
                    val item = response.body<GoogleTaskItem>()
                    val title = item.title?.takeIf { it.isNotBlank() } ?: return@withAuth null
                    val dueDate = item.due?.let {
                        try { Instant.parse(it).atZone(ZoneId.of("UTC")).toLocalDate() } catch (e: Exception) { null }
                    }
                    val dueMillis = dueDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    GoogleTasksCalendarEvent(
                        label = title,
                        taskId = item.id,
                        taskListId = listId,
                        dueDate = dueDate,
                        color = null,
                        startTime = null,
                        endTime = dueMillis ?: (System.currentTimeMillis() + UNDATED_TASK_OFFSET_MS),
                        allDay = true,
                        isCompleted = item.status == "completed",
                        description = item.notes,
                        calendarName = null,
                    )
                }
            } catch (e: Exception) {
                Log.e("GoogleTasksProvider", "Error fetching task", e)
                null
            }
        }
    }

    override val namespace: String = "google.tasks"

    companion object {
        private const val BASE_URL = "https://tasks.googleapis.com/tasks/v1"
        private const val UNDATED_TASK_OFFSET_MS = 365L * 24 * 60 * 60 * 1000
    }
}
