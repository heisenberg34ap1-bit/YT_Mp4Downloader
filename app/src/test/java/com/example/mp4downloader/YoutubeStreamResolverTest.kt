package com.example.mp4downloader

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo

class YoutubeStreamResolverTest {

    @Test
    fun specificYoutubeWatchUrlResolvesToDownloadableStream() {
        val streamInfo = StreamInfo.getInfo(
            ServiceList.YouTube,
            "https://www.youtube.com/watch?v=DPiUuc8VyjU"
        )

        val resolved = YoutubeStreamResolver.resolve(streamInfo, "fallback_name")

        assertNotNull(resolved)
        assertTrue(resolved!!.url.startsWith("http"))
        assertTrue(resolved.fileName.substringAfterLast('.').isNotBlank())
        assertTrue(resolved.mimeType.startsWith("video/"))
        assertTrue(resolved.fileName.substringAfterLast('.') in setOf("mp4", "webm", "3gp"))
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUpExtractor() {
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

            NewPipe.init(
                DownloaderImpl.init(client),
                Localization("en", "US"),
                ContentCountry("US")
            )
        }
    }
}
