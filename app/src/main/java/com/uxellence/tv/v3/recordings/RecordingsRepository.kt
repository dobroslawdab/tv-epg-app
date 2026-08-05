package com.uxellence.tv.v3.recordings

import android.content.Context
import android.util.Log
import com.uxellence.tv.v3.version001.VodContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * STAŁA lista nagrań makiety (`assets/nagrania.json`).
 *
 * Wcześniej wiersze "Pojedyncze nagrania" / "SERIE" / "ZAPLANOWANE" brały
 * `vodContentList.shuffled().take(10)` — skład i kolejność zmieniały się przy
 * każdym starcie procesu. Na badaniach to problem: każdy uczestnik widział inne
 * nagrania, a screenshoty z sesji nie zgadzały się między sobą.
 *
 * Teraz lista jest zapisana w assecie: ten sam zestaw, ta sama kolejność, na
 * każdym urządzeniu i po każdym restarcie. Żeby zmienić nagrania — edytuj
 * `nagrania.json` (albo wygeneruj go ponownie skryptem z docs).
 *
 * Metadane nagrania (kanał, data, długość) trafiają do `VodContent.description`
 * jako prefiks "TVP1 HD • 3.08, 20:15 • 90 min", bo VodContent nie ma osobnych
 * pól na te dane, a kafle i tak renderują opis.
 */
object RecordingsRepository {
    private const val TAG = "RecordingsRepository"
    private const val ASSET = "nagrania.json"

    @Serializable
    data class Recording(
        @SerialName("tytul") val title: String,
        @SerialName("kategoria") val category: String = "",
        @SerialName("opis") val description: String = "",
        @SerialName("logo_kanalu") val channelLogo: String = "",
        @SerialName("miniaturka_programu") val thumbnail: String = "",
        @SerialName("kanal") val channel: String = "",
        @SerialName("nagrane_dnia") val recordedAt: String = "",
        @SerialName("dlugosc_min") val durationMin: Int = 0,
        @SerialName("typ") val type: String = ""
    )

    @Serializable
    private data class RecordingsFile(
        val pojedyncze: List<Recording> = emptyList(),
        val serie: List<Recording> = emptyList(),
        val zaplanowane: List<Recording> = emptyList()
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Volatile
    private var cache: RecordingsFile? = null

    private fun load(context: Context): RecordingsFile {
        cache?.let { return it }
        return try {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val parsed = json.decodeFromString<RecordingsFile>(raw)
            Log.i(
                TAG,
                "Wczytano nagrania: pojedyncze=${parsed.pojedyncze.size}, " +
                    "serie=${parsed.serie.size}, zaplanowane=${parsed.zaplanowane.size}"
            )
            cache = parsed
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się wczytać $ASSET: ${e.message}", e)
            RecordingsFile()
        }
    }

    /** "2026-08-03T20:15" → "3.08, 20:15" (bez zależności na java.time API desugaring). */
    private fun formatWhen(iso: String): String = try {
        val (date, time) = iso.split("T")
        val (_, month, day) = date.split("-")
        "${day.trimStart('0')}.$month, $time"
    } catch (e: Exception) {
        iso
    }

    private fun Recording.toVodContent(idPrefix: String, index: Int): VodContent {
        val meta = listOfNotNull(
            channel.takeIf { it.isNotBlank() },
            recordedAt.takeIf { it.isNotBlank() }?.let { formatWhen(it) },
            durationMin.takeIf { it > 0 }?.let { "$it min" }
        ).joinToString(" • ")
        return VodContent(
            id = "${idPrefix}_${index}_${title.hashCode()}",
            title = title,
            description = if (meta.isBlank()) description else "$meta\n$description",
            category = category,
            imageUrl = thumbnail,
            channelLogoUrl = channelLogo,
            link = "",
            price = null
        )
    }

    /** Nagrania pojedyncze — stała lista. */
    fun single(context: Context): List<VodContent> =
        load(context).pojedyncze.mapIndexed { i, r -> r.toVodContent("rec_single", i) }

    /** Nagrania seryjne — stała lista. */
    fun series(context: Context): List<VodContent> =
        load(context).serie.mapIndexed { i, r -> r.toVodContent("rec_series", i) }

    /** Zaplanowane (mocki) — stała lista; zlecenia użytkownika doklejane osobno. */
    fun scheduled(context: Context): List<VodContent> =
        load(context).zaplanowane.mapIndexed { i, r -> r.toVodContent("rec_sched", i) }

    /** Wszystkie nagrania (pojedyncze + serie) — dla zbiorczego wiersza "Nagrania". */
    fun all(context: Context): List<VodContent> = single(context) + series(context)
}
