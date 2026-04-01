package de.mm20.launcher2.ui.launcher.widgets.smartsuggestions

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import de.mm20.launcher2.widgets.SmartSuggestionsWidget as SmartSuggestionsWidgetData

@Composable
fun SmartSuggestionsWidget(widget: SmartSuggestionsWidgetData) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SmartSuggestionsWidgetVM = viewModel(
        key = "smart-suggestions-widget-${widget.id}",
        factory = SmartSuggestionsWidgetVM.Factory,
    )

    LaunchedEffect(widget.id, widget.config.suggestionCount) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh(context, widget.config.suggestionCount)
        }
    }

    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.smart_suggestions_widget_permission_required),
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
                    Text(stringResource(R.string.smart_suggestions_widget_grant_permission))
                }
            }
        } else if (suggestions.isEmpty()) {
            Banner(
                modifier = Modifier.padding(16.dp),
                text = stringResource(R.string.smart_suggestions_widget_no_data),
                icon = R.drawable.apps_24px,
            )
        } else {
            SearchResultGrid(suggestions)
        }
    }
}
