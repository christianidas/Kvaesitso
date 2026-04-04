package de.mm20.launcher2.widgets

import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class AgendaWidgetConfig(
    val allDayEvents: Boolean = true,
    val excludedCalendarIds: List<String> = emptyList(),
    val completedTasks: Boolean = true,
    val showCompletedTasks: Boolean = false,
    val excludedTaskCalendars: List<String> = emptyList(),
)

data class AgendaWidget(
    override val id: UUID,
    val config: AgendaWidgetConfig = AgendaWidgetConfig(),
) : Widget() {
    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    companion object {
        const val Type = "agenda"
    }
}
