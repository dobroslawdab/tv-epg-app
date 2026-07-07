package com.uxellence.tv.v3.demolive

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * DEMO RECORDING SCHEDULER — zlecone nagrania (flow Figma "Nagrywanie serii").
 *
 * Compose-observable singleton (jak WatchlistManager/RentalManager):
 * każdy Composable czytający [recordings].value dostaje recompose po zmianie.
 * Persystencja w SharedPreferences (JSON) — zlecenia przeżywają restart.
 *
 * Zlecone nagrania trafiają do sekcji MOJE → Nagrania (Zaplanowane) przez
 * merge w RecordingRepository, a mini-EPG pokazuje czerwoną kropkę nagrywania
 * TYLKO dla programów obecnych w tym schedulerze.
 */
object DemoRecordingScheduler {

    data class ScheduledRecording(
        val title: String,
        val subTitle: String,        // np. "sezon 1, odcinek 235" / gatunek
        val channelId: String,
        val channelName: String,
        val startUtcMs: Long,
        val endUtcMs: Long,
        val imageUrl: String?,
        val isSeries: Boolean,       // Nagraj serię vs Nagraj odcinek
        val keepLabel: String        // "3 miesiące" itd. (Zachowaj na:)
    )

    private const val PREFS = "demo_recording_prefs"
    private const val KEY = "scheduled_json"

    val recordings = mutableStateOf<Map<String, ScheduledRecording>>(emptyMap())

    private fun keyOf(title: String, startUtcMs: Long) = "$title|$startUtcMs"

    fun init(context: Context) {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        if (json == null) {
            android.util.Log.i("DemoRec", "init: brak zapisanych zleceń")
            return
        }
        runCatching {
            val arr = JSONArray(json)
            val map = mutableMapOf<String, ScheduledRecording>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val rec = ScheduledRecording(
                    title = o.getString("title"),
                    subTitle = o.optString("subTitle"),
                    channelId = o.optString("channelId"),
                    channelName = o.optString("channelName"),
                    startUtcMs = o.getLong("startUtcMs"),
                    endUtcMs = o.getLong("endUtcMs"),
                    imageUrl = o.optString("imageUrl").ifBlank { null },
                    isSeries = o.optBoolean("isSeries"),
                    keepLabel = o.optString("keepLabel", "3 miesiące")
                )
                map[keyOf(rec.title, rec.startUtcMs)] = rec
            }
            recordings.value = map
            android.util.Log.i("DemoRec", "init: wczytano ${map.size} zleceń")
        }.onFailure {
            android.util.Log.e("DemoRec", "init: błąd parsowania zleceń", it)
        }
    }

    fun schedule(context: Context, rec: ScheduledRecording) {
        recordings.value = recordings.value + (keyOf(rec.title, rec.startUtcMs) to rec)
        persist(context)
    }

    fun cancel(context: Context, title: String, startUtcMs: Long) {
        recordings.value = recordings.value - keyOf(title, startUtcMs)
        persist(context)
    }

    fun isScheduled(title: String, startUtcMs: Long): Boolean =
        recordings.value.containsKey(keyOf(title, startUtcMs))

    fun clearAll(context: Context) {
        recordings.value = emptyMap()
        persist(context)
    }

    private fun persist(context: Context) {
        val arr = JSONArray()
        recordings.value.values.forEach { r ->
            arr.put(JSONObject().apply {
                put("title", r.title)
                put("subTitle", r.subTitle)
                put("channelId", r.channelId)
                put("channelName", r.channelName)
                put("startUtcMs", r.startUtcMs)
                put("endUtcMs", r.endUtcMs)
                put("imageUrl", r.imageUrl ?: "")
                put("isSeries", r.isSeries)
                put("keepLabel", r.keepLabel)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
