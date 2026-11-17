package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay

enum class BottomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    TEAMS_PLAYERS("Teams & Players", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_tab_teams_players"),
    POINTS_STATS("Points & Stats", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard, "nav_tab_points_stats"),
    HISTORY("History", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents, "nav_tab_history")
}

@Composable
fun CrickEnglishApp(
    viewModel: CricketViewModel = viewModel(factory = CricketViewModel.Factory()),
    enablePolling: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Saveable navigation stack represented as string entries:
    // "tab:HOME", "tab:TEAMS_PLAYERS", "tab:POINTS_STATS", "tab:HISTORY",
    // "match:<id>", "team:<id>", "player:<id>"
    var navStack by rememberSaveable { mutableStateOf(listOf("tab:HOME")) }
    val currentRoute = navStack.lastOrNull() ?: "tab:HOME"

    fun pushRoute(route: String) {
        if (navStack.lastOrNull() != route) {
            navStack = navStack + route
        }
    }

    fun selectBottomTab(tab: BottomNavTab) {
        val target = "tab:${tab.name}"
        navStack = listOf(target)
    }

    fun popBack() {
        if (navStack.size > 1) {
            navStack = navStack.dropLast(1)
        } else if (currentRoute != "tab:HOME") {
            navStack = listOf("tab:HOME")
        }
    }

    BackHandler(enabled = navStack.size > 1 || currentRoute != "tab:HOME") {
        popBack()
    }

    val activeBottomTab: BottomNavTab = remember(navStack) {
        val lastTabEntry = navStack.lastOrNull { it.startsWith("tab:") }
        when (lastTabEntry?.removePrefix("tab:")) {
            BottomNavTab.TEAMS_PLAYERS.name -> BottomNavTab.TEAMS_PLAYERS
            BottomNavTab.POINTS_STATS.name -> BottomNavTab.POINTS_STATS
            BottomNavTab.HISTORY.name -> BottomNavTab.HISTORY
            else -> BottomNavTab.HOME
        }
    }

    // Track foreground state for 10-second polling and immediate foreground refresh
    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember { mutableStateOf(true) }
    var resumeCount by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isForeground = true
                    resumeCount += 1
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isForeground = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isHomeVisible = currentRoute == "tab:HOME"
    val openMatchId = if (currentRoute.startsWith("match:")) currentRoute.removePrefix("match:") else null
    val isMatchDetailVisible = openMatchId != null

    // Fetch scoring log whenever a match detail screen is opened or its updated timestamp changes
    val currentMatchUpdated = remember(openMatchId, uiState.matches) {
        openMatchId?.let { id -> uiState.matches.firstOrNull { it.id == id }?.updated }
    }
    LaunchedEffect(openMatchId, currentMatchUpdated) {
        if (openMatchId != null) {
            viewModel.loadMatchScoring(openMatchId, force = false)
        }
    }

    // Immediate refresh when returning to foreground while Home or Match details is visible,
    // and periodic 10-second polling while in foreground on Home or Match details.
    LaunchedEffect(enablePolling, isForeground, isHomeVisible, openMatchId, resumeCount) {
        if (!enablePolling || !isForeground) return@LaunchedEffect
        if (!isHomeVisible && !isMatchDetailVisible) return@LaunchedEffect

        if (resumeCount > 1 && uiState.hasLoadedOnce) {
            viewModel.refreshPublishedMatches(selectedMatchIdForScoring = openMatchId)
        }

        while (isForeground && (isHomeVisible || isMatchDetailVisible)) {
            delay(10_000L)
            if (isForeground && (isHomeVisible || isMatchDetailVisible)) {
                viewModel.refreshPublishedMatches(selectedMatchIdForScoring = openMatchId)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LightBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = ScorecardWhite,
                tonalElevation = 4.dp
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val selected = activeBottomTab == tab && currentRoute == "tab:${tab.name}"
                    val highlighted = activeBottomTab == tab
                    NavigationBarItem(
                        selected = highlighted,
                        onClick = {
                            if (selected) return@NavigationBarItem
                            selectBottomTab(tab)
                        },
                        icon = {
                            Icon(
                                imageVector = if (highlighted) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NavyHeader,
                            selectedTextColor = NavyHeader,
                            indicatorColor = PaleBlueSurface,
                            unselectedIconColor = TextSecondarySlate,
                            unselectedTextColor = TextSecondarySlate
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                currentRoute.startsWith("match:") -> {
                    val matchId = currentRoute.removePrefix("match:")
                    MatchDetailScreen(
                        matchId = matchId,
                        uiState = uiState,
                        onBack = ::popBack,
                        onRefreshScore = {
                            viewModel.refreshPublishedMatches(
                                selectedMatchIdForScoring = matchId,
                                forceScoringFetch = true
                            )
                        },
                        onTabSelected = { tabIdx ->
                            viewModel.setMatchDetailTab(matchId, tabIdx)
                        },
                        onSelectReplayInnings = { innIdx ->
                            viewModel.setSelectedReplayInnings(matchId, innIdx)
                        },
                        onSelectComparisonMetric = { metric ->
                            viewModel.setComparisonMetric(matchId, metric)
                        },
                        onPlayerClick = { playerId ->
                            pushRoute("player:$playerId")
                        },
                        onTeamClick = { teamId ->
                            pushRoute("team:$teamId")
                        }
                    )
                }

                currentRoute.startsWith("team:") -> {
                    val teamId = currentRoute.removePrefix("team:")
                    TeamDetailScreen(
                        teamId = teamId,
                        uiState = uiState,
                        onBack = ::popBack,
                        onPlayerClick = { playerId ->
                            pushRoute("player:$playerId")
                        },
                        onMatchClick = { matchId ->
                            pushRoute("match:$matchId")
                        },
                        onRefresh = { viewModel.loadTournament() }
                    )
                }

                currentRoute.startsWith("player:") -> {
                    val playerId = currentRoute.removePrefix("player:")
                    PlayerProfileScreen(
                        playerId = playerId,
                        uiState = uiState,
                        onBack = ::popBack,
                        onTeamClick = { teamId ->
                            pushRoute("team:$teamId")
                        },
                        onMatchClick = { matchId ->
                            pushRoute("match:$matchId")
                        },
                        onRefresh = { viewModel.loadTournament() }
                    )
                }

                currentRoute == "tab:${BottomNavTab.TEAMS_PLAYERS.name}" -> {
                    TeamsAndPlayersScreen(
                        uiState = uiState,
                        onTeamClick = { teamId -> pushRoute("team:$teamId") },
                        onPlayerClick = { playerId -> pushRoute("player:$playerId") },
                        onRefresh = { viewModel.loadTournament() }
                    )
                }

                currentRoute == "tab:${BottomNavTab.POINTS_STATS.name}" -> {
                    PointsAndStatsScreen(
                        uiState = uiState,
                        onTabSelected = { tabIdx -> viewModel.setPointsStatsTab(tabIdx) },
                        onTeamClick = { teamId -> pushRoute("team:$teamId") },
                        onPlayerClick = { playerId -> pushRoute("player:$playerId") },
                        onRefresh = { viewModel.loadTournament() }
                    )
                }

                currentRoute == "tab:${BottomNavTab.HISTORY.name}" -> {
                    HistoryScreen(
                        uiState = uiState,
                        onRefresh = { viewModel.loadTournament() }
                    )
                }

                else -> {
                    HomeScreen(
                        uiState = uiState,
                        onFilterSelected = { filter -> viewModel.setHomeMatchFilter(filter) },
                        onMatchClick = { matchId -> pushRoute("match:$matchId") },
                        onTeamClick = { teamId -> pushRoute("team:$teamId") },
                        onNavigateToStandings = {
                            viewModel.setPointsStatsTab(0)
                            selectBottomTab(BottomNavTab.POINTS_STATS)
                        },
                        onNavigateToTeams = {
                            selectBottomTab(BottomNavTab.TEAMS_PLAYERS)
                        },
                        onRefresh = { viewModel.loadTournament() }
                    )
                }
            }
        }
    }
}
