package com.franchiseguide.app.model

import java.time.LocalDate

data class TodaySnapshot(val day:LocalDate,val agenda:List<ActivityRules.Event>,val agendaTotal:Int,val next:List<LibraryRules.NextItem>,val nextTotal:Int,val uncertain:Int,val held:Int,val checkIn:TraktCheckInSession?,val backup:ExternalBackupStatus?)
object TodayRules {
    fun build(media:List<MediaItem>,episodes:List<Episode>,settings:AppSettings,queue:List<PendingChange>,checkIn:TraktCheckInSession?,backup:ExternalBackupStatus?,day:LocalDate=LocalDate.now(),limit:Int=12):TodaySnapshot {
        require(limit in 1..100);val agenda=ActivityRules.agenda(media,episodes,ActivityRules.Query(from=day,to=day,includeHidden=settings.includeHiddenInCalendar,includeSpecials=settings.includeSpecials,hideSpoilers=settings.hideEpisodeSpoilers));val next=LibraryRules.nextUp(media,episodes,LibraryRules.Query(section="ready",today=day,includeSpecials=settings.includeSpecials,hideSpoilers=settings.hideEpisodeSpoilers)).records
        return TodaySnapshot(day,agenda.events.take(limit),agenda.events.size,next.take(limit),next.size,queue.count{it.uncertain},queue.count{it.heldReason!=null},checkIn,backup)
    }
}
