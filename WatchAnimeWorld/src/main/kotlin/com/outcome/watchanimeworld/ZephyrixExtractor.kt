package com.outcome.watchanimeworld

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.google.gson.JsonParser

object ZephyrixExtractor {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun extract(
        videoHash: String,
        videoUrl: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        try {
            val origin = "https://play.zephyrix.org"
            val apiUrl = "$origin/player/index.php?data=$videoHash&do=getVideo"

            val resp = app.post(
                apiUrl,
                data = mapOf("hash" to videoHash, "r" to videoUrl),
                headers = mapOf(
                    "User-Agent" to UA,
                    "Referer" to videoUrl,
                    "Origin" to origin,
                    "X-Requested-With" to "XMLHttpRequest",
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                )
            ).text

            var securedLink: String? = null
            var videoSource: String? = null
            try {
                val json = JsonParser.parseString(resp).asJsonObject
                securedLink = json.get("securedLink")?.asString
                videoSource = json.get("videoSource")?.asString
            } catch (_: Throwable) {
                securedLink = Regex("\"securedLink\"\\s*:\\s*\"(https:[^\"]+)\"").find(resp)?.groupValues?.get(1)
                videoSource = Regex("\"videoSource\"\\s*:\\s*\"(https:[^\"]+)\"").find(resp)?.groupValues?.get(1)
            }

            val finalUrl = (securedLink ?: videoSource)?.replace("\\/", "/") ?: return false

            callback.invoke(
                newExtractorLink("Zephyrix", "Zephyrix (HLS)", finalUrl, ExtractorLinkType.M3U8) {
                    this.referer = videoUrl
                    this.quality = 720
                    this.headers = mapOf(
                        "User-Agent" to UA,
                        "Referer" to videoUrl,
                        "Origin" to origin
                    )
                }
            )

            try {
                val sub = SubtitleExtractor.extract(videoUrl)
                if (sub != null) subtitleCallback.invoke(SubtitleFile("English", sub))
            } catch (_: Throwable) {}

            return true
        } catch (e: Throwable) {
            return false
        }
    }
}
