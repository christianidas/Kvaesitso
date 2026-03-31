package de.mm20.launcher2.widgets

import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class IntervalType {
    DAILY,
    WEEKLY,
    MONTHLY,
}

@Serializable
data class RecurrenceRule(
    val id: String,
    val templateText: String,
    val intervalType: IntervalType,
    val interval: Int = 1,
    val daysOfWeek: List<Int>? = null,
    val dayOfMonth: Int? = null,
    val timeOfDay: String? = null,
    val lastMaterialized: String? = null,
)

@Serializable
data class TodoItem(
    val id: String,
    val text: String,
    val completed: Boolean = false,
    val createdAt: String,
    val completedAt: String? = null,
    val recurrenceRuleId: String? = null,
    val dueAt: String? = null,
)

@Serializable
data class TodoWidgetConfig(
    val items: List<TodoItem> = emptyList(),
    val recurrenceRules: List<RecurrenceRule> = emptyList(),
    val showCompleted: Boolean = false,
)

data class TodoWidget(
    override val id: UUID,
    val config: TodoWidgetConfig = TodoWidgetConfig(),
) : Widget() {

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    companion object {
        const val Type = "todo"
    }
}
