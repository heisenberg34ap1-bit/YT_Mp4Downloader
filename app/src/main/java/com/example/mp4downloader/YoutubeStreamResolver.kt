package com.example.mp4downloader

import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.Locale

data class ResolvedVideoStream(
    val url: String,
    val fileName: String,
    val mimeType: String
)

object YoutubeStreamResolver {
    private val invalidFilenameChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val validExtensions = setOf("mp4", "webm", "3gp")

    fun resolve(streamInfo: StreamInfo, fallbackBaseName: String): ResolvedVideoStream? {
        val candidates = streamInfo.videoStreams.mapNotNull { stream ->
            val url = stream.getUrl() ?: return@mapNotNull null
            val formatName = stream.getFormat()?.getName()?.lowercase(Locale.US).orEmpty()
            val extension = normalizeExtension(formatName)

            Candidate(
                url = url,
                extension = extension,
                bitrate = stream.bitrate,
                hasAudio = !isVideoOnly(stream),
                isPreferredMp4 = extension == "mp4"
            )
        }

        val selected = candidates.sortedWith(
            compareByDescending<Candidate> { it.hasAudio && it.isPreferredMp4 }
                .thenByDescending { it.hasAudio }
                .thenByDescending { it.isPreferredMp4 }
                .thenByDescending { it.bitrate }
        ).firstOrNull() ?: return null

        val safeBaseName = sanitizeBaseName(streamInfo.name.ifBlank { fallbackBaseName }, fallbackBaseName)
        val finalExtension = selected.extension.ifBlank { "mp4" }

        return ResolvedVideoStream(
            url = selected.url,
            fileName = "$safeBaseName.$finalExtension",
            mimeType = "video/$finalExtension"
        )
    }

    private fun sanitizeBaseName(candidate: String, fallbackBase: String): String {
        val sanitized = invalidFilenameChars.replace(candidate, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')

        return sanitized.ifBlank { fallbackBase }
    }

    private fun isVideoOnly(stream: Any): Boolean {
        return runCatching {
            val method = stream.javaClass.methods.firstOrNull { method ->
                (method.name == "isVideoOnly" || method.name == "getVideoOnly") &&
                    method.parameterCount == 0
            } ?: return@runCatching false

            method.invoke(stream) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun normalizeExtension(rawFormatName: String): String {
        val normalized = rawFormatName
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "")

        return when {
            normalized in validExtensions -> normalized
            normalized.contains("mp4") -> "mp4"
            normalized.contains("webm") -> "webm"
            normalized.contains("3gp") -> "3gp"
            else -> "mp4"
        }
    }

    private data class Candidate(
        val url: String,
        val extension: String,
        val bitrate: Int,
        val hasAudio: Boolean,
        val isPreferredMp4: Boolean
    )
}
