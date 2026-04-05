package de.mm20.launcher2.homeautomation.providers

import android.util.Log
import de.mm20.launcher2.google.GoogleApiHelper
import de.mm20.launcher2.homeautomation.*
import de.mm20.launcher2.serialization.Json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TAG = "GoogleHomeProvider"

@Serializable
internal data class GoogleHomeStructureResponse(
    val structures: List<GoogleHomeStructureItem>? = null,
)

@Serializable
internal data class GoogleHomeStructureItem(
    val name: String,
    val traits: JsonObject? = null,
)

@Serializable
internal data class GoogleHomeRoomResponse(
    val rooms: List<GoogleHomeRoomItem>? = null,
)

@Serializable
internal data class GoogleHomeRoomItem(
    val name: String,
    val traits: JsonObject? = null,
)

@Serializable
internal data class GoogleHomeDeviceResponse(
    val devices: List<GoogleHomeDeviceItem>? = null,
)

@Serializable
internal data class GoogleHomeDeviceItem(
    val name: String,
    val type: String? = null,
    val traits: JsonObject? = null,
    val parentRelations: List<GoogleHomeParentRelation>? = null,
)

@Serializable
internal data class GoogleHomeParentRelation(
    val parent: String? = null,
    val displayName: String? = null,
)

internal class GoogleHomeProvider(
    private val googleApiHelper: GoogleApiHelper,
) {

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json.Lenient)
            }
        }
    }

    private val homeScopes = arrayOf(
        GoogleApiHelper.HOME_SCOPE_RUN,
        GoogleApiHelper.HOME_SCOPE_READ,
    )

    private suspend fun <T> withAuth(block: suspend (token: String) -> T): T? {
        val token = googleApiHelper.getAccessTokenForScopesOrNull(*homeScopes) ?: return null
        val result = block(token)
        if (result is HttpResponse && result.status == HttpStatusCode.Unauthorized) {
            googleApiHelper.invalidateToken(token)
            val newToken = googleApiHelper.getAccessTokenForScopesOrNull(*homeScopes) ?: return null
            @Suppress("UNCHECKED_CAST")
            return block(newToken) as T
        }
        return result
    }

    suspend fun fetchStructures(): List<HomeStructure> = withContext(Dispatchers.IO) {
        try {
            withAuth { token ->
                val response = httpClient.get("$BASE_URL/structures") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                if (!response.status.isSuccess()) return@withAuth emptyList()
                val structures = response.body<GoogleHomeStructureResponse>().structures ?: return@withAuth emptyList()

                structures.map { structure ->
                    val structureId = structure.name
                    val rooms = fetchRooms(token, structureId)
                    HomeStructure(
                        id = structureId,
                        name = extractDisplayName(structureId),
                        rooms = rooms,
                    )
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching structures", e)
            emptyList()
        }
    }

    private suspend fun fetchRooms(token: String, structureId: String): List<HomeRoom> {
        return try {
            val response = httpClient.get("$BASE_URL/$structureId/rooms") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (!response.status.isSuccess()) return emptyList()
            val rooms = response.body<GoogleHomeRoomResponse>().rooms ?: return emptyList()
            rooms.map { room ->
                HomeRoom(
                    id = room.name,
                    name = extractDisplayName(room.name),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching rooms", e)
            emptyList()
        }
    }

    suspend fun fetchDevices(): List<HomeDevice> = withContext(Dispatchers.IO) {
        try {
            withAuth { token ->
                val response = httpClient.get("$BASE_URL/devices") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                if (!response.status.isSuccess()) return@withAuth emptyList()
                val devices = response.body<GoogleHomeDeviceResponse>().devices ?: return@withAuth emptyList()
                devices.map { it.toHomeDevice() }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching devices", e)
            emptyList()
        }
    }

    suspend fun executeCommand(command: HomeCommand) = withContext(Dispatchers.IO) {
        try {
            withAuth { token ->
                val body = command.toRequestBody()
                httpClient.post("$BASE_URL/${command.deviceId}:executeCommand") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command", e)
        }
    }

    private fun GoogleHomeDeviceItem.toHomeDevice(): HomeDevice {
        val room = parentRelations?.firstOrNull()?.displayName
        return HomeDevice(
            id = name,
            name = extractDisplayName(name),
            room = room,
            type = mapDeviceType(type),
            traits = parseTraits(traits),
        )
    }

    private fun mapDeviceType(type: String?): DeviceType = when {
        type == null -> DeviceType.Other
        type.contains("LIGHT", ignoreCase = true) -> DeviceType.Light
        type.contains("SWITCH", ignoreCase = true) || type.contains("OUTLET", ignoreCase = true) -> DeviceType.Switch
        type.contains("THERMOSTAT", ignoreCase = true) -> DeviceType.Thermostat
        type.contains("LOCK", ignoreCase = true) -> DeviceType.Lock
        type.contains("CAMERA", ignoreCase = true) -> DeviceType.Camera
        type.contains("SPEAKER", ignoreCase = true) -> DeviceType.Speaker
        type.contains("DISPLAY", ignoreCase = true) -> DeviceType.Display
        type.contains("FAN", ignoreCase = true) -> DeviceType.Fan
        type.contains("BLIND", ignoreCase = true) || type.contains("CURTAIN", ignoreCase = true) -> DeviceType.Blinds
        type.contains("GARAGE", ignoreCase = true) -> DeviceType.Garage
        type.contains("VACUUM", ignoreCase = true) -> DeviceType.Vacuum
        else -> DeviceType.Other
    }

    private fun parseTraits(traits: JsonObject?): List<DeviceTrait> {
        if (traits == null) return emptyList()
        val result = mutableListOf<DeviceTrait>()
        // Parse based on known trait keys in the Google Home API response
        // The exact key names depend on the API version; adjust as needed
        traits["OnOff"]?.let {
            val on = (it as? JsonObject)?.get("on")?.toString()?.toBooleanStrictOrNull() ?: false
            result.add(DeviceTrait.OnOff(on))
        }
        traits["Brightness"]?.let {
            val level = (it as? JsonObject)?.get("brightness")?.toString()?.toIntOrNull() ?: 0
            result.add(DeviceTrait.Brightness(level))
        }
        traits["LockUnlock"]?.let {
            val locked = (it as? JsonObject)?.get("isLocked")?.toString()?.toBooleanStrictOrNull() ?: false
            result.add(DeviceTrait.LockUnlock(locked))
        }
        return result
    }

    private fun HomeCommand.toRequestBody(): JsonObject = when (this) {
        is HomeCommand.SetOnOff -> buildJsonObject {
            put("command", "action.devices.commands.OnOff")
            putJsonObject("params") { put("on", on) }
        }
        is HomeCommand.SetBrightness -> buildJsonObject {
            put("command", "action.devices.commands.BrightnessAbsolute")
            putJsonObject("params") { put("brightness", level) }
        }
        is HomeCommand.SetColorTemperature -> buildJsonObject {
            put("command", "action.devices.commands.ColorAbsolute")
            putJsonObject("params") {
                putJsonObject("color") { put("temperatureK", kelvin) }
            }
        }
        is HomeCommand.SetThermostat -> buildJsonObject {
            put("command", "action.devices.commands.ThermostatTemperatureSetpoint")
            putJsonObject("params") {
                if (setpointCelsius != null) put("thermostatTemperatureSetpoint", setpointCelsius)
            }
        }
        is HomeCommand.SetLock -> buildJsonObject {
            put("command", "action.devices.commands.LockUnlock")
            putJsonObject("params") { put("lock", locked) }
        }
        is HomeCommand.SetFanSpeed -> buildJsonObject {
            put("command", "action.devices.commands.SetFanSpeed")
            putJsonObject("params") { put("fanSpeed", speed.toString()) }
        }
        is HomeCommand.SetOpenClose -> buildJsonObject {
            put("command", "action.devices.commands.OpenClose")
            putJsonObject("params") { put("openPercent", openPercent) }
        }
    }

    private fun extractDisplayName(resourceName: String): String {
        return resourceName.substringAfterLast("/")
    }

    companion object {
        private const val BASE_URL = "https://home.googleapis.com/v1"
    }
}
