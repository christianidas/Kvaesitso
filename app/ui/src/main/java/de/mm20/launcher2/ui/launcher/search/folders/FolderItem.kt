package de.mm20.launcher2.ui.launcher.search.folders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.Folder
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalGridSettings

@Composable
fun FolderItemGridPopup(
    folder: Folder,
    show: MutableTransitionState<Boolean>,
    animationProgress: Float,
    origin: IntRect,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.TopEnd,
        ) { origin.size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.TopEnd,
        ) { origin.size },
    ) {
        FolderItemContent(
            modifier = Modifier
                .fillMaxWidth()
                .offset(
                    x = lerp(16.dp, 0.dp, animationProgress),
                    y = lerp((-16).dp, 0.dp, animationProgress)
                ),
            folder = folder,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun FolderItemContent(
    modifier: Modifier = Modifier,
    folder: Folder,
    onDismiss: () -> Unit,
) {
    val viewModel: FolderItemVM = viewModel(key = "folder-${folder.id}")
    val context = LocalContext.current
    val iconSize = 32.dp.toPixels()

    LaunchedEffect(folder) {
        viewModel.init(folder, iconSize.toInt())
    }

    val items by viewModel.folderItems.collectAsState()
    val columns = LocalGridSettings.current.columnCount - 1

    val bottomSheetManager = LocalBottomSheetManager.current

    Column(modifier = modifier.padding(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = folder.labelOverride ?: folder.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                bottomSheetManager.showEditFolderSheet(folder.id)
                onDismiss()
            }) {
                Icon(
                    painterResource(R.drawable.edit_24px),
                    contentDescription = stringResource(R.string.edit_folder_title),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceAtLeast(3)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items, key = { it.key }) { item ->
                GridItem(
                    item = item,
                    showLabels = true,
                )
            }
        }
    }
}
