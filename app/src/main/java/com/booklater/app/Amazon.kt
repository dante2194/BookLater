package com.booklater.app

import android.webkit.CookieManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AmazonException(message: String, val captcha: Boolean = false) : Exception(message)

/**
 * Downloads Amazon pages as plain HTML and reads them with regular expressions.
 * Every pattern lives in this file — if Amazon changes its markup, fix it here.
 */
object Amazon {
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val DOT = RegexOption.DOT_MATCHES_ALL
    private val IC = RegexOption.IGNORE_CASE

    // ------------------------------------------------------------ patterns
    private val ASIN = Regex("[A-Z0-9]{10}")
    private val ASIN_LINK = Regex(
        "<a\\b[^>]*?href=\"[^\"]*?(?:/dp/|/gp/product/|%2Fdp%2F)([A-Z0-9]{10})[^\"]*\"[^>]*>(.*?)</a>", DOT
    )
    private val R_IMG = Regex("<img\\b[^>]*>")
    private val R_H = Regex("<h[23]\\b[^>]*>(.*?)</h[23]>", DOT)
    private val R_RESULT = Regex("<div\\b[^>]*data-component-type=\"s-search-result\"[^>]*>")
    private val R_AUTHOR_ROW = Regex("class=\"a-row a-size-base a-color-secondary\"[^>]*>(.*?)</div>", DOT)
    private val R_AUTHOR_LINK = Regex("<a\\b[^>]*href=\"[^\"]*/e/[^\"]*\"[^>]*>(.*?)</a>", DOT)
    private val R_STARS = Regex("([0-9](?:[.,][0-9])?) out of 5 stars")
    private val R_COUNT = Regex("s-underline-text\"[^>]*>\\s*([0-9][0-9,.KkMm]*)\\s*<")
    private val R_TITLE = Regex("id=\"productTitle\"[^>]*>(.*?)</span>", DOT)
    private val R_RATING = Regex("id=\"acrPopover\"[^>]*?title=\"([0-9](?:[.,][0-9])?) out of 5")
    private val R_RCOUNT = Regex("id=\"acrCustomerReviewText\"[^>]*>\\s*([^<]+)<")
    private val R_COVER = Regex("<img\\b[^>]*id=\"(?:landingImage|imgBlkFront|ebooksImgBlkFront)\"[^>]*>")
    private val R_BULLET = Regex("<span class=\"a-text-bold\">(.*?)</span>\\s*<span>(.*?)</span>", DOT)
    private val R_TH = Regex("<th[^>]*>(.*?)</th>\\s*<td[^>]*>(.*?)</td>", DOT)
    private val R_RPI = Regex(
        "rpi-attribute-label[^>]*>\\s*<span[^>]*>(.*?)</span>.{0,400}?rpi-attribute-value[^>]*>\\s*(?:<a[^>]*>)?\\s*<span[^>]*>(.*?)</span>",
        DOT
    )
    private val R_WIDGET =
        Regex("<div\\b[^>]*(?:id=\"[^\"]*_feature_div\"|cel_widget_id=\"[^\"]*\")[^>]*>")
    private val R_REVIEW = Regex("<div\\b[^>]*data-hook=\"review\"[^>]*>")
    private val R_HIST = Regex("([1-5])\\s*stars?\\s*(\\d{1,3})\\s*%", IC)
    private val R_DIV = Regex("<(/?)div\\b")
    private val R_ENT_DEC = Regex("&#(\\d+);")
    private val R_ENT_HEX = Regex("&#x([0-9a-fA-F]+);")
    private val R_SCRIPT = Regex("<(script|style|noscript)\\b.*?</\\1>", setOf(DOT, IC))
    private val R_COMMENT = Regex("<!--.*?-->", DOT)
    private val R_BLOCK_END = Regex("<br\\s*/?>|</(p|div|li|h[1-6]|tr|ul|ol|table|section)>", IC)
    private val R_TAG = Regex("<[^>]+>")
    private val R_WS = Regex("\\s+")
    private val R_BIDI = Regex("[\\u200E\\u200F\\u202A-\\u202E]")
    private val NOISE = Regex("^(read more|see more|show more|see less|show less|read less)$", IC)
    private val REVIEW_STOP = Regex("^(Helpful|Report|Was this|Translate|One person|\\d+ (people|person)|Sponsored)", IC)
    private val KEYWORDS = Regex(
        "also bought|also viewed|might also like|similar|related|sponsored|stars and above|stars & up|" +
            "customers who|inspired by|more by|frequently|recommend|genre|books based on|best ?sellers|more to explore",
        IC
    )

