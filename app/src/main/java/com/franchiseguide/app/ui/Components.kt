package com.franchiseguide.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.franchiseguide.app.model.*
import java.util.Locale

fun mediaKindIcon(kind: MediaKind) = when (kind) {
    MediaKind.SHOW -> Icons.Default.Tv
    MediaKind.MOVIE -> Icons.Default.Movie
    MediaKind.GAME -> Icons.Default.SportsEsports
    MediaKind.BOOK -> Icons.Default.MenuBook
}

fun mediaKindLabel(kind: MediaKind): String = when (kind) {
    MediaKind.SHOW -> "Série"
    MediaKind.MOVIE -> "Film"
    MediaKind.GAME -> "Jeu vidéo"
    MediaKind.BOOK -> "Livre"
}

fun showStatusLabel(status: ShowStatus, raw: String? = null): String = when (status) {
    ShowStatus.CONTINUING -> "Continuing"
    ShowStatus.UPCOMING -> "Upcoming"
    ShowStatus.IN_PRODUCTION -> "In production"
    ShowStatus.PILOT -> "Pilot"
    ShowStatus.ENDED -> "Ended"
    ShowStatus.CANCELED -> "Canceled"
    ShowStatus.UNKNOWN -> raw?.takeIf { it.isNotBlank() } ?: "Unknown"
}

@Composable
fun MediaPoster(item: MediaItem, modifier: Modifier = Modifier) {
    val imageModifier = modifier.width(76.dp).height(112.dp).clip(RoundedCornerShape(10.dp))
    var failed by remember(item.posterUrl) { mutableStateOf(false) }
    if (!item.posterUrl.isNullOrBlank() && !failed) {
        AsyncImage(model = item.posterUrl, contentDescription = item.title, modifier = imageModifier, contentScale = ContentScale.Crop, onError = { failed = true })
    } else {
        Box(
            modifier = imageModifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))),
            contentAlignment = Alignment.Center
        ) { Icon(mediaKindIcon(item.kind), null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
    }
}

@Composable
fun MediaBackdrop(item: MediaItem, modifier: Modifier = Modifier) {
    val m = modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
    var failed by remember(item.backdropUrl) { mutableStateOf(false) }
    if (!item.backdropUrl.isNullOrBlank() && !failed) {
        Box(m) {
            AsyncImage(model = item.backdropUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = .92f)))))
        }
    } else Box(m.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))))
}

