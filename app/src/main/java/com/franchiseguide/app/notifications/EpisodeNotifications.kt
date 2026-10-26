package com.franchiseguide.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.franchiseguide.app.MainActivity
import com.franchiseguide.app.R
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.model.ActivityRules
import java.time.LocalDateTime
import java.time.ZoneId

object EpisodeNotificationScheduler {
    private const val PREFS = "franchise_guide_notification_scheduler"
    private const val IDS = "scheduled_ids"

    fun schedule(context: Context, repo: AppRepository) {
        if(repo.isRecoveryOnly()) return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(IDS, emptySet()).orEmpty().forEach { key ->
            existingPending(context, key)?.let(alarm::cancel)
            // Cancel the pre-V0.16 hash-only request as a migration precaution too.
            PendingIntent.getBroadcast(context,key.hashCode(),Intent(context,EpisodeNotificationReceiver::class.java),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let(alarm::cancel)
        }
        if (!repo.settings.value.notificationsEnabled) {
            prefs.edit().putStringSet(IDS, emptySet()).apply()
            return
        }
        val now = System.currentTimeMillis()
        val hour = repo.settings.value.notificationHour.coerceIn(0, 23)
        val ids = linkedSetOf<String>()
        repo.upcomingEpisodes().asSequence()
            .filter { !it.watched && !it.skipped }
            .filter { ep -> repo.media.firstOrNull { it.id == ep.showId }?.notify == true }
            .take(96)
            .forEach { ep ->
                val date = ep.airDate ?: return@forEach
                val trigger = LocalDateTime.of(date, java.time.LocalTime.of(hour, 0))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (trigger <= now) return@forEach
                val show = repo.media.firstOrNull { it.id == ep.showId } ?: return@forEach
                val pi = pending(context, ep.id, show.title, "${ep.code} · ${ep.title}", PendingIntent.FLAG_UPDATE_CURRENT)
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                ids += ep.id
            }
        prefs.edit().putStringSet(IDS, ids).apply()
    }

    private fun existingPending(context: Context, episodeId: String): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            episodeId.hashCode(),
            Intent(context, EpisodeNotificationReceiver::class.java).setAction(ActivityRules.notificationAction(episodeId)),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    private fun pending(context: Context, episodeId: String, showTitle: String?, episodeText: String?, flag: Int): PendingIntent {
        val intent = Intent(context, EpisodeNotificationReceiver::class.java).setAction(ActivityRules.notificationAction(episodeId))
            .putExtra("episode_id", episodeId)
            .putExtra("show_title", showTitle)
            .putExtra("episode_text", episodeText)
        return PendingIntent.getBroadcast(
            context,
            episodeId.hashCode(),
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class EpisodeNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val episodeId = intent.getStringExtra("episode_id") ?: return
        val repo = AppRepository.getInstance(context.applicationContext)
        if(repo.isRecoveryOnly()) return
        val episode = repo.episodes.firstOrNull { it.id == episodeId } ?: return
        val show = repo.media.firstOrNull { it.id == episode.showId } ?: return
        if (!repo.settings.value.notificationsEnabled || !show.added || !show.notify || episode.watched || episode.skipped || show.hidden&&!repo.settings.value.includeHiddenInCalendar || episode.season==0&&!repo.settings.value.includeSpecials) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Épisodes à venir", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val openApp = PendingIntent.getActivity(
            context,
            episodeId.hashCode(),
            Intent(context, MainActivity::class.java).setAction(ActivityRules.notificationAction(episodeId)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(show.title)
            .setContentText(if(repo.settings.value.hideEpisodeSpoilers) "${episode.code} · Épisode non vu" else "${episode.code} · ${episode.title}")
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        manager.notify(episodeId, 0, notification)
    }

    companion object { private const val CHANNEL_ID = "franchiseguide_episodes" }
}
