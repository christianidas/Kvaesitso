package de.mm20.launcher2.ui.launcher.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.data.customattrs.CustomTextIcon
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Folder
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.IconPicker
import de.mm20.launcher2.ui.component.BottomSheet
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.emojipicker.EmojiPicker
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalGridSettings

@Composable
fun EditFolderSheet(
    expanded: Boolean,
    folderId: String?,
    onDismiss: () -> Unit,
) {
    BottomSheet(
        state = folderId to expanded,
        expanded = { it.second },
    ) { (folderId, _) ->
        var confirmDismiss by remember { mutableStateOf(false) }
        val viewModel: EditFolderSheetVM = viewModel()
        val isCreatingNewFolder = folderId == null

        val density = LocalDensity.current
        LaunchedEffect(folderId) {
            viewModel.init(folderId, with(density) { 56.dp.toPx().toInt() })
        }
        if (viewModel.loading) return@BottomSheet

        if (confirmDismiss) {
            AlertDialog(
                onDismissRequest = { confirmDismiss = false },
                dismissButton = {
                    OutlinedButton(onClick = {
                        confirmDismiss = false
                    }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        confirmDismiss = false
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.action_quit))
                    }
                },
                text = {
                    Text(stringResource(R.string.dialog_discard_unsaved))
                }
            )
        }

        Column {
            CenterAlignedTopAppBar(
                title = {
                    when (viewModel.page) {
                        EditFolderSheetPage.CreateFolder,
                        EditFolderSheetPage.CustomizeFolder -> {
                            Text(stringResource(if (isCreatingNewFolder) R.string.create_folder_title else R.string.edit_folder_title))
                        }

                        EditFolderSheetPage.PickItems -> {
                            Text(stringResource(R.string.folder_select_items))
                        }

                        else -> {}
                    }
                },
                navigationIcon = {
                    if (viewModel.page == EditFolderSheetPage.PickIcon) {
                        FilledTonalIconButton(onClick = {
                            viewModel.closeIconPicker()
                        }) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                stringResource(R.string.menu_back)
                            )
                        }
                    }
                    if (viewModel.page == EditFolderSheetPage.PickItems && viewModel.wasOnLastPage) {
                        FilledTonalIconButton(onClick = {
                            viewModel.closeItemPicker()
                        }) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                stringResource(R.string.menu_back)
                            )
                        }
                    }
                },
                actions = {
                    if (viewModel.page != EditFolderSheetPage.PickIcon && viewModel.page != EditFolderSheetPage.PickItems || !viewModel.wasOnLastPage) {
                        FilledTonalIconButton(
                            onClick = {
                                if (viewModel.page == EditFolderSheetPage.PickItems || viewModel.page == EditFolderSheetPage.CustomizeFolder) {
                                    confirmDismiss = true
                                } else {
                                    onDismiss()
                                }
                            },
                        ) {
                            Icon(
                                painterResource(R.drawable.close_24px),
                                stringResource(R.string.close)
                            )
                        }
                    }
                },
            )

            AnimatedContent(
                viewModel.page,
                modifier = Modifier.weight(1f, false)
            ) { page ->
                when (page) {
                    EditFolderSheetPage.CreateFolder -> FolderCreatePage(viewModel)
                    EditFolderSheetPage.PickItems -> FolderPickItems(viewModel)
                    EditFolderSheetPage.CustomizeFolder -> FolderCustomize(viewModel)
                    EditFolderSheetPage.PickIcon -> FolderPickIcon(viewModel)
                }
            }
            AnimatedVisibility(
                viewModel.page == EditFolderSheetPage.CustomizeFolder ||
                        viewModel.page == EditFolderSheetPage.CreateFolder ||
                        viewModel.page == EditFolderSheetPage.PickItems
            ) {
                FlexibleBottomAppBar(
                    horizontalArrangement = Arrangement.End,
                ) {
                    when (viewModel.page) {
                        EditFolderSheetPage.CreateFolder -> {
                            Button(
                                modifier = Modifier.navigationBarsPadding(),
                                enabled = viewModel.folderName.isNotBlank(),
                                onClick = { viewModel.onClickContinue() }) {
                                Text(stringResource(R.string.action_next))
                            }
                        }

                        EditFolderSheetPage.PickItems -> {
                            Button(
                                modifier = Modifier.navigationBarsPadding(),
                                enabled = viewModel.folderItems.isNotEmpty() || viewModel.wasOnLastPage,
                                onClick = { viewModel.onClickContinue() }) {
                                Text(stringResource(if (viewModel.wasOnLastPage) R.string.action_done else R.string.action_next))
                            }
                        }

                        EditFolderSheetPage.CustomizeFolder -> {
                            Button(
                                modifier = Modifier.navigationBarsPadding(),
                                enabled = viewModel.folderName.isNotBlank(),
                                onClick = {
                                    viewModel.save()
                                    onDismiss()
                                }) {
                                Text(
                                    stringResource(if (viewModel.folderItems.isEmpty()) R.string.menu_delete else R.string.save)
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderCreatePage(viewModel: EditFolderSheetVM) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardActions = KeyboardActions(
                onDone = {
                    viewModel.onClickContinue()
                }
            ),
            label = { Text(stringResource(R.string.folder_name)) },
            value = viewModel.folderName,
            onValueChange = { viewModel.folderName = it }
        )
    }
}

@Composable
private fun FolderPickItems(viewModel: EditFolderSheetVM) {
    val columns = LocalGridSettings.current.columnCount - 1

    if (viewModel.wasOnLastPage) {
        BackHandler {
            viewModel.closeItemPicker()
        }
    }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(viewModel.selectableApps) {
            val iconSize = 32.dp.toPixels()
            val icon by remember(it.item.key) {
                viewModel.getIcon(it.item, iconSize.toInt())
            }.collectAsState(null)
            FolderSelectableListItem(item = it, icon = icon, onSelectionChanged = { selected ->
                if (selected) viewModel.selectItem(it.item)
                else viewModel.deselectItem(it.item)
            })
        }

        if (viewModel.selectableOther.isNotEmpty()) {
            item(span = { GridItemSpan(columns) }) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .fillMaxWidth()
                        .height(1.dp)
                )
            }

            items(viewModel.selectableOther) {
                val iconSize = 32.dp.toPixels()
                val icon by remember(it.item.key) {
                    viewModel.getIcon(it.item, iconSize.toInt())
                }.collectAsState(null)
                FolderSelectableListItem(item = it, icon = icon, onSelectionChanged = { selected ->
                    if (selected) viewModel.selectItem(it.item)
                    else viewModel.deselectItem(it.item)
                })
            }
        }
    }
}

