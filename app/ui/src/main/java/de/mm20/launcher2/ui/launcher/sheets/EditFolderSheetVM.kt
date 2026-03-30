package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Folder
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.services.folders.FoldersService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditFolderSheetVM : ViewModel(), KoinComponent {

    private val foldersService: FoldersService by inject()
    private val iconService: IconService by inject()
    private val appRepository: AppRepository by inject()
    private val searchableRepository: SavableSearchableRepository by inject()

    private var folder by mutableStateOf<Folder?>(null)
    var folderName by mutableStateOf("")
    var folderCustomIcon = MutableStateFlow<CustomIcon?>(null)
    var folderIcon = emptyFlow<LauncherIcon?>()

    var loading by mutableStateOf(true)

    var page by mutableStateOf(EditFolderSheetPage.CreateFolder)
    var wasOnLastPage by mutableStateOf(false)

    var folderItems by mutableStateOf(emptyList<SavableSearchable>())
    var selectableApps by mutableStateOf(emptyList<FolderSelectableItem>())
    var selectableOther by mutableStateOf(emptyList<FolderSelectableItem>())

    fun init(folderId: String?, iconSize: Int) {
        loading = true
        viewModelScope.launch(Dispatchers.Default) {
            if (folderId != null) {
                val folderKey = "${Folder.Domain}://$folderId"
                val searchables = searchableRepository.getByKeys(listOf(folderKey)).first()
                val existingFolder = searchables.filterIsInstance<Folder>().firstOrNull()
                    ?: Folder(id = folderId, name = "")
                folder = existingFolder
                folderName = existingFolder.name

                val items = foldersService.getFolderItems(folderId).first()
                val customIcon = iconService.getCustomIcon(existingFolder).first()
                folderCustomIcon.value = customIcon
                folderIcon = folderCustomIcon.map {
                    iconService.resolveCustomIcon(existingFolder, iconSize, it).first()
                }
                folderItems = items
            }

            val isCreating = folderId == null
            page = if (isCreating) EditFolderSheetPage.CreateFolder else EditFolderSheetPage.CustomizeFolder
            wasOnLastPage = page == EditFolderSheetPage.CustomizeFolder

            val apps = appRepository.findMany().first { it.isNotEmpty() }.sorted()
            selectableApps = apps.map { app ->
                FolderSelectableItem(app, folderItems.any { app.key == it.key })
            }
            selectableOther = folderItems.mapNotNull { item ->
                if (apps.any { item.key == it.key }) null
                else FolderSelectableItem(item, true)
            }.sortedBy { it.item }

            loading = false
        }
    }

    fun save() {
        val existingFolder = folder
        if (folderName.isBlank()) return

        if (folderItems.isEmpty() && existingFolder != null) {
            foldersService.deleteFolder(existingFolder)
        } else if (existingFolder != null) {
            foldersService.updateFolder(existingFolder, newName = folderName, items = folderItems)
        } else {
            val newFolder = foldersService.createFolder(folderName, folderItems)
            folder = newFolder
        }

        val savedFolder = folder ?: return
        val icon = folderCustomIcon.value
        if (icon != null) {
            iconService.setCustomIcon(savedFolder, icon)
        } else {
            iconService.setCustomIcon(savedFolder, null)
        }
    }

    fun onClickContinue() {
        page = when (page) {
            EditFolderSheetPage.CreateFolder -> EditFolderSheetPage.PickItems
            else -> EditFolderSheetPage.CustomizeFolder
        }
        if (page == EditFolderSheetPage.CustomizeFolder) {
            wasOnLastPage = true
        }
    }

    fun getIcon(item: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(item, size)
    }

    fun openItemPicker() {
        page = EditFolderSheetPage.PickItems
    }

    fun openIconPicker() {
        page = EditFolderSheetPage.PickIcon
    }

    fun closeIconPicker() {
        page = EditFolderSheetPage.CustomizeFolder
    }

    fun selectIcon(icon: CustomIcon?) {
        folderCustomIcon.value = icon
        closeIconPicker()
    }

    fun closeItemPicker() {
        page = EditFolderSheetPage.CustomizeFolder
    }

    fun selectItem(item: SavableSearchable) {
        folderItems = folderItems + item
        updateSelectableLists()
    }

    fun deselectItem(item: SavableSearchable) {
        folderItems = folderItems.filter { it.key != item.key }
        updateSelectableLists()
    }

    private fun updateSelectableLists() {
        selectableApps = selectableApps.map { app ->
            app.copy(isSelected = folderItems.any { it.key == app.item.key })
        }
        selectableOther = selectableOther.map { oth ->
            oth.copy(isSelected = folderItems.any { it.key == oth.item.key })
        }
    }
}

enum class EditFolderSheetPage {
    CreateFolder,
    PickItems,
    CustomizeFolder,
    PickIcon,
}

@Stable
data class FolderSelectableItem(val item: SavableSearchable, val isSelected: Boolean)
