package com.example

import com.example.domain.CricketCalculations
import com.example.domain.MatchFilter
import com.example.domain.ScoringReplay
import com.example.model.*
import com.example.network.PocketBaseJsonParser
import com.example.network.PocketBaseRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleUnitTest {

    @Test
    fun `history parses optional captains and photo and accepts older records`() {
        val updated = PocketBaseJsonParser.parseSeasonHistory(JSONObject("""
            {
              "id":"archive8", "season_number":8, "year":2025, "published":true,
              "champion":"Literary Lions", "runner_up":"Ink Strikers",
              "champion_captain":"Arif Hasan", "runner_up_captain":"Tanvir Islam",
              "team_photo":"season_8_team.webp"
            }
        """.trimIndent()))
        assertEquals("Arif Hasan", updated.championCaptain)
        assertEquals("Tanvir Islam", updated.runnerUpCaptain)
        assertEquals("season_8_team.webp", updated.teamPhoto)
        assertEquals("Literary Lions", updated.champion)
        assertEquals("Ink Strikers", updated.runnerUp)

        val legacy = PocketBaseJsonParser.parseSeasonHistory(JSONObject("""
            {"id":"archive7", "champion":"Verse Vipers", "runner_up":"Prose Panthers",
             "champion_captain":null, "team_photo":null}
        """.trimIndent()))
        assertEquals("", legacy.championCaptain)
        assertEquals("", legacy.runnerUpCaptain)
        assertEquals("", legacy.teamPhoto)
        assertEquals("Verse Vipers", legacy.champion)
    }

    @Test
    fun `overs formatting and calculation for 17 legal balls`() {
        assertEquals("2.5", CricketCalculations.formatOvers(17))
        assertEquals(17.0 / 6.0, CricketCalculations.oversAsDouble(17), 1e-9)
        // Run rate for 34 runs in 17 legal balls = 6 * 34 / 17 = 12.00
        assertEquals("12.00", CricketCalculations.formatRunRateOrEconomy(34, 17))
        assertEquals("—", CricketCalculations.formatRunRateOrEconomy(10, 0))
        assertEquals("—", CricketCalculations.formatStrikeRate(10, 0))
    }

    @Test
    fun `highest strike rate threshold requires 10 cumulative balls and ranks by unrounded aggregate`() {
        val players = listOf(
            CePlayer(id = "p_nine", name = "Nine Baller", team = "t1"),
            CePlayer(id = "p_ten", name = "Ten Baller", team = "t1"),
            CePlayer(id = "p_eleven", name = "Eleven Baller", team = "t2")
        )
        val teams = listOf(
            CeTeam(id = "t1", name = "Alpha", shortName = "ALP"),
            CeTeam(id = "t2", name = "Beta", shortName = "BET")
        )

        val match1 = CeMatch(
            id = "m1",
            teamA = "t1",
            teamB = "t2",
            status = "completed",
            published = true,
            scorecard = CeScorecard(
                playerStats = listOf(
                    // 9 balls in season -> excluded from Highest SR
                    PlayerMatchStat(player = "p_nine", team = "t1", runs = 36, ballsFaced = 9, fours = 3, sixes = 4, batted = true),
                    // 6 balls in match 1 + 4 balls in match 2 = 10 balls -> qualifies!
                    PlayerMatchStat(player = "p_ten", team = "t1", runs = 12, ballsFaced = 6, fours = 1, sixes = 1, batted = true),
                    // 11 balls with 20 runs -> SR = 181.818...
                    PlayerMatchStat(player = "p_eleven", team = "t2", runs = 20, ballsFaced = 11, fours = 2, sixes = 1, batted = true)
                )
            )
        )

        val match2 = CeMatch(
            id = "m2",
            teamA = "t1",
            teamB = "t2",
            status = "completed",
            published = true,
            scorecard = CeScorecard(
                playerStats = listOf(
                    // 4 more balls for p_ten -> total 10 balls, 19 runs -> SR = 190.0
                    PlayerMatchStat(player = "p_ten", team = "t1", runs = 7, ballsFaced = 4, fours = 1, sixes = 0, batted = true)
                )
            )
        )

        val stats = CricketCalculations.computeTournamentStats(
            matches = listOf(match1, match2),
            players = players,
            teams = teams,
            legacyStatsByMatchId = emptyMap()
        )

        // p_nine is in Most Runs (36 runs), but excluded from Highest Strike Rate (< 10 balls)
        assertEquals("p_nine", stats.mostRuns.first().player.id)
        assertFalse(stats.highestStrikeRate.any { it.player.id == "p_nine" })

        // p_ten (6 + 4 = 10 balls, 19 runs -> 190.0) ranks ahead of p_eleven (20/11 -> 181.8)
        assertEquals(2, stats.highestStrikeRate.size)
        assertEquals("p_ten", stats.highestStrikeRate[0].player.id)
        assertEquals("190.0", stats.highestStrikeRate[0].strikeRateDisplay)
        assertEquals("p_eleven", stats.highestStrikeRate[1].player.id)
        assertEquals("181.8", stats.highestStrikeRate[1].strikeRateDisplay)
    }

    @Test
    fun `single source rule never double counts embedded and legacy stats and ignores live private abandoned`() {
        val players = listOf(
            CePlayer(id = "p1", name = "Batter One", team = "t1"),
            CePlayer(id = "p2", name = "Batter Two", team = "t2")
        )
        val teams = listOf(
            CeTeam(id = "t1", name = "Team One"),
            CeTeam(id = "t2", name = "Team Two")
        )

        // Match 1 has embedded playerStats -> legacy row for m1 MUST be ignored
        val m1 = CeMatch(
            id = "m1",
            status = "completed",
            published = true,
            scorecard = CeScorecard(
                playerStats = listOf(
                    PlayerMatchStat(player = "p1", team = "t1", runs = 25, ballsFaced = 15, fours = 3, sixes = 1, batted = true)
                )
            )
        )

        // Match 2 has an explicitly empty embedded playerStats array [] -> authoritative empty! Legacy row must be ignored.
        val m2Json = JSONObject("""{"playerStats": []}""")
        val m2Scorecard = PocketBaseJsonParser.parseScorecard(m2Json, "m2")
        val m2 = CeMatch(
            id = "m2",
            status = "completed",
            published = true,
            scorecard = m2Scorecard
        )

        // Match 3 has null playerStats -> falls back to legacy row
        val m3 = CeMatch(
            id = "m3",
            status = "completed",
            published = true,
            scorecard = CeScorecard(playerStats = null)
        )

        // Live, private (published = false), and abandoned matches -> contribute 0 to tournament stats & boundaries
        val mLive = CeMatch(
            id = "m_live",
            status = "live",
            published = true,
            scorecard = CeScorecard(
                playerStats = listOf(
                    PlayerMatchStat(player = "p1", team = "t1", runs = 99, ballsFaced = 30, fours = 10, sixes = 10, batted = true)
                )
            )
        )
        val mPrivate = CeMatch(
            id = "m_priv",
            status = "completed",
            published = false,
            scorecard = CeScorecard(
                playerStats = listOf(
                    PlayerMatchStat(player = "p1", team = "t1", runs = 88, ballsFaced = 25, fours = 8, sixes = 8, batted = true)
                )
            )
        )
        val mAbandoned = CeMatch(
            id = "m_aban",
            status = "abandoned",
            published = true,
            scorecard = CeScorecard(
                playerStats = listOf(
                    PlayerMatchStat(player = "p1", team = "t1", runs = 77, ballsFaced = 20, fours = 7, sixes = 7, batted = true)
                )
            )
        )

        val legacyMap = mapOf(
            "m1" to listOf(
                PlayerMatchStat(match = "m1", player = "p1", team = "t1", runs = 100, ballsFaced = 50, fours = 10, sixes = 10, batted = true)
            ),
            "m2" to listOf(
                PlayerMatchStat(match = "m2", player = "p1", team = "t1", runs = 50, ballsFaced = 20, fours = 5, sixes = 5, batted = true)
            ),
            "m3" to listOf(
                PlayerMatchStat(match = "m3", player = "p2", team = "t2", runs = 15, ballsFaced = 10, fours = 2, sixes = 1, batted = true)
            )
        )

        val summary = CricketCalculations.computeTournamentStats(
            matches = listOf(m1, m2, m3, mLive, mPrivate, mAbandoned),
            players = players,
            teams = teams,
            legacyStatsByMatchId = legacyMap
        )

        // Total fours = 3 (from m1) + 0 (from m2) + 2 (from m3) = 5
        // Total sixes = 1 (from m1) + 0 (from m2) + 1 (from m3) = 2
        assertEquals(5, summary.totalFours)
        assertEquals(2, summary.totalSixes)

        val p1Row = summary.mostRuns.first { it.player.id == "p1" }
        val p2Row = summary.mostRuns.first { it.player.id == "p2" }
        assertEquals(25, p1Row.runs)
        assertEquals(15, p2Row.runs)
    }

    @Test
    fun `NRR uses full quota for all out innings and actual balls for innings ended early without all out`() {
        val t1 = CeTeam(id = "t1", name = "Lions")
        val t2 = CeTeam(id = "t2", name = "Tigers")

        // Match overs = 10 (60 balls).
        // Team 1 is all out in 40 balls for 60 runs -> NRR uses quota 10 * 6 = 60 balls (rate = 6.0)
        // Team 2 ends innings early without being all out in 30 balls for 45 runs -> NRR uses actual 30 balls (rate = 9.0)
        val match = CeMatch(
            id = "m_nrr",
            stage = "league",
            status = "completed",
            published = true,
            overs = 10,
            teamA = "t1",
            teamB = "t2",
            winner = "t1",
            scorecard = CeScorecard(
                innings = listOf(
                    CeInnings(team = "t1", runs = 60, wickets = 10, balls = 40, allOut = true, endedEarly = false),
                    CeInnings(team = "t2", runs = 45, wickets = 3, balls = 30, allOut = false, endedEarly = true)
                )
            )
        )

        // Abandoned league match adds 1 played and 1 point, but no NRR data
        val abandonedMatch = CeMatch(
            id = "m_ab",
            stage = "group",
            status = "abandoned",
            published = true,
            overs = 10,
            teamA = "t1",
            teamB = "t2"
        )

        val table = CricketCalculations.computePointsTable(
            teams = listOf(t1, t2),
            matches = listOf(match, abandonedMatch)
        )

        val rowT1 = table.first { it.team.id == "t1" }
        val rowT2 = table.first { it.team.id == "t2" }

        // T1: 2 played, 1 won, 1 T/NR, 3 points. NRR = 6 * (60/60 - 45/30) = 6 * (1.0 - 1.5) = -3.000
        assertEquals(2, rowT1.played)
        assertEquals(3, rowT1.points)
        assertEquals("-3.000", rowT1.nrrDisplay)

        // T2: 2 played, 0 won, 1 lost, 1 T/NR, 1 point. NRR = +3.000
        assertEquals(2, rowT2.played)
        assertEquals(1, rowT2.points)
        assertEquals("+3.000", rowT2.nrrDisplay)
    }

    @Test
    fun `scoring replay handles wides, leg byes, adjustments, wickets, batter replacement, and early close`() {
        val log = CeScoringLog(
            version = 1,
            overs = 6,
            lineups = listOf(
                CeScoringLineup(team = "t1", players = listOf("b1", "b2", "b3", "b4"))
            ),
            innings = listOf(
                CeScoringInnings(
                    team = "t1",
                    opening = CeScoringOpening(striker = "b1", partner = "b2", bowler = "bw1"),
                    plays = listOf(
                        // 1. Wide (0 additional runs): adds 1 team run, 0 legal balls, position 0.1, chip "wd"
                        CeScoringPlay.Ball(CeScoringDelivery(kind = "wide", runType = "bat", runs = 0)),
                        // 2. Legal leg bye (1 run): adds 1 team run, 1 legal ball, position 0.1, chip "1lb", swaps strike (odd runs)
                        CeScoringPlay.Ball(CeScoringDelivery(kind = "legal", runType = "legBye", runs = 1)),
                        // Now b2 is striker!
                        // 3. No-ball + 4 boundary: adds 5 team runs, 0 legal balls, position 0.2, chip "nb+4"
                        CeScoringPlay.Ball(CeScoringDelivery(kind = "noBall", runType = "bat", runs = 4, boundary = 4)),
                        // 4. AdjustRuns (-2): changes team runs without adding a delivery
                        CeScoringPlay.AdjustRuns(runs = -2),
                        // 5. Wicket (Run out of b2, next = b3, incomingOnStrike = false -> survivor b1 takes strike)
                        CeScoringPlay.Ball(
                            CeScoringDelivery(
                                kind = "legal",
                                runType = "bat",
                                runs = 0,
                                wicket = CeScoringWicket(
                                    player = "b2",
                                    how = "Run out",
                                    next = "b3",
                                    incomingOnStrike = false
                                )
                            )
                        ),
                        // 6. ReplaceBatter: b3 replaced by b4 at non-striker end
                        CeScoringPlay.ReplaceBatter(outgoing = "b3", incoming = "b4"),
                        // 7. CloseInnings early
                        CeScoringPlay.CloseInnings
                    )
                )
            )
        )

        val replayed = ScoringReplay.replay(log)
        assertNotNull(replayed)
        val inn = replayed!!.innings.first()

        // Total runs: 1 (wd) + 1 (1lb) + 5 (nb+4) - 2 (adjust) + 0 (W) = 5
        assertEquals(5, inn.totalRuns)
        // Legal balls: only the 1lb and the W delivery were legal -> 2 legal balls ("0.2")
        assertEquals(2, inn.legalBalls)
        assertEquals("0.2", inn.oversDisplay)
        assertEquals(1, inn.totalWickets)
        assertTrue(inn.closedEarly)

        // Deliveries count = 4 (AdjustRuns and CloseInnings do not add deliveries)
        assertEquals(4, inn.deliveries.size)
        assertEquals(1, inn.adjustments.size)
        assertEquals("-2", inn.adjustments.first().signedText)

        assertEquals("0.1", inn.deliveries[0].ballPosition)
        assertEquals("wd", inn.deliveries[0].resultChip)

        assertEquals("0.1", inn.deliveries[1].ballPosition)
        assertEquals("1lb", inn.deliveries[1].resultChip)

        assertEquals("0.2", inn.deliveries[2].ballPosition)
        assertEquals("nb+4", inn.deliveries[2].resultChip)

        assertEquals("0.2", inn.deliveries[3].ballPosition)
        assertEquals("W", inn.deliveries[3].resultChip)

        // After Run out with incomingOnStrike = false, survivor b1 is striker, incoming b3 was partner, then replaced by b4
        assertEquals("b1", inn.currentStriker)
        assertEquals("b4", inn.currentPartner)
    }

    @Test
    fun `live PocketBase API contract verification`() = runBlocking {
        org.junit.Assume.assumeTrue("Enable the live API check with -PliveApi=true", java.lang.Boolean.getBoolean("crickEnglish.liveApi"))
        val repo = PocketBaseRepository()
        val result = repo.fetchFullTournament()
        assertTrue("Expected live PocketBase fetch to succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val snapshot = result.getOrThrow()

        assertTrue("Expected at least one season from ce_seasons", snapshot.seasons.isNotEmpty())
        assertNotNull("Expected active or fallback season to be selected", snapshot.activeSeason)
        assertTrue("Expected teams to load for selected season", snapshot.teams.isNotEmpty())
        assertTrue("Expected players to load from ce_players", snapshot.players.isNotEmpty())
        assertTrue("Expected published matches to load", snapshot.matches.isNotEmpty())

        // Verify match detail scoring endpoint works
        val firstMatch = snapshot.matches.first()
        val scoringRes = repo.fetchMatchScoring(firstMatch.id)
        assertTrue("Expected match scoring endpoint to succeed", scoringRes.isSuccess)
    }
}
