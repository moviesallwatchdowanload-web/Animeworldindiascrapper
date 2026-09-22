package com.outcome.watchanimeworld

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URLEncoder

class WatchAnimeWorld : MainAPI() {
    override var mainUrl = "https://watchanimeworld.one"
    override var name = "WatchAnimeWorld"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.Movie,
        TvType.Cartoon
    )

    companion object {
        private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val headers = mapOf(
            "User-Agent" to UA,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9,hi;q=0.8"
        )
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) mainUrl else "$mainUrl/page/$page/"
        val doc = app.get(url, headers = headers).document

        val cards = doc.select("li.post, article.post").mapNotNull { parseCard(it) }.distinctBy { it.url }

        val sections = if (cards.isEmpty()) emptyList()
            else listOf(HomePageList("Latest", cards))

        return newHomePageResponse(
            sections,
            hasNext = doc.select("a.next, a.page-numbers.next").isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=" + URLEncoder.encode(query, "UTF-8")
        val doc = app.get(url, headers = headers).document

        return doc.select("li.post, article.post")
            .mapNotNull { parseCard(it) }
            .distinctBy { it.url }
    }

    private fun parseCard(el: Element): SearchResponse? {
        val title = el.selectFirst("h2.entry-title")?.text()?.trim()
            ?: el.selectFirst("h3.entry-title")?.text()?.trim()
            ?: return null

        val href = el.selectFirst("a.lnk-blk")?.attr("href")
            ?: el.selectFirst("a")?.attr("href")
            ?: return null

        val fullUrl = if (href.startsWith("http")) href else mainUrl + href

        val poster = el.selectFirst("img")?.let { img ->
            val src = img.attr("src").ifBlank { img.attr("data-src") }
            if (src.startsWith("//")) "https:$src" else src
        }

        val type = when {
            fullUrl.contains("/series/") -> TvType.Anime
            fullUrl.contains("/episode/") -> TvType.Anime
            else -> TvType.Movie
        }

        return newAnimeSearchResponse(title, fullUrl, type) {
            this.posterUrl = poster
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = headers).document

        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: doc.title().substringBefore(" - ").trim()

        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("div.poster img, div.post-thumbnail img")?.let {
                val s = it.attr("src").ifBlank { it.attr("data-src") }
                if (s.startsWith("//")) "https:$s" else s
            }

        val plot = doc.selectFirst("div.description, div.wp-content p, p.plot")?.text()?.trim()
        val year = doc.selectFirst("span.year, a.year")?.text()?.toIntOrNull()
        val genres = doc.select("div.sgeneros a, a[rel=tag]")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }

        val episodes = doc.select("article.post.dfx.fcl.episodes").mapNotNull { ep ->
            val epTitle = ep.selectFirst("h2.entry-title")?.text()?.trim() ?: return@mapNotNull null
            val epNum = ep.selectFirst("span.num-epi")?.text()?.trim()
            val epHref = ep.selectFirst("a.lnk-blk")?.attr("href") ?: return@mapNotNull null
            val epFull = if (epHref.startsWith("http")) epHref else mainUrl + epHref

            val epPoster = ep.selectFirst("img")?.let {
                val s = it.attr("src").ifBlank { it.attr("data-src") }
                if (s.startsWith("//")) "https:$s" else s
            }

            newEpisode(epFull) {
                this.name = if (epNum != null) "$epNum - $epTitle" else epTitle
                this.posterUrl = epPoster
                this.episode = epNum?.substringAfterLast("x")?.toIntOrNull()
            }
        }

        return if (episodes.isNotEmpty()) {
            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
                addEpisodes(DubStatus.Subbed, episodes)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = headers).document
        var found = false

        val iframes = doc.select("iframe[src], iframe[data-src]")
        for (frame in iframes) {
            val src = frame.attr("src").ifBlank { frame.attr("data-src") }
            if (src.isBlank()) continue

            if (src.contains("play.zephyrix.org/video/")) {
                val hash = src.substringAfterLast("/video/").substringBefore("?").substringBefore("/")
                val ok = ZephyrixExtractor.extract(hash, src, callback, subtitleCallback)
                if (ok) found = true
            } else if (src.startsWith("http")) {
                try {
                    loadExtractor(src, data, subtitleCallback, callback)
                    found = true
                } catch (_: Throwable) {}
            }
        }

        if (!found) {
            doc.select("iframe").forEach { frame ->
                val src = frame.attr("src").ifBlank { frame.attr("data-src") }
                if (src.startsWith("http")) {
                    try {
                        loadExtractor(src, data, subtitleCallback, callback)
                        found = true
                    } catch (_: Throwable) {}
                }
            }
        }

        return found
    }
}
