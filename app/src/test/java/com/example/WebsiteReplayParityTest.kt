package com.example

import com.example.domain.ScoringReplay
import com.example.network.PocketBaseJsonParser
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebsiteReplayParityTest {
    @Test
    fun websiteScoringFixturesProduceTheSameDeliveryLog() {
        val input = checkNotNull(javaClass.getResource("/website-replay-parity.json")).readText()
        val cases = JSONArray(input)
        for (index in 0 until cases.length()) {
            val fixture = cases.getJSONObject(index)
            val name = fixture.getString("name")
            val log = checkNotNull(PocketBaseJsonParser.parseScoringLog(fixture.getJSONObject("log")))
            val replay = checkNotNull(ScoringReplay.replay(log))
            val innings = fixture.getJSONArray("innings")
            assertEquals(name, innings.length(), replay.innings.size)
            replay.innings.forEachIndexed { inningsIndex, actual ->
                val expected = innings.getJSONObject(inningsIndex)
                assertEquals(name, expected.getInt("runs"), actual.totalRuns)
                assertEquals(name, expected.getInt("wickets"), actual.totalWickets)
                assertEquals(name, expected.getInt("balls"), actual.legalBalls)
                assertEquals(name, expected.getString("striker"), actual.currentStriker)
                assertEquals(name, expected.getString("partner"), actual.currentPartner)
                assertEquals(name, expected.getString("bowler").ifBlank { null }, actual.currentBowler)
                assertEquals(name, expected.getBoolean("closedEarly"), actual.closedEarly)

                val deliveries = expected.getJSONArray("deliveries")
                assertEquals(name, deliveries.length(), actual.deliveries.size)
                actual.deliveries.forEachIndexed { ballIndex, ball ->
                    val wanted = deliveries.getJSONObject(ballIndex)
                    assertEquals(name, wanted.getInt("index"), ball.eventIndex)
                    assertEquals(name, wanted.getString("batter"), ball.batterId)
                    assertEquals(name, wanted.getString("bowler"), ball.bowlerId)
                    assertEquals(name, wanted.getString("over"), ball.ballPosition)
                    assertEquals(name, wanted.getString("label"), ball.resultChip)
                    assertEquals(name, wanted.getInt("totalRuns"), ball.runningRuns)
                    assertEquals(name, wanted.getInt("totalWickets"), ball.runningWickets)
                }
            }
        }
    }
}
