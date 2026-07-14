package com.uxellence.tv.v3.demolive

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loader kanału "TVP1 Retro" — nagrania realnej anteny pocięte na programy
 * wg ramówki EPG (paczka z tools/record_tvp1.sh: program_NN.mp4 + manifest.json)
 * wgrywane przez adb push do katalogu aplikacji:
 *   /sdcard/Android/data/<pkg>/files/tvp1rec/
 * (getExternalFilesDir — zero dodatkowych uprawnień, scoped-storage-safe).
 *
 * Zwraca gotową listę BarkerItem z url = file://… — DemoChannelPlayerController
 * rozpoznaje lokalny plik i pomija pobieranie. Brak paczki → null → kanał
 * po prostu nie powstaje (bez plansz błędów).
 */
object RecordedChannelLoader {

    private const val TAG = "RecordedChannel"

    @Serializable
    data class ManifestItem(
        val file: String,
        val title: String,
        val genre: String = "program TV",
        val description: String = "",
        val age: String = "12 lat",
        val durMs: Long,
    )

    @Serializable
    data class Manifest(
        val channelName: String = "TVP1 Retro",
        val channelNumber: Int = 130,
        val recordedAtWallMs: Long = 0L,
        val sourceChannel: String = "TVP 1",
        val items: List<ManifestItem> = emptyList(),
    )

    data class RecordedChannel(
        val id: String,
        val name: String,
        val number: Int,
        val items: List<BarkerSchedule.BarkerItem>,
        // Realny start nagrania (wall-clock) — oś anteny kanału; ramówka EPG
        // pokrywa się z godzinami faktycznej emisji
        val recordedAtWallMs: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Wszystkie kanały z nagrań. Rooty (aplikacyjne, bez uprawnień):
     * - getExternalFilesDirs(null) — pamięć wewnętrzna [0] + KARTA SD/USB [1+]
     *   (/storage/<UUID>/Android/data/<pkg>/files/…)
     * - filesDir — Android 11+ blokuje adb push do Android/data pamięci
     *   wewnętrznej; deploy: push → /data/local/tmp + `run-as <pkg> cp`
     * W każdym roocie paczką jest KAŻDY podkatalog z manifest.json
     * (np. tvp1rec/, pnewsrec/). Duplikaty katalogów (ta sama nazwa na kilku
     * rootach) — wygrywa pierwszy znaleziony.
     */
    fun loadAll(context: Context): List<RecordedChannel> {
        val roots =
            context.getExternalFilesDirs(null).filterNotNull() + context.filesDir
        val seen = mutableSetOf<String>()
        val channels = mutableListOf<RecordedChannel>()
        for (root in roots) {
            val subdirs = root.listFiles { f: File ->
                f.isDirectory && File(f, "manifest.json").exists()
            } ?: continue
            for (dir in subdirs) {
                if (!seen.add(dir.name)) continue
                loadDir(dir)?.let { channels += it }
            }
        }
        return channels.sortedBy { it.number }
    }

    private fun loadDir(dir: File): RecordedChannel? {
        val manifestFile = File(dir, "manifest.json")
        return try {
            val manifest = json.decodeFromString<Manifest>(manifestFile.readText())
            val items = manifest.items.mapNotNull { mi ->
                val f = File(dir, mi.file)
                if (!f.exists() || f.length() == 0L) {
                    Log.w(TAG, "Pominięty brakujący plik: ${mi.file}")
                    return@mapNotNull null
                }
                BarkerSchedule.BarkerItem(
                    url = "file://${f.absolutePath}",
                    title = mi.title,
                    genre = mi.genre,
                    year = "",                    // nagranie z anteny — rok nieistotny
                    country = "Polska",
                    age = mi.age,
                    description = mi.description,
                    coverUrl = null,              // okładka = klatka z materiału
                    nominalDurMs = mi.durMs,
                )
            }
            if (items.isEmpty()) {
                Log.w(TAG, "Manifest bez działających plików (${dir.name}) — kanał pominięty")
                null
            } else {
                Log.i(TAG, "Kanał '${manifest.channelName}' (#${manifest.channelNumber}): " +
                    "${items.size} programów z ${dir.absolutePath}")
                RecordedChannel(
                    dir.name, manifest.channelName, manifest.channelNumber, items,
                    // 0 w manifeście (stare paczki) → fallback na wspólną oś 9:00
                    recordedAtWallMs = manifest.recordedAtWallMs
                        .takeIf { it > 0 } ?: BarkerSchedule.barkerStartWallMs(),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd manifestu: ${e.message}")
            null
        }
    }
}
