package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.HttpRequestAction
import de.mm20.launcher2.searchactions.actions.OpenUrlAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.actions.SendSmsAction

data class KeywordShortcutBuilder(
    override val label: String,
    val keyword: String,
    val actionType: ActionType,
    val phoneNumber: String? = null,
    val messageBody: String? = null,
    val silentSms: Boolean = false,
    val url: String? = null,
    val httpMethod: String? = null,
    val httpBody: String? = null,
    val httpHeaders: Map<String, String>? = null,
    override val icon: SearchActionIcon = SearchActionIcon.Search,
    override val iconColor: Int = 0,
    override val customIcon: String? = null,
) : CustomizableSearchActionBuilder {

    enum class ActionType { Sms, OpenUrl, HttpRequest }

    override val key: String
        get() = "keyword://$keyword"

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (!classifiedQuery.text.trim().equals(keyword, ignoreCase = true)) return null
        return when (actionType) {
            ActionType.Sms -> SendSmsAction(
                label = label,
                phoneNumber = phoneNumber ?: return null,
                messageBody = messageBody ?: return null,
                silent = silentSms,
                icon = icon,
                iconColor = iconColor,
                customIcon = customIcon,
            )
            ActionType.OpenUrl -> OpenUrlAction(
                label = label,
                url = url ?: return null,
                icon = icon,
                iconColor = iconColor,
                customIcon = customIcon,
            )
            ActionType.HttpRequest -> HttpRequestAction(
                label = label,
                url = url ?: return null,
                method = httpMethod ?: "GET",
                body = httpBody,
                httpHeaders = httpHeaders ?: emptyMap(),
                icon = icon,
                iconColor = iconColor,
                customIcon = customIcon,
            )
        }
    }
}
