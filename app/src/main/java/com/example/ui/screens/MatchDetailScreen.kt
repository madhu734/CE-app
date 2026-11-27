package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.*
import com.example.model.*
import com.example.ui.ComparisonMetric
import com.example.ui.CricketUiState
import com.example.ui.MatchScoringUiState
import com.example.ui.ScoringLoadState
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun MatchDetailScreen(
    matchId: String,
    uiState: CricketUiState,
    onBack: () -> Unit,
    onRefreshScore: () -> Unit,
    onRetryScoring: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onSelectReplayInnings: (Int) -> Unit,
    onSelectComparisonMetric: (ComparisonMetric) -> Unit,
    onPlayerClick: (String) -> Unit,
    onTeamClick: (String) -> Unit
) {
    val match = uiState.matches.firstOrNull { it.id == matchId }
    val teamMap = uiState.teams.associateBy { it.id }
    val playerMap = uiState.players.associateBy { it.id }
    val selectedTab = uiState.matchDetailTabsByMatchId[matchId] ?: 0
    val scoringState = uiState.scoringStatesByMatchId[matchId] ?: MatchScoringUiState(matchId = matchId)

    Column(modifier = Modifier.fillMaxSize()) {
        val teamAName = teamMap[match?.teamA]?.shortName?.ifBlank { teamMap[match?.teamA]?.name } ?: "TBD"
        val teamBName = teamMap[match?.teamB]?.shortName?.ifBlank { teamMap[match?.teamB]?.name } ?: "TBD"
        CrickTopHeader(
            title = if (match != null) "$teamAName vs $teamBName" else "Match Details",
            subtitle = match?.let { "${CricketCalculations.stageLabel(it.stage)} • ${it.overs} Overs" },
            onBack = onBack,
            onRefresh = onRefreshScore,
            isRefreshing = uiState.isRefreshing
        )

        if (uiState.errorMessage != null && uiState.hasLoadedOnce) {
            ConnectionErrorBanner(
                message = uiState.errorMessage,
                lastUpdatedDhaka = uiState.lastSuccessFormattedDhaka,
                onRetry = onRefreshScore
            )
        }

        if (match == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isInitialLoading) {
                    CircularProgressIndicator(color = LavenderPrimary)
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                        border = BorderStroke(1.dp, DividerSlate)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Match record unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimarySlate
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "This fixture could not be found in the current season.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondarySlate
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = onRefreshScore) {
                                Text("Refresh")
                            }
                        }
                    }
                }
            }
            return
        }

        val resolvedPlayerStats = remember(match, uiState.legacyStatsByMatchId) {
            CricketCalculations.resolveMatchPlayerStats(match, uiState.legacyStatsByMatchId)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("match_detail_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "match_summary_hero") {
                MatchSummaryHeaderCard(
                    match = match,
                    teamMap = teamMap,
                    onTeamClick = onTeamClick,
                    onRefreshScore = onRefreshScore,
                    isRefreshing = uiState.isRefreshing
                )
            }

            item(key = "match_tabs") {
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
                                text = "Scorecard",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("tab_scorecard")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { onTabSelected(1) },
                        text = {
                            Text(
                                text = "Squads",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("tab_squads")
                    )
                }
            }

            if (selectedTab == 0) {
                val awarded = playerMap[match.playerOfMatch].takeIf { match.status == "completed" }
                if (awarded != null) {
                    item(key = "player_of_match") {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onPlayerClick(awarded.id) },
                            colors = CardDefaults.cardColors(containerColor = GoldAmberBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                PlayerAvatar(awarded, size = 48.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Player of the Match", style = MaterialTheme.typography.labelLarge, color = TextSecondarySlate)
                                    Text(awarded.name, style = MaterialTheme.typography.titleMedium, color = TextPrimarySlate)
                                    resolvedPlayerStats.firstOrNull { it.player == awarded.id }?.let {
                                        Text("${it.runs} runs · ${it.wickets} wickets", style = MaterialTheme.typography.bodySmall, color = TextSecondarySlate)
                                    }
                                }
                            }
                        }
                    }
                }
                // Recent deliveries strip (up to 20 chronological)
                val recentDeliveries = match.scorecard?.recent?.takeLast(20) ?: emptyList()
                if (recentDeliveries.isNotEmpty()) {
                    item(key = "recent_deliveries") {
                        RecentDeliveriesStrip(recent = recentDeliveries)
                    }
                }

                // Current batters and bowler when present
                val currentBatters = match.scorecard?.currentBatters ?: emptyList()
                val currentBowler = match.scorecard?.currentBowler
                if (currentBatters.isNotEmpty() || !currentBowler.isNullOrBlank()) {
                    item(key = "current_participants") {
                        CurrentCreaseCard(
                            currentBatters = currentBatters,
                            currentBowler = currentBowler,
                            playerMap = playerMap,
                            resolvedStats = resolvedPlayerStats,
                            onPlayerClick = onPlayerClick
                        )
                    }
                }

                match.scorecard?.summaryNote?.takeIf { it.isNotBlank() }?.let { note ->
                    item(key = "paper_summary_note") {
                        Text(note, style = MaterialTheme.typography.bodySmall, color = TextSecondarySlate,
                            modifier = Modifier.fillMaxWidth().padding(12.dp))
                    }
                }

                // Team scorecards
                val inningsOrder = buildTeamOrderForScorecard(match)
                if (match.scorecard == null) {
                    item(key = "unscored_fixture_notice") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                            border = BorderStroke(1.dp, DividerSlate),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Scorecard not yet available",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimarySlate
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Batting and bowling figures will appear once play starts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondarySlate,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    for ((idx, teamId) in inningsOrder.withIndex()) {
                        val team = teamMap[teamId]
                        val opponentId = if (teamId == match.teamA) match.teamB else match.teamA
                        val opponentTeam = teamMap[opponentId]
                        val innings = match.scorecard?.innings?.firstOrNull { it.team == teamId }
                        val teamBatters = resolvedPlayerStats.filter { it.team == teamId && it.batted }
                        val opponentBowlers = resolvedPlayerStats.filter {
                            it.team == opponentId && (it.ballsBowled > 0 || it.runsConceded > 0 || it.wickets > 0)
                        }

                        if (innings != null || teamBatters.isNotEmpty() || opponentBowlers.isNotEmpty()) {
                            item(key = "team_scorecard_${teamId}_$idx") {
                                TeamInningsScorecardSection(
                                    battingTeam = team,
                                    battingTeamFallback = teamId,
                                    bowlingTeam = opponentTeam,
                                    innings = innings,
                                    batters = teamBatters,
                                    bowlers = opponentBowlers,
                                    playerMap = playerMap,
                                    onPlayerClick = onPlayerClick
                                )
                            }
                        }
                    }
                }

                val superOver = match.scorecard?.superOver
                if (superOver != null) {
                    item(key = "super_over_heading") {
                        Text("Super Over", style = MaterialTheme.typography.titleLarge, color = TextPrimarySlate)
                    }
                    for (innings in superOver.innings) {
                        val opponentId = if (innings.team == match.teamA) match.teamB else match.teamA
                        val figures = superOver.playerStats.orEmpty()
                        item(key = "super_over_${innings.team}") {
                            TeamInningsScorecardSection(
                                battingTeam = teamMap[innings.team],
                                battingTeamFallback = innings.team,
                                bowlingTeam = teamMap[opponentId],
                                innings = innings,
                                batters = figures.filter { it.team == innings.team && it.batted },
                                bowlers = figures.filter { it.team == opponentId && (it.ballsBowled > 0 || it.runsConceded > 0 || it.wickets > 0) },
                                playerMap = playerMap,
                                onPlayerClick = onPlayerClick
                            )
                        }
                    }
                }
                val replayed = scoringState.replayed
                // Load replay only for matches with a published scorecard.
                if (match.scorecard != null) item(key = "ball_by_ball_section") {
                    BallByBallSection(
                        scoringState = scoringState,
                        teamMap = teamMap,
                        playerMap = playerMap,
                        onSelectInnings = onSelectReplayInnings,
                        onRetryScoring = onRetryScoring,
                        onPlayerClick = onPlayerClick
                    )
                }
                // Innings Comparison Chart (for completed match with delivery data for both innings)
                if (match.status.equals("completed", ignoreCase = true) &&
                    replayed != null &&
                    replayed.canCompareInnings
                ) {
                    item(key = "innings_comparison_chart") {
                        InningsComparisonSection(
                            replayed = replayed,
                            teamMap = teamMap,
                            selectedMetric = scoringState.comparisonMetric,
                            onSelectMetric = onSelectComparisonMetric
                        )
                    }
                }

            } else {
                // Squads Tab
                item(key = "squads_section") {
                    MatchSquadsSection(
                        match = match,
                        teamMap = teamMap,
                        allPlayers = uiState.players,
                        playerMap = playerMap,
                        onPlayerClick = onPlayerClick,
                        onTeamClick = onTeamClick
                    )
                }
            }
        }
    }
}

