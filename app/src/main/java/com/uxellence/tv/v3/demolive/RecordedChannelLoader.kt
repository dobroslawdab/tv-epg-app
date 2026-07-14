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

    // Opisy epg.ovh mają prefiks metadanych przed pustą linią:
    // "G: serial obyczajowy. S7E81. R: 2013. W: 12. \n \nWłaściwy opis…"
    // Do UI idzie tylko czysty opis; rok (R: RRRR) trafia do metadanych.
    private val yearRegex = Regex("""R:\s*(\d{4})""")

    private fun splitEpgDescription(raw: String): Pair<String, String> {
        val parts = raw.split(Regex("\n\\s*\n"), limit = 2)
        val clean = (if (parts.size == 2) parts[1] else raw).trim()
        val year = if (parts.size == 2) {
            yearRegex.find(parts[0])?.groupValues?.get(1)?.let { "$it r." } ?: ""
        } else ""
        return clean to year
    }

    // Jednorazowe generowanie okładek programów w tle (jeden wątek — duże pliki
    // z karty SD; klatka z 40% materiału, JPEG 320x180). Gotowe okładki są
    // trwałe — kolejne uruchomienia używają ich bez generowania.
    private val coverExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun scheduleCoverGeneration(dir: File, manifest: Manifest) {
        val coversDir = File(dir, "covers")
        val missing = manifest.items.filter { mi ->
            val c = File(coversDir, "${mi.file.removeSuffix(".mp4")}.jpg")
            (!c.exists() || c.length() == 0L) && File(dir, mi.file).exists()
        }
        if (missing.isEmpty()) return
        coverExecutor.execute {
            coversDir.mkdirs()
            var done = 0
            for (mi in missing) {
                val out = File(coversDir, "${mi.file.removeSuffix(".mp4")}.jpg")
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(File(dir, mi.file).absolutePath)
                    val atUs = (mi.durMs * 2 / 5) * 1000L   // 40% materiału
                    val frame = retriever.getFrameAtTime(
                        atUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: continue
                    val scaled = android.graphics.Bitmap.createScaledBitmap(frame, 320, 180, true)
                    java.io.FileOutputStream(out).use { fos ->
                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, fos)
                    }
                    scaled.recycle()
                    if (scaled !== frame) frame.recycle()
                    done++
                } catch (e: Exception) {
                    Log.w(TAG, "Okładka ${mi.file}: ${e.message}")
                    out.delete()
                } finally {
                    runCatching { retriever.release() }
                }
            }
            Log.i(TAG, "Wygenerowano $done/${missing.size} okładek → ${coversDir.absolutePath}")
        }
    }

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
                val (cleanDesc, year) = splitEpgDescription(mi.description)
                // Trwała okładka programu (covers/<plik>.jpg): wygenerowana RAZ
                // z klatki ~40% materiału (początek to często reklamy) — EPG ma
                // miniaturki natychmiast, bez czekania na ekstrakcję klatek
                val cover = File(File(dir, "covers"), "${mi.file.removeSuffix(".mp4")}.jpg")
                    .takeIf { it.exists() && it.length() > 0L }
                BarkerSchedule.BarkerItem(
                    url = "file://${f.absolutePath}",
                    title = mi.title,
                    genre = mi.genre,
                    year = year,                  // z prefiksu opisu epg.ovh (R: RRRR)
                    country = "Polska",
                    age = mi.age,
                    description = cleanDesc,      // bez prefiksu "G: … W: …"
                    coverUrl = cover?.let { "file://${it.absolutePath}" },
                    nominalDurMs = mi.durMs,
                )
            }
            scheduleCoverGeneration(dir, manifest)
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
