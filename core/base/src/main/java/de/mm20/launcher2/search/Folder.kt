package de.mm20.launcher2.search

import android.content.Context
import android.os.Bundle
import de.mm20.launcher2.base.R
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.VectorLayer

data class Folder(
    val id: String,
    val name: String,
    override val labelOverride: String? = null
) : SavableSearchable {

    override val domain: String = Domain

    override val key: String = "$domain://$id"
    override val label: String = name

    override val preferDetailsOverLaunch: Boolean = true

    override fun launch(context: Context, options: Bundle?): Boolean {
        return false
    }

    override fun overrideLabel(label: String): SavableSearchable {
        return this.copy(labelOverride = label)
    }

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        return StaticLauncherIcon(
            foregroundLayer = VectorLayer(R.drawable.folder_24px),
            backgroundLayer = ColorLayer()
        )
    }

    override fun getSerializer(): SearchableSerializer {
        return FolderSerializer()
    }

    companion object {
        const val Domain = "folder"
    }
}
