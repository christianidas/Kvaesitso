package de.mm20.launcher2.calendar.providers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import de.mm20.launcher2.calendar.GoogleTasksCalendarEventSerializer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SearchableSerializer
import java.time.LocalDate

data class GoogleTasksCalendarEvent(
    override val label: String,
    val taskId: String,
    val taskListId: String,
    val dueDate: LocalDate?,
    override val color: Int?,
    override val startTime: Long?,
    override val endTime: Long,
    override val allDay: Boolean,
    override val isCompleted: Boolean?,
    override val description: String?,
    override val calendarName: String?,
    override val labelOverride: String? = null,
) : CalendarEvent {

    override val domain: String = Domain
    override val key: String = "$Domain://$taskId"
    override val location: String? = null
    override val attendees: List<String> = emptyList()

    override fun overrideLabel(label: String): GoogleTasksCalendarEvent = copy(labelOverride = label)

    override fun launch(context: Context, options: Bundle?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setData("https://tasks.google.com/".toUri())
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return context.tryStartActivity(intent, options)
    }

    override fun getSerializer(): SearchableSerializer = GoogleTasksCalendarEventSerializer()

    companion object {
        const val Domain = "google.tasks"
    }
}
