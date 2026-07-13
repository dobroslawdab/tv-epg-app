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
    private const val DIR_NAME = "tvp1rec"

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
        val recordedAtWallMs: Long = 0L,
        val sourceChannel: String = "TVP 1",
        val items: List<ManifestItem> = emptyList(),
    )

    data class RecordedChannel(
        val name: String,
        val items: List<BarkerSchedule.BarkerItem>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): RecordedChannel? {
        // Dwie lokalizacje paczki:
        // - getExternalFilesDir/tvp1rec — adb push wprost (emulator / starsze API)
        // - filesDir/tvp1rec — Android 11+ blokuje push do Android/data; deploy:
        //   adb push → /data/local/tmp, potem `run-as <pkg> cp` (debug build)
        val candidates = listOf(
            File(context.getExternalFilesDir(null), DIR_NAME),
            File(context.filesDir, DIR_NAME),
        )
        val dir = candidates.firstOrNull { File(it, "manifest.json").exists() }
        if (dir == null) {
            Log.i(TAG, "Brak paczki nagrania (${candidates.joinToString()}) — kanał pominięty")
            return null
        }
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
                Log.w(TAG, "Manifest bez działających plików — kanał pominięty")
                null
            } else {
                Log.i(TAG, "Kanał '${manifest.channelName}': ${items.size} programów z nagrania")
                RecordedChannel(manifest.channelName, items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd manifestu: ${e.message}")
            null
        }
    }
}
