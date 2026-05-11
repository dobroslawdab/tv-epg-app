package com.uxellence.tv.v3.pakiety

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PaketChannel(
    val name: String = "",
    val logo: String = "",
    val channelUrl: String = "",
    val productId: String = ""
)

@Serializable
data class PaketCollection(
    val name: String = "",
    val url: String? = null
)

@Serializable
data class PaketDom(
    val name: String,
    val url: String = "",
    val categoryType: String? = null,
    val logo: String = "",
    val logoHd: String = "",
    val image: String = "",
    val imageHd: String = "",
    val description: String = "",
    val pointsPerCycle: String = "",
    val active: Boolean = false,
    val includedInSubscription: Boolean = false,
    val channelsCount: Int = 0,
    val channels: List<PaketChannel> = emptyList(),
    val collectionsCount: Int = 0,
    val collections: List<PaketCollection> = emptyList()
)

@Serializable
private data class PaketyDomFile(
    val source: String = "",
    val title: String = "",
    val count: Int = 0,
    val items: List<PaketDom> = emptyList()
)

object PaketRepository {
    private val streamingKeywords = listOf(
        "MAX ", "MAX Pod", "SkyShowtime", "Viaplay",
        "Netflix", "Amazon", "Wideoteka", "Prime"
    )

    fun loadAll(context: Context): List<PaketDom> = try {
        val raw = context.assets.open("pakiety_dom.json")
            .bufferedReader().use { it.readText() }
        // coerceInputValues=true → null in JSON for non-null String fields falls back
        // to the default ("") instead of throwing. JSON has image/imageHd=null on a
        // few packages (e.g. items[9]).
        val parser = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        val parsed = parser.decodeFromString<PaketyDomFile>(raw).items
        android.util.Log.d("PaketRepository", "Loaded ${parsed.size} packages")
        parsed
    } catch (e: Exception) {
        android.util.Log.e("PaketRepository", "Failed to load pakiety_dom.json", e)
        emptyList()
    }

    fun isStreaming(p: PaketDom): Boolean =
        streamingKeywords.any { p.name.contains(it, ignoreCase = true) } ||
            p.collections.any { c ->
                streamingKeywords.any { c.name.contains(it, ignoreCase = true) }
            }

    fun split(items: List<PaketDom>): Pair<List<PaketDom>, List<PaketDom>> {
        val streaming = items.filter { isStreaming(it) }
        val tv = items.filterNot { isStreaming(it) }
        return tv to streaming
    }
}
