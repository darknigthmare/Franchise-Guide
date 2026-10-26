package com.franchiseguide.app.model

/** A current read/play-through is independent of its historical completed runs. */
object ProgressRules {
    val states = setOf("planned","active","paused","abandoned","completed")
    fun applicable(m:MediaItem)=m.kind in setOf(MediaKind.GAME,MediaKind.BOOK)
    fun status(m:MediaItem)=m.trackingStatus?.takeIf { it in states } ?: if(m.watched)"completed" else if(m.started||m.progressCurrent>0)"active" else "planned"
    fun label(m:MediaItem)=when(status(m)){"active"->if(m.kind==MediaKind.BOOK)"Lecture en cours" else "En cours";"paused"->"En pause";"abandoned"->"Abandonné";"completed"->if(m.kind==MediaKind.BOOK)"Parcours lu" else "Parcours terminé";else->if(m.kind==MediaKind.BOOK)"À lire" else "À jouer"}
    fun parse(value:String,label:String):Int {val s=value.trim();if(s.isEmpty())return 0;require(s.all { it in '0'..'9' }) { "$label : entier positif ou nul attendu." };return s.toIntOrNull()?.takeIf { it in 0..10000000 }?:error("$label : valeur trop élevée.")}
    fun update(m:MediaItem,current:Int,total:Int,unit:String=m.progressUnit,state:String="auto",platform:String?=m.personalPlatform,format:String?=m.personalFormat,minutes:Int?=m.playtimeMinutes,now:Long=System.currentTimeMillis()):MediaItem {
        require(applicable(m));require(current in 0..10000000&&total in 0..10000000&&(minutes==null||minutes in 0..10000000))
        require(total==0||current<=total) { "La progression dépasse le total. Utilise 0 pour un total inconnu." }
        val next=if(state=="auto")when{total>0&&current==total->"completed";current>0||status(m)=="completed"->"active";else->status(m)}else state
        require(next in states) { "État de parcours invalide." }
        var out=m.copy(progressCurrent=current,progressTotal=total,progressUnit=unit.trim().take(30),trackingStatus=next,started=next!="planned"||current>0,personalPlatform=platform?.trim()?.take(120)?.ifBlank { null },personalFormat=format?.trim()?.take(120)?.ifBlank { null },playtimeMinutes=minutes)
        if(next=="completed"){
            if(status(m)!="completed"||!m.watched)out=TrackingRules.completed(out,true,now,replay=m.watched)
            out=out.copy(trackingStatus="completed",started=true,progressCurrent=if(total>0)total else current)
        }
        return out
    }
    fun finish(m:MediaItem,now:Long=System.currentTimeMillis(),replay:Boolean=false):MediaItem {
        val out=TrackingRules.completed(m,true,now,replay||applicable(m)&&m.watched&&status(m)!="completed")
        return if(applicable(m))out.copy(trackingStatus="completed",progressCurrent=if(m.progressTotal>0)m.progressTotal else m.progressCurrent)else out
    }
    fun restart(m:MediaItem):MediaItem {require(applicable(m));return m.copy(progressCurrent=0,started=true,trackingStatus="active")}
    fun clear(m:MediaItem)=TrackingRules.completed(m,false).copy(progressCurrent=0,started=false,trackingStatus=if(applicable(m))"planned" else m.trackingStatus)
}
