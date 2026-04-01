package de.mm20.launcher2.ui.launcher.widgets.smartsuggestions

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.search.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.Calendar

class SmartSuggestionsWidgetVM(
    private val appRepository: AppRepository,
) : ViewModel() {

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission = _hasPermission.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Application>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    fun refresh(context: Context, count: Int) {
        viewModelScope.launch {
            _hasPermission.value = checkPermission(context)
            if (_hasPermission.value) {
                loadSuggestions(context, count)
            }
        }
    }

    private fun checkPermission(context: Context): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private suspend fun loadSuggestions(context: Context, count: Int) = withContext(Dispatchers.IO) {
        val usageStatsManager = context.getSystemService<UsageStatsManager>() ?: return@withContext
        val launcherPackage = context.packageName

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)

        // Look back 14 days for patterns
        val endTime = now.timeInMillis
        val startTime = endTime - 14L * 24 * 60 * 60 * 1000

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        // Count foreground events per package per hour-of-day
        // hourScores[packageName] = total score for current time window
        val hourScores = mutableMapOf<String, Float>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue

            val pkg = event.packageName
            if (pkg == launcherPackage) continue

            val eventCal = Calendar.getInstance().apply { timeInMillis = event.timeStamp }
            val eventHour = eventCal.get(Calendar.HOUR_OF_DAY)

            // Score based on proximity to current hour (±2 hour window, gaussian-ish)
            val hourDist = minOf(
                Math.abs(eventHour - currentHour),
                24 - Math.abs(eventHour - currentHour),
            )
            val score = when (hourDist) {
                0 -> 1.0f
                1 -> 0.6f
                2 -> 0.2f
                else -> 0f
            }
            if (score > 0f) {
                hourScores[pkg] = (hourScores[pkg] ?: 0f) + score
            }
        }

        val rankedPackages = hourScores.entries
            .sortedByDescending { it.value }
            .take(count)
            .map { it.key }

        // Resolve to Application objects
        val allApps = appRepository.findMany().first()
        val appsByPackage = allApps.groupBy { it.componentName.packageName }

        val resolved = rankedPackages.mapNotNull { pkg ->
            appsByPackage[pkg]?.firstOrNull()
        }

        _suggestions.value = resolved
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                SmartSuggestionsWidgetVM(get())
            }
        }
    }
}
