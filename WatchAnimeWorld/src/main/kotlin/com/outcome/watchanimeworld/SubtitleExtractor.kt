package com.outcome.watchanimeworld

import com.lagradost.cloudstream3.app

object SubtitleExtractor {

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun extract(videoUrl: String): String? {
        try {
            val html = app.get(videoUrl, headers = mapOf("User-Agent" to UA)).text
            val unpacked = DeanEdwards.unpack(html) ?: return null

            val altRegex = Regex(
                "\"kind\"\\s*:\\s*\"captions\"[\\s\\S]*?\"file\"\\s*:\\s*\"(https:[^\"]+?\\.html)\"",
                RegexOption.DOT_MATCHES_ALL
            )
            val subtitleRegex = Regex(
                "\"file\"\\s*:\\s*\"(https:[^\"]+?\\.html)\"[\\s\\S]*?\"kind\"\\s*:\\s*\"captions\"",
                RegexOption.DOT_MATCHES_ALL
            )

            val raw = altRegex.find(unpacked)?.groupValues?.get(1)
                ?: subtitleRegex.find(unpacked)?.groupValues?.get(1)
                ?: return null

            return raw.replace("\\/", "/")
        } catch (_: Throwable) {
            return null
        }
    }
}
