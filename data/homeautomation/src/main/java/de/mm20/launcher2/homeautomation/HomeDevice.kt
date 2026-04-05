package de.mm20.launcher2.homeautomation

import kotlinx.serialization.Serializable

@Serializable
data class HomeDevice(
    val id: String,
    val name: String,
    val room: String?,
    val type: DeviceType,
    val traits: List<DeviceTrait>,
)

@Serializable
enum class DeviceType {
    Light, Switch, Thermostat, Lock, Camera, Speaker, Display, Fan,
    Blinds, Garage, Vacuum, Other
}

@Serializable
sealed class DeviceTrait {
    @Serializable
    data class OnOff(val isOn: Boolean) : DeviceTrait()

    @Serializable
    data class Brightness(val level: Int) : DeviceTrait()

    @Serializable
    data class ColorTemperature(val kelvin: Int) : DeviceTrait()

    @Serializable
    data class ThermostatMode(
        val mode: String,
        val setpointCelsius: Float?,
        val ambientCelsius: Float?,
    ) : DeviceTrait()

    @Serializable
    data class LockUnlock(val isLocked: Boolean) : DeviceTrait()

    @Serializable
    data class FanSpeed(val speed: Int) : DeviceTrait()

    @Serializable
    data class OpenClose(val openPercent: Int) : DeviceTrait()
}
