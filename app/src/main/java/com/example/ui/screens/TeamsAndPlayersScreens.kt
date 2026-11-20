package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CricketCalculations
import com.example.model.CePlayer
import com.example.ui.CricketUiState
import com.example.ui.components.*
import com.example.ui.theme.*
import java.time.Instant
import java.util.Locale

@Composable
fun TeamsAndPlayersScreen(
    uiState: CricketUiState,
    onTeamClick: (String) -> Unit,
    onPlayerClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val playersByTeam = remember(uiState.players) {
        uiState.players.groupBy { it.team }
    }
    val teamMap = remember(uiState.teams) {
        uiState.teams.associateBy { it.id }
    }

    val seasonTitle = uiState.activeSeason?.let {
        if (it.name.isNotBlank()) it.name else "Season ${it.number}"
    } ?: "Active Season"

    val matchingPlayers = remember(uiState.players, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.US)
        if (q.isEmpty()) emptyList()
        else uiState.players.filter {
            it.name.lowercase(Locale.US).contains(q) ||
                it.role.lowercase(Locale.US).contains(q) ||
                it.batch.lowercase(Locale.US).contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CrickTopHeader(
            title = "Teams & Players",
            subtitle = "$seasonTitle • ${uiState.teams.size} Teams",
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
                .testTag("teams_players_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "player_search_bar") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search players by name, role, or batch…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search players"
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ScorecardWhite,
                        unfocusedContainerColor = ScorecardWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_search_input")
                )
            }

            if (searchQuery.isNotBlank()) {
                item(key = "search_results_header") {
                    Text(
                        text = "Matching Players (${matchingPlayers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimarySlate
                    )
                }
                if (matchingPlayers.isEmpty()) {
                    item(key = "empty_search") {
                        Text(
                            text = "No players match \"$searchQuery\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondarySlate,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(
                        items = matchingPlayers,
                        key = { "search_player_${it.id}" }
                    ) { player ->
                        val team = teamMap[player.team]
                        PlayerRowCard(
                            player = player,
                            teamColorHex = team?.color ?: "",
                            teamName = team?.shortName?.ifBlank { team.name },
                            onClick = { onPlayerClick(player.id) }
                        )
                    }
                }
            } else {
                item(key = "teams_section_title") {
                    Text(
                        text = "Participating Teams",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimarySlate
                    )
                }

                if (uiState.teams.isEmpty()) {
                    item(key = "empty_teams") {
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
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = TextMutedSlate,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No teams registered for this season.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondarySlate
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.teams,
                        key = { "team_${it.id}" }
                    ) { team ->
                        val rosterCount = playersByTeam[team.id]?.size ?: 0
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTeamClick(team.id) }
                                .testTag("team_list_card_${team.id}"),
                            colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                            border = BorderStroke(1.dp, DividerSlate),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    TeamCrestAvatar(team = team, size = 44.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = team.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimarySlate
                                        )
                                        val sub = buildString {
                                            if (team.shortName.isNotBlank()) {
                                                append(team.shortName)
                                                append(" • ")
                                            }
                                            append("$rosterCount players")
                                        }
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondarySlate
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "View ${team.name}",
                                    tint = TextMutedSlate
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeamDetailScreen(
    teamId: String,
    uiState: CricketUiState,
    onBack: () -> Unit,
    onPlayerClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val team = uiState.teams.firstOrNull { it.id == teamId }
    val teamMap = remember(uiState.teams) { uiState.teams.associateBy { it.id } }
    val squad = remember(uiState.players, teamId) {
        uiState.players.filter { it.team == teamId }
    }
    val teamMatches = remember(uiState.matches, teamId) {
        uiState.matches
            .filter { it.published && (it.teamA == teamId || it.teamB == teamId) }
            .sortedWith(
                compareBy<com.example.model.CeMatch> {
                    CricketCalculations.parsePocketBaseInstant(it.startsAt) ?: Instant.MAX
                }.thenBy { it.startsAt }
            )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CrickTopHeader(
            title = team?.name ?: "Team Profile",
            subtitle = team?.shortName?.takeIf { it.isNotBlank() }?.let { "$it • ${squad.size} Players" },
            onBack = onBack,
            onRefresh = onRefresh,
            isRefreshing = uiState.isRefreshing
        )

        if (uiState.errorMessage != null) {
            ConnectionErrorBanner(uiState.errorMessage, uiState.lastSuccessFormattedDhaka, onRefresh)
        }

        if (team == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Team not found in the active season.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("team_detail_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "team_header_card") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                    border = BorderStroke(1.dp, DividerSlate),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TeamCrestAvatar(team = team, size = 60.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = team.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimarySlate
                            )
                            Text(
                                text = "${squad.size} Squad Members • ${teamMatches.size} Published Matches",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondarySlate
                            )
                        }
                    }
                }
            }

            if (team.ownerName.isNotBlank() || team.ownerPhoto.isNotBlank()) {
                item(key = "team_owner") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                        border = BorderStroke(1.dp, DividerSlate),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val photoUrl = CricketCalculations.buildFileUrl(team.collectionId, team.id, team.ownerPhoto)
                            if (photoUrl != null) {
                                SubcomposeAsyncImage(
                                    model = "$photoUrl?thumb=256x256",
                                    contentDescription = "${team.ownerName.ifBlank { "Team owner" }} photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(CircleShape),
                                    loading = { Icon(Icons.Default.Person, contentDescription = null) },
                                    error = { Icon(Icons.Default.Person, contentDescription = null) }
                                )
                                Spacer(Modifier.width(14.dp))
                            }
                            Column {
                                Text("TEAM OWNER", style = MaterialTheme.typography.labelSmall, color = TextSecondarySlate)
                                Text(team.ownerName.ifBlank { "Team owner" }, style = MaterialTheme.typography.titleMedium, color = TextPrimarySlate)
                            }
                        }
                    }
                }
            }

            item(key = "squad_heading") {
                val captain = squad.find { it.id == team.captain }
                if (captain != null) {
                    Text("TEAM CAPTAIN", style = MaterialTheme.typography.labelSmall, color = TextSecondarySlate)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        PlayerAvatar(player = captain, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(captain.name, style = MaterialTheme.typography.titleMedium, color = TextPrimarySlate)
                    }
                }
                if (team.managerName.isNotBlank() || team.managerPhoto.isNotBlank()) {
                    Text("TEAM MANAGER", style = MaterialTheme.typography.labelSmall, color = TextSecondarySlate)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        val managerPhoto = CricketCalculations.buildFileUrl(team.collectionId, team.id, team.managerPhoto)
                        if (managerPhoto != null) {
                            SubcomposeAsyncImage(
                                model = managerPhoto,
                                contentDescription = "Team manager",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(CircleShape),
                                loading = { Icon(Icons.Default.Person, contentDescription = null) },
                                error = { Icon(Icons.Default.Person, contentDescription = null) }
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(team.managerName.ifBlank { "Team manager" }, style = MaterialTheme.typography.titleMedium, color = TextPrimarySlate)
                    }
                }
                GraphicCardActions(team.name, "Team", Modifier.fillMaxWidth().padding(bottom = 12.dp)) { context ->
                    renderTeamCard(context, team, squad, uiState.activeSeason?.name.orEmpty())
                }
                Text(
                    text = "Full Squad (${squad.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimarySlate,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (squad.isEmpty()) {
                item(key = "empty_squad") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                        border = BorderStroke(1.dp, DividerSlate),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No squad members listed for this team.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMutedSlate,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(
                    items = squad,
                    key = { "squad_member_${it.id}" }
                ) { player ->
                    PlayerRowCard(
                        player = player,
                        teamColorHex = team.color,
                        teamName = null,
                        isCaptain = player.id == team.captain,
                        onClick = { onPlayerClick(player.id) }
                    )
                }
            }

            item(key = "team_matches_heading") {
                Text(
                    text = "Season Matches (${teamMatches.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimarySlate,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (teamMatches.isEmpty()) {
                item(key = "empty_team_matches") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                        border = BorderStroke(1.dp, DividerSlate),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No published matches for this team yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMutedSlate,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(
                    items = teamMatches,
                    key = { "team_match_${it.id}" }
                ) { match ->
                    CompactMatchCard(
                        match = match,
                        teamMap = teamMap,
                        onClick = { onMatchClick(match.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerRowCard(
    player: CePlayer,
    teamColorHex: String,
    teamName: String?,
    isCaptain: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("player_row_${player.id}"),
        colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
        border = BorderStroke(1.dp, DividerSlate),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerAvatar(
                player = player,
                teamColorHex = teamColorHex,
                size = 42.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name + if (isCaptain) " (C)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimarySlate
                )
                val details = listOfNotNull(
                    teamName?.takeIf { it.isNotBlank() },
                    player.role.takeIf { it.isNotBlank() },
                    player.batch.takeIf { it.isNotBlank() }?.let { "Batch $it" }
                )
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondarySlate
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open ${player.name}",
                tint = TextMutedSlate,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun PlayerProfileScreen(
    playerId: String,
    uiState: CricketUiState,
    onBack: () -> Unit,
    onTeamClick: (String) -> Unit,
    onMatchClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val player = uiState.players.firstOrNull { it.id == playerId }
    if (player == null) {
        Column(Modifier.fillMaxSize()) {
            CrickTopHeader(title = "Player unavailable", onBack = onBack, onRefresh = onRefresh)
            Text("This player is unavailable. Refresh to load the latest records.", Modifier.padding(24.dp))
        }
        return
    }

    val summary = remember(player, uiState.teams, uiState.matches, uiState.legacyStatsByMatchId) {
        CricketCalculations.computePlayerProfile(
            player = player,
            teams = uiState.teams,
            matches = uiState.matches,
            legacyStatsByMatchId = uiState.legacyStatsByMatchId
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CrickTopHeader(
            title = summary.player.name,
            subtitle = listOfNotNull(
                summary.player.role.takeIf { it.isNotBlank() },
                summary.team?.name
            ).joinToString(" • "),
            onBack = onBack,
            onRefresh = onRefresh,
            isRefreshing = uiState.isRefreshing
        )

        if (uiState.errorMessage != null) {
            ConnectionErrorBanner(uiState.errorMessage, uiState.lastSuccessFormattedDhaka, onRefresh)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("player_profile_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Player identity header card
            item(key = "player_identity_card") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                    border = BorderStroke(1.dp, DividerSlate),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayerAvatar(
                                player = summary.player,
                                teamColorHex = summary.team?.color ?: "",
                                size = 64.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = summary.player.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TextPrimarySlate
                                )
                                val meta = listOfNotNull(
                                    summary.player.role.takeIf { it.isNotBlank() },
                                    summary.player.batch.takeIf { it.isNotBlank() }?.let { "Batch $it" }
                                )
                                if (meta.isNotEmpty()) {
                                    Text(
                                        text = meta.joinToString(" • "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondarySlate
                                    )
                                }
                                if (summary.team != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = PaleBlueSurface,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .clickable { onTeamClick(summary.team.id) }
                                            .testTag("player_profile_team_link")
                                    ) {
                                        Text(
                                            text = summary.team.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = LavenderPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = ScorecardSubtle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Completed Season Matches: ${summary.matchCount}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyHeader
                        )
                    }
                }
            }

            item(key = "player_graphic_card") {
                GraphicCardActions(player.name, "Player", Modifier.fillMaxWidth()) { context ->
                    renderPlayerCard(context, player, summary.team, uiState.activeSeason?.name.orEmpty())
                }
            }
            item(key = "player_batting_section") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                    border = BorderStroke(1.dp, DividerSlate),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Batting Summary",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimarySlate
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatPillBox("Runs", summary.totalRuns.toString(), Modifier.weight(1f))
                            StatPillBox("HS", summary.highestScoreDisplay, Modifier.weight(1f))
                            StatPillBox("4s / 6s", "${summary.totalFours} / ${summary.totalSixes}", Modifier.weight(1f))
                            StatPillBox("SR", summary.seasonStrikeRateDisplay, Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (summary.battingRows.isEmpty()) {
                            EmptyStatNotice("No batting innings in finalized matches this season.")
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ScorecardSubtle, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Opponent & Date",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondarySlate,
                                    modifier = Modifier.weight(1f)
                                )
                                ProfileColHeader("R", 38.dp)
                                ProfileColHeader("B", 34.dp)
                                ProfileColHeader("4s", 30.dp)
                                ProfileColHeader("6s", 30.dp)
                            }

                            summary.battingRows.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onMatchClick(row.match.id) }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                        .testTag("player_batting_match_${row.match.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "vs ${row.opponent?.name ?: "Opponent"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = LavenderPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = CricketCalculations.formatDhakaShortDate(row.match.startsAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMutedSlate
                                        )
                                    }
                                    val runsStr = "${row.runs}${if (row.notOut) "*" else ""}"
                                    ProfileColCell(runsStr, 38.dp, bold = true)
                                    ProfileColCell(row.ballsFaced.toString(), 34.dp)
                                    ProfileColCell(row.fours.toString(), 30.dp)
                                    ProfileColCell(row.sixes.toString(), 30.dp)
                                }
                                if (idx < summary.battingRows.lastIndex) {
                                    HorizontalDivider(color = ScorecardSubtle)
                                }
                            }
                        }
                    }
                }
            }

            // Bowling Summary & Match-by-match table
            item(key = "player_bowling_section") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ScorecardWhite),
                    border = BorderStroke(1.dp, DividerSlate),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Bowling Summary",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimarySlate
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatPillBox("Wickets", summary.totalWickets.toString(), Modifier.weight(1f))
                            StatPillBox("Overs", summary.totalOversDisplay, Modifier.weight(1f))
                            StatPillBox("Best", summary.bestBowlingDisplay, Modifier.weight(1f))
                            StatPillBox("Econ", summary.seasonEconomyDisplay, Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (summary.bowlingRows.isEmpty()) {
                            EmptyStatNotice("No bowling figures in finalized matches this season.")
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ScorecardSubtle, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Opponent & Date",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondarySlate,
                                    modifier = Modifier.weight(1f)
                                )
                                ProfileColHeader("O", 42.dp)
                                ProfileColHeader("R", 38.dp)
                                ProfileColHeader("W", 38.dp)
                            }

                            summary.bowlingRows.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onMatchClick(row.match.id) }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                        .testTag("player_bowling_match_${row.match.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "vs ${row.opponent?.name ?: "Opponent"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = LavenderPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = CricketCalculations.formatDhakaShortDate(row.match.startsAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMutedSlate
                                        )
                                    }
                                    ProfileColCell(row.oversDisplay, 42.dp)
                                    ProfileColCell(row.runsConceded.toString(), 38.dp)
                                    ProfileColCell(row.wickets.toString(), 38.dp, bold = true)
                                }
                                if (idx < summary.bowlingRows.lastIndex) {
                                    HorizontalDivider(color = ScorecardSubtle)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPillBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ScorecardSubtle,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, DividerSlate),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NavyHeader
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMutedSlate
            )
        }
    }
}

@Composable
private fun EmptyStatNotice(message: String) {
    Surface(
        color = ScorecardSubtle,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = TextMutedSlate,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondarySlate
            )
        }
    }
}

@Composable
private fun ProfileColHeader(text: String, width: androidx.compose.ui.unit.Dp) {
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
private fun ProfileColCell(
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
