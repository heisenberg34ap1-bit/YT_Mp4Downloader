package com.example.mp4downloader

import android.content.Context
import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Protocol
import okhttp3.Request
import java.io.IOException
import javax.net.ssl.SSLException

object VideoFileDownloader {
    @Throws(IOException::class)
    fun download(context: Context, url: String, destinationUri: Uri) {
        val baseClient = DownloaderImpl.getInstance()?.client()
            ?: throw IOException("HTTP client is not initialized")
        val client = baseClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .build()

        context.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
            try {
                executeDownload(client, url, output, closeConnection = false)
            } catch (e: SSLException) {
                executeDownload(client, url, output, closeConnection = true)
            } catch (e: IOException) {
                if (e.message?.contains("ssl=", ignoreCase = true) == true) {
                    executeDownload(client, url, output, closeConnection = true)
                } else {
                    throw e
                }
            }
        } ?: throw IOException("Could not open destination for writing")
    }

    @Throws(IOException::class)
    private fun executeDownload(
        client: okhttp3.OkHttpClient,
        url: String,
        output: java.io.OutputStream,
        closeConnection: Boolean
    ) {
        val parsedUrl = url.toHttpUrlOrNull()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", DownloaderImpl.defaultUserAgent())
            .header("Accept-Language", "en-US,en;q=0.9")

        if (closeConnection) {
            requestBuilder.header("Connection", "close")
        }

        if (parsedUrl?.host?.contains("googlevideo.com", ignoreCase = true) == true) {
            requestBuilder.header("Referer", "https://www.youtube.com/")
            requestBuilder.header("Origin", "https://www.youtube.com")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty response body")
            body.byteStream().use { input ->
                input.copyTo(output)
                output.flush()
            }
        }
    }
}
