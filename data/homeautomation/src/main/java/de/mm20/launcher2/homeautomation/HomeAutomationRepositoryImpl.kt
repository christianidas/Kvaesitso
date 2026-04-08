package de.mm20.launcher2.homeautomation

import android.content.Intent
import android.util.Log
import de.mm20.launcher2.homeautomation.providers.GoogleHomeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class HomeAutomationRepositoryImpl(
    private val googleHomeProvider: GoogleHomeProvider,
) : HomeAutomationRepository {

    private val _structures = MutableStateFlow<List<HomeStructure>>(emptyList())
    private val _devices = MutableStateFlow<List<HomeDevice>>(emptyList())
    private val _scenes = MutableStateFlow<List<HomeScene>>(emptyList())

    override fun getStructures(): Flow<List<HomeStructure>> = _structures
    override fun getDevices(): Flow<List<HomeDevice>> = _devices
    override fun getScenes(): Flow<List<HomeScene>> = _scenes

    override suspend fun executeCommand(command: HomeCommand) {
        googleHomeProvider.executeCommand(command)
        if (command is HomeCommand.SetOnOff) {
            _devices.value = _devices.value.map { device ->
                if (device.id == command.deviceId) {
                    device.copy(
                        traits = device.traits.map { trait ->
                            if (trait is DeviceTrait.OnOff) DeviceTrait.OnOff(command.on) else trait
                        }
                    )
                } else device
            }
        }
    }

    override suspend fun activateScene(sceneId: String) {
        // To be implemented when the API schema for scenes is confirmed
    }

    override suspend fun refresh(): Intent? {
        Log.d("HomeAutoRepo", "refresh() called")
        val consentIntent = googleHomeProvider.getConsentIntentIfNeeded()
        Log.d("HomeAutoRepo", "consentIntent: $consentIntent")
        if (consentIntent != null) return consentIntent

        val structures = googleHomeProvider.fetchStructures()
        Log.d("HomeAutoRepo", "structures: ${structures.size}")
        _structures.value = structures

        val devices = googleHomeProvider.fetchDevices()
        Log.d("HomeAutoRepo", "devices: ${devices.size}")
        _devices.value = devices
        return null
    }
}