@Composable
private fun FolderSelectableListItem(
    item: FolderSelectableItem,
    icon: LauncherIcon?,
    onSelectionChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ShapedLauncherIcon(
                icon = { icon },
                size = 48.dp,
                modifier = Modifier
                    .padding(4.dp)
                    .clickable {
                        onSelectionChanged(!item.isSelected)
                    },
            )
            if (item.isSelected) {
                Surface(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.BottomEnd),
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    onClick = {
                        onSelectionChanged(false)
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.check_20px),
                        null,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
        Text(
            modifier = Modifier
                .padding(top = 8.dp)
                .padding(horizontal = 12.dp),
            text = item.item.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderCustomize(viewModel: EditFolderSheetVM) {
    val iconSize = 32.dp.toPixels()
    val folderIcon by remember(viewModel.folderCustomIcon) { viewModel.folderCustomIcon }.collectAsState()
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val outlineVariant = MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .clip(CircleShape)
                    .clickable {
                        viewModel.openIconPicker()
                    }
                    .size(72.dp)
                        then (
                        if (folderIcon != null) {
                            Modifier
                        } else {
                            Modifier.drawBehind {
                                val w = with(density) { 2.dp.toPx() }
                                drawCircle(
                                    color = outlineVariant,
                                    style = Stroke(
                                        width = w,
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(
                                                w * 2,
                                                w * 2
                                            )
                                        )
                                    )
                                )
                            }
                        }
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (folderIcon != null) {
                    val icon =
                        remember(viewModel.folderIcon) { viewModel.folderIcon }.collectAsState(null)
                    ShapedLauncherIcon(
                        size = 56.dp,
                        icon = { icon.value },
                        shape = CircleShape,
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.folder_24px),
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name)) },
                value = viewModel.folderName,
                onValueChange = { viewModel.folderName = it },
                isError = viewModel.folderName.isBlank(),
                supportingText = if (viewModel.folderName.isBlank()) {
                    {
                        Text(stringResource(R.string.folder_name_empty_error))
                    }
                } else null
            )
        }

        val icon1 = remember(viewModel.folderItems.getOrNull(0)?.key) {
            viewModel.folderItems.getOrNull(0)?.let {
                viewModel.getIcon(it, iconSize.toInt())
            }
        }?.collectAsState(null)
        val icon2 = remember(viewModel.folderItems.getOrNull(1)?.key) {
            viewModel.folderItems.getOrNull(1)?.let {
                viewModel.getIcon(it, iconSize.toInt())
            }
        }?.collectAsState(null)
        val icon3 = remember(viewModel.folderItems.getOrNull(2)?.key) {
            viewModel.folderItems.getOrNull(2)?.let {
                viewModel.getIcon(it, iconSize.toInt())
            }
        }?.collectAsState(null)
        TextButton(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            onClick = { viewModel.openItemPicker() }) {
            Text(
                modifier = Modifier.weight(1f),
                text = pluralStringResource(
                    R.plurals.folder_selected_items,
                    viewModel.folderItems.size,
                    viewModel.folderItems.size
                )
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(64.dp)
                    .height(32.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                icon1?.value?.let {
                    ShapedLauncherIcon(
                        size = 32.dp,
                        icon = { it },
                        modifier = Modifier.offset(x = -0.dp)
                    )
                }
                icon2?.value?.let {
                    ShapedLauncherIcon(
                        size = 32.dp,
                        icon = { it },
                        modifier = Modifier.offset(x = -16.dp)
                    )
                }
                icon3?.value?.let {
                    ShapedLauncherIcon(
                        size = 32.dp,
                        icon = { it },
                        modifier = Modifier.offset(x = -32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderPickIcon(
    viewModel: EditFolderSheetVM,
) {
    BackHandler {
        viewModel.closeIconPicker()
    }
    val icon by remember(viewModel.folderCustomIcon) { viewModel.folderCustomIcon }.collectAsState()
    val folder = Folder(id = "", name = viewModel.folderName)
    val selectedTabIndex = remember {
        mutableIntStateOf(
            when (icon) {
                is CustomTextIcon -> 1
                else -> 0
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            ToggleButton(
                modifier = Modifier.weight(1f),
                checked = selectedTabIndex.intValue == 0,
                onCheckedChange = { selectedTabIndex.intValue = 0 },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
            ) {
                Icon(
                    painterResource(
                        if (selectedTabIndex.intValue == 0) R.drawable.check_20px else R.drawable.apps_20px
                    ),
                    null,
                    modifier = Modifier
                        .padding(end = ToggleButtonDefaults.IconSpacing)
                        .size(
                            ToggleButtonDefaults.IconSize
                        )
                )
                Text(stringResource(R.string.folder_icon_customicon))
            }
            ToggleButton(
                modifier = Modifier.weight(1f),
                checked = selectedTabIndex.intValue == 1,
                onCheckedChange = { selectedTabIndex.intValue = 1 },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
            ) {
                Icon(
                    painterResource(
                        if (selectedTabIndex.intValue == 1) R.drawable.check_20px else R.drawable.mood_20px
                    ),
                    null,
                    modifier = Modifier
                        .padding(end = ToggleButtonDefaults.IconSpacing)
                        .size(ToggleButtonDefaults.IconSize)
                )
                Text(stringResource(R.string.folder_icon_emoji))
            }
        }
        AnimatedContent(
            selectedTabIndex.intValue,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            when (it) {
                0 -> {
                    IconPicker(
                        searchable = folder,
                        onSelect = { viewModel.selectIcon(it) },
                        contentPadding = PaddingValues(
                            bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding()
                        )
                    )
                }

                1 -> {
                    EmojiPicker(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onEmojiSelected = {
                            viewModel.selectIcon(CustomTextIcon(text = it))
                        },
                        contentPadding = PaddingValues(
                            bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding()
                        )
                    )
                }
            }
        }
    }
}
