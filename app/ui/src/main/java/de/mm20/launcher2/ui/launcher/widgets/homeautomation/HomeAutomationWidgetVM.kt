package de.mm20.launcher2.ui.launcher.widgets.homeautomation

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.google.GoogleApiHelper
import de.mm20.launcher2.google.GoogleLoginActivity
import de.mm20.launcher2.homeautomation.DeviceTrait
import de.mm20.launcher2.homeautomation.HomeAutomationRepository
import de.mm20.launcher2.homeautomation.HomeCommand
import de.mm20.launcher2.homeautomation.HomeDevice
import de.mm20.launcher2.homeautomation.HomeStructure
import de.mm20.launcher2.widgets.HomeAutomationWidget
import de.mm20.launcher2.widgets.HomeAutomationWidgetConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HomeAutomationWidgetVM : ViewModel(), KoinComponent {

    private val repository: HomeAutomationRepository by inject()
    private val googleApiHelper: GoogleApiHelper by inject()

    private val widgetConfig = MutableStateFlow(HomeAutomationWidgetConfig())

    val devices = mutableStateOf<List<HomeDevice>>(emptyList())
    val structures = mutableStateOf<List<HomeStructure>>(emptyList())
    val isSignedIn = mutableStateOf(false)
    val isLoading = mutableStateOf(false)
    val needsConsent = mutableStateOf<Intent?>(null)
    val selectedRoom = mutableStateOf<String?>(null)

    fun updateWidget(widget: HomeAutomationWidget) {
        widgetConfig.value = widget.config
        loadDevices()
    }

    fun onResume() {
        isSignedIn.value = googleApiHelper.isSignedIn()
        if (isSignedIn.value) {
            loadDevices()
        }
    }

    private fun loadDevices() {
        Log.d("HomeAutoVM", "loadDevices() called, isSignedIn=${googleApiHelper.isSignedIn()}")
        isLoading.value = true
        viewModelScope.launch {
            try {
                val consentIntent = repository.refresh()
                Log.d("HomeAutoVM", "refresh returned consentIntent=$consentIntent")
                if (consentIntent != null) {
                    needsConsent.value = consentIntent
                    isLoading.value = false
                    return@launch
                }
                needsConsent.value = null
                isLoading.value = false
            } catch (e: Exception) {
                Log.e("HomeAutoVM", "Error in loadDevices", e)
                isLoading.value = false
            }
        }
        viewModelScope.launch {
            combine(repository.getDevices(), widgetConfig) { devices, config ->
                filterDevices(devices, config)
            }.collectLatest {
                devices.value = it
            }
        }
        viewModelScope.launch {
            repository.getStructures().collectLatest {
                structures.value = it
            }
        }
    }

    private fun filterDevices(
        devices: List<HomeDevice>,
        config: HomeAutomationWidgetConfig,
    ): List<HomeDevice> {
        var filtered = devices
        if (config.showRooms.isNotEmpty()) {
            filtered = filtered.filter { it.room in config.showRooms }
        }
        if (config.showDeviceTypes.isNotEmpty()) {
            filtered = filtered.filter { it.type.name in config.showDeviceTypes }
        }
        val room = selectedRoom.value
        if (room != null) {
            filtered = filtered.filter { it.room == room }
        }
        return filtered
    }

    fun selectRoom(room: String?) {
        selectedRoom.value = room
        viewModelScope.launch {
            repository.getDevices().collectLatest {
                devices.value = filterDevices(it, widgetConfig.value)
            }
        }
    }

    fun toggleDevice(device: HomeDevice) {
        val onOff = device.traits.filterIsInstance<DeviceTrait.OnOff>().firstOrNull() ?: return
        val command = HomeCommand.SetOnOff(device.id, !onOff.isOn)
        viewModelScope.launch {
            repository.executeCommand(command)
        }
    }

    fun setBrightness(device: HomeDevice, level: Int) {
        val command = HomeCommand.SetBrightness(device.id, level)
        viewModelScope.launch {
            repository.executeCommand(command)
        }
    }

    fun onConsentGranted() {
        needsConsent.value = null
        loadDevices()
    }

    fun signIn(context: Context) {
        context.startActivity(Intent(context, GoogleLoginActivity::class.java))
    }

    fun refresh() {
        loadDevices()
    }
}
