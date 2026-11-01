package app.videogir

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import okhttp3.Request as OkRequest

/**
 * دانلود فایل. اگر یوتیوب با ۴۰۳ جواب بدهد، به‌ترتیب چند روش درخواست را امتحان می‌کند:
 * ۱) تکه‌تکه با پارامتر range در لینک
 * ۲) تکه‌تکه با هدر Range
 * ۳) یک درخواست کامل بدون range
 */
object Fetcher {
    private const val CHUNK = 8L * 1024 * 1024

    private enum class Mode { PARAM, HEADER, WHOLE }

    fun uaFor(url: String): String = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
        YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
        else -> Http.DEFAULT_UA
    }

    fun clientOf(url: String): String = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(url) -> "android"
        YoutubeParsingHelper.isIosStreamingUrl(url) -> "ios"
        YoutubeParsingHelper.isWebEmbeddedPlayerStreamingUrl(url) -> "web-embedded"
        YoutubeParsingHelper.isWebStreamingUrl(url) -> "web"
        else -> "other"
    }

    /** طول فایل از پارامتر clen داخل لینک؛ اگر نبود -1. */
    fun clen(url: String): Long =
        Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: -1L

    fun length(url: String): Long {
        val c = clen(url)
        if (c > 0) return c
        return try {
            val req = OkRequest.Builder().url(url).head().header("User-Agent", uaFor(url)).build()
            Http.client.newCall(req).execute().use { r ->
                if (r.isSuccessful) r.header("Content-Length")?.toLongOrNull() ?: -1L else -1L
            }
        } catch (e: IOException) {
            -1L
        }
    }

    private fun buildRequest(url: String, ua: String, range: String?): OkRequest {
        val b = OkRequest.Builder().url(url).header("User-Agent", ua)
        if (range != null) b.header("Range", "bytes=$range")
        val c = clientOf(url)
        if (c != "android" && c != "ios") {
            b.header("Referer", "https://www.youtube.com/")
            b.header("Origin", "https://www.youtube.com")
        }
        return b.build()
    }

    private fun is403(e: IOException) = (e.message ?: "").startsWith("HTTP 403")

    /** onBytes همیشه «مجموع بایت‌های دانلودشده تا الان» را می‌گیرد. */
    suspend fun download(url: String, dest: File, onBytes: (Long) -> Unit) {
        val ua = uaFor(url)
        val total = length(url)
        val modes = if (total > 0) listOf(Mode.PARAM, Mode.HEADER, Mode.WHOLE) else listOf(Mode.WHOLE)
        for (m in modes) {
            try {
                downloadWith(m, url, ua, total, dest, onBytes)
                return
            } catch (e: IOException) {
                if (!is403(e)) throw e
            }
        }
        throw IOException("HTTP 403 [${clientOf(url)}]")
    }

    private suspend fun downloadWith(
        mode: Mode, url: String, ua: String, total: Long, dest: File, onBytes: (Long) -> Unit
    ) {
        dest.delete()
        onBytes(0L)
        FileOutputStream(dest).use { out ->
            if (mode == Mode.WHOLE) {
                fetch(url, ua, null, out, 0L, onBytes)
                return
            }
            var pos = 0L
            while (pos < total) {
                val end = minOf(pos + CHUNK, total) - 1
                val sep = if ('?' in url) "&" else "?"
                val target = if (mode == Mode.PARAM) "$url${sep}range=$pos-$end" else url
                val header = if (mode == Mode.HEADER) "$pos-$end" else null
                var attempt = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    try {
                        val n = fetch(target, ua, header, out, pos, onBytes)
                        if (n != end - pos + 1) throw IOException("short chunk")
                        pos += n
                        break
                    } catch (e: IOException) {
                        if (is403(e)) throw e
                        attempt++
                        if (attempt >= 4) throw e
                        out.channel.truncate(pos)
                        out.channel.position(pos)
                        onBytes(pos)
                        delay(800L * attempt)
                    }
                }
            }
        }
    }

    private suspend fun fetch(
        url: String, ua: String, range: String?, out: FileOutputStream,
        base: Long, onBytes: (Long) -> Unit
    ): Long {
        val req = buildRequest(url, ua, range)
        return Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            val buf = ByteArray(64 * 1024)
            var n = 0L
            body.byteStream().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    n += r
                    onBytes(base + n)
                }
            }
            n
        }
    }
}
