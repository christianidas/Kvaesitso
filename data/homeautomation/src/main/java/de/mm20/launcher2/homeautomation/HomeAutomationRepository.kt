package de.mm20.launcher2.homeautomation

import kotlinx.coroutines.flow.Flow

interface HomeAutomationRepository {
    fun getStructures(): Flow<List<HomeStructure>>
    fun getDevices(): Flow<List<HomeDevice>>
    fun getScenes(): Flow<List<HomeScene>>
    suspend fun executeCommand(command: HomeCommand)
    suspend fun activateScene(sceneId: String)
    suspend fun refresh()
}
