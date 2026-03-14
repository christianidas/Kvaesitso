package de.mm20.launcher2.ui.launcher.search.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Folder
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.folders.FoldersService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FolderItemVM : ViewModel(), KoinComponent {

    private val foldersService: FoldersService by inject()
    private val iconService: IconService by inject()

    private val folderId = MutableStateFlow<String?>(null)

    val folderItems = folderId.flatMapLatest { id ->
        if (id != null) foldersService.getFolderItems(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun init(folder: Folder, iconSize: Int) {
        folderId.value = folder.id
    }

    fun getIcon(item: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(item, size)
    }
}
