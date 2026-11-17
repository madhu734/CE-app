package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.*
import com.example.model.*
import com.example.network.CricketRepository
import com.example.network.PocketBaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.Instant

enum class ComparisonMetric {
    RUNS, WICKETS, RUN_RATE
}

enum class ScoringLoadState {
    IDLE, LOADING, LOADED, MISSING, ERROR
}

data class MatchScoringUiState(
    val matchId: String,
    val fetchedForUpdated: String = "",
    val state: ScoringLoadState = ScoringLoadState.IDLE,
    val replayed: ReplayedMatchLog? = null,
    val selectedInningsIndex: Int? = null,
    val comparisonMetric: ComparisonMetric = ComparisonMetric.RUNS,
    val errorMessage: String? = null
)

data class CricketUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val errorMessage: String? = null,
    val isStaleOnError: Boolean = false,
    val lastSuccessFormattedDhaka: String? = null,
    val seasons: List<CeSeason> = emptyList(),
    val activeSeason: CeSeason? = null,
    val teams: List<CeTeam> = emptyList(),
    val players: List<CePlayer> = emptyList(),
    val matches: List<CeMatch> = emptyList(),
    val legacyStatsByMatchId: Map<String, List<PlayerMatchStat>> = emptyMap(),
    val history: List<CeSeasonHistory> = emptyList(),
    // Preserved UI selections
    val homeMatchFilter: MatchFilter = MatchFilter.ALL,
    val pointsStatsTabIndex: Int = 0, // 0 = Points Table, 1 = Tournament Stats
    val matchDetailTabsByMatchId: Map<String, Int> = emptyMap(), // 0 = Scorecard, 1 = Squads
    val scoringStatesByMatchId: Map<String, MatchScoringUiState> = emptyMap(),
    // Precomputed derived views
    val pointsTable: List<PointsTableRow> = emptyList(),
    val tournamentStats: TournamentStatsSummary = TournamentStatsSummary(
        totalFours = 0,
        totalSixes = 0,
        mostRuns = emptyList(),
        mostWickets = emptyList(),
        highestStrikeRate = emptyList()
    )
)

