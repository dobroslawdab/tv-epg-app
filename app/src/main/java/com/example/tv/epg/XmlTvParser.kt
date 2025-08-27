package com.example.tv.epg

import android.content.Context
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.net.URL
import java.net.HttpURLConnection
import java.io.IOException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object XmlTvParser {
    private val dtfStrict = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")

    fun parseGz(context: Context, assetPath: String): EpgGuide {
        val input = context.assets.open(assetPath)
        return parse(GZIPInputStream(input))
    }

    fun parseAssetXml(context: Context, assetPath: String): EpgGuide {
        context.assets.open(assetPath).use { return parse(it) }
    }

    suspend fun parseUrl(url: String): EpgGuide = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 6000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "gzip")
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code ${conn.responseMessage}")
            }
            conn.inputStream.use { parse(it) }
        } finally {
            conn.disconnect()
        }
    }

    // Szybkie wczytanie: tylko pierwsze N kanałów i programy, które lecą TERAZ
    suspend fun parseUrlNowForTopChannels(url: String, maxChannels: Int): EpgGuide = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "gzip")
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code ${conn.responseMessage}")
            }
            val isGzip = (conn.getHeaderField("Content-Encoding")?.contains("gzip", true) == true) || url.endsWith(".gz", true)
            val baseStream = conn.inputStream
            val stream = if (isGzip) GZIPInputStream(baseStream) else baseStream
            stream.use { input ->
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(input, null)

                val channels = mutableListOf<EpgChannel>()
                val programs = mutableListOf<EpgProgram>()
                val selectedIds = linkedSetOf<String>()
                val foundNowFor = mutableSetOf<String>()

                val now = Instant.now()
                var event = parser.eventType
                var currentTag: String? = null
                var channelId: String? = null
                var channelName: String? = null

                var progChannel: String? = null
                var title: String? = null
                var subTitle: String? = null
                var desc: String? = null
                var icon: String? = null
                val cats = mutableListOf<String>()
                var start: Instant? = null
                var stop: Instant? = null

                var channelsClosed = false

                loop@ while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            when (currentTag) {
                                "channel" -> if (!channelsClosed && selectedIds.size < maxChannels) {
                                    channelId = parser.getAttributeValue(null, "id")
                                    channelName = null
                                } else {
                                    // ignore additional channels
                                    channelId = null; channelName = null
                                }
                                "programme" -> {
                                    channelsClosed = true // programme sekcja po kanałach
                                    progChannel = parser.getAttributeValue(null, "channel")
                                    if (progChannel != null && selectedIds.contains(progChannel)) {
                                        start = parseTime(parser.getAttributeValue(null, "start"))
                                        stop = parseTime(parser.getAttributeValue(null, "stop"))
                                        title = null
                                    } else {
                                        // nie interesuje nas – zignoruj
                                        progChannel = null; title = null; start = null; stop = null
                                    }
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            when (currentTag) {
                                "display-name" -> if (!channelsClosed && channelId != null && selectedIds.size < maxChannels) {
                                    channelName = parser.text
                                }
                                "title" -> if (progChannel != null) title = parser.text
                                "sub-title" -> if (progChannel != null) subTitle = parser.text
                                "desc" -> if (progChannel != null) desc = parser.text
                                "category" -> if (progChannel != null) cats += parser.text
                                "icon" -> if (progChannel != null) {
                                    val src = parser.getAttributeValue(null, "src")
                                    if (!src.isNullOrBlank()) icon = src
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "channel" -> if (!channelsClosed && channelId != null && channelName != null && selectedIds.size < maxChannels) {
                                    channels += EpgChannel(channelId!!, channelName!!)
                                    selectedIds += channelId!!
                                    channelId = null; channelName = null
                                }
                                "programme" -> if (progChannel != null) {
                                    if (start != null && stop != null && title != null) {
                                        if (!now.isBefore(start) && now.isBefore(stop)) {
                                            programs += EpgProgram(progChannel!!, title!!, start!!, stop!!)
                                            foundNowFor += progChannel!!
                                            if (foundNowFor.size >= selectedIds.size && selectedIds.isNotEmpty()) {
                                                break@loop
                                            }
                                        }
                                    }
                                    progChannel = null; title = null; start = null; stop = null
                                }
                            }
                            currentTag = null
                        }
                    }
                    event = parser.next()
                }

                EpgGuide(channels, programs)
            }
        } finally {
            conn.disconnect()
        }
    }

    // Wczytaj tylko programy, które przecinają podane okno czasowe, dla pierwszych N kanałów
    suspend fun parseUrlWindowForTopChannels(
        url: String,
        maxChannels: Int,
        windowStart: Instant,
        windowEnd: Instant
    ): EpgGuide = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code ${conn.responseMessage}")
            }
            // Stream parsing without full buffering
            val base = BufferedInputStream(conn.inputStream)
            val gzip = conn.getHeaderField("Content-Encoding")?.contains("gzip", true) == true || url.endsWith(".gz", true)
            val stream: InputStream = if (gzip) GZIPInputStream(base) else base

            stream.use { input ->
                val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
                val parser = factory.newPullParser()
                try {
                    parser.setInput(InputStreamReader(input, StandardCharsets.UTF_8))
                } catch (e: Exception) {
                    throw IOException("Failed to parse XML content: ${e.message}", e)
                }

                val channels = mutableListOf<EpgChannel>()
                val programs = mutableListOf<EpgProgram>()
                val selectedIds = linkedSetOf<String>()

                var event = parser.eventType
                var currentTag: String? = null
                var channelId: String? = null
                var channelName: String? = null

                var progChannel: String? = null
                var title: String? = null
                var start: Instant? = null
                var stop: Instant? = null
                var subTitle: String? = null
                var desc: String? = null
                var icon: String? = null
                val cats = mutableListOf<String>()
                var withinWindow = false

                var channelsClosed = false

                while (event != XmlPullParser.END_DOCUMENT) {
                    try {
                        when (event) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            when (currentTag) {
                                "channel" -> if (!channelsClosed && selectedIds.size < maxChannels) {
                                    channelId = parser.getAttributeValue(null, "id")
                                    channelName = null
                                } else {
                                    channelId = null; channelName = null
                                }
                                "programme" -> {
                                    channelsClosed = true
                                    progChannel = parser.getAttributeValue(null, "channel")
                                    if (progChannel != null && selectedIds.contains(progChannel)) {
                                        start = parseTime(parser.getAttributeValue(null, "start"))
                                        stop = parseTime(parser.getAttributeValue(null, "stop"))
                                        withinWindow = (start != null && stop != null && stop!!.isAfter(windowStart) && start!!.isBefore(windowEnd))
                                        title = null; subTitle = null; desc = null; icon = null; cats.clear()
                                    } else {
                                        progChannel = null; title = null; start = null; stop = null
                                        subTitle = null; desc = null; icon = null; cats.clear(); withinWindow = false
                                    }
                                }
                                "icon" -> if (progChannel != null && withinWindow) {
                                    icon = parser.getAttributeValue(null, "src")
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            when (currentTag) {
                                "display-name" -> if (!channelsClosed && channelId != null && selectedIds.size < maxChannels) channelName = parser.text
                                "title" -> if (progChannel != null && withinWindow) title = parser.text
                                "sub-title" -> if (progChannel != null && withinWindow) subTitle = parser.text
                                "desc" -> if (progChannel != null && withinWindow) desc = parser.text
                                "category" -> if (progChannel != null && withinWindow) cats += parser.text
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "channel" -> if (!channelsClosed && channelId != null && channelName != null && selectedIds.size < maxChannels) {
                                    channels += EpgChannel(channelId!!, channelName!!)
                                    selectedIds += channelId!!
                                    channelId = null; channelName = null
                                }
                                "programme" -> if (progChannel != null) {
                                    val ch = progChannel
                                    val t = title
                                    val s = start
                                    val e = stop
                                    if (withinWindow && ch != null && t != null && s != null && e != null) {
                                        programs += EpgProgram(
                                            channelId = ch,
                                            title = t,
                                            startUtc = s,
                                            endUtc = e,
                                            subTitle = subTitle,
                                            description = desc,
                                            categories = cats.toList(),
                                            iconUrl = icon
                                        )
                                    }
                                    progChannel = null; title = null; start = null; stop = null; subTitle = null; desc = null; icon = null; cats.clear(); withinWindow = false
                                }
                            }
                            currentTag = null
                        }
                        }
                        event = parser.next()
                    } catch (e: Exception) {
                        // Skip problematic XML element and continue
                        try {
                            event = parser.next()
                        } catch (skipError: Exception) {
                            break // Can't continue parsing
                        }
                    }
                }

                EpgGuide(channels, programs)
            }
        } finally {
            conn.disconnect()
        }
    }

    fun parse(input: InputStream): EpgGuide {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        val channels = mutableListOf<EpgChannel>()
        val programs = mutableListOf<EpgProgram>()

        var event = parser.eventType
        var currentTag: String? = null
        var channelId: String? = null
        var channelName: String? = null
        var progChannel: String? = null
        var title: String? = null
        var start: Instant? = null
        var stop: Instant? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "channel" -> {
                            channelId = parser.getAttributeValue(null, "id")
                            channelName = null
                        }
                        "programme" -> {
                            progChannel = parser.getAttributeValue(null, "channel")
                            start = parseTime(parser.getAttributeValue(null, "start"))
                            stop = parseTime(parser.getAttributeValue(null, "stop"))
                            title = null
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    when (currentTag) {
                        "display-name" -> channelName = parser.text
                        "title" -> title = parser.text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            if (channelId != null && channelName != null) {
                                channels += EpgChannel(channelId!!, channelName!!)
                            }
                            channelId = null; channelName = null
                        }
                        "programme" -> {
                            if (progChannel != null && title != null && start != null && stop != null) {
                                programs += EpgProgram(progChannel!!, title!!, start!!, stop!!)
                            }
                            progChannel = null; title = null; start = null; stop = null
                        }
                    }
                    currentTag = null
                }
            }
            event = parser.next()
        }

        return EpgGuide(channels, programs)
    }

    private fun parseTime(raw: String?): Instant? {
        if (raw == null) return null
        // Akceptuj różne warianty z XMLTV: yyyyMMddHHmmss[ ]Z lub yyyyMMddHHmm[ ]Z
        // Spotykane błędne wartości (np. dd=="00") — normalizujemy do 01.
        val trimmed = raw.trim()
        // Wyodrębnij część cyfr i strefę
        val firstNonDigit = trimmed.indexOfFirst { !it.isDigit() }.let { if (it == -1) trimmed.length else it }
        var digits = trimmed.substring(0, firstNonDigit)
        val zoneStart = trimmed.indexOfAny(charArrayOf('+', '-'), startIndex = firstNonDigit).let { if (it == -1) firstNonDigit else it }
        var zone = if (zoneStart < trimmed.length) trimmed.substring(zoneStart).replace(" ", "") else "+0000"

        // Uzupełnij sekundy do 14 cyfr
        if (digits.length == 12) digits += "00" else if (digits.length == 13) digits += "0"
        if (digits.length < 14) return null
        digits = digits.substring(0, 14)

        // Napraw dzień "00" -> "01"
        val day = digits.substring(6, 8)
        if (day == "00") digits = digits.substring(0, 6) + "01" + digits.substring(8)

        // Złóż do formatu oczekiwanego przez formatter
        // Upewnij się, że strefa ma format +HHmm
        if (zone.length >= 5) zone = zone.substring(0, 5)
        val s = "$digits $zone"
        return try {
            dtfStrict.withZone(ZoneOffset.UTC).parse(s, Instant::from)
        } catch (_: Exception) {
            null
        }
    }
}


