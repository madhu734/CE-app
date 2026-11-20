package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.domain.CricketCalculations
import com.example.model.CeMatch
import com.example.model.CePlayer
import com.example.model.CeTeam
import com.example.ui.theme.*
import java.util.Locale

fun parseTeamColor(hex: String, fallback: Color = LavenderPrimary): Color {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return fallback
    return try {
        val longVal = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000 or longVal)
        } else {
            Color(longVal)
        }
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun CrickTopHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false
) {
    Surface(
        color = NavyHeader,
        contentColor = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else {
                Spacer(modifier = Modifier.width(4.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onRefresh != null) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("header_refresh_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = PaleBlueBorder
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TeamCrestAvatar(
    team: CeTeam?,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val imageUrl = if (team != null && team.logo.isNotBlank()) {
        CricketCalculations.buildFileUrl(team.collectionId, team.id, team.logo)
    } else null

    val bgColor = parseTeamColor(team?.color ?: "", LavenderPrimary)
    val contentColor = if (bgColor.luminance() > 0.55f) NavyHeader else Color.White
    val shortText = when {
        !team?.shortName.isNullOrBlank() -> team!!.shortName.take(4).uppercase(Locale.US)
        !team?.name.isNullOrBlank() -> team!!.name.take(3).uppercase(Locale.US)
        else -> "TM"
    }

    val fallbackBox: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = shortText,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.34f).sp,
                maxLines = 1
            )
        }
    }

    if (imageUrl == null) {
        Box(modifier = modifier) { fallbackBox() }
    } else {
        val context = LocalContext.current
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .apply {
                    teamLogoFrame(team?.logo)?.let { frame ->
                        allowHardware(false)
                        transformations(frame)
                    }
                }
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = "${team?.name ?: "Team"} crest",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 5))
                .background(Color.White)
                .padding(3.dp),
            loading = { fallbackBox() },
            error = { fallbackBox() }
        )
    }
}

@Composable
fun PlayerAvatar(
    player: CePlayer?,
    teamColorHex: String = "",
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val imageUrl = if (player != null && player.photo.isNotBlank()) {
        CricketCalculations.buildFileUrl(player.collectionId, player.id, player.photo)
    } else null

    val initials = CricketCalculations.playerInitials(player?.name ?: "?")
    val bg = if (teamColorHex.isNotBlank()) {
        parseTeamColor(teamColorHex, PaleBlueSurface)
    } else {
        PaleBlueSurface
    }
    val fg = if (bg.luminance() > 0.55f) NavyHeader else Color.White

    val fallbackBox: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
                maxLines = 1
            )
        }
    }

    if (imageUrl == null) {
        Box(modifier = modifier) { fallbackBox() }
    } else {
        val context = LocalContext.current
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = "${player?.name ?: "Player"} photo",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            loading = { fallbackBox() },
            error = { fallbackBox() }
        )
    }
}

@Composable
fun ArchiveLogoSlot(
    collectionId: String,
    recordId: String,
    filename: String,
    teamName: String,
    isChampion: Boolean,
    size: Dp = 44.dp
) {
    val imageUrl = CricketCalculations.buildFileUrl(collectionId, recordId, filename)
    val bgColor = if (isChampion) GoldAmberBg else PaleBlueSurface
    val iconTint = if (isChampion) GoldAmber else LavenderPrimary
    val initials = CricketCalculations.playerInitials(teamName)

    val fallbackBox: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            if (teamName.isNotBlank()) {
                Text(
                    text = initials,
                    color = iconTint,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.34f).sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }

    if (imageUrl == null) {
        fallbackBox()
    } else {
        val context = LocalContext.current
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = "$teamName logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            loading = { fallbackBox() },
            error = { fallbackBox() }
        )
    }
}

