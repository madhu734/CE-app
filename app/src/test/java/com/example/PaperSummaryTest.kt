package com.example

import com.example.domain.CricketCalculations
import com.example.model.*
import com.example.network.PocketBaseJsonParser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PaperSummaryTest {
    @Test fun missingPaperBallsDoNotInflateLaterStrikeRates() {
        val player = CePlayer(id="p",name="Nipun",team="r")
        val paper = PlayerMatchStat(player="p",team="r",runs=59,batted=true,unrecorded=setOf("balls_faced"))
        val normal = PlayerMatchStat(player="p",team="r",runs=10,ballsFaced=10,batted=true)
        val matches = listOf(paper,normal).mapIndexed { index, stat ->
            CeMatch(id="m$index",teamA="r",teamB="p",status="completed",scorecard=CeScorecard(playerStats=listOf(stat)))
        }
        val stats=CricketCalculations.computeTournamentStats(matches,listOf(player),emptyList(),emptyMap())
        assertEquals(69,stats.mostRuns.single().runs)
        assertEquals("100.0",stats.highestStrikeRate.single().strikeRateDisplay)
        assertEquals("100.0",CricketCalculations.computePlayerProfile(player,emptyList(),matches,emptyMap()).seasonStrikeRateDisplay)
        assertTrue(CricketCalculations.computeTournamentStats(matches.take(1),listOf(player),emptyList(),emptyMap()).highestStrikeRate.isEmpty())
    }

    @Test fun summaryMetadataAndUnknownCellsRemainDistinctFromZero() {
        val stat=PocketBaseJsonParser.parsePlayerMatchStat(JSONObject("""{"runs":59,"balls_faced":0,"unrecorded":["balls_faced"]}"""))
        assertEquals("—",stat.display("balls_faced",stat.ballsFaced))
        assertEquals("0",stat.display("fours",stat.fours))
        val card=PocketBaseJsonParser.parseScorecard(JSONObject("""{"innings":[],"summaryNote":"Paper scorecard"}"""))!!
        assertEquals("Paper scorecard",card.summaryNote)
    }
}