    private class Spec(val title: String, val head: String, val ids: List<String> = emptyList())

    private val SPECS = listOf(
        Spec("What is it about", "What is it about"),
        Spec("Get to know this book", "Get to know this book"),
        Spec("Popular highlights", "Popular highlights"),
        Spec("Editorial reviews", "Editorial Reviews?", listOf("editorialReviews_feature_div")),
        Spec("About the author", "About the Authors?"),
        Spec("From the publisher", "From the Publisher", listOf("aplus_feature_div", "aplus"))
    )

    // ------------------------------------------------------------ download

    private fun get(url: String): String {
        val cookie = try {
            CookieManager.getInstance().getCookie(url)
        } catch (e: Throwable) {
            null
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            if (!cookie.isNullOrBlank()) conn.setRequestProperty("Cookie", cookie)

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            val head = body.take(8000)
            if (code == 503 || head.contains("validateCaptcha") || head.contains("Robot Check", true)) {
                throw AmazonException("Amazon is asking for a human check.", true)
            }
            if (code !in 200..299) throw AmazonException("Amazon answered with HTTP $code.")
            return body
        } catch (e: AmazonException) {
            throw e
        } catch (e: IOException) {
            throw AmazonException("Network problem: ${e.message ?: "can't reach Amazon"}")
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------ text helpers

    private val ENTITIES = listOf(
        "&rlm;" to "", "&lrm;" to "", "&nbsp;" to " ", "&quot;" to "\"", "&apos;" to "'",
        "&lt;" to "<", "&gt;" to ">", "&rsquo;" to "’", "&lsquo;" to "‘", "&ldquo;" to "“",
        "&rdquo;" to "”", "&ndash;" to "–", "&mdash;" to "—", "&hellip;" to "…", "&amp;" to "&"
    )

    private fun codePoint(n: Int?, original: String): String =
        if (n != null && n in 1..0x10FFFF) String(Character.toChars(n)) else original

    private fun unescape(s: String): String {
        var r = s
        for ((k, v) in ENTITIES) r = r.replace(k, v)
        r = R_ENT_DEC.replace(r) { m -> codePoint(m.groupValues[1].toIntOrNull(), m.value) }
        r = R_ENT_HEX.replace(r) { m -> codePoint(m.groupValues[1].toIntOrNull(16), m.value) }
        return r
    }

    /** HTML → readable text, one paragraph per line. */
    private fun strip(html: String): String {
        var s = html
        s = R_SCRIPT.replace(s, " ")
        s = R_COMMENT.replace(s, " ")
        s = R_BLOCK_END.replace(s, "\n")
        s = R_TAG.replace(s, " ")
        s = unescape(s)
        s = R_BIDI.replace(s, "")
        val seen = HashSet<String>()
        return s.lines()
            .map { R_WS.replace(it, " ").trim() }
            .filter { it.isNotEmpty() && !NOISE.matches(it) && seen.add(it) }
            .joinToString("\n")
    }

    /** HTML → one clean line. */
    private fun text(html: String): String = strip(html).replace("\n", " ").trim()

    private fun attr(tag: String, name: String): String {
        val m = Regex("(?<![\\w-])" + name + "=\"([^\"]*)\"").find(tag) ?: return ""
        return unescape(m.groupValues[1])
    }

    private fun dropHead(body: String, head: Regex): String {
        val lines = body.lines()
        return if (lines.isNotEmpty() && lines[0].length < 80 && head.containsMatchIn(lines[0])) {
            lines.drop(1).joinToString("\n")
        } else {
            body
        }
    }

    /** The full <div>…</div> that starts at index [start] (balanced). */
    private fun divFrom(html: String, start: Int): String {
        var depth = 0
        for (t in R_DIV.findAll(html, start)) {
            depth += if (t.groupValues[1] == "/") -1 else 1
            if (depth <= 0) {
                val end = html.indexOf('>', t.range.first)
                return html.substring(start, if (end < 0) html.length else end + 1)
            }
        }
        return html.substring(start, minOf(html.length, start + 20_000))
    }

    /** The <div> carrying id="[id]" (whole balanced block), or null. */
    private fun divById(html: String, id: String): String? {
        val m = Regex("id=\"" + Regex.escape(id) + "\"").find(html) ?: return null
        val start = html.lastIndexOf("<div", m.range.first)
        if (start < 0) return null
        return divFrom(html, start)
    }

    /** Finds a section by its visible heading and returns the text of the block around it. */
    private fun byHeading(html: String, head: String): String? {
        val m = Regex(">\\s*(?:$head)[^<>]{0,40}<", IC).find(html) ?: return null
        val pos = m.range.first
        val from = maxOf(0, pos - 30_000)
        val window = html.substring(from, pos)
        var last: MatchResult? = null
        for (x in R_WIDGET.findAll(window)) last = x
        var block: String? = null
        if (last != null) {
            val start = from + last.range.first
            val b = divFrom(html, start)
            if (start + b.length > pos) block = b
        }
        if (block == null) block = html.substring(pos, minOf(html.length, pos + 5_000))
        val body = dropHead(strip(block), Regex("^(?:$head)", IC))
        return if (body.length > 30) body.take(6_000) else null
    }

    // ------------------------------------------------------------ search

    fun search(domain: String, query: String): List<Book> {
        val html = get("https://$domain/s?k=${URLEncoder.encode(query, "UTF-8")}&i=stripbooks")
        val starts = R_RESULT.findAll(html).toList()
        val out = LinkedHashMap<String, Book>()
        for ((i, m) in starts.withIndex()) {
            val tag = m.value
            if (tag.contains("AdHolder")) continue
            val asin = attr(tag, "data-asin")
            if (!ASIN.matches(asin) || out.containsKey(asin)) continue
            val end = if (i + 1 < starts.size) starts[i + 1].range.first else minOf(html.length, m.range.first + 20_000)
            val chunk = html.substring(m.range.first, end)

            val title = R_H.find(chunk)?.let { text(it.groupValues[1]) }.orEmpty()
            if (title.isBlank()) continue

            val imgTag = Regex("<img\\b[^>]*s-image[^>]*>").find(chunk)?.value.orEmpty()
            val img = attr(imgTag, "src")
            val rating = R_STARS.find(chunk)?.groupValues?.get(1).orEmpty()
            val count = R_COUNT.find(chunk)?.groupValues?.get(1).orEmpty()

            var author = R_AUTHOR_ROW.find(chunk)?.let { text(it.groupValues[1]) }.orEmpty()
            if (author.isBlank()) {
                author = R_AUTHOR_LINK.find(chunk)?.let { text(it.groupValues[1]) }.orEmpty()
            }
            author = author.replace(Regex("^by\\s+", IC), "").substringBefore("|").trim()

            out[asin] = Book(asin, title, author, img, rating, count)
        }
        return out.values.toList()
    }

    // ------------------------------------------------------------ detail

    fun detail(domain: String, asin: String, hint: Book?): BookDetail {
        val html = get("https://$domain/dp/$asin")

        val title = R_TITLE.find(html)?.let { text(it.groupValues[1]) }.orEmpty()
        if (title.isBlank()) {
            throw AmazonException("Couldn't read this book's page. Amazon may have changed it or blocked the request.")
        }

        var author = divById(html, "bylineInfo")?.let { block ->
            R_AUTHOR_LINK.findAll(block).map { text(it.groupValues[1]) }
                .filter { it.isNotBlank() }.distinct().toList().joinToString(", ")
        }.orEmpty()
        if (author.isBlank()) author = hint?.author.orEmpty()

        val coverTag = R_COVER.find(html)?.value.orEmpty()
        var img = attr(coverTag, "data-old-hires")
        if (img.isBlank()) img = attr(coverTag, "src")
        if (img.isBlank() || img.startsWith("data:")) img = hint?.imageUrl.orEmpty()

        var rating = R_RATING.find(html)?.groupValues?.get(1).orEmpty()
        if (rating.isBlank()) rating = hint?.rating.orEmpty()
        var count = R_RCOUNT.find(html)?.let { text(it.groupValues[1]) }.orEmpty()
        if (count.isBlank()) count = hint?.ratingCount.orEmpty()

        val descBlock = divById(html, "bookDescription_feature_div") ?: divById(html, "productDescription")
        val desc = descBlock?.let { dropHead(strip(it), Regex("^(book )?description$", IC)) }.orEmpty()

        val sections = ArrayList<TextSection>()
        for (sp in SPECS) {
            var body: String? = null
            for (id in sp.ids) {
                val b = divById(html, id) ?: continue
                val t = dropHead(strip(b), Regex("^(?:${sp.head})", IC))
                if (t.length > 30) {
                    body = t.take(6_000)
                    break
                }
            }
            if (body == null) body = byHeading(html, sp.head)
            if (body != null && sections.none { it.body == body }) {
                sections.add(TextSection(sp.title, body))
            }
        }

        val customersSay = byHeading(html, "Customers say").orEmpty()

        return BookDetail(
            book = Book(asin, title, author, img, rating, count),
            description = desc,
            facts = facts(html),
            sections = sections,
            customersSay = customersSay,
            histogram = histogram(html),
            reviews = reviews(html).take(10),
            groups = groups(html, asin)
        )
    }

    private fun put(out: MutableMap<String, String>, k0: String, v0: String) {
        val k = k0.trim().trimEnd(':', ' ').trim()
        val v = v0.trim()
        if (k.isNotEmpty() && v.isNotEmpty() && k.length < 60) out.putIfAbsent(k, v.take(300))
    }

    private fun facts(html: String): List<Fact> {
        val out = LinkedHashMap<String, String>()
        val bullets = divById(html, "detailBullets_feature_div") ?: divById(html, "detailBulletsWrapper_feature_div")
        if (bullets != null) {
            for (m in R_BULLET.findAll(bullets)) put(out, text(m.groupValues[1]), text(m.groupValues[2]))
        }
        val tableAt = html.indexOf("id=\"productDetailsTable\"")
        if (tableAt >= 0) {
            val block = html.substring(tableAt, minOf(html.length, tableAt + 15_000))
            for (m in R_TH.findAll(block)) put(out, text(m.groupValues[1]), text(m.groupValues[2]))
        }
        for (m in R_RPI.findAll(html)) put(out, text(m.groupValues[1]), text(m.groupValues[2]))
        return out.map { Fact(it.key, it.value) }
    }

    private fun histogram(html: String): List<StarShare> {
        var at = html.indexOf("id=\"histogramTable\"")
        if (at < 0) at = html.indexOf("cm_cr_dp_d_rating_histogram")
        if (at < 0) return emptyList()
        val flat = text(html.substring(at, minOf(html.length, at + 10_000)))
        val out = LinkedHashMap<Int, Int>()
        for (m in R_HIST.findAll(flat)) {
            out.putIfAbsent(m.groupValues[1].toInt(), m.groupValues[2].toInt().coerceIn(0, 100))
        }
        return out.map { StarShare(it.key, it.value) }.sortedByDescending { it.stars }
    }

    private fun reviews(html: String): List<Review> {
        val starts = R_REVIEW.findAll(html).map { it.range.first }.toList()
        val out = ArrayList<Review>()
        for ((i, s) in starts.withIndex()) {
            val end = minOf(if (i + 1 < starts.size) starts[i + 1] else html.length, s + 12_000)
            val chunk = html.substring(s, end)

            val bodyRaw = Regex("data-hook=\"review-body\"[^>]*>(.*?)(?=data-hook=\"|\\z)", DOT)
                .find(chunk)?.groupValues?.get(1).orEmpty()
            val body = strip(bodyRaw).lines().takeWhile { !REVIEW_STOP.containsMatchIn(it) }
                .joinToString("\n").trim()
            if (body.isBlank()) continue

            val author = Regex("class=\"a-profile-name\">(.*?)</span>", DOT)
                .find(chunk)?.let { text(it.groupValues[1]) }.orEmpty()
            val stars = R_STARS.find(chunk)?.groupValues?.get(1).orEmpty()
            val titleRaw = Regex("data-hook=\"review-title\"[^>]*>(.{0,700})", DOT)
                .find(chunk)?.groupValues?.get(1).orEmpty()
            val title = strip(titleRaw).lines().firstOrNull().orEmpty()
                .replace(Regex("^[0-9](?:[.,][0-9])? out of 5 stars\\s*"), "")
                .substringBefore("Reviewed in").trim()
            val date = Regex("data-hook=\"review-date\"[^>]*>(.*?)</span>", DOT)
                .find(chunk)?.let { text(it.groupValues[1]) }.orEmpty()

            out.add(Review(author, title, stars, body, date))
        }
        return out
    }

    // ------------------------------------------------------------ recommendation rows

    private fun collect(chunk: String, self: String): List<Book> {
        val map = LinkedHashMap<String, Book>()
        for (m in ASIN_LINK.findAll(chunk)) {
            val asin = m.groupValues[1]
            if (asin == self) continue
            val inner = m.groupValues[2]
            val imgTag = R_IMG.find(inner)?.value.orEmpty()
            var src = attr(imgTag, "data-src")
            if (src.isBlank()) src = attr(imgTag, "src")
            if (src.startsWith("data:")) src = ""
            var t = attr(imgTag, "alt")
            if (t.isBlank()) t = text(inner).take(140)
            val prev = map[asin]
            val title = prev?.title?.takeIf { it.isNotBlank() } ?: t
            val image = prev?.imageUrl?.takeIf { it.isNotBlank() } ?: src
            if (title.isNotBlank()) map[asin] = Book(asin, title, imageUrl = image)
        }
        return map.values.take(30)
    }

    private fun groups(html: String, self: String): List<BookGroup> {
        val heads = R_H.findAll(html).toList()
        val out = LinkedHashMap<String, LinkedHashMap<String, Book>>()
        for ((i, h) in heads.withIndex()) {
            val title = text(h.groupValues[1])
            if (title.isEmpty() || title.length > 120) continue
            val from = h.range.last + 1
            val to = minOf(if (i + 1 < heads.size) heads[i + 1].range.first else html.length, from + 40_000)
            if (to <= from) continue
            val chunk = html.substring(from, to)
            if (!chunk.contains("a-carousel-card") && !KEYWORDS.containsMatchIn(title)) continue
            val books = collect(chunk, self)
            if (books.isEmpty()) continue
            val map = out.getOrPut(title) { LinkedHashMap() }
            for (b in books) map.putIfAbsent(b.asin, b)
        }
        if (out.isEmpty() && html.contains("a-carousel-card")) {
            val all = collect(html, self)
            if (all.isNotEmpty()) out["Related books"] = LinkedHashMap(all.associateBy { it.asin })
        }
        return out.map { BookGroup(it.key, it.value.values.toList()) }
    }
}
