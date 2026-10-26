package com.franchiseguide.app.model

import java.net.URI

/** externalId is the provider identity; username is display data, never an authentication token. */
data class GamingAccount(val id:String,val provider:String,val externalId:String,val label:String,val autoSync:Boolean=false,val autoImport:Boolean=false,val lastLibrarySyncAt:Long?=null,val avatarUrl:String?=null,val username:String?=null)
data class AchievementGroup(val id:String,val name:String,val kind:String="unknown")
data class AchievementRarity(val percent:Double,val source:String)
data class GameAchievement(
    val id:String,val title:String,val description:String="",val groupId:String="unknown",val grade:String="achievement",
    val iconUrl:String?=null,val lockedIconUrl:String?=null,val hidden:Boolean=false,val points:Int?=null,val rarity:AchievementRarity?=null,
    val earned:Boolean?=null,val earnedAt:Long?=null,val hardcoreEarned:Boolean?=null,val hardcoreEarnedAt:Long?=null,
    val progressCurrent:Double?=null,val progressTarget:Double?=null,val localDone:Boolean=false,val pinned:Boolean=false,
    val note:String="",val online:Boolean?=null,val missable:Boolean?=null,val unobtainable:Boolean?=null,val guideUrl:String?=null
)
data class AchievementSet(
    val id:String,val mediaId:String,val provider:String,val platform:String,val externalGameId:String,val title:String,
    val accountId:String?=null,val edition:String="",val groups:List<AchievementGroup> = emptyList(),val achievements:List<GameAchievement> = emptyList(),
    val completeCatalog:Boolean=false,val source:String="file-import",val observedAt:Long?=null,val lastSyncAt:Long?=null,val syncError:String="",val requiresSteamSignIn:Boolean=false,val catalogStatus:String="unknown"
)
data class GamingState(val version:Int=1,val accounts:List<GamingAccount> = emptyList(),val sets:List<AchievementSet> = emptyList())
data class AchievementProgress(val total:Int,val earned:Int,val unknown:Int,val localDone:Int,val platinum:Boolean,val complete:Boolean) {
    val percent:Int? get()=if(total>0)kotlin.math.round(earned*100.0/total).toInt() else null
}
object GamingRules {
    val providers=setOf("steam","psn","xbox","retro","epic","gog","manual")
    val grades=setOf("bronze","silver","gold","platinum","achievement","mythic")
    private val retroUsername=Regex("[A-Za-z0-9_-]{2,64}")
    private val retroStableId=Regex("[0-7][0-9A-HJKMNP-TV-Z]{25}",RegexOption.IGNORE_CASE)
    fun safeUrl(raw:String?):String?=runCatching { URI(raw?:return null).takeIf { it.scheme=="https"&&it.host!=null&&it.userInfo==null&&it.toString().length<=2000 }?.toString() }.getOrNull()
    fun accountUrl(account:GamingAccount):String? = when(account.provider) {
        "steam" -> account.externalId.takeIf { it.matches(Regex("[0-9]{17}")) }?.let { "https://steamcommunity.com/profiles/$it" }
        "psn" -> account.externalId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{3,32}")) }?.let { "https://psnprofiles.com/$it" }
        "retro" -> (account.username?:account.externalId.takeUnless { retroStableId.matches(it) })?.takeIf { retroUsername.matches(it) }?.let { "https://retroachievements.org/user/$it" }
        else -> null
    }
    private fun nonblank(s:String,max:Int=160){require(s.isNotBlank()&&s.length<=max&&!s.any { it.code in 0..8||it.code in 11..12||it.code in 14..31 }) { "Texte de trophée invalide." }}
    private fun optional(s:String,max:Int){require(s.length<=max&&!s.any { it.code in 0..8||it.code in 11..12||it.code in 14..31 }) { "Texte trop long ou invalide." }}
    private fun timestamp(n:Long?){require(n==null||n in 0..8640000000000000L) { "Date de trophée invalide." }}
    fun identity(s:AchievementSet)=listOf(s.mediaId,s.provider,s.platform,s.accountId.orEmpty(),s.externalGameId,s.edition)
    fun validate(g:GamingState,gameIds:Set<String>?=null):GamingState {
        require(g.version==1&&g.accounts.size<=100&&g.sets.size<=2000) { "Module trophées non pris en charge." }
        require(g.accounts.map { it.id }.toSet().size==g.accounts.size) { "Comptes en double." }
        require(g.accounts.map { it.provider to if(it.provider=="psn")it.externalId.lowercase() else it.externalId }.toSet().size==g.accounts.size) { "Profils en double." }
        g.accounts.forEach { a->
            timestamp(a.lastLibrarySyncAt);require(!a.autoImport||a.provider in setOf("steam","retro"));
            nonblank(a.id);nonblank(a.externalId);nonblank(a.label,120);require(a.provider in providers)
            a.username?.let { nonblank(it,64);if(a.provider=="retro")require(retroUsername.matches(it)) { "Pseudo RetroAchievements invalide." } }
            if(a.provider=="steam")require(a.externalId.matches(Regex("[0-9]{17}"))) { "SteamID64 de 17 chiffres requis." }
            if(a.provider=="psn")require(a.externalId.matches(Regex("[A-Za-z0-9_-]{3,32}")))
            if(a.provider=="retro")require(retroUsername.matches(a.externalId))
        }
        require(g.sets.map { it.id }.toSet().size==g.sets.size&&g.sets.map(::identity).toSet().size==g.sets.size) { "Listes en double." }
        require(g.sets.sumOf { it.achievements.size.toLong() }<=100000) { "Plus de 100 000 succès." }
        val accounts=g.accounts.associateBy { it.id }
        g.sets.forEach { s->
            require(!s.requiresSteamSignIn||s.provider=="steam");require(s.catalogStatus in setOf("unknown","none-exposed"));
            nonblank(s.id);nonblank(s.mediaId);nonblank(s.externalGameId);nonblank(s.title,250);nonblank(s.platform,80);optional(s.edition,120);optional(s.syncError,300)
            require(s.provider in providers);require(gameIds==null||s.mediaId in gameIds) { "Liste sans jeu." }
            require(s.accountId==null||accounts[s.accountId]?.provider==s.provider) { "Compte incompatible." }
            if(s.provider in setOf("steam","retro"))require(s.externalGameId.matches(Regex("[1-9][0-9]{0,9}"))&&(s.externalGameId.toLongOrNull()?:Long.MAX_VALUE)<=4294967295L)
            require(s.groups.size<=300&&s.groups.map { it.id }.toSet().size==s.groups.size)
            s.groups.forEach { nonblank(it.id);nonblank(it.name,180);require(it.kind in setOf("base","dlc","unknown")) }
            require(s.achievements.size<=5000&&s.achievements.map { it.id }.toSet().size==s.achievements.size) { "Succès en double ou liste trop grande." }
            val groups=s.groups.map { it.id }.toSet()
            s.achievements.forEach { a->
                nonblank(a.id);nonblank(a.title,300);optional(a.description,5000);optional(a.note,3000);require(a.groupId in groups&&a.grade in grades)
                timestamp(a.earnedAt);timestamp(a.hardcoreEarnedAt);require(a.earnedAt==null||a.earned==true);require(a.hardcoreEarnedAt==null||a.hardcoreEarned==true)
                require(a.points==null||a.points in 0..1000000)
                a.rarity?.let { require(it.percent.isFinite()&&it.percent in 0.0..100.0);nonblank(it.source,100) }
                listOfNotNull(a.progressCurrent,a.progressTarget).forEach { require(it.isFinite()&&it in 0.0..100000000.0) }
                if(a.progressCurrent!=null&&a.progressTarget!=null)require(a.progressTarget>0&&a.progressCurrent<=a.progressTarget)
                if(s.source=="manual")require(a.earned!=true&&a.hardcoreEarned!=true) { "Une case personnelle ne débloque pas de trophée officiel, y compris en Hardcore." }
            }
            require(s.source in setOf("manual","file-import","steam-api","retro-api"));timestamp(s.observedAt);timestamp(s.lastSyncAt)
        }
        return g
    }
    fun earnedState(achievement:GameAchievement,hardcore:Boolean=false):Boolean? = if(hardcore)achievement.hardcoreEarned else achievement.earned
    fun progress(s:AchievementSet,groupId:String?=null,hardcore:Boolean=false):AchievementProgress {
        val rows=if(groupId==null)s.achievements else s.achievements.filter { it.groupId==groupId }
        val earned=rows.count { earnedState(it,hardcore)==true }
        return AchievementProgress(rows.size,earned,rows.count { earnedState(it,hardcore)==null },rows.count { it.localDone },!hardcore&&rows.any { it.grade=="platinum"&&it.earned==true },rows.isNotEmpty()&&s.completeCatalog&&earned==rows.size)
    }
    fun merge(old:AchievementSet,fresh:AchievementSet):AchievementSet {
        require(identity(old)==identity(fresh)) { "Jeu, compte ou édition différents." }
        val newIds=fresh.achievements.map { it.id }.toSet();require(old.achievements.all { it.id in newIds }) { "Relevé incomplet : les anciens succès sont conservés." }
        val map=old.achievements.associateBy { it.id }
        return fresh.copy(id=old.id,achievements=fresh.achievements.map { a->map[a.id]?.let { p->a.copy(localDone=p.localDone,pinned=p.pinned,note=p.note,guideUrl=p.guideUrl?:a.guideUrl,online=a.online?:p.online,missable=a.missable?:p.missable,unobtainable=a.unobtainable?:p.unobtainable) }?:a })
    }
}
