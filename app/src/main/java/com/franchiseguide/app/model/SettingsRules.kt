package com.franchiseguide.app.model
import java.util.Locale
/** Pure validation for edits; no truncation/clamping and no secret echoed in failures. */
object SettingsRules {
 fun number(raw:String,label:String,min:Int,max:Int):Int {val v=raw.trim();require(v.matches(Regex("[0-9]+"))){"$label : entier attendu entre $min et $max."};return v.toLongOrNull()?.takeIf{it in min.toLong()..max.toLong()}?.toInt()?:error("$label : valeur attendue entre $min et $max.")}
 fun secret(raw:String,label:String):String {val v=raw.trim();require(v.length<=8192&&v.none{it.code<=32||it.code==127}){"$label : retire espaces internes ou caractères de contrôle."};return v}
 fun normalize(s:AppSettings):AppSettings {
  val m=Regex("([A-Za-z]{2})(?:-([A-Za-z]{2}))?").matchEntire(s.language.trim().replace('_','-'))?:error("Langue invalide : fr-FR, en-US ou fr par exemple.")
  val language=m.groupValues[1].lowercase(Locale.ROOT)+m.groupValues[2].takeIf{it.isNotBlank()}?.let{"-"+it.uppercase(Locale.ROOT)}.orEmpty()
  val region=s.region.trim().uppercase(Locale.ROOT);require(region.matches(Regex("[A-Z]{2}"))){"Pays invalide : deux lettres attendues."}
  require(s.recentDays in 0..36500&&s.upcomingDays in 0..36500){"Agenda : de 0 à 36500 jours."};require(s.traktSyncIntervalHours in 1..168){"Trakt : de 1 à 168 heures."};require(s.autoBackupMinutes in 1..1440){"Sauvegardes : de 1 à 1440 minutes."};require(s.detailJingleVolume in 0..100){"Volume : de 0 à 100."};require(s.notificationHour in 0..23){"Heure : de 0 à 23."}
  val id=secret(s.traktClientId,"Client Trakt");val token=secret(s.traktClientSecret,"Secret Trakt");require(id.isNotBlank()||token.isBlank()){"Un secret Trakt nécessite son Client ID."}
  return s.copy(language=language,region=region,tmdbApiKey=secret(s.tmdbApiKey,"Accès TMDb"),rawgApiKey=secret(s.rawgApiKey,"Accès RAWG"),traktClientId=id,traktClientSecret=token)
 }
 fun privateChanged(a:AppSettings,b:AppSettings)=a.tmdbApiKey!=b.tmdbApiKey||a.rawgApiKey!=b.rawgApiKey||traktChanged(a,b)
 fun traktChanged(a:AppSettings,b:AppSettings)=a.traktClientId!=b.traktClientId||a.traktClientSecret!=b.traktClientSecret
 fun validReference(s:String)=s.matches(Regex("slot-[A-Za-z0-9-]{1,90}"))
}
