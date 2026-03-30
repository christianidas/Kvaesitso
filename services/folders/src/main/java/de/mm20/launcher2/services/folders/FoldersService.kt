package de.mm20.launcher2.services.folders

import de.mm20.launcher2.search.Folder
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface FoldersService {
    fun createFolder(name: String, items: List<SavableSearchable>): Folder
    fun deleteFolder(folder: Folder)
    fun updateFolder(folder: Folder, newName: String? = null, items: List<SavableSearchable>? = null)
    fun getFolderItems(folderId: String): Flow<List<SavableSearchable>>
    fun addItemToFolder(item: SavableSearchable, folderId: String)
    fun removeItemFromFolder(item: SavableSearchable, folderId: String)
}