class CricketViewModel(
    private val repository: CricketRepository = PocketBaseRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CricketUiState())
    val uiState: StateFlow<CricketUiState> = _uiState.asStateFlow()

    private val fullLoadMutex = Mutex()
    private val matchRefreshMutex = Mutex()
    private val scoringLoadMutexes = mutableMapOf<String, Mutex>()

    init {
        loadTournament(isInitial = true)
    }

    fun loadTournament(isInitial: Boolean = false, preferredSeasonId: String? = null) {
        viewModelScope.launch {
            if (!fullLoadMutex.tryLock()) return@launch
            try {
                _uiState.update { current ->
                    current.copy(
                        isInitialLoading = isInitial && !current.hasLoadedOnce,
                        isRefreshing = !isInitial || current.hasLoadedOnce,
                        errorMessage = if (isInitial && !current.hasLoadedOnce) null else current.errorMessage
                    )
                }

                val seasonToFetch = preferredSeasonId ?: _uiState.value.activeSeason?.id
                val result = repository.fetchFullTournament(seasonToFetch)
                result.fold(
                    onSuccess = { snapshot ->
                        val nowIso = Instant.ofEpochMilli(snapshot.fetchedAtMillis).toString()
                        val formattedSuccess = CricketCalculations.formatDhakaTimeOnly(nowIso)
                        val points = CricketCalculations.computePointsTable(snapshot.teams, snapshot.matches)
                        val stats = CricketCalculations.computeTournamentStats(
                            matches = snapshot.matches,
                            players = snapshot.players,
                            teams = snapshot.teams,
                            legacyStatsByMatchId = snapshot.legacyStatsByMatchId
                        )

                        _uiState.update { current ->
                            current.copy(
                                isInitialLoading = false,
                                isRefreshing = false,
                                hasLoadedOnce = true,
                                errorMessage = null,
                                isStaleOnError = false,
                                lastSuccessFormattedDhaka = formattedSuccess,
                                seasons = snapshot.seasons,
                                activeSeason = snapshot.activeSeason,
                                teams = snapshot.teams,
                                players = snapshot.players,
                                matches = snapshot.matches,
                                legacyStatsByMatchId = snapshot.legacyStatsByMatchId,
                                history = snapshot.history,
                                pointsTable = points,
                                tournamentStats = stats
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update { current ->
                            current.copy(
                                isInitialLoading = false,
                                isRefreshing = false,
                                errorMessage = err.message ?: "Unable to connect to Crick English server.",
                                isStaleOnError = current.hasLoadedOnce
                            )
                        }
                    }
                )
            } finally {
                fullLoadMutex.unlock()
            }
        }
    }

    /**
     * Lightweight match list refresh used by 10-second polling on Home / Match details,
     * and when returning to foreground. Avoids overlapping requests.
     */
    fun refreshPublishedMatches(selectedMatchIdForScoring: String? = null, forceScoringFetch: Boolean = false) {
        val currentState = _uiState.value
        val activeSeasonId = currentState.activeSeason?.id
        if (activeSeasonId.isNullOrBlank()) {
            loadTournament(isInitial = !currentState.hasLoadedOnce)
            return
        }

        viewModelScope.launch {
            if (!matchRefreshMutex.tryLock()) return@launch
            try {
                _uiState.update { it.copy(isRefreshing = true) }
                val result = repository.fetchMatchesForSeason(activeSeasonId)
                result.fold(
                    onSuccess = { freshMatches ->
                        val nowIso = Instant.now().toString()
                        val formattedSuccess = CricketCalculations.formatDhakaTimeOnly(nowIso)

                        var shouldRefetchScoring = forceScoringFetch
                        if (selectedMatchIdForScoring != null) {
                            val updatedMatch = freshMatches.firstOrNull { it.id == selectedMatchIdForScoring }
                            val existingScoringState = _uiState.value.scoringStatesByMatchId[selectedMatchIdForScoring]
                            if (updatedMatch != null &&
                                (existingScoringState == null ||
                                    existingScoringState.fetchedForUpdated != updatedMatch.updated ||
                                    existingScoringState.state == ScoringLoadState.ERROR)
                            ) {
                                shouldRefetchScoring = true
                            }
                        }

                        _uiState.update { st ->
                            val points = CricketCalculations.computePointsTable(st.teams, freshMatches)
                            val stats = CricketCalculations.computeTournamentStats(
                                matches = freshMatches,
                                players = st.players,
                                teams = st.teams,
                                legacyStatsByMatchId = st.legacyStatsByMatchId
                            )
                            st.copy(
                                isRefreshing = false,
                                errorMessage = null,
                                isStaleOnError = false,
                                lastSuccessFormattedDhaka = formattedSuccess,
                                matches = freshMatches,
                                pointsTable = points,
                                tournamentStats = stats
                            )
                        }

                        if (selectedMatchIdForScoring != null && shouldRefetchScoring) {
                            loadMatchScoring(selectedMatchIdForScoring, force = true)
                        }
                    },
                    onFailure = { err ->
                        _uiState.update { st ->
                            st.copy(
                                isRefreshing = false,
                                errorMessage = err.message ?: "Temporary connection loss while refreshing scores.",
                                isStaleOnError = st.hasLoadedOnce
                            )
                        }
                    }
                )
            } finally {
                matchRefreshMutex.unlock()
            }
        }
    }

    fun loadMatchScoring(matchId: String, force: Boolean = false) {
        val currentMatch = _uiState.value.matches.firstOrNull { it.id == matchId }
        val existing = _uiState.value.scoringStatesByMatchId[matchId]
        if (!force && existing != null &&
            (existing.state == ScoringLoadState.LOADED || existing.state == ScoringLoadState.MISSING) &&
            (currentMatch == null || existing.fetchedForUpdated == currentMatch.updated)
        ) {
            return
        }

        viewModelScope.launch {
            val mutex = synchronized(scoringLoadMutexes) {
                scoringLoadMutexes.getOrPut(matchId) { Mutex() }
            }
            if (!mutex.tryLock()) return@launch
            try {
                _uiState.update { st ->
                    val prev = st.scoringStatesByMatchId[matchId] ?: MatchScoringUiState(matchId = matchId)
                    st.copy(
                        scoringStatesByMatchId = st.scoringStatesByMatchId + (
                            matchId to prev.copy(
                                state = if (prev.replayed != null) ScoringLoadState.LOADED else ScoringLoadState.LOADING,
                                errorMessage = null
                            )
                        )
                    )
                }

                val result = repository.fetchMatchScoring(matchId)
                result.fold(
                    onSuccess = { scoringResult ->
                        val matchOvers = _uiState.value.matches.firstOrNull { it.id == matchId }?.overs ?: 0
                        val replayed = ScoringReplay.replay(scoringResult.scoring, matchOvers)
                        val hasDeliveries = replayed != null && replayed.innings.any { it.deliveries.isNotEmpty() || it.adjustments.isNotEmpty() }

                        _uiState.update { st ->
                            val prev = st.scoringStatesByMatchId[matchId] ?: MatchScoringUiState(matchId = matchId)
                            val defaultInningsIdx = replayed?.innings?.lastIndex?.takeIf { it >= 0 }
                            val preservedInningsIdx = prev.selectedInningsIndex?.takeIf {
                                replayed != null && it in replayed.innings.indices
                            } ?: defaultInningsIdx

                            val newScoringState = prev.copy(
                                fetchedForUpdated = scoringResult.updated.ifEmpty { currentMatch?.updated ?: "" },
                                state = if (hasDeliveries) ScoringLoadState.LOADED else ScoringLoadState.MISSING,
                                replayed = if (hasDeliveries) replayed else null,
                                selectedInningsIndex = preservedInningsIdx,
                                errorMessage = null
                            )
                            st.copy(
                                scoringStatesByMatchId = st.scoringStatesByMatchId + (matchId to newScoringState)
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update { st ->
                            val prev = st.scoringStatesByMatchId[matchId] ?: MatchScoringUiState(matchId = matchId)
                            val newScoringState = prev.copy(
                                state = if (prev.replayed != null) ScoringLoadState.LOADED else ScoringLoadState.ERROR,
                                errorMessage = err.message ?: "Could not load ball-by-ball log."
                            )
                            st.copy(
                                scoringStatesByMatchId = st.scoringStatesByMatchId + (matchId to newScoringState)
                            )
                        }
                    }
                )
            } finally {
                mutex.unlock()
            }
        }
    }

    fun setHomeMatchFilter(filter: MatchFilter) {
        _uiState.update { it.copy(homeMatchFilter = filter) }
    }

    fun setPointsStatsTab(tabIndex: Int) {
        _uiState.update { it.copy(pointsStatsTabIndex = tabIndex.coerceIn(0, 1)) }
    }

    fun setMatchDetailTab(matchId: String, tabIndex: Int) {
        _uiState.update { st ->
            st.copy(
                matchDetailTabsByMatchId = st.matchDetailTabsByMatchId + (matchId to tabIndex.coerceIn(0, 1))
            )
        }
    }

    fun setSelectedReplayInnings(matchId: String, inningsIndex: Int) {
        _uiState.update { st ->
            val prev = st.scoringStatesByMatchId[matchId] ?: return@update st
            st.copy(
                scoringStatesByMatchId = st.scoringStatesByMatchId + (
                    matchId to prev.copy(selectedInningsIndex = inningsIndex)
                )
            )
        }
    }

    fun setComparisonMetric(matchId: String, metric: ComparisonMetric) {
        _uiState.update { st ->
            val prev = st.scoringStatesByMatchId[matchId] ?: MatchScoringUiState(matchId = matchId)
            st.copy(
                scoringStatesByMatchId = st.scoringStatesByMatchId + (
                    matchId to prev.copy(comparisonMetric = metric)
                )
            )
        }
    }

    class Factory(
        private val repository: CricketRepository = PocketBaseRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CricketViewModel(repository) as T
        }
    }
}
