package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.mutableStateOf
import com.example.model.*
import com.example.network.CricketRepository
import com.example.network.MatchScoringResult
import com.example.network.TournamentSnapshot
import com.example.ui.CrickEnglishApp
import com.example.ui.CricketViewModel
import com.example.ui.CricketUiState
import com.example.ui.screens.HistoryScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `history displays both captains and omits empty optional details`() {
        val entry = CeSeasonHistory(
            id = "archive8", seasonNumber = 8, champion = "Literary Lions",
            runnerUp = "Ink Strikers", championCaptain = "Arif Hasan",
            runnerUpCaptain = "Tanvir Islam", teamPhoto = "team.webp", published = true
        )
        val state = mutableStateOf(CricketUiState(history = listOf(entry)))
        composeTestRule.setContent {
            MyApplicationTheme { HistoryScreen(uiState = state.value, onRefresh = {}) }
        }
        composeTestRule.onNodeWithText("Captain: Arif Hasan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Captain: Tanvir Islam").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_photo_8").assertExists()

        composeTestRule.runOnIdle {
            state.value = CricketUiState(history = listOf(entry.copy(
                championCaptain = "", runnerUpCaptain = "   ", teamPhoto = ""
            )))
        }
        composeTestRule.onNodeWithText("Literary Lions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ink Strikers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Captain:", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("history_photo_8").assertDoesNotExist()
    }

    private class FakeTournamentRepository : CricketRepository {
        var scoreRunsInnings1 = 78
        var matchUpdated = "2026-11-17 08:15:00.000Z"

        val seasons = listOf(
            CeSeason(id = "s4", number = 4, name = "Season 4", active = true)
        )
        val teams = listOf(
            CeTeam(id = "t1", season = "s4", name = "Elizabethan XI", shortName = "ELI", color = "#4338CA"),
            CeTeam(id = "t2", season = "s4", name = "Romantic Strikers", shortName = "ROM", color = "#DC2626")
        )
        val players = listOf(
            CePlayer(id = "p1", name = "John Milton", batch = "18", role = "Batsman", team = "t1"),
            CePlayer(id = "p2", name = "William Blake", batch = "19", role = "Bowler", team = "t2")
        )
        val history = listOf(
            CeSeasonHistory(
                id = "h3",
                seasonNumber = 3,
                year = 2025,
                champion = "Victorian Vanguard",
                runnerUp = "Modernist Mavericks",
                published = true
            )
        )

        override suspend fun fetchFullTournament(preferredSeasonId: String?): Result<TournamentSnapshot> {
            return Result.success(
                TournamentSnapshot(
                    seasons = seasons,
                    activeSeason = seasons.first(),
                    teams = teams,
                    players = players,
                    matches = currentMatches(),
                    legacyStatsByMatchId = emptyMap(),
                    history = history
                )
            )
        }

        override suspend fun fetchMatchesForSeason(seasonId: String): Result<List<CeMatch>> {
            return Result.success(currentMatches())
        }

        override suspend fun fetchMatchScoring(matchId: String): Result<MatchScoringResult> {
            val log = CeScoringLog(
                version = 1,
                overs = 6,
                lineups = listOf(
                    CeScoringLineup(team = "t1", players = listOf("p1")),
                    CeScoringLineup(team = "t2", players = listOf("p2"))
                ),
                innings = listOf(
                    CeScoringInnings(
                        team = "t1",
                        opening = CeScoringOpening(striker = "p1", partner = "p1", bowler = "p2"),
                        plays = listOf(
                            CeScoringPlay.Ball(CeScoringDelivery(kind = "legal", runType = "bat", runs = 4, boundary = 4))
                        )
                    )
                )
            )
            return Result.success(MatchScoringResult(matchId = matchId, updated = matchUpdated, scoring = log))
        }

        override suspend fun fetchSeasonHistory(): Result<List<CeSeasonHistory>> {
            return Result.success(history)
        }

        private fun currentMatches(): List<CeMatch> {
            return listOf(
                CeMatch(
                    id = "m_live_1",
                    season = "s4",
                    teamA = "t1",
                    teamB = "t2",
                    startsAt = "2026-11-17 08:00:00.000Z",
                    stage = "league",
                    overs = 8,
                    status = "live",
                    published = true,
                    updated = matchUpdated,
                    scorecard = CeScorecard(
                        innings = listOf(
                            CeInnings(team = "t1", runs = scoreRunsInnings1, wickets = 2, balls = 17, endedEarly = false)
                        ),
                        result = "Elizabethan XI batting",
                        recent = listOf("0", "4", "1", "wd"),
                        currentBatters = listOf("p1"),
                        currentBowler = "p2",
                        playingSides = listOf(
                            CePlayingSide(team = "t1", players = listOf("p1")),
                            CePlayingSide(team = "t2", players = listOf("p2"))
                        ),
                        playerStats = listOf(
                            PlayerMatchStat(
                                match = "m_live_1",
                                player = "p1",
                                team = "t1",
                                runs = scoreRunsInnings1,
                                ballsFaced = 17,
                                fours = 8,
                                sixes = 3,
                                batted = true,
                                notOut = true
                            ),
                            PlayerMatchStat(
                                match = "m_live_1",
                                player = "p2",
                                team = "t2",
                                ballsBowled = 17,
                                runsConceded = scoreRunsInnings1,
                                wickets = 2
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `verify app name resource`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Crick English", context.getString(R.string.app_name))
    }

    @Test
    fun `refreshing match preserves navigation state and selected tab while updating score`() {
        val fakeRepo = FakeTournamentRepository()
        val vm = CricketViewModel(repository = fakeRepo)

        composeTestRule.setContent {
            MyApplicationTheme {
                CrickEnglishApp(viewModel = vm, enablePolling = false)
            }
        }
        composeTestRule.waitForIdle()

        // Home screen shows match card with 78/2 and (2.5) overs for 17 legal balls
        composeTestRule.onNodeWithTag("match_card_m_live_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("78/2").assertIsDisplayed()
        composeTestRule.onNodeWithText("(2.5)").assertIsDisplayed()

        // Open match detail
        composeTestRule.onNodeWithTag("match_detail_lazy_column").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("match_card_m_live_1"))
        composeTestRule.onNodeWithTag("match_card_m_live_1").performClick()
        composeTestRule.waitForIdle()

        // Scroll to tabs and switch to Squads tab
        composeTestRule.onNodeWithTag("match_detail_lazy_column")
            .performScrollToNode(hasTestTag("tab_squads"))
        composeTestRule.onNodeWithTag("tab_squads").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("match_detail_lazy_column")
            .performScrollToNode(hasTestTag("squad_player_p1"))
        composeTestRule.onNodeWithTag("squad_player_p1").assertIsDisplayed()

        // Update backend score and trigger manual Refresh score
        fakeRepo.scoreRunsInnings1 = 84
        fakeRepo.matchUpdated = "2026-11-17 08:16:00.000Z"
        composeTestRule.onNodeWithTag("match_detail_lazy_column")
            .performScrollToNode(hasTestTag("refresh_score_button"))
        composeTestRule.onNodeWithTag("refresh_score_button").performClick()
        composeTestRule.waitForIdle()

        // Updated score is displayed, and Squads tab remains selected
        composeTestRule.onNodeWithText("84/2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("match_detail_lazy_column")
            .performScrollToNode(hasTestTag("squad_player_p1"))
        composeTestRule.onNodeWithTag("squad_player_p1").assertIsDisplayed()
    }
}
