package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
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
import kotlinx.coroutines.sync.withLock
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
    private val repository: CricketRepository = PocketBaseRepository(),
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CricketUiState(
        homeMatchFilter = MatchFilter.entries.firstOrNull { it.name == savedState.get<String>("homeFilter") } ?: MatchFilter.ALL,
        pointsStatsTabIndex = savedState.get<Int>("statsTab") ?: 0,
        matchDetailTabsByMatchId = savedState.get<HashMap<String, Int>>("matchTabs") ?: emptyMap()
    ))
    val uiState: StateFlow<CricketUiState> = _uiState.asStateFlow()

    private val tournamentMutex = Mutex()
    private val scoringLoadMutexes = mutableMapOf<String, Mutex>()

    init {
        loadTournament(isInitial = true)
    }

    fun loadTournament(isInitial: Boolean = false) {
        viewModelScope.launch {
            if (!tournamentMutex.tryLock()) return@launch
            try {
                _uiState.update {
                    it.copy(isInitialLoading = isInitial && !it.hasLoadedOnce, isRefreshing = !isInitial)
                }
                repository.fetchFullTournament().fold(
                    onSuccess = { snapshot ->
                        _uiState.update {
                            val seasonChanged = it.activeSeason?.id != snapshot.activeSeason?.id
                            it.copy(
                                isInitialLoading = false,
                                hasLoadedOnce = true,
                                seasons = snapshot.seasons,
                                activeSeason = snapshot.activeSeason,
                                teams = snapshot.teams,
                                players = snapshot.players,
                                legacyStatsByMatchId = snapshot.legacyStatsByMatchId,
                                history = snapshot.history,
                                scoringStatesByMatchId = if (seasonChanged) emptyMap() else it.scoringStatesByMatchId
                            ).withMatches(snapshot.matches, Instant.ofEpochMilli(snapshot.fetchedAtMillis))
                        }
                    },
                    onFailure = { showLoadError("Could not load tournament data. Check your connection and try again.") }
                )
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
                tournamentMutex.unlock()
            }
        }
    }

    suspend fun pollPublishedMatches() {
        if (!tournamentMutex.tryLock()) return
        try {
            refreshMatches()
        } finally {
            tournamentMutex.unlock()
        }
    }

    fun refreshPublishedMatches(matchId: String) {
        if (_uiState.value.activeSeason == null) {
            loadTournament()
            return
        }

        viewModelScope.launch {
            tournamentMutex.withLock {
                _uiState.update { it.copy(isRefreshing = true) }
                try {
                    refreshMatches()
                } finally {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
            fetchScoring(matchId, force = true)
        }
    }

    private suspend fun refreshMatches() {
        val season = _uiState.value.activeSeason ?: return
        repository.fetchMatchesForSeason(season.id).fold(
            onSuccess = { matches -> _uiState.update { it.withMatches(matches, Instant.now()) } },
            onFailure = { showLoadError("Could not refresh scores. Check your connection and try again.") }
        )
    }

    private fun CricketUiState.withMatches(matches: List<CeMatch>, fetchedAt: Instant): CricketUiState = copy(
        errorMessage = null,
        isStaleOnError = false,
        lastSuccessFormattedDhaka = CricketCalculations.formatDhakaTimeOnly(fetchedAt.toString()),
        matches = matches,
        pointsTable = CricketCalculations.computePointsTable(teams, matches),
        tournamentStats = CricketCalculations.computeTournamentStats(matches, players, teams, legacyStatsByMatchId)
    )

    private fun showLoadError(message: String) {
        _uiState.update {
            it.copy(isInitialLoading = false, errorMessage = message, isStaleOnError = it.hasLoadedOnce)
        }
    }

    fun retryMatchScoring(matchId: String) {
        viewModelScope.launch { fetchScoring(matchId, force = true) }
    }

    suspend fun fetchScoring(matchId: String, force: Boolean = false) {
        val mutex = scoringLoadMutexes.getOrPut(matchId) { Mutex() }
        mutex.withLock {
            val match = _uiState.value.matches.firstOrNull { it.id == matchId && it.scorecard != null }
                ?: return@withLock
            val previous = _uiState.value.scoringStatesByMatchId[matchId]
            if (!force && previous != null && previous.fetchedForUpdated == match.updated &&
                previous.errorMessage == null && previous.state in listOf(ScoringLoadState.LOADED, ScoringLoadState.MISSING)
            ) return@withLock

            updateScoring(matchId) {
                copy(state = if (replayed == null) ScoringLoadState.LOADING else ScoringLoadState.LOADED, errorMessage = null)
            }
            repository.fetchMatchScoring(matchId).fold(
                onSuccess = { result ->
                    val replay = ScoringReplay.replay(result.scoring, match.overs)
                    updateScoring(matchId) {
                        copy(
                            fetchedForUpdated = result.updated.ifBlank { match.updated },
                            state = if (replay == null) ScoringLoadState.MISSING else ScoringLoadState.LOADED,
                            replayed = replay,
                            selectedInningsIndex = selectedInningsIndex?.takeIf { index -> replay != null && index in replay.innings.indices },
                            errorMessage = null
                        )
                    }
                },
                onFailure = {
                    updateScoring(matchId) {
                        copy(
                            state = if (replayed == null) ScoringLoadState.ERROR else ScoringLoadState.LOADED,
                            errorMessage = "Could not refresh the ball-by-ball log."
                        )
                    }
                }
            )
        }
    }

    private fun updateScoring(matchId: String, update: MatchScoringUiState.() -> MatchScoringUiState) {
        _uiState.update { current ->
            if (current.matches.none { it.id == matchId }) {
                return@update current
            }
            val previous = current.scoringStatesByMatchId[matchId] ?: MatchScoringUiState(
                matchId = matchId,
                selectedInningsIndex = savedState["innings-$matchId"],
                comparisonMetric = ComparisonMetric.entries.firstOrNull {
                    it.name == savedState.get<String>("metric-$matchId")
                } ?: ComparisonMetric.RUNS
            )
            current.copy(scoringStatesByMatchId = current.scoringStatesByMatchId + (matchId to previous.update()))
        }
    }

    fun setHomeMatchFilter(filter: MatchFilter) {
        savedState["homeFilter"] = filter.name
        _uiState.update { it.copy(homeMatchFilter = filter) }
    }

    fun setPointsStatsTab(tabIndex: Int) {
        savedState["statsTab"] = tabIndex.coerceIn(0, 1)
        _uiState.update { it.copy(pointsStatsTabIndex = tabIndex.coerceIn(0, 1)) }
    }

    fun setMatchDetailTab(matchId: String, tabIndex: Int) {
        savedState["matchTabs"] = HashMap(_uiState.value.matchDetailTabsByMatchId + (matchId to tabIndex.coerceIn(0, 1)))
        _uiState.update { st ->
            st.copy(
                matchDetailTabsByMatchId = st.matchDetailTabsByMatchId + (matchId to tabIndex.coerceIn(0, 1))
            )
        }
    }

    fun setSelectedReplayInnings(matchId: String, inningsIndex: Int) {
        savedState["innings-$matchId"] = inningsIndex
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
        savedState["metric-$matchId"] = metric.name
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

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return CricketViewModel(repository, extras.createSavedStateHandle()) as T
        }
    }
}
