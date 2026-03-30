package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.widget.Toast
import de.mm20.launcher2.crashreporter.CrashReporter
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HttpRequestAction(
    override val label: String,
    val url: String,
    val method: String = "GET",
    val body: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
    override val icon: SearchActionIcon = SearchActionIcon.Website,
    override val iconColor: Int = 0,
    override val customIcon: String? = null,
) : SearchAction {

    override fun start(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HttpClient()
                client.request(url) {
                    this.method = HttpMethod.parse(this@HttpRequestAction.method)
                    if (body != null) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                    headers {
                        httpHeaders.forEach { (key, value) ->
                            append(key, value)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "$label sent", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                CrashReporter.logException(e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "$label failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
