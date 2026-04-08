package de.mm20.launcher2.homeautomation

import android.content.Intent
import kotlinx.coroutines.flow.Flow

interface HomeAutomationRepository {
    fun getStructures(): Flow<List<HomeStructure>>
    fun getDevices(): Flow<List<HomeDevice>>
    fun getScenes(): Flow<List<HomeScene>>
    suspend fun executeCommand(command: HomeCommand)
    suspend fun activateScene(sceneId: String)

    /**
     * Refresh device/structure data from the API.
     * Returns a consent [Intent] if the user needs to grant Home scopes, or null on success.
     */
    suspend fun refresh(): Intent?
}
