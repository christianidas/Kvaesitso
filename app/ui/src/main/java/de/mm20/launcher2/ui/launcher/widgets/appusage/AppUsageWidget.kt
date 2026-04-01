package de.mm20.launcher2.ui.launcher.widgets.appusage

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.AppUsageWidget as AppUsageWidgetData

@Composable
fun AppUsageWidget(widget: AppUsageWidgetData) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: AppUsageWidgetVM = viewModel(
        key = "app-usage-widget-${widget.id}",
        factory = AppUsageWidgetVM.Factory,
    )

    LaunchedEffect(widget.id, widget.config.topAppsCount) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh(context, widget.config.topAppsCount)
        }
    }

    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val totalScreenTime by viewModel.totalScreenTime.collectAsStateWithLifecycle()
    val topApps by viewModel.topApps.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (!hasPermission) {
            Text(
                text = stringResource(R.string.app_usage_widget_permission_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.app_usage_widget_grant_permission))
            }
        } else if (topApps.isEmpty()) {
            Text(
                text = stringResource(R.string.app_usage_widget_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.app_usage_widget_total_today),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(totalScreenTime),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            for (app in topApps) {
                AppUsageRow(
                    entry = app,
                    maxTimeMs = topApps.first().usageTimeMs,
                )
            }
        }
    }
}

@Composable
private fun AppUsageRow(
    entry: AppUsageEntry,
    maxTimeMs: Long,
) {
    val context = LocalContext.current
    val icon = remember(entry.packageName) {
        try {
            context.packageManager.getApplicationIcon(entry.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = icon,
            contentDescription = entry.appName,
            modifier = Modifier.size(28.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = entry.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = formatDuration(entry.usageTimeMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (maxTimeMs > 0) {
                LinearProgressIndicator(
                    progress = { (entry.usageTimeMs.toFloat() / maxTimeMs).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
