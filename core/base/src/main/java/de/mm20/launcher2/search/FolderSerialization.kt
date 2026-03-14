package de.mm20.launcher2.search

import org.json.JSONObject

class FolderSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as Folder
        val json = JSONObject()
        json.put("id", searchable.id)
        json.put("name", searchable.name)
        return json.toString()
    }

    override val typePrefix: String
        get() = "folder"
}

class FolderDeserializer : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable {
        val json = JSONObject(serialized)
        return Folder(
            id = json.getString("id"),
            name = json.getString("name"),
        )
    }
}
