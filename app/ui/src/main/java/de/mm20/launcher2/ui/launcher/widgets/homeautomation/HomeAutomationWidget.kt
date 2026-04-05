package de.mm20.launcher2.ui.launcher.widgets.homeautomation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.homeautomation.DeviceTrait
import de.mm20.launcher2.homeautomation.DeviceType
import de.mm20.launcher2.homeautomation.HomeDevice
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.HomeAutomationWidget as HomeAutomationWidgetData

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeAutomationWidget(
    widget: HomeAutomationWidgetData,
) {
    val viewModel: HomeAutomationWidgetVM = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(widget) {
        viewModel.updateWidget(widget)
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume()
        }
    }

    val devices by viewModel.devices
    val isSignedIn by viewModel.isSignedIn
    val isLoading by viewModel.isLoading
    val selectedRoom by viewModel.selectedRoom
    val structures by viewModel.structures

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(16.dp),
    ) {
        if (!isSignedIn) {
            SignInPrompt(onSignIn = { viewModel.signIn(context) })
            return@Column
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(
                        painterResource(R.drawable.autorenew_24px),
                        contentDescription = "Refresh",
                    )
                }
            }
        }

        // Room filter chips
        val rooms = structures.flatMap { it.rooms }.map { it.name }.distinct()
        if (rooms.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedRoom == null,
                    onClick = { viewModel.selectRoom(null) },
                    label = { Text("All") },
                )
                rooms.forEach { room ->
                    FilterChip(
                        selected = selectedRoom == room,
                        onClick = { viewModel.selectRoom(room) },
                        label = { Text(room) },
                    )
                }
            }
        }

        // Device list
        if (devices.isEmpty() && !isLoading) {
            Text(
                text = "No devices found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            devices.forEach { device ->
                DeviceCard(
                    device = device,
                    onToggle = { viewModel.toggleDevice(device) },
                    onBrightnessChange = { viewModel.setBrightness(device, it) },
                )
            }
        }
    }
}

@Composable
private fun SignInPrompt(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sign in with Google to control your smart home devices",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onSignIn) {
            Text("Sign in")
        }
    }
}

@Composable
fun DeviceCard(
    device: HomeDevice,
    onToggle: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
) {
    val onOff = device.traits.filterIsInstance<DeviceTrait.OnOff>().firstOrNull()
    val brightness = device.traits.filterIsInstance<DeviceTrait.Brightness>().firstOrNull()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(deviceTypeIcon(device.type)),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 0.dp),
                tint = if (onOff?.isOn == true)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                device.room?.let { room ->
                    Text(
                        text = room,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onOff != null) {
                Switch(
                    checked = onOff.isOn,
                    onCheckedChange = { onToggle() },
                )
            }
        }

        // Expanded controls
        if (expanded && brightness != null && onOff?.isOn == true) {
            var sliderValue by remember(brightness.level) { mutableStateOf(brightness.level.toFloat()) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onBrightnessChange(sliderValue.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${sliderValue.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun deviceTypeIcon(type: DeviceType): Int {
    return when (type) {
        DeviceType.Light -> R.drawable.light_mode_24px
        DeviceType.Switch -> R.drawable.toggle_on_24px
        DeviceType.Thermostat -> R.drawable.device_thermostat_24px
        DeviceType.Lock -> R.drawable.lock_24px
        DeviceType.Fan -> R.drawable.air_24px
        else -> R.drawable.home_24px
    }
}
