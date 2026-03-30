package de.mm20.launcher2.ui.settings.keywordshortcuts

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.searchactions.SearchActionService
import de.mm20.launcher2.searchactions.builders.KeywordShortcutBuilder
import de.mm20.launcher2.searchactions.builders.SearchActionBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class KeywordShortcutsSettingsScreenVM : ViewModel(), KoinComponent {
    private val searchActionService: SearchActionService by inject()

    private val allBuilders = searchActionService
        .getSearchActionBuilders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val keywordShortcuts = allBuilders
        .map { it.filterIsInstance<KeywordShortcutBuilder>() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun addShortcut(shortcut: KeywordShortcutBuilder) {
        val actions = allBuilders.value.filter { it.key != shortcut.key } + shortcut
        searchActionService.saveSearchActionBuilders(actions)
        showCreateDialog.value = false
    }

    fun removeShortcut(shortcut: KeywordShortcutBuilder) {
        val actions = allBuilders.value.filter { it.key != shortcut.key }
        searchActionService.saveSearchActionBuilders(actions)
        showEditDialogFor.value = null
    }

    fun updateShortcut(old: KeywordShortcutBuilder, new: KeywordShortcutBuilder) {
        val actions = allBuilders.value
            .mapNotNull { if (it.key == old.key) new else if (it.key == new.key) null else it }
        searchActionService.saveSearchActionBuilders(actions)
        showEditDialogFor.value = null
    }

    val showEditDialogFor = mutableStateOf<KeywordShortcutBuilder?>(null)
    val showCreateDialog = mutableStateOf(false)

    fun editShortcut(shortcut: KeywordShortcutBuilder) {
        showEditDialogFor.value = shortcut
    }

    fun createShortcut() {
        showCreateDialog.value = true
    }

    fun dismissDialogs() {
        showCreateDialog.value = false
        showEditDialogFor.value = null
    }
}