@Composable
fun MatchStatusBadge(status: String) {
    val label = CricketCalculations.statusLabel(status)
    val isLive = status.equals("live", ignoreCase = true)
    val isCompleted = status.equals("completed", ignoreCase = true)
    val isAbandoned = status.equals("abandoned", ignoreCase = true)

    val bgColor = when {
        isLive -> LiveRedBg
        isCompleted -> PaleBlueSurface
        isAbandoned -> Color(0xFFF1F5F9)
        else -> PaleSkyChip
    }
    val textColor = when {
        isLive -> LiveRed
        isCompleted -> LavenderPrimary
        isAbandoned -> TextSecondarySlate
        else -> PaleSkyText
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(LiveRed)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
fun StageBadge(stage: String) {
    val label = CricketCalculations.stageLabel(stage)
    Surface(
        color = ScorecardSubtle,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, DividerSlate)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondarySlate,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun CompactMatchCard(
    match: CeMatch,
    teamMap: Map<String, CeTeam>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val teamA = teamMap[match.teamA]
    val teamB = teamMap[match.teamB]
    val inningsList = match.scorecard?.innings ?: emptyList()
    // Team innings are matched by team ID, even when Team B bats first.
    val inningsA = inningsList.firstOrNull { it.team == match.teamA }
    val inningsB = inningsList.firstOrNull { it.team == match.teamB }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("match_card_${match.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(
            width = 1.dp,
            color = if (match.status.equals("live", ignoreCase = true)) LiveRed.copy(alpha = 0.35f) else DividerSlate
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StageBadge(match.stage)
                    if (match.overs > 0) {
                        Text(
                            text = "${match.overs} ov",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMutedSlate
                        )
                    }
                }
                MatchStatusBadge(match.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            MatchTeamScoreRow(
                team = teamA,
                fallbackName = match.teamA.ifBlank { "Team A" },
                runs = inningsA?.runs,
                wickets = inningsA?.wickets,
                balls = inningsA?.balls,
                isWinner = match.winner.isNotBlank() && match.winner == match.teamA
            )

            Spacer(modifier = Modifier.height(8.dp))

            MatchTeamScoreRow(
                team = teamB,
                fallbackName = match.teamB.ifBlank { "Team B" },
                runs = inningsB?.runs,
                wickets = inningsB?.wickets,
                balls = inningsB?.balls,
                isWinner = match.winner.isNotBlank() && match.winner == match.teamB
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = ScorecardSubtle, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            val resultText = match.scorecard?.result?.takeIf { it.isNotBlank() }
            if (resultText != null) {
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (match.status.equals("live", ignoreCase = true)) LiveRed else LavenderPrimary
                )
            } else {
                Text(
                    text = CricketCalculations.formatDhakaDateTime(match.startsAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondarySlate
                )
            }
        }
    }
}

@Composable
private fun MatchTeamScoreRow(
    team: CeTeam?,
    fallbackName: String,
    runs: Int?,
    wickets: Int?,
    balls: Int?,
    isWinner: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            TeamCrestAvatar(team = team, size = 28.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = team?.name?.ifBlank { fallbackName } ?: fallbackName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Medium,
                color = TextPrimarySlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (runs != null && wickets != null && balls != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$runs/$wickets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimarySlate
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "(${CricketCalculations.formatOvers(balls)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedSlate
                )
            }
        } else {
            Text(
                text = "Yet to bat",
                style = MaterialTheme.typography.labelSmall,
                color = TextMutedSlate
            )
        }
    }
}

@Composable
fun ConnectionErrorBanner(
    message: String,
    lastUpdatedDhaka: String?,
    onRetry: () -> Unit
) {
    Surface(
        color = Color(0xFFFEF2F2),
        border = BorderStroke(1.dp, Color(0xFFFECACA)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = LiveRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF991B1B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!lastUpdatedDhaka.isNullOrBlank()) {
                    Text(
                        text = "Showing last successful update: $lastUpdatedDhaka",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB91C1C)
                    )
                }
            }
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag("retry_banner_button")
            ) {
                Text(
                    text = "Retry",
                    fontWeight = FontWeight.Bold,
                    color = LiveRed
                )
            }
        }
    }
}

@Composable
fun DeliveryChipBadge(label: String) {
    val upper = label.uppercase(Locale.US)
    val isWicket = upper.contains("W") && !upper.startsWith("WD") || upper.endsWith("+W") || upper == "W"
    val isSix = upper.contains("6") && !upper.contains("B") && !upper.contains("LB")
    val isFour = upper.contains("4") && !upper.contains("B") && !upper.contains("LB")

    val bg = when {
        isWicket -> LiveRedBg
        isSix -> Color(0xFFEDE9FE)
        isFour -> PaleSkyChip
        else -> ScorecardSubtle
    }
    val fg = when {
        isWicket -> LiveRed
        isSix -> Color(0xFF6D28D9)
        isFour -> PaleSkyText
        else -> TextPrimarySlate
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 28.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}
