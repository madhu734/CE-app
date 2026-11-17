package com.example

import androidx.lifecycle.SavedStateHandle
import com.example.model.*
import com.example.network.CricketRepository
import com.example.network.MatchScoringResult
import com.example.network.TournamentSnapshot
import com.example.ui.CricketViewModel
import com.example.ui.ScoringLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshRegressionTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private class Repository : CricketRepository {
        var season = CeSeason("s1", active = true)
        var requestedSeason: String? = "unexpected"
        var scoringCalls = 0
        var scoringFailure = false
        var scheduled = false
        var revision = "1"
        var log = CeScoringLog(
            overs = 2,
            innings = listOf(CeScoringInnings(
                team = "a", opening = CeScoringOpening("a1", "a2", "b1"),
                plays = listOf(CeScoringPlay.Ball(CeScoringDelivery(runs = 4)))
            ))
        )
        fun matches() = listOf(CeMatch(
            id = "m1", season = season.id, teamA = "a", teamB = "b", overs = 2,
            published = true, status = if (scheduled) "scheduled" else "live",
            scorecard = if (scheduled) null else CeScorecard(), updated = revision
        ))
        override suspend fun fetchFullTournament(preferredSeasonId: String?): Result<TournamentSnapshot> {
            requestedSeason = preferredSeasonId
            return Result.success(TournamentSnapshot(
                listOf(season), season, emptyList(), emptyList(), matches(), emptyMap(), emptyList()
            ))
        }
        override suspend fun fetchMatchesForSeason(seasonId: String) = Result.success(matches())
        override suspend fun fetchMatchScoring(matchId: String): Result<MatchScoringResult> {
            scoringCalls++
            return if (scoringFailure) Result.failure(IllegalStateException("offline"))
                else Result.success(MatchScoringResult(matchId, revision, log))
        }
        override suspend fun fetchSeasonHistory() = Result.success(emptyList<CeSeasonHistory>())
    }

    @Test fun scheduledFixtureDoesNotRequestABallLog() = runTest(dispatcher) {
        val repository = Repository().apply { scheduled = true }
        val vm = CricketViewModel(repository)
        runCurrent()
        vm.fetchScoring("m1")
        assertEquals(0, repository.scoringCalls)
    }

    @Test fun replayRefreshKeepsFailuresVisibleAndFollowsLatestInningsByDefault() = runTest(dispatcher) {
        val repository = Repository()
        val vm = CricketViewModel(repository)
        runCurrent()
        vm.fetchScoring("m1")
        assertNull(vm.uiState.value.scoringStatesByMatchId["m1"]!!.selectedInningsIndex)

        repository.scoringFailure = true
        vm.fetchScoring("m1", force = true)
        val failed = vm.uiState.value.scoringStatesByMatchId["m1"]!!
        assertEquals(ScoringLoadState.LOADED, failed.state)
        assertNotNull(failed.replayed)
        assertNotNull(failed.errorMessage)

        repository.scoringFailure = false
        repository.revision = "2"
        repository.log = repository.log.copy(innings = repository.log.innings + CeScoringInnings(
            team = "b", opening = CeScoringOpening("b1", "b2", "a1")
        ))
        vm.pollPublishedMatches()
        vm.fetchScoring("m1")
        val refreshed = vm.uiState.value.scoringStatesByMatchId["m1"]!!
        assertNull(refreshed.errorMessage)
        assertEquals(2, refreshed.replayed!!.innings.size)
        assertNull(refreshed.selectedInningsIndex)

        vm.setSelectedReplayInnings("m1", 0)
        vm.fetchScoring("m1", force = true)
        assertEquals(0, vm.uiState.value.scoringStatesByMatchId["m1"]!!.selectedInningsIndex)
    }

    @Test fun fullRefreshDiscoversNewSeason() = runTest(dispatcher) {
        val repository = Repository()
        val vm = CricketViewModel(repository)
        runCurrent()
        repository.season = CeSeason("s2", active = true)
        vm.loadTournament()
        runCurrent()
        assertNull(repository.requestedSeason)
        assertEquals("s2", vm.uiState.value.activeSeason!!.id)
    }

    @Test fun matchTabSurvivesViewModelRecreation() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val repository = Repository()
        val first = CricketViewModel(repository, saved)
        runCurrent()
        first.setMatchDetailTab("m1", 1)
        val restored = CricketViewModel(repository, saved)
        runCurrent()
        assertEquals(1, restored.uiState.value.matchDetailTabsByMatchId["m1"])
    }
}
