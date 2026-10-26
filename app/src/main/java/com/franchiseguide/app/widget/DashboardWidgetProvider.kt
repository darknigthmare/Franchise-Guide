package com.franchiseguide.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.franchiseguide.app.MainActivity
import com.franchiseguide.app.R
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.notifications.EpisodeNotificationScheduler

class DashboardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val repo = AppRepository.getInstance(context.applicationContext)
        if(repo.isRecoveryOnly()) return
        val today=repo.todaySnapshot(3)
        val activeCheckIn=today.checkIn
        val next = repo.upcomingEpisodes().firstOrNull { !it.watched && !it.skipped }
        val show = next?.let { e -> repo.media.firstOrNull { it.id == e.showId } }
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_dashboard)
            views.setTextViewText(R.id.widget_title, "FranchiseGuide")
            views.setTextViewText(R.id.widget_stats, "${repo.shows().size} séries · ${repo.movies().size} films · ${repo.games().size} jeux · ${repo.books().size} livres")
            views.setTextViewText(R.id.widget_next, when {
                activeCheckIn!=null -> "Check-in : ${activeCheckIn.title}${activeCheckIn.code?.let{" · $it"}.orEmpty()} · ${activeCheckIn.status}"
                today.backup?.due==true -> "Sauvegarde externe à vérifier · ${today.nextTotal} titre(s) prêts"
                next != null && show != null -> "À venir : ${show.title} · ${next.code} ${next.title}"
                else -> "${today.nextTotal} titre(s) prêts · aucun épisode à venir"
            })
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            if (next != null) {
                views.setViewVisibility(R.id.widget_watch, View.VISIBLE)
                val watch = PendingIntent.getBroadcast(
                    context,
                    next.id.hashCode(),
                    Intent(context, DashboardWidgetProvider::class.java).setAction(ACTION_WATCH_NEXT).putExtra(EXTRA_EPISODE_ID, next.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_watch, watch)
            } else {
                views.setViewVisibility(R.id.widget_watch, View.GONE)
            }
            manager.updateAppWidget(id, views)
        }
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WATCH_NEXT) {
            val episodeId = intent.getStringExtra(EXTRA_EPISODE_ID)
            if (!episodeId.isNullOrBlank()) {
                val repo = AppRepository.getInstance(context.applicationContext)
                if(repo.isRecoveryOnly()) return
                repo.markEpisodeWatched(episodeId, true)
                EpisodeNotificationScheduler.schedule(context.applicationContext, repo)
                refresh(context.applicationContext)
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_WATCH_NEXT = "com.franchiseguide.app.action.WATCH_NEXT"
        private const val EXTRA_EPISODE_ID = "episode_id"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DashboardWidgetProvider::class.java))
            if (ids.isNotEmpty()) DashboardWidgetProvider().onUpdate(context, manager, ids)
        }
    }

}
