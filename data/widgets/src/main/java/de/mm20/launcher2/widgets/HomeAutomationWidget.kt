package de.mm20.launcher2.widgets

import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class HomeAutomationWidgetConfig(
    val showRooms: List<String> = emptyList(),
    val showDeviceTypes: List<String> = emptyList(),
    val showScenes: Boolean = true,
    val compactMode: Boolean = false,
)

data class HomeAutomationWidget(
    override val id: UUID,
    val config: HomeAutomationWidgetConfig = HomeAutomationWidgetConfig(),
) : Widget() {

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    companion object {
        const val Type = "home_automation"
    }
}
