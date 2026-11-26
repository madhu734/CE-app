package com.example

import com.example.network.PocketBaseJsonParser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FallOfWicketsTest {
    @Test fun parsesSeparateRegulationAndSuperOverFalls() {
        val card = PocketBaseJsonParser.parseScorecard(JSONObject("""
            {"innings":[{"team":"a","runs":76,"wickets":2,"balls":36,"quota":24,
              "fallOfWickets":[{"wicket":1,"runs":34,"player":"a2","balls":17},
                {"wicket":2,"runs":70,"player":"a1","balls":36}]}],
             "superOver":{"innings":[{"team":"a","runs":8,"wickets":1,"balls":6,
               "fallOfWickets":[{"wicket":1,"runs":4,"player":"a3","balls":2}]}]}}
        """.trimIndent()))!!
        val innings = card.innings.single()
        assertEquals(24, innings.quota)
        assertEquals(listOf(34, 70), innings.fallOfWickets.map { it.runs })
        assertEquals("a2", innings.fallOfWickets.first().player)
        assertEquals(17, innings.fallOfWickets.first().balls)
        assertEquals(4, card.superOver!!.innings.single().fallOfWickets.single().runs)
    }

    @Test fun missingLegacyDetailsRemainEmpty() {
        val card = PocketBaseJsonParser.parseScorecard(JSONObject("""{"innings":[{"team":"a","wickets":1}]}"""))!!
        assertTrue(card.innings.single().fallOfWickets.isEmpty())
    }
}
