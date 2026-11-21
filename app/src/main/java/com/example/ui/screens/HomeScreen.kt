package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CricketCalculations
import com.example.domain.MatchFilter
import com.example.domain.PointsTableRow
import com.example.ui.CricketUiState
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    uiState: CricketUiState,
    onFilterSelected: (MatchFilter) -> Unit,
    onMatchClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    onNavigateToStandings: () -> Unit,
    onNavigateToTeams: () -> Unit,
    onRefresh: () -> Unit
) {
    val teamMap = uiState.teams.associateBy { it.id }
    val filteredMatches = CricketCalculations.filterMatchesForHome(
        matches = uiState.matches,
        filter = uiState.homeMatchFilter
    )
    val seasonTitle = uiState.activeSeason?.let { s ->
        if (s.name.isNotBlank()) s.name else "Season ${s.number}"
    } ?: "Active Season"

    Column(modifier = Modifier.fillMaxSize()) {
        CrickTopHeader(
            title = "Crick English",
            subtitle = "$seasonTitle • Department of English",
            onRefresh = onRefresh,
            isRefreshing = uiState.isRefreshing
        )

        if (uiState.errorMessage != null && uiState.hasLoadedOnce) {
            ConnectionErrorBanner(
                message = uiState.errorMessage,
                lastUpdatedDhaka = uiState.lastSuccessFormattedDhaka,
                onRetry = onRefresh
            )
        }

        if (uiState.isInitialLoading && !uiState.hasLoadedOnce) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LavenderPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading tournament fixtures & live scores…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySlate
                    )
                }
            }
            return
        }

        if (uiState.errorMessage != null && !uiState.hasLoadedOnce) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                    border = BorderStroke(1.dp, DividerSlate),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsCricket,
                            contentDescription = null,
                            tint = LavenderPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Unable to load tournament data",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimarySlate
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondarySlate,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("initial_retry_button")
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero_banner") {
                CompactCricketHeroBanner(
                    seasonTitle = seasonTitle,
                    teamCount = uiState.teams.size,
                    matchCount = uiState.matches.size
                )
            }

            item(key = "matches_header_and_filters") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Matches",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimarySlate
                        )
                        if (!uiState.lastSuccessFormattedDhaka.isNullOrBlank()) {
                            Text(
                                text = "Updated ${uiState.lastSuccessFormattedDhaka}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMutedSlate
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MatchFilterRow(
                        selectedFilter = uiState.homeMatchFilter,
                        onFilterSelected = onFilterSelected
                    )
                }
            }

            if (filteredMatches.isEmpty()) {
                item(key = "empty_matches") {
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
                                imageVector = Icons.Default.SportsCricket,
                                contentDescription = null,
                                tint = TextMutedSlate,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (uiState.homeMatchFilter) {
                                    MatchFilter.ALL -> "No matches started yet. Upcoming matches are in Fixtures."
                                    MatchFilter.FIXTURES -> "No upcoming scheduled fixtures."
                                    MatchFilter.RESULTS -> "No completed or abandoned results yet."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondarySlate,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredMatches,
                    key = { "match_${it.id}" }
                ) { match ->
                    CompactMatchCard(
                        match = match,
                        teamMap = teamMap,
                        onClick = { onMatchClick(match.id) }
                    )
                }
            }

            item(key = "standings_preview") {
                Spacer(modifier = Modifier.height(4.dp))
                StandingsPreviewCard(
                    rows = uiState.pointsTable.take(5),
                    onTeamClick = onTeamClick,
                    onViewFullStandings = onNavigateToStandings
                )
            }

            item(key = "teams_players_shortcut") {
                TeamsAndPlayersShortcutCard(
                    teamCount = uiState.teams.size,
                    playerCount = uiState.players.size,
                    onClick = onNavigateToTeams
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CompactCricketHeroBanner(
    seasonTitle: String,
    teamCount: Int,
    matchCount: Int
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.cricket_hero),
                contentDescription = "Department of English Cricket Tournament Hero",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                NavyHeader.copy(alpha = 0.92f),
                                NavyHeader.copy(alpha = 0.72f),
                                NavyHeader.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = LavenderAccent.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, PaleBlueBorder.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = seasonTitle.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PaleBlueSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "$teamCount Teams • $matchCount Matches",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Column {
                    Text(
                        text = "Department of English Cricket",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Official spectator scorecards, live chase updates & standings",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E8F0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchFilterRow(
    selectedFilter: MatchFilter,
    onFilterSelected: (MatchFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf(
            MatchFilter.ALL to "All matches",
            MatchFilter.FIXTURES to "Fixtures",
            MatchFilter.RESULTS to "Results"
        )
        for ((filter, label) in options) {
            val selected = selectedFilter == filter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NavyHeader,
                    selectedLabelColor = Color.White,
                    containerColor = ScorecardWhite,
                    labelColor = TextSecondarySlate
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = DividerSlate,
                    selectedBorderColor = NavyHeader
                ),
                modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun StandingsPreviewCard(
    rows: List<PointsTableRow>,
    onTeamClick: (String) -> Unit,
    onViewFullStandings: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, DividerSlate),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = null,
                        tint = LavenderPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Points Table Preview",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimarySlate
                    )
                }
                TextButton(
                    onClick = onViewFullStandings,
                    modifier = Modifier.testTag("home_full_standings_button")
                ) {
                    Text(
                        text = "Full standings",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = LavenderPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = LavenderPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (rows.isEmpty()) {
                Text(
                    text = "Standings will appear once teams are registered for this season.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondarySlate,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ScorecardSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Team",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedSlate,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "P",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedSlate,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = "W",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedSlate,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = "Pts",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimarySlate,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(30.dp)
                    )
                    Text(
                        text = "NRR",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedSlate,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(56.dp)
                    )
                }

                rows.forEachIndexed { idx, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTeamClick(row.team.id) }
                            .padding(horizontal = 8.dp, vertical = 9.dp)
                            .testTag("home_standings_team_${row.team.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMutedSlate,
                            modifier = Modifier.width(18.dp)
                        )
                        TeamCrestAvatar(team = row.team, size = 24.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = row.team.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimarySlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = row.played.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondarySlate,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = row.won.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondarySlate,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = row.points.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimarySlate,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(30.dp)
                        )
                        Text(
                            text = row.nrrDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondarySlate,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(56.dp)
                        )
                    }
                    if (idx < rows.lastIndex) {
                        HorizontalDivider(color = ScorecardSubtle)
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamsAndPlayersShortcutCard(
    teamCount: Int,
    playerCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("home_teams_shortcut_card"),
        colors = CardDefaults.cardColors(containerColor = PaleBlueSurface),
        border = BorderStroke(1.dp, PaleBlueBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = LavenderPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Explore Teams & Players",
                        style = MaterialTheme.typography.titleMedium,
                        color = NavyHeader
                    )
                    Text(
                        text = "$teamCount squads • $playerCount player profiles & match figures",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondarySlate
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open Teams & Players",
                tint = LavenderPrimary
            )
        }
    }
}
