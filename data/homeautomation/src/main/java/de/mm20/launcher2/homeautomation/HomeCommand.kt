package de.mm20.launcher2.homeautomation

sealed class HomeCommand {
    abstract val deviceId: String

    data class SetOnOff(override val deviceId: String, val on: Boolean) : HomeCommand()
    data class SetBrightness(override val deviceId: String, val level: Int) : HomeCommand()
    data class SetColorTemperature(override val deviceId: String, val kelvin: Int) : HomeCommand()
    data class SetThermostat(
        override val deviceId: String,
        val mode: String?,
        val setpointCelsius: Float?,
    ) : HomeCommand()
    data class SetLock(override val deviceId: String, val locked: Boolean) : HomeCommand()
    data class SetFanSpeed(override val deviceId: String, val speed: Int) : HomeCommand()
    data class SetOpenClose(override val deviceId: String, val openPercent: Int) : HomeCommand()
}

data class HomeScene(
    val id: String,
    val name: String,
)

sealed class HomeSceneCommand {
    data class Activate(val sceneId: String) : HomeSceneCommand()
}
