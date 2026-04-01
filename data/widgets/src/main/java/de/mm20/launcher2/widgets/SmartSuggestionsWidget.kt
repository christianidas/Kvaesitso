package de.mm20.launcher2.widgets

import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class SmartSuggestionsWidgetConfig(
    val suggestionCount: Int = 8,
)

data class SmartSuggestionsWidget(
    override val id: UUID,
    val config: SmartSuggestionsWidgetConfig = SmartSuggestionsWidgetConfig(),
) : Widget() {

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    companion object {
        const val Type = "smartsuggestions"
    }
}
