package app.videogir

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit
import okhttp3.Request as OkRequest

object Http {
    const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/** پیاده‌سازی Downloader که NewPipeExtractor برای درخواست‌های شبکه لازم دارد. */
class DownloaderImpl : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()

        val body = when {
            data != null -> data.toRequestBody(null, 0, data.size)
            method.equals("POST", ignoreCase = true) -> ByteArray(0).toRequestBody()
            else -> null
        }

        val builder = OkRequest.Builder()
            .method(method, body)
            .url(url)
            .header("User-Agent", Http.DEFAULT_UA)

        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            for (v in values) builder.addHeader(name, v)
        }

        return Http.client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", url)
            val text = resp.body?.string()
            Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                text,
                resp.request.url.toString()
            )
        }
    }
}
