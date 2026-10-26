package com.franchiseguide.app.model

data class ExternalBackupRecord(val version:Int=1,val proposedAt:Long?=null,val proposedKind:String?=null,val fileName:String?=null,val verifiedAt:Long?=null,val snoozedUntil:Long?=null)
data class ExternalBackupStatus(val due:Boolean,val pending:Boolean,val snoozed:Boolean,val ageDays:Int?)
object BackupSafetyRules {
    const val DAY=86_400_000L
    fun validate(r:ExternalBackupRecord):ExternalBackupRecord {require(r.version==1);r.proposedAt?.let{require(it>0)};r.verifiedAt?.let{require(it>0)};r.snoozedUntil?.let{require(it>0)};r.proposedKind?.let{require(it in setOf("json","protected") )};r.fileName?.let{require(it.isNotBlank()&&it.length<=300&&it.none{c->c.code<32||c.code==127})};return r}
    fun proposed(r:ExternalBackupRecord,kind:String,name:String,now:Long)=validate(validate(r).copy(proposedAt=now,proposedKind=kind,fileName=name))
    fun verified(r:ExternalBackupRecord,now:Long)=validate(validate(r).copy(verifiedAt=now,snoozedUntil=null))
    fun snooze(r:ExternalBackupRecord,days:Int,now:Long):ExternalBackupRecord {require(days in 1..90);return validate(validate(r).copy(snoozedUntil=now+days*DAY))}
    fun status(r:ExternalBackupRecord,now:Long=System.currentTimeMillis(),thresholdDays:Int=14):ExternalBackupStatus {val v=validate(r);require(thresholdDays in 1..365);val age=v.verifiedAt?.let{((now-it).coerceAtLeast(0)/DAY).toInt()};val pending=v.proposedAt!=null&&(v.verifiedAt==null||v.proposedAt>v.verifiedAt);val snoozed=v.snoozedUntil?.let{now<it}?:false;return ExternalBackupStatus(!snoozed&&(pending||age==null||age>=thresholdDays),pending,snoozed,age)}
}