@Composable
fun MediaRow(
    item: MediaItem,
    subtitle: String = item.subtitle,
    trailingLabel: String? = null,
    onClick: () -> Unit,
    onPrimaryAction: (() -> Unit)? = null
) {
    val alpha = if (item.isSuggestion || !item.added) 0.50f else 1f
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).alpha(alpha).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaPoster(item)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(mediaKindIcon(item.kind), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(5.dp))
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (item.favorite) { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Star, "Favori", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp)) }
                if (item.hidden) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.VisibilityOff, "Masqué", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val meta = listOfNotNull(
                item.year?.toString(), item.contentRating, item.creator, item.network,
                item.platforms.takeIf { it.isNotEmpty() }?.take(2)?.joinToString(" / "),
                item.runtimeMinutes?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h${(it % 60).toString().padStart(2, '0')}" else "$it min" },
                item.franchiseName
            ).filter { it.isNotBlank() }.distinct().joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val score = item.ratings.firstOrNull()?.normalized10 ?: item.catalogRating
            if (score != null) Text("★ ${String.format(Locale.getDefault(), "%.1f", score)}/10", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if ((item.kind == MediaKind.GAME || item.kind == MediaKind.BOOK) && item.progressTotal > 0 && !item.isSuggestion) {
                Spacer(Modifier.height(5.dp)); LinearProgressIndicator(progress = { item.progressRatio }, modifier = Modifier.fillMaxWidth())
                Text("${item.progressCurrent} / ${item.progressTotal}${item.progressUnit.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.isSuggestion) Text("Non ajouté · suggestion de franchise", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            trailingLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onPrimaryAction != null) IconButton(onClick = onPrimaryAction) { Icon(if (item.isSuggestion || !item.added) Icons.Default.AddCircle else Icons.Default.CheckCircle, null) }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 106.dp))
}

@Composable
fun EpisodeRow(
    episode: Episode,
    hideSpoiler: Boolean = false,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onCollect: () -> Unit,
    onReplay: () -> Unit,
    onCheckIn: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
        val thumb = Modifier.width(118.dp).height(68.dp).clip(RoundedCornerShape(9.dp))
        var stillFailed by remember(episode.stillUrl) { mutableStateOf(false) }
        if (!episode.stillUrl.isNullOrBlank() && !stillFailed && !(hideSpoiler && !episode.watched)) {
            AsyncImage(model = episode.stillUrl, contentDescription = episode.title, modifier = thumb, contentScale = ContentScale.Crop, onError = { stillFailed = true })
        } else {
            Box(thumb.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(if (hideSpoiler && !episode.watched) Icons.Default.VisibilityOff else Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = episode.watched, onCheckedChange = { onToggle() })
                Column(Modifier.weight(1f)) {
                    Text("${episode.code}  ${if (hideSpoiler && !episode.watched) "Épisode non vu" else episode.title}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(buildString {
                        append(episode.airDate?.toString() ?: "Date inconnue")
                        append(" · ${episode.runtimeMinutes} min")
                        if (episode.catalogRating != null) append(" · ★ ${String.format(Locale.getDefault(), "%.1f", episode.catalogRating)}/10")
                        if (episode.plays > 1) append(" · ${episode.plays} lectures")
                        if (episode.collected) append(" · Collection")
                        if (episode.skipped) append(" · Ignoré")
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (episode.overview.isNotBlank() && !(hideSpoiler && !episode.watched)) Text(episode.overview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(onClick = onCollect, label = { Text(if (episode.collected) "Collecté" else "Collection") }, leadingIcon = { Icon(Icons.Default.Inventory2, null, Modifier.size(16.dp)) })
                AssistChip(onClick = onSkip, label = { Text(if (episode.skipped) "Réactiver" else "Ignorer") }, leadingIcon = { Icon(if (episode.skipped) Icons.Default.Undo else Icons.Default.SkipNext, null, Modifier.size(16.dp)) })
                if (episode.watched) AssistChip(onClick = onReplay, label = { Text("Revoir") }, leadingIcon = { Icon(Icons.Default.Replay, null, Modifier.size(16.dp)) })
                onCheckIn?.let { action -> AssistChip(onClick = action, label = { Text("Check-in") }, leadingIcon = { Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp)) }) }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 140.dp))
}

@Composable
fun SeasonProgressCard(progress: SeasonProgress, expanded: Boolean, onToggle: () -> Unit, onWatchAll: () -> Unit, onCollectAll: () -> Unit, onSkipAll: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onToggle)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val image = Modifier.width(68.dp).height(98.dp).clip(RoundedCornerShape(8.dp))
            var posterFailed by remember(progress.posterUrl) { mutableStateOf(false) }
            if (!progress.posterUrl.isNullOrBlank() && !posterFailed) AsyncImage(model = progress.posterUrl, contentDescription = null, modifier = image, contentScale = ContentScale.Crop, onError = { posterFailed = true })
            else Box(image.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.VideoLibrary, null) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (progress.season == 0) "Épisodes spéciaux" else "Saison ${progress.season}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    ProgressPill(progress.watched, progress.total)
                }
                Spacer(Modifier.height(7.dp)); LinearProgressIndicator(progress = { progress.completion }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                val details = buildList {
                    add("${progress.remaining} restant${if (progress.remaining > 1) "s" else ""}")
                    if (progress.unreleased > 0) add("${progress.unreleased} à sortir")
                    if (progress.skipped > 0) add("${progress.skipped} ignoré${if (progress.skipped > 1) "s" else ""}")
                    if (progress.collected > 0) add("${progress.collected} en collection")
                }
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onWatchAll, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Tout vu") }
                    TextButton(onClick = onCollectAll, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Tout collecter") }
                    TextButton(onClick = onSkipAll, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Ignorer") }
                }
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
    }
}

@Composable
fun CreditsCarousel(credits: List<PersonCredit>, showImages: Boolean = true, onOpen: (PersonCredit) -> Unit = {}) {
    if (credits.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(credits, key = { "${it.id}:${it.role}" }) { person ->
            Column(Modifier.width(106.dp).clickable { onOpen(person) }, horizontalAlignment = Alignment.CenterHorizontally) {
                val portrait = Modifier.size(if (showImages) 78.dp else 56.dp).clip(CircleShape)
                var portraitFailed by remember(person.profileUrl) { mutableStateOf(false) }
                if (showImages && !person.profileUrl.isNullOrBlank() && !portraitFailed) AsyncImage(model = person.profileUrl, contentDescription = person.name, modifier = portrait, contentScale = ContentScale.Crop, onError = { portraitFailed = true })
                else Box(portrait.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text(person.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(6.dp)); Text(person.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (person.role.isNotBlank()) Text(person.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun RatingsRow(item: MediaItem, onOpen: (String) -> Unit = {}) {
    val ratings = if (item.ratings.isNotEmpty()) item.ratings else item.catalogRating?.let { listOf(RatingSource("catalog", "Catalogue", it, 10.0, item.catalogRatingVotes)) }.orEmpty()
    if (ratings.isEmpty() && item.rating == null) return
    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item.rating?.let { own -> RatingChip("Ta note", own.toDouble(), 10.0, null) {} }
        ratings.forEach { r -> RatingChip(r.label, r.score, r.maxScore, r.votes) { r.externalUrl?.let(onOpen) } }
    }
}

@Composable
private fun RatingChip(label: String, score: Double, max: Double, votes: Int?, onClick: () -> Unit) {
    ElevatedCard(Modifier.widthIn(min = 108.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${String.format(Locale.getDefault(), "%.1f", score)}/${String.format(Locale.getDefault(), if (max % 1.0 == 0.0) "%.0f" else "%.1f", max)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            votes?.let { Text("$it votes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun EmptyState(icon: @Composable () -> Unit, title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        icon(); Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp)); Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProgressPill(done: Int, total: Int) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text("$done / $total", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
