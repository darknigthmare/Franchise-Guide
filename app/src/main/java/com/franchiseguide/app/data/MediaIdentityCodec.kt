package com.franchiseguide.app.data

import com.franchiseguide.app.model.MediaItem
import com.franchiseguide.app.model.BookSelectionRules
import java.math.BigDecimal
import org.json.JSONObject

/** Portable catalogue aliases only. No account, session or provider authentication is created here. */
object MediaIdentityCodec {
    private const val UINT32_MAX=4294967295L
    private const val JS_SAFE_INTEGER_MAX=9007199254740991L
    private val decimalIdentity=Regex("[1-9][0-9]{0,15}")
    private val openLibraryWork=Regex("/works/OL[1-9][0-9]*W")

    private fun value(o:JSONObject,key:String):Any? = if(!o.has(key)||o.isNull(key))null else o.opt(key)

    private fun numeric(raw:Any?,key:String,max:Long):String? {
        if(raw==null)return null
        val text=when(raw) {
            is String -> raw
            // Older Web versions can leave an exact JSON number in a provider alias.
            is Number -> runCatching {
                val encoded=raw.toString()
                require(encoded.length<=64)
                val number=BigDecimal(encoded)
                // Check the range before expanding exponents such as 1e1000000000.
                require(number>=BigDecimal.ONE&&number<=BigDecimal.valueOf(max))
                number.toBigIntegerExact().toString()
            }.getOrElse { error("Identifiant $key invalide.") }
            else -> error("Identifiant $key invalide.")
        }
        require(decimalIdentity.matches(text)&&(text.toLongOrNull()?:Long.MAX_VALUE)<=max) { "Identifiant $key invalide." }
        return text
    }

    private fun work(raw:Any?):String? {
        if(raw==null)return null
        require(raw is String&&raw.length<=160&&openLibraryWork.matches(raw)) { "Identifiant Open Library invalide : chemin /works/OL…W requis." }
        return raw
    }

    fun readInto(o:JSONObject,media:MediaItem):MediaItem = BookSelectionRules.validateMedia(media.copy(
        selectedEdition=BookSelectionCodec.read(o.opt("selectedEdition")),
        rawgId=numeric(value(o,"rawgId"),"RAWG",JS_SAFE_INTEGER_MAX),
        openLibraryId=work(value(o,"openLibraryId")),
        steamAppId=numeric(value(o,"steamAppId"),"Steam",UINT32_MAX),
        retroGameId=numeric(value(o,"retroGameId"),"RetroAchievements",UINT32_MAX)
    ))

    fun writeInto(o:JSONObject,media:MediaItem):JSONObject = o.apply {
        BookSelectionRules.validateMedia(media);put("selectedEdition",BookSelectionCodec.write(media.selectedEdition))
        put("rawgId",numeric(media.rawgId,"RAWG",JS_SAFE_INTEGER_MAX)?:JSONObject.NULL)
        put("openLibraryId",work(media.openLibraryId)?:JSONObject.NULL)
        put("steamAppId",numeric(media.steamAppId,"Steam",UINT32_MAX)?:JSONObject.NULL)
        put("retroGameId",numeric(media.retroGameId,"RetroAchievements",UINT32_MAX)?:JSONObject.NULL)
    }

    /** A catalogue refresh often omits aliases originating from a different provider. */
    fun preserveAliases(previous:MediaItem,fresh:MediaItem):MediaItem = fresh.copy(
        selectedEdition=previous.selectedEdition?:fresh.selectedEdition,
        rawgId=fresh.rawgId?:previous.rawgId,
        openLibraryId=fresh.openLibraryId?:previous.openLibraryId,
        steamAppId=fresh.steamAppId?:previous.steamAppId,
        retroGameId=fresh.retroGameId?:previous.retroGameId
    )
}
