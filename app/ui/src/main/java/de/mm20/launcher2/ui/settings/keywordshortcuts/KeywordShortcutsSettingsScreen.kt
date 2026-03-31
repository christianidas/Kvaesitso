package de.mm20.launcher2.ui.settings.keywordshortcuts

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.searchactions.builders.KeywordShortcutBuilder
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchActionIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object KeywordShortcutsSettingsRoute : NavKey

@Composable
fun KeywordShortcutsSettingsScreen() {
    val viewModel: KeywordShortcutsSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current
    val systemUiController = rememberSystemUiController()
    systemUiController.setStatusBarColor(MaterialTheme.colorScheme.surface)
    systemUiController.setNavigationBarColor(Color.Black)

    val activity = LocalActivity.current as? AppCompatActivity

    val shortcuts by viewModel.keywordShortcuts.collectAsState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Keyword Shortcuts",
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 1
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (backStack.size <= 1) {
                            activity?.onBackPressed()
                        } else {
                            backStack.removeLastOrNull()
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { contentPadding ->

        LazyColumn(
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(
                items = shortcuts,
                key = { _, it -> it.key }
            ) { index, item ->
                val shape = getShape(index, shortcuts.size)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clip(shape)
                ) {
                    Preference(
                        icon = {
                            SearchActionIcon(item)
                        },
                        title = item.label,
                        summary = buildSummary(item),
                        onClick = {
                            viewModel.editShortcut(item)
                        },
                        controls = {
                            var showDropdown by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { showDropdown = true }
                            ) {
                                Icon(
                                    painterResource(R.drawable.more_vert_24px),
                                    stringResource(R.string.edit)
                                )
                                DropdownMenuPopup(
                                    expanded = showDropdown,
                                    onDismissRequest = { showDropdown = false }
                                ) {
                                    DropdownMenuGroup(
                                        shapes = MenuDefaults.groupShapes()
                                    ) {
                                        DropdownMenuItem(
                                            shape = MenuDefaults.leadingItemShape,
                                            leadingIcon = {
                                                Icon(
                                                    painterResource(R.drawable.edit_24px),
                                                    contentDescription = null
                                                )
                                            },
                                            text = { Text(stringResource(R.string.edit)) },
                                            onClick = {
                                                viewModel.editShortcut(item)
                                                showDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            shape = MenuDefaults.trailingItemShape,
                                            leadingIcon = {
                                                Icon(
                                                    painterResource(R.drawable.delete_24px),
                                                    contentDescription = null
                                                )
                                            },
                                            text = { Text(stringResource(R.string.menu_delete)) },
                                            onClick = {
                                                viewModel.removeShortcut(item)
                                                showDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
            item(key = "add-button") {
                FilledTonalButton(
                    modifier = Modifier
                        .padding(top = 10.dp, start = 12.dp, end = 12.dp)
                        .navigationBarsPadding(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    onClick = {
                        viewModel.createShortcut()
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.add_20px),
                        null,
                        modifier = Modifier
                            .padding(end = ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize)
                    )
                    Text("New shortcut")
                }
            }
        }
    }

    val editShortcut by viewModel.showEditDialogFor
    val createShortcut by viewModel.showCreateDialog

    EditKeywordShortcutSheet(
        expanded = createShortcut,
        initial = null,
        onSave = { viewModel.addShortcut(it) },
        onDismiss = { viewModel.dismissDialogs() },
    )
    EditKeywordShortcutSheet(
        expanded = editShortcut != null,
        initial = editShortcut,
        onSave = { viewModel.updateShortcut(editShortcut!!, it) },
        onDismiss = { viewModel.dismissDialogs() },
    )
}

private fun buildSummary(item: KeywordShortcutBuilder): String {
    val typeLabel = when (item.actionType) {
        KeywordShortcutBuilder.ActionType.Sms -> if (item.silentSms) "SMS (silent)" else "SMS"
        KeywordShortcutBuilder.ActionType.OpenUrl -> "Open URL"
        KeywordShortcutBuilder.ActionType.HttpRequest -> "${item.httpMethod ?: "GET"} request"
    }
    return "\"${item.keyword}\" → $typeLabel"
}

@Composable
private fun getShape(index: Int, total: Int): androidx.compose.ui.graphics.Shape {
    if (total == 1) {
        return MaterialTheme.shapes.medium
    }
    if (total > 1 && index > 0 && index < total - 1) {
        return MaterialTheme.shapes.extraSmall
    }
    val xs = MaterialTheme.shapes.extraSmall
    val md = MaterialTheme.shapes.medium
    if (index == 0) {
        return xs.copy(topStart = md.topStart, topEnd = md.topEnd)
    } else {
        return xs.copy(bottomStart = md.bottomStart, bottomEnd = md.bottomEnd)
    }
}
