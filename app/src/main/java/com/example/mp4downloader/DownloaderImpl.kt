package com.example.mp4downloader

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

class DownloaderImpl(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class, InterruptedException::class)
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val url = request.url()
        val headers = request.headers()
        val method = request.httpMethod()
        val dataToSend = request.dataToSend()

        val requestBuilder = Request.Builder()
            .url(url)
            .method(method, dataToSend?.toRequestBody())

        headers.forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            requestBuilder.header("User-Agent", DEFAULT_USER_AGENT)
        }

        if (headers.keys.none { it.equals("Accept-Language", ignoreCase = true) }) {
            requestBuilder.header("Accept-Language", "en-US,en;q=0.9")
        }

        val okRequest = requestBuilder.build()

        try {
            client.newCall(okRequest).execute().use { okResponse ->
                val responseBody = okResponse.body?.string()
                val responseHeaders = okResponse.headers.toMultimap()

                return Response(
                    okResponse.code,
                    okResponse.message,
                    responseHeaders,
                    responseBody,
                    okResponse.request.url.toString()
                )
            }
        } catch (e: Exception) {
            Log.e("Downloader", "Extraction failed for: $url", e)
            throw e
        }
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

        @Volatile
        private var instance: DownloaderImpl? = null

        @Synchronized
        fun init(client: OkHttpClient): DownloaderImpl {
            val current = instance
            if (current != null) return current
            return DownloaderImpl(client).also { instance = it }
        }

        fun getInstance(): DownloaderImpl? = instance

        fun defaultUserAgent(): String = DEFAULT_USER_AGENT
    }

    fun client(): OkHttpClient = client
}
