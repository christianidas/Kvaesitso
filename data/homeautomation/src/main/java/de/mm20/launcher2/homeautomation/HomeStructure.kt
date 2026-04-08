package de.mm20.launcher2.homeautomation

data class HomeStructure(
    val id: String,
    val name: String,
    val rooms: List<HomeRoom>,
)

data class HomeRoom(
    val id: String,
    val name: String,
)
