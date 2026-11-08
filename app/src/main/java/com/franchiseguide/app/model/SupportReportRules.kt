package com.franchiseguide.app.model

import org.json.JSONObject

/** Whitelist, not a regex over arbitrary private content. Never accept raw records as output fields. */
object SupportReportRules {
    fun build(version:String,media:Int,episodes:Int,queued:Int,levels:List<String>,recovery:Boolean,credentialsProtected:Boolean):JSONObject {
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Version de rapport invalide." }
        require(listOf(media,episodes,queued).all { it >= 0 })
        return JSONObject().put("format","franchiseguide-support-v1").put("version",version).put("platform","android")
            .put("mediaCount",media).put("episodeCount",episodes).put("queuedCount",queued)
            .put("errorCount",levels.count { it=="error" }).put("warningCount",levels.count { it=="warn" })
            .put("recoveryOnly",recovery).put("credentialVaultAvailable",credentialsProtected)
            .put("rawMessagesIncluded",false).put("libraryIncluded",false).put("accountsIncluded",false)
    }
}
