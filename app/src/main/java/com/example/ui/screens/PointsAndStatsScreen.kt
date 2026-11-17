package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.PlayerLeaderboardRow
import com.example.domain.PointsTableRow
import com.example.domain.TournamentStatsSummary
import com.example.ui.CricketUiState
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun PointsAndStatsScreen(
    uiState: CricketUiState,
    onTabSelected: (Int) -> Unit,
    onTeamClick: (String) -> Unit,
    onPlayerClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val selectedTab = uiState.pointsStatsTabIndex
    val seasonTitle = uiState.activeSeason?.let {
        if (it.name.isNotBlank()) it.name else "Season ${it.number}"
    } ?: "Active Season"

    Column(modifier = Modifier.fillMaxSize()) {
        CrickTopHeader(
            title = "Points & Stats",
            subtitle = "$seasonTitle • Standings & Leaderboards",
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ScorecardWhite,
                contentColor = LavenderPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    text = {
                        Text(
                            text = "Points Table",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("tab_points_table")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    text = {
                        Text(
                            text = "Tournament Stats",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("tab_tournament_stats")
                )
            }
        }

        if (selectedTab == 0) {
            PointsTableTabContent(
                rows = uiState.pointsTable,
                onTeamClick = onTeamClick
            )
        } else {
            TournamentStatsTabContent(
                stats = uiState.tournamentStats,
                onPlayerClick = onPlayerClick
            )
        }
    }
}

@Composable
private fun PointsTableTabContent(
    rows: List<PointsTableRow>,
    onTeamClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("points_table_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "points_table_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                border = BorderStroke(1.dp, DividerSlate),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "League Standings",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimarySlate
                    )
                    Text(
                        text = "Completed & abandoned league stage matches • Win: 2, Tie/NR: 1",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedSlate
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Columns: Team, Played, Won, Lost, T/NR, Pts, NRR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyHeader, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Team",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ScorecardWhite,
                            modifier = Modifier.weight(1f)
                        )
                        TableHeaderCell("Played", 38.dp)
                        TableHeaderCell("Won", 30.dp)
                        TableHeaderCell("Lost", 30.dp)
                        TableHeaderCell("T/NR", 34.dp)
                        TableHeaderCell("Pts", 30.dp)
                        TableHeaderCell("NRR", 54.dp)
                    }

                    if (rows.isEmpty()) {
                        Text(
                            text = "No teams available for the league standings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondarySlate,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        rows.forEachIndexed { idx, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTeamClick(row.team.id) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                                    .testTag("points_row_${row.team.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMutedSlate,
                                        modifier = Modifier.width(18.dp)
                                    )
                                    TeamCrestAvatar(team = row.team, size = 26.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = row.team.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimarySlate,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TableDataCell(row.played.toString(), 38.dp)
                                TableDataCell(row.won.toString(), 30.dp)
                                TableDataCell(row.lost.toString(), 30.dp)
                                TableDataCell(row.tiedOrNoResult.toString(), 34.dp)
                                TableDataCell(row.points.toString(), 30.dp, bold = true)
                                TableDataCell(row.nrrDisplay, 54.dp)
                            }
                            if (idx < rows.lastIndex) {
                                HorizontalDivider(color = ScorecardSubtle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = PaleBlueSurface,
        textAlign = TextAlign.End,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun TableDataCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    bold: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = TextPrimarySlate,
        textAlign = TextAlign.End,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun TournamentStatsTabContent(
    stats: TournamentStatsSummary,
    onPlayerClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tournament_stats_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Boundary Totals: Tournament fours & Tournament sixes
        item(key = "boundary_totals") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BoundaryCounterCard(
                    title = "Tournament fours",
                    count = stats.totalFours,
                    accentColor = PaleSkyText,
                    bgColor = PaleSkyChip,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tournament_fours_card")
                )
                BoundaryCounterCard(
                    title = "Tournament sixes",
                    count = stats.totalSixes,
                    accentColor = LavenderPrimary,
                    bgColor = PaleBlueSurface,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tournament_sixes_card")
                )
            }
        }

        // 1. Most runs
        item(key = "most_runs_leaderboard") {
            LeaderboardCard(
                title = "Most runs",
                subtitle = null,
                rows = stats.mostRuns,
                primaryStatLabel = "Runs",
                secondaryStatLabel = "SR",
                primaryValueSelector = { it.runs.toString() },
                secondaryValueSelector = { "${it.ballsFaced}b • SR ${it.strikeRateDisplay}" },
                onPlayerClick = onPlayerClick,
                testTagPrefix = "most_runs"
            )
        }

        // 2. Most wickets
        item(key = "most_wickets_leaderboard") {
            LeaderboardCard(
                title = "Most wickets",
                subtitle = null,
                rows = stats.mostWickets,
                primaryStatLabel = "Wickets",
                secondaryStatLabel = "Runs",
                primaryValueSelector = { it.wickets.toString() },
                secondaryValueSelector = { "${it.runsConceded} runs conceded" },
                onPlayerClick = onPlayerClick,
                testTagPrefix = "most_wickets"
            )
        }

        // 3. Highest strike rate (Minimum 10 balls faced)
        item(key = "highest_sr_leaderboard") {
            LeaderboardCard(
                title = "Highest strike rate",
                subtitle = "Minimum 10 balls faced",
                rows = stats.highestStrikeRate,
                primaryStatLabel = "SR",
                secondaryStatLabel = "Runs (Balls)",
                primaryValueSelector = { it.strikeRateDisplay },
                secondaryValueSelector = { "${it.runs} runs (${it.ballsFaced} balls)" },
                onPlayerClick = onPlayerClick,
                testTagPrefix = "highest_sr"
            )
        }
    }
}

@Composable
private fun BoundaryCounterCard(
    title: String,
    count: Int,
    accentColor: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, DividerSlate),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                color = bgColor,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NavyHeader
            )
        }
    }
}

@Composable
private fun LeaderboardCard(
    title: String,
    subtitle: String?,
    rows: List<PlayerLeaderboardRow>,
    primaryStatLabel: String,
    secondaryStatLabel: String,
    primaryValueSelector: (PlayerLeaderboardRow) -> String,
    secondaryValueSelector: (PlayerLeaderboardRow) -> String,
    onPlayerClick: (String) -> Unit,
    testTagPrefix: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, DividerSlate),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimarySlate
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMutedSlate
                        )
                    }
                }
                Text(
                    text = primaryStatLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondarySlate
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = DividerSlate)

            if (rows.isEmpty()) {
                Text(
                    text = "No qualifying players from completed matches yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedSlate,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                rows.forEachIndexed { idx, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayerClick(row.player.id) }
                            .padding(vertical = 9.dp)
                            .testTag("${testTagPrefix}_row_${row.player.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (idx == 0) GoldAmber else TextMutedSlate,
                            modifier = Modifier.width(24.dp)
                        )
                        PlayerAvatar(
                            player = row.player,
                            teamColorHex = row.team?.color ?: "",
                            size = 34.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.player.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimarySlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subText = listOfNotNull(
                                row.team?.shortName?.ifBlank { row.team.name },
                                secondaryValueSelector(row)
                            ).joinToString(" • ")
                            Text(
                                text = subText,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondarySlate
                            )
                        }
                        Text(
                            text = primaryValueSelector(row),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyHeader
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
