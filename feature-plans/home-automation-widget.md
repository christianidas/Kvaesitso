# Home Automation Widget - Feature Plan

## Overview

Add a home automation widget to Kvaesitso that integrates with Google Home via the Google Home APIs. Reuses the existing Google authentication from `libs/google/`. Supports both a launcher widget and search keyword triggers for controlling devices.

---

## Google Cloud Project Setup

Assuming the project already exists with Tasks API enabled for the Todo widget:

### 1. Enable APIs

In [Google Cloud Console](https://console.cloud.google.com/apis/library):

- Enable **Google Home API** (`Google Home Developer Console` - see step 2)
- The Home APIs use the **Google Home Developer Console** at https://home.google.com/developer-console/ rather than the standard Cloud Console API library

### 2. Google Home Developer Console Setup

1. Go to https://home.google.com/developer-console/
2. Select your existing project (same one used for Tasks)
3. Go to **Home APIs** and enable access
4. Under **OAuth consent**, ensure the consent screen includes the Home scopes
5. Required OAuth scopes:
   - `https://www.googleapis.com/auth/homegraph` - read home structure (rooms, devices)
   - `https://www.googleapis.com/auth/sdm.service` - device control (if using Device Access / SDM)
   
   **Note:** Google Home APIs are evolving. As of 2025, the newer **Home APIs** (not legacy SDM) use:
   - `https://www.googleapis.com/auth/home.run` - execute device commands
   - `https://www.googleapis.com/auth/home.read` - read device state and structure

6. Add these scopes to your OAuth consent screen in Cloud Console > **APIs & Services > OAuth consent screen > Edit > Scopes**

### 3. Verify OAuth Client ID

The existing Web OAuth client ID (from `local.properties` `google.clientId`) should work. No new client ID is needed since we're adding scopes to the same Google account sign-in.

### 4. Device Access Console (if using SDM path)

If using the Smart Device Management API for Nest devices:
1. Go to https://console.nest.google.com/device-access
2. Create a project (one-time $5 fee)
3. Link your OAuth client ID
4. Note the **Project ID** - needed for API calls

---

## Architecture

### Auth Changes

**File:** `libs/google/src/main/java/de/mm20/launcher2/google/GoogleApiHelper.kt`

Currently requests only `TASKS_SCOPE`. Needs to support multiple scopes:

```
Current:  "oauth2:$TASKS_SCOPE"
Proposed: "oauth2:$TASKS_SCOPE $HOME_SCOPE_RUN $HOME_SCOPE_READ"
```

Changes needed:
- Add scope constants: `HOME_SCOPE_RUN`, `HOME_SCOPE_READ`
- Combine scopes in `getAccessToken()` as space-separated string (GoogleAuthUtil supports this)
- Add a dedicated `getHomeAccessToken()` method that requests only Home scopes (in case user wants Tasks but not Home, or vice versa)
- Update `GoogleLoginActivity.requestTasksConsent()` to also request Home scopes, or add a separate consent flow triggered from the Home widget settings

**Alternative (preferred for fork-friendliness):** Add a new method `getAccessTokenForScopes(vararg scopes: String)` that the caller provides scopes to, keeping the existing `getAccessToken()` unchanged. The Home widget code passes its own scopes.

### New Module: `data/homeautomation/`

New Gradle module following the pattern of `data/calendar/`:

```
data/homeautomation/
  build.gradle.kts
  src/main/java/de/mm20/launcher2/homeautomation/
    HomeAutomationRepository.kt      -- Main repository interface
    HomeAutomationDevice.kt          -- Device data model
    HomeAutomationRoom.kt            -- Room/structure data model  
    HomeAutomationCommand.kt         -- Command sealed class
    providers/
      GoogleHomeProvider.kt          -- Google Home API implementation
    Module.kt                        -- Koin DI module
```

### Data Models

```kotlin
// Device representation
data class HomeDevice(
    val id: String,
    val name: String,
    val room: String?,
    val type: DeviceType,           // Light, Thermostat, Switch, Lock, Camera, Speaker, etc.
    val traits: List<DeviceTrait>,  // On/Off, Brightness, ColorTemp, ThermostatMode, etc.
    val state: Map<String, Any?>,   // Current state values
)

enum class DeviceType {
    Light, Switch, Thermostat, Lock, Camera, Speaker, Display, Fan, 
    Blinds, Garage, Vacuum, Other
}

sealed class DeviceTrait {
    data class OnOff(val isOn: Boolean) : DeviceTrait()
    data class Brightness(val level: Int) : DeviceTrait()      // 0-100
    data class ColorTemperature(val kelvin: Int) : DeviceTrait()
    data class ThermostatMode(val mode: String, val setpointF: Float?) : DeviceTrait()
    data class LockUnlock(val isLocked: Boolean) : DeviceTrait()
    // Extend as needed
}

// Command to execute
sealed class HomeCommand {
    data class SetOnOff(val deviceId: String, val on: Boolean) : HomeCommand()
    data class SetBrightness(val deviceId: String, val level: Int) : HomeCommand()
    data class SetThermostat(val deviceId: String, val mode: String, val setpoint: Float?) : HomeCommand()
    data class RunScene(val sceneId: String) : HomeCommand()
    // Extend as needed
}
```

### Widget Implementation

**Data model:** `data/widgets/src/main/java/de/mm20/launcher2/widgets/HomeAutomationWidget.kt`

```kotlin
@Serializable
data class HomeAutomationWidgetConfig(
    val showRooms: List<String> = emptyList(),     // Empty = show all
    val showDeviceTypes: List<String> = emptyList(), // Empty = show all
    val showScenes: Boolean = true,
    val compactMode: Boolean = false,
)

data class HomeAutomationWidget(
    override val id: UUID,
    val config: HomeAutomationWidgetConfig = HomeAutomationWidgetConfig(),
) : Widget() {
    companion object {
        const val Type = "home_automation"
    }
}
```

**Registration:** Add to `WidgetsService.getBuiltInWidgets()`:
```kotlin
BuiltInWidgetInfo(type = HomeAutomationWidget.Type, label = "Home")
```

**Deserialization:** Add case in `Widget.fromDatabaseEntity()`:
```kotlin
HomeAutomationWidget.Type -> HomeAutomationWidget(
    id = entity.id,
    config = entity.config?.let { Json.decodeFromStringOrNull(it) } ?: HomeAutomationWidgetConfig(),
)
```

**UI:** `app/ui/src/main/java/de/mm20/launcher2/ui/launcher/widgets/homeautomation/`
```
HomeAutomationWidget.kt       -- Main composable
HomeAutomationWidgetVM.kt     -- ViewModel
DeviceCard.kt                 -- Individual device control card
SceneCard.kt                  -- Scene/routine activation card
```

Widget UI concept:
- Header with "Home" title and room filter chips
- Quick scene/routine buttons row (e.g., "Good morning", "Away", "Movie time")
- Device cards in a grid/list showing:
  - Device name + room
  - Current state (on/off, temperature, brightness)
  - Primary toggle (tap to on/off)
  - Expandable controls (brightness slider, thermostat dial, etc.)
- Pull-to-refresh for state updates
- Settings gear to configure visible rooms/device types

### Search Keyword Integration

Two approaches, implement both:

#### A. Built-in Search Provider

New search provider in `services/search/` that responds to queries like:
- "lights off" / "lights on"
- "turn off [device name]"
- "set [device] to [value]"
- "[scene name]" (matched against known scenes)

**File:** `data/homeautomation/src/main/java/de/mm20/launcher2/homeautomation/HomeAutomationSearchProvider.kt`

This integrates with `SearchService` to show device/scene results inline with other search results. Each result is a `Searchable` that, when activated, executes the command.

#### B. Keyword Shortcuts (User-Configurable)

Users can create keyword shortcuts in the existing search actions system:

The existing `KeywordShortcutBuilder` supports `HttpRequest` action type. Users could configure:
- Keyword: "lights off"  
- Action: HTTP request to Google Home API (via a local proxy or direct API call)

However, direct API calls from keyword shortcuts would require embedding auth tokens, which isn't practical. Instead, add a new `ActionType` to `KeywordShortcutBuilder`:

```kotlin
enum class ActionType { Sms, OpenUrl, HttpRequest, HomeAutomation }
```

Or better: add a new builder type `HomeAutomationActionBuilder` that:
- Matches keywords against device names and common commands
- Uses the `HomeAutomationRepository` to execute
- Shows device state as the search result icon/subtitle

---

## Implementation Order

### Phase 1: Auth Foundation
1. Update `GoogleApiHelper` to support multiple scopes (add `getAccessTokenForScopes()`)
2. Add Home scope constants
3. Update login flow to request Home scopes (or add separate consent trigger)

### Phase 2: Data Layer
4. Create `data/homeautomation/` module
5. Implement `GoogleHomeProvider` with REST API calls
6. Implement `HomeAutomationRepository` interface
7. Register Koin module

### Phase 3: Widget
8. Add `HomeAutomationWidget` data model in `data/widgets/`
9. Register in `WidgetsService` and `Widget.fromDatabaseEntity()`
10. Build widget UI composables
11. Build ViewModel with state management and command execution

### Phase 4: Search Integration
12. Implement `HomeAutomationSearchProvider`
13. Register in search service
14. Add `HomeAutomationActionBuilder` for keyword shortcuts

### Phase 5: Polish
15. Widget configuration UI (room/device type filters)
16. Error handling (offline devices, auth failures, API rate limits)
17. Caching strategy for device state (avoid excessive API calls)
18. Icons for device types

---

## Google Home API Reference

### Endpoints (Home APIs - newer)

Base URL: `https://home.googleapis.com/v1/`

- `GET /homes` - List homes/structures
- `GET /homes/{homeId}/rooms` - List rooms
- `GET /homes/{homeId}/devices` - List devices
- `POST /homes/{homeId}/devices/{deviceId}:executeCommand` - Control device
- `GET /homes/{homeId}/scenes` - List scenes
- `POST /homes/{homeId}/scenes/{sceneId}:activate` - Activate scene

### Endpoints (SDM / Device Access - for Nest)

Base URL: `https://smartdevicemanagement.googleapis.com/v1/`

- `GET /enterprises/{projectId}/structures` - List structures
- `GET /enterprises/{projectId}/devices` - List devices  
- `POST /enterprises/{projectId}/devices/{deviceId}:executeCommand` - Execute command

### Common Device Traits (Google Home ecosystem)

| Trait | Commands | States |
|-------|----------|--------|
| OnOff | `on`, `off` | `on: bool` |
| Brightness | `setBrightness(level)` | `brightness: int` |
| ColorSetting | `setColor(...)` | `color: {...}` |
| TemperatureSetting | `setTemperature(temp)` | `temperatureSetpoint`, `ambientTemperature` |
| LockUnlock | `lock`, `unlock` | `isLocked: bool` |
| Scene | `activate` | N/A |
| FanSpeed | `setSpeed(speed)` | `currentSpeed` |
| OpenClose | `open(pct)`, `close` | `openPercent: int` |

---

## Key Files to Modify

| File | Change |
|------|--------|
| `libs/google/.../GoogleApiHelper.kt` | Add Home scopes, multi-scope token method |
| `libs/google/.../GoogleLoginActivity.kt` | Request Home scope consent |
| `data/widgets/.../Widget.kt` | Add `HomeAutomationWidget` deserialization case |
| `services/widgets/.../WidgetsService.kt` | Register widget in `getBuiltInWidgets()` |
| `services/search/.../SearchService.kt` | Add home automation provider |
| `data/search-actions/.../SearchActionBuilder.kt` | Add HomeAutomation action type (optional) |
| `libs/google/build.gradle.kts` | No changes needed (same auth libs) |
| `settings.gradle.kts` | Include new `data/homeautomation` module |
| `app/ui/build.gradle.kts` | Depend on new module |

### New Files

| File | Purpose |
|------|---------|
| `data/homeautomation/build.gradle.kts` | Module build config |
| `data/homeautomation/.../HomeAutomationRepository.kt` | Repository interface + impl |
| `data/homeautomation/.../HomeDevice.kt` | Device data model |
| `data/homeautomation/.../HomeCommand.kt` | Command sealed class |
| `data/homeautomation/.../providers/GoogleHomeProvider.kt` | Google Home REST API |
| `data/homeautomation/.../Module.kt` | Koin DI |
| `data/widgets/.../HomeAutomationWidget.kt` | Widget data model |
| `app/ui/.../widgets/homeautomation/HomeAutomationWidget.kt` | Widget UI |
| `app/ui/.../widgets/homeautomation/HomeAutomationWidgetVM.kt` | ViewModel |
| `app/ui/.../widgets/homeautomation/DeviceCard.kt` | Device control composable |

---

## Open Questions

1. **Which Google Home API version?** The newer Home APIs (2024+) vs the older SDM/Device Access API. The newer APIs are simpler but may have limited device support. Could support both behind the provider interface.
2. **Local vs cloud control?** Google Home supports local SDK for some devices - adds complexity but faster response. Start with cloud-only.
3. **Polling vs push for state updates?** Cloud API doesn't support push. Options: poll on widget visibility, manual refresh, or periodic background sync.
4. **Should scenes be separate from devices in the widget?** Recommend yes - scenes as a quick-action row at the top.
5. **Rate limits?** Google Home API has rate limits. Need caching strategy - cache device list aggressively, cache state for ~30s.
