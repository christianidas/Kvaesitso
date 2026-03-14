package de.mm20.launcher2.services.folders.impl

import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.search.Folder
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.services.folders.FoldersService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

internal class FoldersServiceImpl(
    private val customAttributesRepository: CustomAttributesRepository,
    private val searchableRepository: SavableSearchableRepository,
) : FoldersService {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun createFolder(name: String, items: List<SavableSearchable>): Folder {
        val folder = Folder(
            id = UUID.randomUUID().toString(),
            name = name,
        )
        scope.launch {
            searchableRepository.upsert(folder, pinned = true)
            customAttributesRepository.setItemsForFolder(folder.id, items)
        }
        return folder
    }

    override fun deleteFolder(folder: Folder) {
        searchableRepository.delete(folder)
        customAttributesRepository.deleteFolder(folder.id)
    }

    override fun updateFolder(folder: Folder, newName: String?, items: List<SavableSearchable>?) {
        scope.launch {
            if (items != null) {
                customAttributesRepository.setItemsForFolder(folder.id, items).join()
            }
            if (newName != null && newName != folder.name) {
                val updatedFolder = folder.copy(name = newName)
                searchableRepository.replace(folder.key, updatedFolder)
            }
        }
    }

    override fun getFolderItems(folderId: String): Flow<List<SavableSearchable>> {
        return customAttributesRepository.getItemsForFolder(folderId)
    }

    override fun addItemToFolder(item: SavableSearchable, folderId: String) {
        customAttributesRepository.addItemToFolder(item, folderId)
    }

    override fun removeItemFromFolder(item: SavableSearchable, folderId: String) {
        customAttributesRepository.removeItemFromFolder(item, folderId)
    }
}