private fun buildTeamOrderForScorecard(match: CeMatch): List<String> {
    val inningsTeams = match.scorecard?.innings?.map { it.team }?.filter { it.isNotBlank() } ?: emptyList()
    val ordered = mutableListOf<String>()
    for (t in inningsTeams) {
        if (t !in ordered) ordered.add(t)
    }
    if (match.teamA.isNotBlank() && match.teamA !in ordered) ordered.add(match.teamA)
    if (match.teamB.isNotBlank() && match.teamB !in ordered) ordered.add(match.teamB)
    return ordered
}

@Composable
private fun MatchSummaryHeaderCard(
    match: CeMatch,
    teamMap: Map<String, CeTeam>,
    onTeamClick: (String) -> Unit,
    onRefreshScore: () -> Unit,
    isRefreshing: Boolean
) {
    val inningsList = match.scorecard?.innings ?: emptyList()
    val inningsA = inningsList.firstOrNull { it.team == match.teamA }
    val inningsB = inningsList.firstOrNull { it.team == match.teamB }
    val teamA = teamMap[match.teamA]
    val teamB = teamMap[match.teamB]
    val isLive = match.status.equals("live", ignoreCase = true)

    Card(
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, if (isLive) LiveRed.copy(alpha = 0.4f) else DividerSlate),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StageBadge(match.stage)
                    Text(
                        text = "${match.overs} overs/side",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondarySlate
                    )
                }
                MatchStatusBadge(match.status)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = CricketCalculations.formatDhakaDateTime(match.startsAt),
                style = MaterialTheme.typography.labelSmall,
                color = TextMutedSlate
            )

            Spacer(modifier = Modifier.height(14.dp))

            DetailedInningsSummaryRow(
                team = teamA,
                fallbackName = "TBD",
                innings = inningsA,
                onClick = { if (match.teamA.isNotBlank()) onTeamClick(match.teamA) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            DetailedInningsSummaryRow(
                team = teamB,
                fallbackName = "TBD",
                innings = inningsB,
                onClick = { if (match.teamB.isNotBlank()) onTeamClick(match.teamB) }
            )

            val resultText = match.scorecard?.result?.takeIf { it.isNotBlank() }
            if (resultText != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = if (isLive) LiveRedBg.copy(alpha = 0.6f) else PaleBlueSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = resultText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLive) LiveRed else NavyHeader,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (isLive) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (match.updated.isNotBlank()) {
                            "Last update: ${CricketCalculations.formatDhakaDateTime(match.updated)}"
                        } else {
                            "Live match in progress"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondarySlate,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onRefreshScore,
                        enabled = !isRefreshing,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("refresh_score_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Refresh score",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailedInningsSummaryRow(
    team: CeTeam?,
    fallbackName: String,
    innings: CeInnings?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                TeamCrestAvatar(team = team, size = 34.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = team?.name?.ifBlank { fallbackName } ?: fallbackName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimarySlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (innings != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${innings.runs}/${innings.wickets}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimarySlate
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${CricketCalculations.formatOvers(innings.balls)} ov)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondarySlate
                        )
                    }
                    Text(
                        text = "RR ${CricketCalculations.formatRunRateOrEconomy(innings.runs, innings.balls)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedSlate
                    )
                }
            } else {
                Text(
                    text = "Yet to bat",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedSlate
                )
            }
        }

        if (innings?.endedEarly == true) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = GoldAmberBg,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Innings ended early",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldAmber,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentDeliveriesStrip(recent: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, DividerSlate),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Recent Deliveries",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimarySlate
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                recent.forEach { label ->
                    DeliveryChipBadge(label = label)
                }
            }
        }
    }
}

