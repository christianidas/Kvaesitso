package de.mm20.launcher2.homeautomation.providers

import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.UserRecoverableAuthException
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
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TAG = "GoogleHomeProvider"

// SDM API response models

@Serializable
internal data class SdmDeviceListResponse(
    val devices: List<SdmDevice>? = null,
)

@Serializable
internal data class SdmDevice(
    val name: String,
    val type: String? = null,
    val traits: JsonObject? = null,
    val parentRelations: List<SdmParentRelation>? = null,
)

@Serializable
internal data class SdmParentRelation(
    val parent: String? = null,
    val displayName: String? = null,
)

@Serializable
internal data class SdmStructureListResponse(
    val structures: List<SdmStructure>? = null,
)

@Serializable
internal data class SdmStructure(
    val name: String,
    val traits: JsonObject? = null,
)

@Serializable
internal data class SdmRoomListResponse(
    val rooms: List<SdmRoom>? = null,
)

@Serializable
internal data class SdmRoom(
    val name: String,
    val traits: JsonObject? = null,
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
        GoogleApiHelper.SDM_SCOPE,
    )

    private val projectId: String
        get() = de.mm20.launcher2.homeautomation.BuildConfig.SDM_PROJECT_ID

    private val baseUrl: String
        get() = "$BASE_URL/enterprises/$projectId"

    suspend fun getConsentIntentIfNeeded(): Intent? {
        if (projectId.isBlank()) {
            Log.w(TAG, "SDM project ID not configured in local.properties (google.sdmProjectId)")
            return null
        }
        Log.d(TAG, "Checking consent for SDM scope, signed in: ${googleApiHelper.isSignedIn()}")
        return try {
            val token = googleApiHelper.getAccessTokenForScopes(*homeScopes)
            Log.d(TAG, "Got SDM token: ${if (token != null) "yes" else "null"}")
            null
        } catch (e: UserRecoverableAuthException) {
            Log.d(TAG, "Need consent for SDM scope, intent: ${e.intent}")
            e.intent
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error checking consent", e)
            null
        }
    }

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
        if (projectId.isBlank()) return@withContext emptyList()
        try {
            withAuth { token ->
                val response = httpClient.get("$baseUrl/structures") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                Log.d(TAG, "Structures response: ${response.status}")
                if (!response.status.isSuccess()) {
                    Log.e(TAG, "Structures error: ${response.bodyAsText()}")
                    return@withAuth emptyList()
                }
                val structures = response.body<SdmStructureListResponse>().structures ?: return@withAuth emptyList()

                structures.map { structure ->
                    val structureId = structure.name
                    val rooms = fetchRooms(token, structureId)
                    val displayName = structure.traits
                        ?.get("sdm.structures.traits.Info")
                        ?.jsonObject?.get("customName")
                        ?.jsonPrimitive?.content
                        ?: extractDisplayName(structureId)
                    HomeStructure(
                        id = structureId,
                        name = displayName,
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
            val response = httpClient.get("$baseUrl/rooms") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (!response.status.isSuccess()) return emptyList()
            val rooms = response.body<SdmRoomListResponse>().rooms ?: return emptyList()
            rooms.filter { it.name.startsWith(structureId) }.map { room ->
                val displayName = room.traits
                    ?.get("sdm.structures.traits.Info")
                    ?.jsonObject?.get("customName")
                    ?.jsonPrimitive?.content
                    ?: extractDisplayName(room.name)
                HomeRoom(
                    id = room.name,
                    name = displayName,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching rooms", e)
            emptyList()
        }
    }

    suspend fun fetchDevices(): List<HomeDevice> = withContext(Dispatchers.IO) {
        if (projectId.isBlank()) return@withContext emptyList()
        try {
            withAuth { token ->
                val response = httpClient.get("$baseUrl/devices") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                Log.d(TAG, "Devices response: ${response.status}")
                if (!response.status.isSuccess()) {
                    Log.e(TAG, "Devices error: ${response.bodyAsText()}")
                    return@withAuth emptyList()
                }
                val devices = response.body<SdmDeviceListResponse>().devices ?: return@withAuth emptyList()
                Log.d(TAG, "Got ${devices.size} devices")
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

    private fun SdmDevice.toHomeDevice(): HomeDevice {
        val room = parentRelations?.firstOrNull()?.displayName
        return HomeDevice(
            id = name,
            name = extractDeviceName(this),
            room = room,
            type = mapSdmDeviceType(type),
            traits = parseSdmTraits(traits),
        )
    }

    private fun extractDeviceName(device: SdmDevice): String {
        // SDM devices don't always have a friendly name in the API response.
        // The parent relation displayName is usually the room name.
        // Try to get name from traits or fall back to type + room.
        val room = device.parentRelations?.firstOrNull()?.displayName ?: ""
        val typeName = when {
            device.type?.contains("THERMOSTAT") == true -> "Thermostat"
            device.type?.contains("CAMERA") == true -> "Camera"
            device.type?.contains("DOORBELL") == true -> "Doorbell"
            device.type?.contains("DISPLAY") == true -> "Display"
            else -> "Device"
        }
        return if (room.isNotBlank()) "$room $typeName" else typeName
    }

    private fun mapSdmDeviceType(type: String?): DeviceType = when {
        type == null -> DeviceType.Other
        type.contains("THERMOSTAT") -> DeviceType.Thermostat
        type.contains("CAMERA") -> DeviceType.Camera
        type.contains("DOORBELL") -> DeviceType.Camera
        type.contains("DISPLAY") -> DeviceType.Display
        else -> DeviceType.Other
    }

    private fun parseSdmTraits(traits: JsonObject?): List<DeviceTrait> {
        if (traits == null) return emptyList()
        val result = mutableListOf<DeviceTrait>()

        // Thermostat traits
        traits["sdm.devices.traits.ThermostatMode"]?.jsonObject?.let { mode ->
            val currentMode = mode["mode"]?.jsonPrimitive?.content ?: "OFF"
            val ambient = traits["sdm.devices.traits.Temperature"]
                ?.jsonObject?.get("ambientTemperatureCelsius")
                ?.jsonPrimitive?.float
            val setpoint = traits["sdm.devices.traits.ThermostatTemperatureSetpoint"]
                ?.jsonObject?.get("heatCelsius")
                ?.jsonPrimitive?.float
                ?: traits["sdm.devices.traits.ThermostatTemperatureSetpoint"]
                    ?.jsonObject?.get("coolCelsius")
                    ?.jsonPrimitive?.float
            result.add(DeviceTrait.ThermostatMode(currentMode, setpoint, ambient))
        }

        // Camera/Doorbell - just mark as having on/off if it has live stream
        traits["sdm.devices.traits.CameraLiveStream"]?.let {
            result.add(DeviceTrait.OnOff(true))
        }

        return result
    }

    private fun HomeCommand.toRequestBody(): JsonObject = when (this) {
        is HomeCommand.SetOnOff -> buildJsonObject {
            put("command", "sdm.devices.commands.CameraLiveStream.GenerateRtspStream")
            putJsonObject("params") {}
        }
        is HomeCommand.SetThermostat -> buildJsonObject {
            put("command", "sdm.devices.commands.ThermostatTemperatureSetpoint.SetHeat")
            putJsonObject("params") {
                if (setpointCelsius != null) put("heatCelsius", setpointCelsius)
            }
        }
        is HomeCommand.SetBrightness,
        is HomeCommand.SetColorTemperature,
        is HomeCommand.SetLock,
        is HomeCommand.SetFanSpeed,
        is HomeCommand.SetOpenClose -> buildJsonObject {
            // These device types are not supported by SDM API
            // SDM only covers Nest devices (thermostats, cameras, doorbells)
        }
    }

    private fun extractDisplayName(resourceName: String): String {
        return resourceName.substringAfterLast("/")
    }

    companion object {
        private const val BASE_URL = "https://smartdevicemanagement.googleapis.com/v1"
    }
}
