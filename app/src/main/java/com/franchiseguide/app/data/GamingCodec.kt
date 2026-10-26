package com.franchiseguide.app.data

import com.franchiseguide.app.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Portable gaming extension v1. Whitelisted fields only; no credentials or access tokens. */
object GamingCodec {
    private fun obj(root:JSONObject,k:String):JSONObject? { if(!root.has(k)||root.isNull(k))return null;return root.optJSONObject(k)?:error("Objet $k invalide.") }
    private fun rows(root:JSONObject,k:String):List<JSONObject> { if(!root.has(k))return emptyList();val a=root.optJSONArray(k)?:error("Tableau $k invalide.");return (0 until a.length()).map { a.optJSONObject(it)?:error("Entrée $k invalide.") } }
    private fun str(o:JSONObject,k:String,default:String=""):String { if(!o.has(k)||o.isNull(k))return default;return o.opt(k) as? String?:error("Texte $k invalide.") }
    private fun bool(o:JSONObject,k:String):Boolean? { if(!o.has(k)||o.isNull(k))return null;return o.opt(k) as? Boolean?:error("Booléen $k invalide.") }
    private fun number(o:JSONObject,k:String):Double? { if(!o.has(k)||o.isNull(k))return null;val n=(o.opt(k) as? Number)?.toDouble()?:error("Nombre $k invalide.");require(n.isFinite());return n }
    private fun long(o:JSONObject,k:String):Long?=number(o,k)?.let { require(it>=0&&it<=8640000000000000.0&&it==it.toLong().toDouble());it.toLong() }
    private fun array(items:List<JSONObject>)=JSONArray().apply { items.forEach { put(it) } }
    private fun put(o:JSONObject,k:String,v:Any?){o.put(k,v?:JSONObject.NULL)}
    fun read(o:JSONObject?):GamingState {
        if(o==null)return GamingState()
        require(number(o,"version")==1.0) { "Version du module trophées inconnue." }
        val accounts=rows(o,"accounts").map { a->GamingAccount(id=str(a,"id"),provider=str(a,"provider"),externalId=str(a,"externalId"),label=str(a,"label"),autoSync=bool(a,"autoSync")==true,autoImport=bool(a,"autoImport")==true,lastLibrarySyncAt=long(a,"lastLibrarySyncAt"),avatarUrl=GamingRules.safeUrl(str(a,"avatarUrl")),username=str(a,"username").ifBlank { null }) }
        val sets=rows(o,"sets").map(::readSet)
        return GamingRules.validate(GamingState(accounts=accounts,sets=sets))
    }
    /** A portable file preserves identities and readings but never grants permission to contact accounts. */
    fun readPortableImport(o:JSONObject?):GamingState = read(o).let { g->g.copy(accounts=g.accounts.map { it.copy(autoSync=false,autoImport=false) }) }
    /** Optional account reference in the neutral achievement-file format, never a connected session. */
    fun readAccountReference(o:JSONObject,provider:String,id:String):GamingAccount? {
        val externalId=str(o,"accountExternalId").ifBlank { null }
        val username=str(o,"accountUsername").ifBlank { null }
        require(username==null||externalId!=null) { "Un pseudo doit accompagner l’identifiant du compte du relevé." }
        if(externalId==null)return null
        val account=GamingAccount(id,provider,externalId,externalId,username=username)
        GamingRules.validate(GamingState(accounts=listOf(account)))
        return account
    }
    fun readSteam(root:JSONObject):SteamGameMetadata? {
        val o=obj(root,"steam")?:return null
        val accounts=obj(o,"accounts")?:JSONObject()
        val map=accounts.keys().asSequence().associateWith { id->
            val a=accounts.optJSONObject(id)?:error("Compte Steam invalide.")
            SteamPlaytime(number(a,"playtimeMinutes")?.let { require(it==it.toLong().toDouble());it.toLong() },long(a,"lastPlayedAt"))
        }
        return SteamMetadataRules.validate(SteamGameMetadata(str(o,"appId"),map,bool(o,"hasStats")))
    }
    fun writeSteam(s:SteamGameMetadata?):Any {
        if(s==null)return JSONObject.NULL
        SteamMetadataRules.validate(s)
        return JSONObject().put("appId",s.appId).put("hasStats",s.hasStats?:JSONObject.NULL).put("accounts",JSONObject().apply {
            s.accounts.forEach { (id,value)->put(id,JSONObject().put("playtimeMinutes",value.playtimeMinutes?:JSONObject.NULL).put("lastPlayedAt",value.lastPlayedAt?:JSONObject.NULL)) }
        })
    }
    fun readSet(o:JSONObject):AchievementSet {
        require(o.has("groups")&&o.has("achievements")) { "Groupes et succès requis, même pour une liste vide." }
        val groups=rows(o,"groups").map { g->AchievementGroup(str(g,"id"),str(g,"name"),str(g,"kind","unknown")) }
        val achievements=rows(o,"achievements").map { a->
            val points=number(a,"points");require(points==null||(points==points.toInt().toDouble()&&points in 0.0..1000000.0))
            GameAchievement(id=str(a,"id"),title=str(a,"title"),description=str(a,"description"),groupId=str(a,"groupId","unknown"),grade=str(a,"grade","achievement"),iconUrl=GamingRules.safeUrl(str(a,"iconUrl")),lockedIconUrl=GamingRules.safeUrl(str(a,"lockedIconUrl")),hidden=bool(a,"hidden")==true,points=points?.toInt(),
                rarity=obj(a,"rarity")?.let { r->AchievementRarity(number(r,"percent")?:error("Rareté sans pourcentage."),str(r,"source")) },earned=bool(a,"earned"),earnedAt=long(a,"earnedAt"),hardcoreEarned=bool(a,"hardcoreEarned"),hardcoreEarnedAt=long(a,"hardcoreEarnedAt"),progressCurrent=number(a,"progressCurrent"),progressTarget=number(a,"progressTarget"),
                localDone=bool(a,"localDone")==true,pinned=bool(a,"pinned")==true,note=str(a,"note"),online=bool(a,"online"),missable=bool(a,"missable"),unobtainable=bool(a,"unobtainable"),guideUrl=GamingRules.safeUrl(str(a,"guideUrl")))
        }
        return AchievementSet(id=str(o,"id"),mediaId=str(o,"mediaId"),provider=str(o,"provider"),platform=str(o,"platform"),externalGameId=str(o,"externalGameId"),title=str(o,"title"),accountId=str(o,"accountId").ifBlank { null },edition=str(o,"edition"),groups=groups,achievements=achievements,completeCatalog=bool(o,"completeCatalog")==true,source=str(o,"source","file-import"),observedAt=long(o,"observedAt"),lastSyncAt=long(o,"lastSyncAt"),syncError=str(o,"syncError"),requiresSteamSignIn=bool(o,"requiresSteamSignIn")==true,catalogStatus=str(o,"catalogStatus","unknown"))
    }
    fun write(g:GamingState):JSONObject {
        GamingRules.validate(g)
        return JSONObject().put("version",1).put("accounts",array(g.accounts.map { a->JSONObject().put("id",a.id).put("provider",a.provider).put("externalId",a.externalId).put("label",a.label).put("autoSync",a.autoSync).put("autoImport",a.autoImport).put("lastLibrarySyncAt",a.lastLibrarySyncAt?:JSONObject.NULL).put("avatarUrl",a.avatarUrl?:JSONObject.NULL).put("username",a.username?:JSONObject.NULL) })).put("sets",array(g.sets.map(::writeSet)))
    }
    fun writeSet(s:AchievementSet):JSONObject=JSONObject().apply {
        put("id",s.id);put("mediaId",s.mediaId);put("provider",s.provider);put("platform",s.platform);put("externalGameId",s.externalGameId);put("title",s.title);put("accountId",s.accountId?:JSONObject.NULL);put("edition",s.edition);put("source",s.source);put("completeCatalog",s.completeCatalog);put("observedAt",s.observedAt?:JSONObject.NULL);put("lastSyncAt",s.lastSyncAt?:JSONObject.NULL);put("syncError",s.syncError);put("requiresSteamSignIn",s.requiresSteamSignIn);put("catalogStatus",s.catalogStatus)
        put("groups",array(s.groups.map { JSONObject().put("id",it.id).put("name",it.name).put("kind",it.kind) }))
        put("achievements",array(s.achievements.map { a->JSONObject().apply {
            put("id",a.id);put("title",a.title);put("description",a.description);put("groupId",a.groupId);put("grade",a.grade);put("hidden",a.hidden);put("localDone",a.localDone);put("pinned",a.pinned);put("note",a.note)
            GamingCodec.put(this,"iconUrl",a.iconUrl);GamingCodec.put(this,"lockedIconUrl",a.lockedIconUrl);GamingCodec.put(this,"points",a.points);GamingCodec.put(this,"earned",a.earned);GamingCodec.put(this,"earnedAt",a.earnedAt);GamingCodec.put(this,"hardcoreEarned",a.hardcoreEarned);GamingCodec.put(this,"hardcoreEarnedAt",a.hardcoreEarnedAt);GamingCodec.put(this,"progressCurrent",a.progressCurrent);GamingCodec.put(this,"progressTarget",a.progressTarget);GamingCodec.put(this,"online",a.online);GamingCodec.put(this,"missable",a.missable);GamingCodec.put(this,"unobtainable",a.unobtainable);GamingCodec.put(this,"guideUrl",a.guideUrl)
            GamingCodec.put(this,"rarity",a.rarity?.let { JSONObject().put("percent",it.percent).put("source",it.source) })
        } }))
    }
}