@Composable
private fun CurrentCreaseCard(
    currentBatters: List<String>,
    currentBowler: String?,
    playerMap: Map<String, CePlayer>,
    resolvedStats: List<PlayerMatchStat>,
    onPlayerClick: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, DividerSlate),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "At the Crease",
                style = MaterialTheme.typography.titleSmall,
                color = LavenderPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            currentBatters.forEachIndexed { index, batterId ->
                val player = playerMap[batterId]
                val stat = resolvedStats.firstOrNull { it.player == batterId }
                val isStriker = index == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayerClick(batterId) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${player?.name ?: batterId}${if (isStriker) " *" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Medium,
                        color = TextPrimarySlate
                    )
                    if (stat != null) {
                        Text(
                            text = "${stat.runs} (${stat.ballsFaced}b, ${stat.fours}x4, ${stat.sixes}x6)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondarySlate
                        )
                    }
                }
            }

            if (!currentBowler.isNullOrBlank()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = ScorecardSubtle
                )
                val bowler = playerMap[currentBowler]
                val bStat = resolvedStats.firstOrNull { it.player == currentBowler }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayerClick(currentBowler) }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bowler: ${bowler?.name ?: currentBowler}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimarySlate
                    )
                    if (bStat != null) {
                        Text(
                            text = "${ bStat.wickets }/${ bStat.runsConceded } (${CricketCalculations.formatOvers(bStat.ballsBowled)} ov)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondarySlate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamInningsScorecardSection(
    battingTeam: CeTeam?,
    battingTeamFallback: String,
    bowlingTeam: CeTeam?,
    innings: CeInnings?,
    batters: List<PlayerMatchStat>,
    bowlers: List<PlayerMatchStat>,
    playerMap: Map<String, CePlayer>,
    onPlayerClick: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, DividerSlate),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Innings Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyHeader, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = battingTeam?.name?.ifBlank { battingTeamFallback } ?: battingTeamFallback,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (innings != null) {
                    Text(
                        text = "${innings.runs}/${innings.wickets} (${CricketCalculations.formatOvers(innings.balls)} ov)",
                        style = MaterialTheme.typography.titleMedium,
                        color = PaleBlueSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (innings?.endedEarly == true) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Innings ended early",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldAmber,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Batting Table Header: Batter, R, B, 4s, 6s, SR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ScorecardSubtle, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Batter",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondarySlate,
                    modifier = Modifier.weight(1f)
                )
                ScoreColumnHeader("R", width = 34.dp)
                ScoreColumnHeader("B", width = 32.dp)
                ScoreColumnHeader("4s", width = 28.dp)
                ScoreColumnHeader("6s", width = 28.dp)
                ScoreColumnHeader("SR", width = 46.dp)
            }

            if (batters.isEmpty()) {
                Text(
                    text = "No batting figures recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedSlate,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                batters.forEachIndexed { idx, st ->
                    val player = playerMap[st.player]
                    val runsDisplay = "${st.runs}${if (st.notOut) "*" else ""}"
                    val srDisplay = if (st.hasRecorded("runs") && st.hasRecorded("balls_faced"))
                        CricketCalculations.formatStrikeRate(st.runs, st.ballsFaced) else "—"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (st.player.isNotBlank()) onPlayerClick(st.player) }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .testTag("scorecard_batter_${st.player}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (player?.name?.ifBlank { st.player } ?: st.player) + if (battingTeam?.captain == st.player) " (C)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = LavenderPrimary
                            )
                            val statusNote = when {
                                st.retired -> "Retired"
                                st.notOut -> "not out"
                                else -> "out"
                            }
                            Text(
                                text = statusNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (st.retired) GoldAmber else TextMutedSlate
                            )
                        }
                        ScoreColumnCell(runsDisplay, width = 34.dp, bold = true)
                        ScoreColumnCell(st.display("balls_faced", st.ballsFaced), width = 32.dp)
                        ScoreColumnCell(st.display("fours", st.fours), width = 28.dp)
                        ScoreColumnCell(st.display("sixes", st.sixes), width = 28.dp)
                        ScoreColumnCell(srDisplay, width = 46.dp)
                    }
                    if (idx < batters.lastIndex) {
                        HorizontalDivider(color = ScorecardSubtle)
                    }
                }
            }

            // Extras & Adjusted Runs
            if (innings != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = DividerSlate
                )
                val extrasVal = innings.extras?.toString() ?: "—"
                val breakdownText = innings.extrasBreakdown?.let { eb ->
                    "(w ${eb.wide}, nb ${eb.noBall}, b ${eb.bye}, lb ${eb.legBye})"
                } ?: ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Extras $breakdownText".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondarySlate
                    )
                    Text(
                        text = extrasVal.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimarySlate
                    )
                }

                val adj = innings.adjustedRuns ?: 0
                if (adj != 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Adjusted runs",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = GoldAmber
                        )
                        Text(
                            text = CricketCalculations.formatSignedAdjustment(adj),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = GoldAmber
                        )
                    }
                }
            }

            if (innings != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Fall of wickets", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = TextPrimarySlate)
                if (innings.fallOfWickets.isEmpty()) {
                    Text(if (innings.wickets == 0) "No wickets lost." else "Wicket details were not recorded for this innings.",
                        style = MaterialTheme.typography.bodySmall, color = TextMutedSlate,
                        modifier = Modifier.padding(top = 6.dp))
                } else {
                    innings.fallOfWickets.forEach { fall ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
                            .testTag("fall_of_wicket_${innings.team}_${fall.wicket}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${fall.runs}/${fall.wicket}", fontWeight = FontWeight.Bold,
                                color = LavenderPrimary, modifier = Modifier.width(60.dp))
                            Text(playerMap[fall.player]?.name ?: "Player",
                                style = MaterialTheme.typography.bodyMedium, color = TextPrimarySlate,
                                modifier = Modifier.weight(1f).clickable { onPlayerClick(fall.player) })
                            Text("${CricketCalculations.formatOvers(fall.balls)} ov",
                                style = MaterialTheme.typography.bodySmall, color = TextSecondarySlate,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            // Bowling Table Header: Bowler, O, R, W
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ScorecardSubtle, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (bowlingTeam != null) "Bowler (${bowlingTeam.shortName.ifBlank { bowlingTeam.name }})" else "Bowler",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondarySlate,
                    modifier = Modifier.weight(1f)
                )
                ScoreColumnHeader("O", width = 40.dp)
                ScoreColumnHeader("R", width = 36.dp)
                ScoreColumnHeader("W", width = 36.dp)
                ScoreColumnHeader("Econ", width = 48.dp)
            }

            if (bowlers.isEmpty()) {
                Text(
                    text = "No bowling figures recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedSlate,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                bowlers.forEachIndexed { idx, st ->
                    val player = playerMap[st.player]
                    val oversStr = CricketCalculations.formatOvers(st.ballsBowled)
                    val econStr = CricketCalculations.formatRunRateOrEconomy(st.runsConceded, st.ballsBowled)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (st.player.isNotBlank()) onPlayerClick(st.player) }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .testTag("scorecard_bowler_${st.player}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (player?.name?.ifBlank { st.player } ?: st.player) + if (bowlingTeam?.captain == st.player) " (C)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = LavenderPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        ScoreColumnCell(oversStr, width = 40.dp)
                        ScoreColumnCell(st.runsConceded.toString(), width = 36.dp)
                        ScoreColumnCell(st.wickets.toString(), width = 36.dp, bold = true)
                        ScoreColumnCell(econStr, width = 48.dp)
                    }
                    if (idx < bowlers.lastIndex) {
                        HorizontalDivider(color = ScorecardSubtle)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreColumnHeader(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = TextSecondarySlate,
        textAlign = TextAlign.End,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun ScoreColumnCell(
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
private fun InningsComparisonSection(
    replayed: ReplayedMatchLog,
    teamMap: Map<String, CeTeam>,
    selectedMetric: ComparisonMetric,
    onSelectMetric: (ComparisonMetric) -> Unit
) {
    val innings1 = replayed.innings.getOrNull(0) ?: return
    val innings2 = replayed.innings.getOrNull(1) ?: return
    val team1 = teamMap[innings1.teamId]
    val team2 = teamMap[innings2.teamId]
    val color1 = parseTeamColor(team1?.color ?: "", LavenderPrimary)
    val rawColor2 = parseTeamColor(team2?.color ?: "", LiveRed)
    val color2 = if (rawColor2 == color1) LiveRed else rawColor2

    Card(
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, DividerSlate),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("innings_comparison_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Innings comparison",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimarySlate
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Toggles for Runs, Wickets, and Run rate
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val metrics = listOf(
                    ComparisonMetric.RUNS to "Runs",
                    ComparisonMetric.WICKETS to "Wickets",
                    ComparisonMetric.RUN_RATE to "Run rate"
                )
                for ((m, label) in metrics) {
                    val selected = selectedMetric == m
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMetric(m) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("comparison_metric_${m.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(
                    color = color1,
                    label = team1?.shortName?.ifBlank { team1.name } ?: "Innings 1"
                )
                LegendItem(
                    color = color2,
                    label = team2?.shortName?.ifBlank { team2.name } ?: "Innings 2"
                )
                if (selectedMetric == ComparisonMetric.RUNS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LiveRed)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Wicket",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMutedSlate
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val maxOvers = maxOf(
                replayed.oversQuota.toDouble(),
                innings1.chartPoints.maxOfOrNull { it.xOvers } ?: 1.0,
                innings2.chartPoints.maxOfOrNull { it.xOvers } ?: 1.0,
                1.0
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(ScorecardSubtle, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                val widthPx = size.width
                val heightPx = size.height
                if (widthPx <= 0f || heightPx <= 0f) return@Canvas

                fun extractSeries(points: List<ReplayChartPoint>): List<Pair<Double, Double>> {
                    return when (selectedMetric) {
                        ComparisonMetric.RUNS ->
                            listOf(0.0 to 0.0) + points.map { it.xOvers to it.runs.toDouble() }
                        ComparisonMetric.WICKETS ->
                            listOf(0.0 to 0.0) + points.map { it.xOvers to it.wickets.toDouble() }
                        ComparisonMetric.RUN_RATE ->
                            // Skip points with zero legal balls on the run-rate chart
                            points.filter { it.legalBalls > 0 && it.runRate != null }
                                .map { it.xOvers to (it.runRate ?: 0.0) }
                    }
                }

                val series1 = extractSeries(innings1.chartPoints)
                val series2 = extractSeries(innings2.chartPoints)
                val maxY = maxOf(
                    series1.maxOfOrNull { it.second } ?: 1.0,
                    series2.maxOfOrNull { it.second } ?: 1.0,
                    1.0
                ) * 1.1

                // Horizontal grid lines
                for (i in 0..4) {
                    val y = heightPx * (i / 4f)
                    drawLine(
                        color = DividerSlate,
                        start = Offset(0f, y),
                        end = Offset(widthPx, y),
                        strokeWidth = 1f
                    )
                }

                fun drawTeamLine(series: List<Pair<Double, Double>>, lineColor: Color) {
                    if (series.size < 2) return
                    val path = Path()
                    series.forEachIndexed { idx, (xOv, yVal) ->
                        val px = ((xOv / maxOvers).toFloat().coerceIn(0f, 1f)) * widthPx
                        val py = heightPx - ((yVal / maxY).toFloat().coerceIn(0f, 1f)) * heightPx
                        if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 5f)
                    )
                }

                drawTeamLine(series1, color1)
                drawTeamLine(series2, color2)

                // Mark wicket deliveries on the runs chart
                if (selectedMetric == ComparisonMetric.RUNS) {
                    for (pt in innings1.chartPoints.filter { it.isWicket }) {
                        val px = ((pt.xOvers / maxOvers).toFloat().coerceIn(0f, 1f)) * widthPx
                        val py = heightPx - ((pt.runs / maxY).toFloat().coerceIn(0f, 1f)) * heightPx
                        drawCircle(color = Color.White, radius = 7f, center = Offset(px, py))
                        drawCircle(color = color1, radius = 5f, center = Offset(px, py))
                    }
                    for (pt in innings2.chartPoints.filter { it.isWicket }) {
                        val px = ((pt.xOvers / maxOvers).toFloat().coerceIn(0f, 1f)) * widthPx
                        val py = heightPx - ((pt.runs / maxY).toFloat().coerceIn(0f, 1f)) * heightPx
                        drawCircle(color = Color.White, radius = 7f, center = Offset(px, py))
                        drawCircle(color = color2, radius = 5f, center = Offset(px, py))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0.0 ov",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedSlate
                )
                Text(
                    text = String.format(Locale.US, "%.1f ov", maxOvers),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedSlate
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp, 4.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimarySlate
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BallByBallSection(
    scoringState: MatchScoringUiState,
    teamMap: Map<String, CeTeam>,
    playerMap: Map<String, CePlayer>,
    onSelectInnings: (Int) -> Unit,
    onRetryScoring: () -> Unit,
    onPlayerClick: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, DividerSlate),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ball_by_ball_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Ball by ball",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimarySlate
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (scoringState.state == ScoringLoadState.LOADED && scoringState.errorMessage != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Showing the previous ball log. " + scoringState.errorMessage,
                        modifier = Modifier.weight(1f),
                        color = LiveRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onRetryScoring) { Text("Retry") }
                }
            }

            when (scoringState.state) {
                ScoringLoadState.IDLE, ScoringLoadState.LOADING -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = LavenderPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Loading ball-by-ball replay…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondarySlate
                        )
                    }
                }

                ScoringLoadState.MISSING -> {
                    Text(
                        text = "Detailed ball-by-ball delivery log is not available for this match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedSlate,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                ScoringLoadState.ERROR -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = scoringState.errorMessage ?: "Could not load ball-by-ball log.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LiveRed,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetryScoring) {
                            Text("Retry")
                        }
                    }
                }

                ScoringLoadState.LOADED -> {
                    val replayed = scoringState.replayed
                    if (replayed == null || replayed.innings.isEmpty()) {
                        Text(
                            text = "No deliveries recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedSlate
                        )
                    } else {
                        val activeInningsIdx = (scoringState.selectedInningsIndex ?: replayed.innings.lastIndex)
                            .coerceIn(0, replayed.innings.lastIndex)
                        val activeInnings = replayed.innings[activeInningsIdx]

                        // Switch batting innings (latest innings selected by default)
                        if (replayed.innings.size > 1) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                replayed.innings.forEachIndexed { idx, inn ->
                                    val tName = teamMap[inn.teamId]?.shortName?.ifBlank { teamMap[inn.teamId]?.name }
                                        ?: "Innings ${idx + 1}"
                                    val selected = idx == activeInningsIdx
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectInnings(idx) },
                                        label = {
                                            Text("$tName (${inn.totalRuns}/${inn.totalWickets})")
                                        },
                                        modifier = Modifier.testTag("replay_innings_chip_$idx")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Show signed run adjustments with their score and overs separately from deliveries
                        if (activeInnings.adjustments.isNotEmpty()) {
                            Surface(
                                color = GoldAmberBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Score Adjustments",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldAmber
                                    )
                                    activeInnings.adjustments.forEach { adj ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Adjustment ${adj.signedText} runs (at ${adj.oversLabel} ov)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextPrimarySlate
                                            )
                                            Text(
                                                text = "${adj.runningRuns}/${adj.runningWickets}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimarySlate
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Expandable over groups, latest over first, most recent open by default
                        val newestOverNum = activeInnings.overGroupsNewestFirst.firstOrNull()?.overNumber ?: -1
                        activeInnings.overGroupsNewestFirst.forEach { overGroup ->
                            ExpandableOverGroupCard(
                                matchId = scoringState.matchId,
                                inningsIdx = activeInningsIdx,
                                overGroup = overGroup,
                                initiallyExpanded = overGroup.overNumber == newestOverNum,
                                playerMap = playerMap,
                                onPlayerClick = onPlayerClick
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableOverGroupCard(
    matchId: String,
    inningsIdx: Int,
    overGroup: ReplayOverGroup,
    initiallyExpanded: Boolean,
    playerMap: Map<String, CePlayer>,
    onPlayerClick: (String) -> Unit
) {
    var expanded by rememberSaveable(matchId, inningsIdx, overGroup.overNumber) {
        mutableStateOf(initiallyExpanded)
    }

    Surface(
        color = ScorecardSubtle,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, DividerSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("over_group_header_${overGroup.overNumber}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Over ${overGroup.overNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NavyHeader
                    )
                    Text(
                        text = "(${overGroup.deliveries.size} balls)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedSlate
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${overGroup.endOfOverRuns}/${overGroup.endOfOverWickets}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimarySlate
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse over" else "Expand over",
                        tint = TextSecondarySlate
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ScorecardWhite)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Within each over, preserve original delivery order
                    overGroup.deliveries.forEachIndexed { idx, del ->
                        val bowlerName = del.bowlerId?.let { playerMap[it]?.name ?: it } ?: "Bowler"
                        val batterName = del.batterId?.let { playerMap[it]?.name ?: it } ?: "Batter"
                        val dismissedName = del.dismissedPlayerId?.let { playerMap[it]?.name ?: it }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = del.ballPosition,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondarySlate,
                                modifier = Modifier.width(34.dp)
                            )
                            DeliveryChipBadge(label = del.resultChip)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$bowlerName to $batterName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimarySlate,
                                    modifier = Modifier.clickable(enabled = !del.batterId.isNullOrBlank()) {
                                        del.batterId?.let(onPlayerClick)
                                    }
                                )
                                if (!del.dismissalText.isNullOrBlank()) {
                                    Text(
                                        text = if (!dismissedName.isNullOrBlank()) {
                                            "WICKET: $dismissedName (${del.dismissalText})"
                                        } else {
                                            "WICKET: ${del.dismissalText}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = LiveRed
                                    )
                                }
                            }
                            Text(
                                text = "${del.runningRuns}/${del.runningWickets}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimarySlate
                            )
                        }
                        if (idx < overGroup.deliveries.lastIndex) {
                            HorizontalDivider(color = ScorecardSubtle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchSquadsSection(
    match: CeMatch,
    teamMap: Map<String, CeTeam>,
    allPlayers: List<CePlayer>,
    playerMap: Map<String, CePlayer>,
    onPlayerClick: (String) -> Unit,
    onTeamClick: (String) -> Unit
) {
    val teamIds = listOf(match.teamA, match.teamB).filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (teamId in teamIds) {
            val team = teamMap[teamId]
            val playingSideIds = match.scorecard?.playingSides
                ?.firstOrNull { it.team == teamId }
                ?.players
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val squadPlayers: List<CePlayer> = if (playingSideIds.isNotEmpty()) {
                playingSideIds.map { pid ->
                    playerMap[pid] ?: CePlayer(id = pid, name = pid, team = teamId)
                }
            } else {
                // Fallback to that team's roster via ce_players.team
                allPlayers.filter { it.team == teamId }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                border = BorderStroke(1.dp, DividerSlate),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTeamClick(teamId) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamCrestAvatar(team = team, size = 32.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = team?.name?.ifBlank { teamId } ?: teamId,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimarySlate
                                )
                                Text(
                                    text = if (playingSideIds.isNotEmpty()) {
                                        "Match Lineup (${squadPlayers.size})"
                                    } else {
                                        "Season Roster (${squadPlayers.size})"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMutedSlate
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = DividerSlate)

                    if (squadPlayers.isEmpty()) {
                        Text(
                            text = "No players listed for this team.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedSlate,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        squadPlayers.forEachIndexed { idx, player ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayerClick(player.id) }
                                    .padding(vertical = 8.dp)
                                    .testTag("squad_player_${player.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlayerAvatar(
                                    player = player,
                                    teamColorHex = team?.color ?: "",
                                    size = 36.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = player.name + if (team?.captain == player.id) " (C)" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimarySlate
                                    )
                                    val metaParts = listOfNotNull(
                                        player.role.takeIf { it.isNotBlank() },
                                        player.batch.takeIf { it.isNotBlank() }?.let { "Batch $it" }
                                    )
                                    if (metaParts.isNotEmpty()) {
                                        Text(
                                            text = metaParts.joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondarySlate
                                        )
                                    }
                                }
                            }
                            if (idx < squadPlayers.lastIndex) {
                                HorizontalDivider(color = ScorecardSubtle)
                            }
                        }
                    }
                }
            }
        }
    }
}
