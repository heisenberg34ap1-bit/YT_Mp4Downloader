package com.example.mp4downloader

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mp4downloader.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingDownload: PendingDownload? = null

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destinationUri ->
            val download = pendingDownload
            pendingDownload = null

            if (destinationUri == null || download == null) {
                binding.downloadButton.isEnabled = true
                Toast.makeText(this, R.string.download_cancelled, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            performDownload(download, destinationUri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.downloadButton.setOnClickListener {
            startDownload()
        }
    }

    private fun startDownload() {
        val rawUrl = binding.urlInput.text?.toString()?.trim().orEmpty()

        if (rawUrl.isBlank()) {
            binding.urlLayout.error = getString(R.string.error_empty_url)
            return
        }

        binding.urlLayout.error = null
        binding.downloadButton.isEnabled = false

        val uri = rawUrl.toSafeUri()
        if (uri == null || !uri.isNetworkUrl()) {
            binding.downloadButton.isEnabled = true
            binding.urlLayout.error = getString(R.string.error_invalid_url)
            return
        }

        if (uri.isYouTubeUrl()) {
            extractAndPrepareYoutube(rawUrl)
        } else if (uri.isAllowedMp4Url()) {
            promptForSaveLocation(
                PendingDownload(
                    url = uri.toString(),
                    fileName = createFileName(uri),
                    mimeType = "video/mp4"
                )
            )
        } else {
            binding.downloadButton.isEnabled = true
            binding.urlLayout.error = getString(R.string.error_not_mp4)
        }
    }

    private fun extractAndPrepareYoutube(url: String) {
        lifecycleScope.launch {
            try {
                val streamInfo = withContext(Dispatchers.IO) {
                    StreamInfo.getInfo(ServiceList.YouTube, url)
                }

                val resolvedStream = YoutubeStreamResolver.resolve(
                    streamInfo = streamInfo,
                    fallbackBaseName = "youtube_${timestamp()}"
                )

                if (resolvedStream != null) {
                    promptForSaveLocation(
                        PendingDownload(
                            url = resolvedStream.url,
                            fileName = resolvedStream.fileName,
                            mimeType = resolvedStream.mimeType
                        )
                    )
                } else {
                    binding.downloadButton.isEnabled = true
                    Toast.makeText(
                        this@MainActivity,
                        R.string.error_no_video_stream,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                binding.downloadButton.isEnabled = true
                Log.e("MainActivity", "Extraction failed", e)
                val message = getString(
                    R.string.error_extraction_failed,
                    e.message ?: getString(R.string.error_unknown)
                )
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun promptForSaveLocation(download: PendingDownload) {
        pendingDownload = download
        createDocumentLauncher.launch(download.fileName)
    }

    private fun performDownload(download: PendingDownload, destinationUri: Uri) {
        Toast.makeText(
            this,
            getString(R.string.download_started_named, download.fileName),
            Toast.LENGTH_LONG
        ).show()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    VideoFileDownloader.download(
                        context = applicationContext,
                        url = download.url,
                        destinationUri = destinationUri
                    )
                }
                binding.urlInput.text?.clear()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.download_completed_named, download.fileName),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e("MP4Downloader", "Download failed", e)
                val message = getString(
                    R.string.error_download_failed,
                    e.message ?: getString(R.string.error_unknown)
                )
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            } finally {
                binding.downloadButton.isEnabled = true
            }
        }
    }

    private fun Uri.isNetworkUrl(): Boolean {
        val schemeValue = scheme?.lowercase(Locale.US)
        return schemeValue == "http" || schemeValue == "https"
    }

    private fun Uri.isYouTubeUrl(): Boolean {
        val normalizedHost = host
            ?.lowercase(Locale.US)
            ?.removePrefix("www.")
            ?.removePrefix("m.")
            ?: return false

        return normalizedHost == "youtube.com" ||
            normalizedHost.endsWith(".youtube.com") ||
            normalizedHost == "youtu.be"
    }

    private fun Uri.isAllowedMp4Url(): Boolean {
        val lastSegment = lastPathSegment.orEmpty()
        val extension = MimeTypeMap.getFileExtensionFromUrl(toString()).lowercase(Locale.US)
        return extension == "mp4" || lastSegment.lowercase(Locale.US).endsWith(".mp4")
    }

    private fun createFileName(uri: Uri): String {
        val baseName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBefore('?')
            ?.takeIf { it.endsWith(".mp4", ignoreCase = true) }
            ?: "video_${timestamp()}.mp4"

        return sanitizeFileName(baseName, "video_${timestamp()}")
    }

    private fun sanitizeFileName(candidate: String, fallbackBase: String): String {
        val sanitized = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]").replace(candidate, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')

        if (sanitized.isBlank()) {
            return "$fallbackBase.mp4"
        }

        return if (sanitized.endsWith(".mp4", ignoreCase = true)) {
            sanitized
        } else {
            "$sanitized.mp4"
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private fun String.toSafeUri(): Uri? {
        return runCatching { Uri.parse(this) }.getOrNull()
    }

    private data class PendingDownload(
        val url: String,
        val fileName: String,
        val mimeType: String
    )
}
