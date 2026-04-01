package de.mm20.launcher2.ui.launcher.widgets.appusage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppUsageEntry(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
)

class AppUsageWidgetVM : ViewModel() {

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission = _hasPermission.asStateFlow()

    private val _totalScreenTime = MutableStateFlow(0L)
    val totalScreenTime = _totalScreenTime.asStateFlow()

    private val _topApps = MutableStateFlow<List<AppUsageEntry>>(emptyList())
    val topApps = _topApps.asStateFlow()

    fun refresh(context: Context, topCount: Int) {
        viewModelScope.launch {
            _hasPermission.value = checkPermission(context)
            if (_hasPermission.value) {
                loadUsageStats(context, topCount)
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

    private suspend fun loadUsageStats(context: Context, topCount: Int) = withContext(Dispatchers.IO) {
        val usageStatsManager = context.getSystemService<UsageStatsManager>() ?: return@withContext
        val pm = context.packageManager

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime,
        )

        val launcherPackage = context.packageName

        val aggregated = stats
            .filter { it.totalTimeInForeground > 0 && it.packageName != launcherPackage }
            .groupBy { it.packageName }
            .mapValues { (_, entries) -> entries.sumOf { it.totalTimeInForeground } }

        val total = aggregated.values.sum()
        _totalScreenTime.value = total

        val top = aggregated.entries
            .sortedByDescending { it.value }
            .take(topCount)
            .map { (pkg, time) ->
                val appName = try {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    pkg
                }
                AppUsageEntry(pkg, appName, time)
            }

        _topApps.value = top
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                AppUsageWidgetVM()
            }
        }
    }
}
