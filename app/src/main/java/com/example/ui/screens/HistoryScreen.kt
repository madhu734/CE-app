package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CeSeasonHistory
import com.example.domain.CricketCalculations
import coil.compose.SubcomposeAsyncImage
import com.example.ui.CricketUiState
import com.example.ui.components.ArchiveLogoSlot
import com.example.ui.components.ConnectionErrorBanner
import com.example.ui.components.CrickTopHeader
import com.example.ui.theme.*

@Composable
fun HistoryScreen(
    uiState: CricketUiState,
    onRefresh: () -> Unit
) {
    val sortedHistory = uiState.history
        .filter { it.published }
        .sortedWith(compareBy<CeSeasonHistory> { it.seasonNumber }.thenBy { it.edition })

    Column(modifier = Modifier.fillMaxSize()) {
        CrickTopHeader(
            title = "Tournament History",
            subtitle = "Past Champions & Runners-up Archive",
            onRefresh = onRefresh,
            isRefreshing = uiState.isRefreshing
        )

        if (uiState.errorMessage != null) {
            ConnectionErrorBanner(
                message = uiState.errorMessage,
                lastUpdatedDhaka = uiState.lastSuccessFormattedDhaka,
                onRetry = onRefresh
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("history_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sortedHistory.isEmpty()) {
                item(key = "empty_history") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                        border = BorderStroke(1.dp, DividerSlate),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = GoldAmber,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No published season archives yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondarySlate
                            )
                        }
                    }
                }
            } else {
                items(
                    items = sortedHistory,
                    key = { "history_${it.id}" }
                ) { entry ->
                    SeasonHistoryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun SeasonHistoryCard(entry: CeSeasonHistory) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, DividerSlate),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${entry.seasonNumber}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = NavyHeader,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Season ${entry.seasonNumber}" + entry.edition.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ScorecardWhite,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (entry.year != null && entry.year > 0) {
                    Surface(
                        color = PaleBlueSurface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entry.year.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = LavenderPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Champion row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArchiveLogoSlot(
                    collectionId = entry.collectionId,
                    recordId = entry.id,
                    filename = entry.championLogo,
                    teamName = entry.champion,
                    isChampion = true,
                    size = 46.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CHAMPION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldAmber
                    )
                    Text(
                        text = entry.champion.ifBlank { "TBA" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimarySlate,
                        overflow = TextOverflow.Clip
                    )
                    CaptainName(entry.championCaptain)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = ScorecardSubtle)
            Spacer(modifier = Modifier.height(10.dp))

            // Runner-up row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArchiveLogoSlot(
                    collectionId = entry.collectionId,
                    recordId = entry.id,
                    filename = entry.runnerUpLogo,
                    teamName = entry.runnerUp,
                    isChampion = false,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RUNNER-UP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondarySlate
                    )
                    Text(
                        text = entry.runnerUp.ifBlank { "TBA" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimarySlate,
                        overflow = TextOverflow.Clip
                    )
                    CaptainName(entry.runnerUpCaptain)
                }
            }

            ArchiveTeamPhoto(entry, entry.teamPhoto, entry.champion, "Champion", "history_photo")
            ArchiveTeamPhoto(entry, entry.runnerUpPhoto, entry.runnerUp, "Runner-up", "history_runner_up_photo")
        }
    }
}

@Composable
private fun ArchiveTeamPhoto(entry: CeSeasonHistory, file: String, team: String, role: String, tag: String) {
    val url = CricketCalculations.buildFileUrl(entry.collectionId, entry.id, file) ?: return
    Spacer(modifier = Modifier.height(16.dp))
    SubcomposeAsyncImage(
        model = "$url?thumb=960x0",
        contentDescription = "$team team photo, Season ${entry.seasonNumber} ${role.lowercase()}",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .testTag("${tag}_${entry.seasonNumber}"),
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Team photo unavailable", color = TextSecondarySlate)
            }
        }
    )
    Text(
        text = "$role · $team",
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondarySlate,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun CaptainName(name: String) {
    if (name.isNotBlank()) {
        Text(
            text = "Captain: ${name.trim()}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondarySlate,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
