package com.example.mp4downloader

import android.app.Application
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val client = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                private val cookieStore = mutableListOf<Cookie>()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    synchronized(cookieStore) {
                        cookies.forEach { newCookie ->
                            cookieStore.removeAll {
                                it.name == newCookie.name &&
                                    it.domain == newCookie.domain &&
                                    it.path == newCookie.path
                            }
                            cookieStore.add(newCookie)
                        }
                        cookieStore.removeAll { it.expiresAt < System.currentTimeMillis() }
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    synchronized(cookieStore) {
                        cookieStore.removeAll { it.expiresAt < System.currentTimeMillis() }
                        return cookieStore.filter { it.matches(url) }
                    }
                }
            })
            .build()

        val localization = Localization("en", "US")
        val contentCountry = ContentCountry("US")

        NewPipe.init(DownloaderImpl.init(client), localization, contentCountry)
    }
}
