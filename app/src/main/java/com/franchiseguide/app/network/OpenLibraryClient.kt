package com.franchiseguide.app.network

import com.franchiseguide.app.model.*
import com.franchiseguide.app.model.MediaItem
import com.franchiseguide.app.model.MediaKind
import com.franchiseguide.app.model.BookEditionRules
import com.franchiseguide.app.model.BookEditionPage
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class OpenLibraryClient {
    fun searchBooks(query: String, limit: Int = 20, language: String = "fr", page: Int = 1): List<MediaItem> {
        val lang = language.substringBefore('-').lowercase().takeIf { it.length == 2 } ?: "fr"
        val url = "https://openlibrary.org/search.json?q=${enc(query)}&limit=${limit.coerceIn(1, 40)}&lang=$lang&page=${page.coerceAtLeast(1)}&fields=key,title,author_name,first_publish_year,cover_i,edition_count,number_of_pages_median,publisher,subject"
        val root = JSONObject(get(url))
        val docs = root.optJSONArray("docs") ?: error("Open Library : tableau de résultats manquant.")
        return buildList {
            for (i in 0 until docs.length()) {
                val o = docs.optJSONObject(i) ?: continue
                val key = BookEditionRules.key(o.optString("key"))
                val title = o.optString("title").trim()
                if (key.isBlank() || title.isBlank()) continue
                val authors = strings(o, "author_name")
                val publishers = strings(o, "publisher")
                val subjects = strings(o, "subject")
                val coverId = o.optInt("cover_i", 0)
                add(
                    MediaItem(
                        id = "openlibrary-book-${key.removePrefix("/").replace('/', '-')}",
                        kind = MediaKind.BOOK,
                        title = title,
                        subtitle = authors.take(3).joinToString(", "),
                        year = o.optInt("first_publish_year", 0).takeIf { it > 0 },
                        overview = subjects.take(5).joinToString(" · "),
                        posterUrl = coverId.takeIf { it > 0 }?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
                        added = false,
                        source = "openlibrary",
                        sourceId = key,
                        creator = authors.joinToString(", ").takeIf { it.isNotBlank() },
                        publisher = publishers.take(3).joinToString(", ").takeIf { it.isNotBlank() },
                        progressTotal = 0,
                        progressUnit = "pages",
                        genres = subjects.take(12).toSet(),
                        externalUrl = "https://openlibrary.org$key"
                    )
                )
            }
        }
    }

    fun discoverBooks(language: String = "fr", limit: Int = 30, page:Int=1): List<MediaItem> = searchBooks("subject:fiction", limit, language, page)
        .map { it.copy(isSuggestion = true) }

    fun getBookDetails(sourceId: String): MediaItem {
        val key = BookEditionRules.key(sourceId)
        val o = JSONObject(get("https://openlibrary.org$key.json"))
        require(BookEditionRules.key(o.optString("key"))==key) { "Réponse Open Library pour une autre œuvre." }
        val title = o.optString("title").ifBlank { "Livre Open Library" }
        val description = when (val desc = o.opt("description")) {
            is JSONObject -> desc.optString("value")
            is String -> desc
            else -> ""
        }
        val subjects = strings(o, "subjects")
        val covers = o.optJSONArray("covers")
        val cover = covers?.optInt(0, 0)?.takeIf { it > 0 }
        return MediaItem(
            id = "openlibrary-book-${key.removePrefix("/").replace('/', '-')}",
            kind = MediaKind.BOOK,
            title = title,
            overview = description,
            posterUrl = cover?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
            added = true,
            source = "openlibrary",
            sourceId = key,
            catalogComplete = true,
            catalogUpdatedAt = System.currentTimeMillis(),
            progressUnit = "pages",
            genres = subjects.take(20).toSet(),
            externalUrl = "https://openlibrary.org$key"
        )
    }

    private fun strings(o: JSONObject, key: String): List<String> = buildList {
        val arr = o.optJSONArray(key)
        if (arr != null) for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    fun isbnSearch(isbn:String):IsbnSearchResult {val raw=JSONObject(get(BookSelectionRules.searchUrl(isbn)));@Suppress("UNCHECKED_CAST") return BookSelectionRules.parseSearch(value(raw,0) as Map<String,Any?>,isbn)}
    fun isbnEdition(c:IsbnCandidate):BookEdition {val id=BookEditionRules.key(c.editionId,"edition");val raw=JSONObject(get("https://openlibrary.org$id.json"));@Suppress("UNCHECKED_CAST") return BookSelectionRules.verifyEdition(value(raw,0) as Map<String,Any?>,c)}
    fun editions(workId:String,offset:Int=0):BookEditionPage {
        val work=BookEditionRules.key(workId);require(offset in 0..100000)
        val raw=JSONObject(get("https://openlibrary.org$work/editions.json?limit=30&offset=$offset"))
        @Suppress("UNCHECKED_CAST") return BookEditionRules.page(value(raw,0) as Map<String,Any?>,work,offset)
    }
    private fun value(v:Any?,depth:Int):Any? {
        require(depth<=24)
        return when(v){null,JSONObject.NULL->null;is JSONObject->{require(v.length()<=1000);val m=linkedMapOf<String,Any?>();val keys=v.keys();while(keys.hasNext()){val k=keys.next();m[k]=value(v.opt(k),depth+1)};m};is JSONArray->{require(v.length()<=10000);(0 until v.length()).map{value(v.get(it),depth+1)}};else->v}
    }
    companion object {
        private val gate=Any();private var nextAt=0L;@Volatile private var retryAt=0L
        private fun reserve()=synchronized(gate){val now=System.currentTimeMillis();check(now>=retryAt){"Délai Open Library en cours."};val wait=(nextAt-now).coerceAtLeast(0);require(wait<=20000){"Trop de lectures en attente."};nextAt=maxOf(nextAt,now)+1050;wait}
        private fun get(url:String):String {
            val u=URI(url);require(u.scheme=="https"&&u.host=="openlibrary.org"&&u.port==-1&&u.userInfo==null&&u.fragment==null)
            require(u.path=="/search.json"||Regex("/books/OL[1-9][0-9]{0,11}M\\.json").matches(u.path)||Regex("/works/OL[1-9][0-9]{0,11}W(?:\\.json|/editions\\.json)").matches(u.path))
            val wait=reserve();if(wait>0)Thread.sleep(wait);check(System.currentTimeMillis()>=retryAt){"Délai Open Library en cours."}
            val c=u.toURL().openConnection() as HttpURLConnection;c.requestMethod="GET";c.instanceFollowRedirects=false;c.connectTimeout=8000;c.readTimeout=20000;c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","FranchiseGuide/0.27 (personal open-source book discovery)")
            try{
                val code=c.responseCode
                if(code==429){val raw=c.getHeaderField("Retry-After");val now=System.currentTimeMillis();val delay=raw?.toLongOrNull()?.coerceIn(0,86400)?.times(1000)?:runCatching{ZonedDateTime.parse(raw,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now}.getOrDefault(60000);retryAt=now+delay.coerceIn(60000,86400000)}
                require(code in 200..299){"Open Library HTTP $code : relevé précédent conservé."};val max=2*1024*1024;require(c.contentLengthLong<0||c.contentLengthLong<=max){"Réponse Open Library trop grande."}
                val bytes=c.inputStream.use{input->val out=ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=max){"Réponse Open Library trop grande."};out.write(buffer,0,n)};out.toByteArray()}
                return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            }finally{c.disconnect()}
        }
    }
}
