package com.franchiseguide.app.model

/** Transported metadata only. An imported file never authenticates a Steam account. */
data class SteamPlaytime(val playtimeMinutes:Long?=null,val lastPlayedAt:Long?=null)
data class SteamGameMetadata(val appId:String,val accounts:Map<String,SteamPlaytime> = emptyMap(),val hasStats:Boolean?=null)
object SteamMetadataRules {
    fun validate(value:SteamGameMetadata):SteamGameMetadata {
        require(value.appId.matches(Regex("[1-9][0-9]{0,9}"))&&(value.appId.toLongOrNull()?:Long.MAX_VALUE)<=4294967295L) { "App ID Steam invalide." }
        require(value.accounts.size<=100)
        value.accounts.forEach { (id,playtime)->
            require(id.matches(Regex("[0-9]{17}"))) { "SteamID64 invalide." }
            require(playtime.playtimeMinutes==null||playtime.playtimeMinutes in 0..10000000000L)
            require(playtime.lastPlayedAt==null||playtime.lastPlayedAt in 0..8640000000000000L)
        }
        return value
    }
}
