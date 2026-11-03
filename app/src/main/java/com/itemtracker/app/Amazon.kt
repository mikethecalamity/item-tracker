package com.itemtracker.app

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read product details from an Amazon link
 *
 * Amazon has no product API for personal apps, so this reads the title and main picture from the product page the
 * way a link preview does. Amazon sometimes blocks this, so every result can be null.
 */
object Amazon {
    private const val TAG = "Amazon"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
    private const val MAX_REDIRECTS = 6
    private const val MAX_PAGE_BYTES = 3_000_000
    private const val MAX_SHORT_NAME = 40
    private val URL_PATTERN = Regex("""https?://\S+""")
    private val AMAZON_HOST_PATTERN = Regex("""(?i)(amazon\.|amzn\.|a\.co/)""")
    private val SHARE_PREFIXES = listOf(
        Regex("""(?i)^check out this (deal|item|product)( on amazon)?[:!]?"""),
        Regex("""(?i)^i found this on amazon[:!]?"""),
    )
    private val SHARE_SUFFIX = Regex("""(?i)\s*(on amazon|amazon\.com)\s*:?\s*$""")
    private val NAME_SEPARATORS = listOf(" - ", " – ", ",", "|", "(", ":")
    private val TITLE_PATTERNS = listOf(
        Regex("""id="productTitle"[^>]*>\s*([^<]+)"""),
        Regex("""<meta[^>]+property="og:title"[^>]+content="([^"]+)""""),
        Regex("""<meta[^>]+name="title"[^>]+content="([^"]+)""""),
        Regex("""<title[^>]*>([^<]+)</title>"""),
    )
    private val IMAGE_PATTERNS = listOf(
        Regex("""data-old-hires="(https://[^"]+)""""),
        Regex(""""hiRes":"(https://[^"]+)""""),
        Regex("""id="landingImage"[^>]*src="(https://[^"]+)""""),
        Regex(""""large":"(https://[^"]+)""""),
        Regex("""<meta[^>]+property="og:image"[^>]+content="([^"]+)""""),
        Regex("""(https://m\.media-amazon\.com/images/I/[A-Za-z0-9%+\-_.]+\.(jpg|png))"""),
    )
    private val TITLE_CATEGORY_SUFFIX = Regex("""\s*:\s*[^:]*$""")

    /** Link and name pulled from shared text */
    data class Shared(val url: String?, val title: String?)

    /** Details read from a product page */
    data class Product(val title: String?, val imageUrl: String?, val finalUrl: String?)

    /**
     * Pull the link and a name out of text shared from the Amazon app or a browser
     *
     * @param text the shared text
     * @param subject the shared subject, which some apps fill with the product title
     * @return the link and name, either of which can be null
     */
    fun parseShare(text: String?, subject: String?): Shared {
        val sharedText = text.orEmpty()
        val url = URL_PATTERN.find(sharedText)?.value?.trimEnd('.', ',', ')', '"')
        val rawTitle = subject?.takeIf(String::isNotBlank) ?: sharedText.replace(URL_PATTERN, "")
        val title = SHARE_PREFIXES.fold(rawTitle.trim()) { current, prefix -> current.replace(prefix, "") }
            .replace(SHARE_SUFFIX, "")
            .trim().trim('"', '“', '”', ':', '-').trim()
        return Shared(url, title.ifBlank { null })
    }

    /**
     * Shorten a long product title to a name for the icon
     *
     * @param title the product title, such as "Brand Thing, 12 Count - Extra Words"
     * @return the short name, such as "Brand Thing"
     */
    fun shortName(title: String): String {
        val beforeSeparators = NAME_SEPARATORS.fold(title) { current, separator ->
            val position = current.indexOf(separator)
            if (position in 3 until current.length) current.substring(0, position) else current
        }.trim()
        val shortened = if (beforeSeparators.length > MAX_SHORT_NAME) {
            beforeSeparators.substring(0, MAX_SHORT_NAME).substringBeforeLast(' ').trim()
        }
        else {
            beforeSeparators
        }
        return shortened.ifBlank { title.take(MAX_SHORT_NAME) }
    }

    /**
     * Check whether a link points to Amazon
     *
     * @param url the link
     * @return true for Amazon links, including a.co short links
     */
    fun looksLikeAmazon(url: String?): Boolean = url != null && AMAZON_HOST_PATTERN.containsMatchIn(url)

    /**
     * Read the title and picture link from a product page; blocking, so call it on Dispatchers.IO
     *
     * @param url the product link
     * @return the details, or null when the page cannot be read
     */
    fun fetchProduct(url: String): Product? = try {
        val connection = open(url)
        val finalUrl = connection.url.toString()
        val html = connection.inputStream.use(::readLimited).toString(Charsets.UTF_8)
        connection.disconnect()
        Product(findTitle(html), findImage(html), finalUrl)
    }
    catch (e: IOException) {
        Log.w(TAG, "Failed to read Amazon page $url", e)
        null
    }
    catch (e: RuntimeException) {
        // Covers links that are not web pages (ClassCastException) and redirect loops (IllegalStateException)
        Log.w(TAG, "Failed to read Amazon page $url", e)
        null
    }

    /**
     * Download a picture; blocking, so call it on Dispatchers.IO
     *
     * @param url the picture link
     * @return the bytes, or null when the download fails
     */
    fun downloadBytes(url: String): ByteArray? = try {
        val connection = open(url)
        connection.inputStream.use(::readLimited).also { connection.disconnect() }
    }
    catch (e: IOException) {
        Log.w(TAG, "Failed to download picture $url", e)
        null
    }
    catch (e: RuntimeException) {
        Log.w(TAG, "Failed to download picture $url", e)
        null
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                // Redirects are followed by hand so http to https hops (a.co short links) also work
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,image/*,*/*;q=0.8")
            }
            val location = connection.getHeaderField("Location")
            if (connection.responseCode !in 300..399 || location == null) return connection
            current = URL(URL(current), location).toString()
            connection.disconnect()
        }
        throw IllegalStateException("Too many redirects for $url")
    }

    private fun readLimited(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (buffer.size() < MAX_PAGE_BYTES) {
            val count = input.read(chunk)
            if (count < 0) break
            buffer.write(chunk, 0, count)
        }
        return buffer.toByteArray()
    }

    private fun decodeEntities(text: String): String = text
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .trim()

    private fun findTitle(html: String): String? = TITLE_PATTERNS.asSequence()
        .mapNotNull { pattern -> pattern.find(html)?.groupValues?.get(1) }
        .map { match ->
            decodeEntities(match).removePrefix("Amazon.com: ").removePrefix("Amazon.com : ")
                .replace(TITLE_CATEGORY_SUFFIX, "").trim()
        }
        .firstOrNull { title ->
            title.isNotBlank() && !title.equals("Amazon.com", true) && !title.contains("Robot Check", true)
        }

    private fun findImage(html: String): String? = IMAGE_PATTERNS.asSequence()
        .mapNotNull { pattern -> pattern.find(html)?.groupValues?.get(1) }
        .firstOrNull(String::isNotBlank)
        ?.let(::decodeEntities)
}
