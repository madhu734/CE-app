package com.example.network

import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject

object PocketBaseJsonParser {

    fun <T> parseListResponse(jsonString: String, itemParser: (JSONObject) -> T): PbListResponse<T> {
        val root = JSONObject(jsonString)
        val page = root.optInt("page", 1)
        val perPage = root.optInt("perPage", 30)
        val totalItems = root.optInt("totalItems", 0)
        val totalPages = root.optInt("totalPages", 1)
        val itemsArray = root.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<T>()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.optJSONObject(i) ?: continue
            items.add(itemParser(obj))
        }
        return PbListResponse(
            page = page,
            perPage = perPage,
            totalItems = totalItems,
            totalPages = totalPages,
            items = items
        )
    }

    fun parseSeason(obj: JSONObject): CeSeason {
        return CeSeason(
            id = obj.optCleanString("id"),
            number = obj.optInt("number", 0),
            name = obj.optCleanString("name"),
            active = obj.optBoolean("active", false)
        )
    }

    fun parseTeam(obj: JSONObject): CeTeam {
        return CeTeam(
            id = obj.optCleanString("id"),
            collectionId = obj.optCleanString("collectionId").ifEmpty { "ce_teams" },
            season = obj.optCleanString("season"),
            name = obj.optCleanString("name"),
            shortName = obj.optCleanString("short_name"),
            color = obj.optCleanString("color"),
            logo = obj.optCleanString("logo"),
            ownerName = obj.optCleanString("owner_name"),
            ownerPhoto = obj.optCleanString("owner_photo"),
            managerName = obj.optCleanString("manager_name"),
            managerPhoto = obj.optCleanString("manager_photo"),
            captain = obj.optCleanString("captain")
        )
    }

    fun parsePlayer(obj: JSONObject): CePlayer {
        return CePlayer(
            id = obj.optCleanString("id"),
            collectionId = obj.optCleanString("collectionId").ifEmpty { "ce_players" },
            name = obj.optCleanString("name"),
            batch = obj.optCleanString("batch"),
            role = obj.optCleanString("role"),
            team = obj.optCleanString("team"),
            photo = obj.optCleanString("photo")
        )
    }

    fun parseMatch(obj: JSONObject): CeMatch {
        val scorecardObj = obj.optJSONObject("scorecard")
        val scoringObj = obj.optJSONObject("scoring")
        return CeMatch(
            id = obj.optCleanString("id"),
            season = obj.optCleanString("season"),
            teamA = obj.optCleanString("team_a"),
            teamB = obj.optCleanString("team_b"),
            startsAt = obj.optCleanString("starts_at"),
            stage = obj.optCleanString("stage"),
            overs = obj.optInt("overs", 0),
            status = obj.optCleanString("status"),
            published = true,
            winner = obj.optCleanString("winner"),
            scorecard = scorecardObj?.let { parseScorecard(it, obj.optCleanString("id")) },
            updated = obj.optCleanString("updated"),
            scoring = scoringObj?.let { parseScoringLog(it) },
            playerOfMatch = obj.optCleanString("player_of_match")
        )
    }

    fun parseScorecard(obj: JSONObject, matchId: String = ""): CeScorecard? {
        if (obj.length() == 0) return null
        val inningsArr = obj.optJSONArray("innings")
        val inningsList = mutableListOf<CeInnings>()
        if (inningsArr != null) {
            for (i in 0 until inningsArr.length()) {
                val innObj = inningsArr.optJSONObject(i) ?: continue
                inningsList.add(parseInnings(innObj))
            }
        }

        val recentArr = obj.optJSONArray("recent")
        val recentList = mutableListOf<String>()
        if (recentArr != null) {
            for (i in 0 until recentArr.length()) {
                val label = recentArr.optString(i, "")
                if (label.isNotEmpty() && label != "null") {
                    recentList.add(label)
                }
            }
        }

        val battersArr = obj.optJSONArray("currentBatters")
        val battersList = mutableListOf<String>()
        if (battersArr != null) {
            for (i in 0 until battersArr.length()) {
                val b = battersArr.optString(i, "")
                if (b.isNotEmpty() && b != "null") {
                    battersList.add(b)
                }
            }
        }

        val playingSidesArr = obj.optJSONArray("playingSides")
        val playingSidesList = mutableListOf<CePlayingSide>()
        if (playingSidesArr != null) {
            for (i in 0 until playingSidesArr.length()) {
                val sideObj = playingSidesArr.optJSONObject(i) ?: continue
                val teamId = sideObj.optCleanString("team")
                val playersArr = sideObj.optJSONArray("players")
                val pList = mutableListOf<String>()
                if (playersArr != null) {
                    for (j in 0 until playersArr.length()) {
                        val pid = playersArr.optString(j, "")
                        if (pid.isNotEmpty() && pid != "null") {
                            pList.add(pid)
                        }
                    }
                }
                playingSidesList.add(CePlayingSide(team = teamId, players = pList))
            }
        }

        // Critical single-source rule:
        // Resolve player figures from scorecard.playerStats whenever that field is present and non-null.
        // Even an empty array is authoritative.
        val hasPlayerStats = obj.has("playerStats") && !obj.isNull("playerStats")
        val playerStatsList: List<PlayerMatchStat>? = if (hasPlayerStats) {
            val statsArr = obj.optJSONArray("playerStats")
            if (statsArr != null) {
                val list = mutableListOf<PlayerMatchStat>()
                for (i in 0 until statsArr.length()) {
                    val stObj = statsArr.optJSONObject(i) ?: continue
                    list.add(parsePlayerMatchStat(stObj, fallbackMatchId = matchId))
                }
                list
            } else {
                null
            }
        } else {
            null
        }

        val resultStr = obj.optCleanString("result").ifEmpty { null }
        val bowlerStr = obj.optCleanString("currentBowler").ifEmpty { null }

        return CeScorecard(
            innings = inningsList,
            result = resultStr,
            recent = recentList,
            currentBatters = battersList,
            currentBowler = bowlerStr,
            playingSides = playingSidesList,
            playerStats = playerStatsList,
            superOver = obj.optJSONObject("superOver")?.let { parseScorecard(it, matchId) }
        )
    }

    private fun parseInnings(obj: JSONObject): CeInnings {
        val breakdownObj = obj.optJSONObject("extrasBreakdown")
        val breakdown = breakdownObj?.let {
            CeExtrasBreakdown(
                wide = it.optInt("wide", 0),
                noBall = it.optInt("noBall", 0),
                bye = it.optInt("bye", 0),
                legBye = it.optInt("legBye", 0)
            )
        }
        val quota = if (obj.has("quota") && !obj.isNull("quota")) obj.optInt("quota", 0).takeIf { it > 0 } else null
        val extras = if (obj.has("extras") && !obj.isNull("extras")) obj.optInt("extras", 0) else null
        val adjustedRuns = if (obj.has("adjustedRuns") && !obj.isNull("adjustedRuns")) obj.optInt("adjustedRuns", 0) else null

        return CeInnings(
            team = obj.optCleanString("team"),
            runs = obj.optInt("runs", 0),
            wickets = obj.optInt("wickets", 0),
            balls = obj.optInt("balls", 0),
            allOut = obj.optBoolean("allOut", false),
            quota = quota,
            extras = extras,
            extrasBreakdown = breakdown,
            endedEarly = obj.optBoolean("endedEarly", false),
            adjustedRuns = adjustedRuns,
            fallOfWickets = obj.optJSONArray("fallOfWickets")?.let { entries ->
                (0 until entries.length()).mapNotNull { index ->
                    val entry = entries.optJSONObject(index) ?: return@mapNotNull null
                    CeFallOfWicket(
                        wicket = entry.optInt("wicket"), runs = entry.optInt("runs"),
                        player = entry.optCleanString("player"), balls = entry.optInt("balls")
                    )
                }
            } ?: emptyList()
        )
    }

    fun parsePlayerMatchStat(obj: JSONObject, fallbackMatchId: String = ""): PlayerMatchStat {
        return PlayerMatchStat(
            id = obj.optCleanString("id"),
            match = obj.optCleanString("match").ifEmpty { fallbackMatchId },
            player = obj.optCleanString("player"),
            team = obj.optCleanString("team"),
            runs = obj.optInt("runs", 0),
            ballsFaced = obj.optInt("balls_faced", 0),
            fours = obj.optInt("fours", 0),
            sixes = obj.optInt("sixes", 0),
            wickets = obj.optInt("wickets", 0),
            ballsBowled = obj.optInt("balls_bowled", 0),
            runsConceded = obj.optInt("runs_conceded", 0),
            batted = obj.optBoolean("batted", false),
            notOut = obj.optBoolean("not_out", false),
            retired = obj.optBoolean("retired", false)
        )
    }

    fun parseSeasonHistory(obj: JSONObject): CeSeasonHistory {
        val yearVal = if (obj.has("year") && !obj.isNull("year")) {
            val y = obj.optInt("year", 0)
            if (y > 0) y else null
        } else null
        return CeSeasonHistory(
            id = obj.optCleanString("id"),
            collectionId = obj.optCleanString("collectionId").ifEmpty { "ce_season_history" },
            seasonNumber = obj.optInt("season_number", 0),
            year = yearVal,
            champion = obj.optCleanString("champion"),
            runnerUp = obj.optCleanString("runner_up"),
            championLogo = obj.optCleanString("champion_logo"),
            runnerUpLogo = obj.optCleanString("runner_up_logo"),
            championCaptain = obj.optCleanString("champion_captain"),
            runnerUpCaptain = obj.optCleanString("runner_up_captain"),
            teamPhoto = obj.optCleanString("team_photo"),
            published = obj.optBoolean("published", false),
            runnerUpPhoto = obj.optCleanString("runner_up_photo"),
            edition = obj.optCleanString("edition")
        )
    }

    fun parseScoringLog(obj: JSONObject): CeScoringLog? {
        if (obj.length() == 0) return null
        val version = obj.optInt("version", 0)
        if (version != 1) return null
        val overs = obj.optInt("overs", 0)
        val outcome = obj.optCleanString("outcome").ifEmpty { null }

        val lineupsArr = obj.optJSONArray("lineups")
        val lineups = mutableListOf<CeScoringLineup>()
        if (lineupsArr != null) {
            for (i in 0 until lineupsArr.length()) {
                val lObj = lineupsArr.optJSONObject(i) ?: continue
                val tId = lObj.optCleanString("team")
                val pArr = lObj.optJSONArray("players")
                val pList = mutableListOf<String>()
                if (pArr != null) {
                    for (j in 0 until pArr.length()) {
                        val pid = pArr.optString(j, "")
                        if (pid.isNotEmpty() && pid != "null") pList.add(pid)
                    }
                }
                lineups.add(CeScoringLineup(team = tId, players = pList))
            }
        }

        val inningsArr = obj.optJSONArray("innings")
        val innings = mutableListOf<CeScoringInnings>()
        if (inningsArr != null) {
            for (i in 0 until inningsArr.length()) {
                val innObj = inningsArr.optJSONObject(i) ?: continue
                val tId = innObj.optCleanString("team")
                val openObj = innObj.optJSONObject("opening")
                val opening = CeScoringOpening(
                    striker = openObj?.optCleanString("striker")?.ifEmpty { null },
                    partner = openObj?.optCleanString("partner")?.ifEmpty { null },
                    bowler = openObj?.optCleanString("bowler")?.ifEmpty { null }
                )
                val playsArr = innObj.optJSONArray("plays")
                val plays = mutableListOf<CeScoringPlay>()
                if (playsArr != null) {
                    for (j in 0 until playsArr.length()) {
                        val pObj = playsArr.getJSONObject(j)
                        plays.add(parseScoringPlay(pObj))
                    }
                }
                innings.add(CeScoringInnings(team = tId, opening = opening, plays = plays))
            }
        }

        return CeScoringLog(
            version = version,
            overs = overs,
            lineups = lineups,
            innings = innings,
            outcome = outcome
        )
    }

    private fun parseScoringPlay(obj: JSONObject): CeScoringPlay {
        return when (obj.optCleanString("type")) {
            "ball" -> {
                val delObj = obj.getJSONObject("delivery")
                val kind = delObj.getString("kind")
                val runType = delObj.getString("runType")
                require(kind in listOf("legal", "wide", "noBall") && runType in listOf("bat", "bye", "legBye"))
                val runs = delObj.optInt("runs", 0)
                val boundary = if (delObj.has("boundary") && !delObj.isNull("boundary")) {
                    delObj.optInt("boundary", 0).takeIf { it == 4 || it == 6 }
                } else null
                val wObj = delObj.optJSONObject("wicket")
                val wicket = wObj?.let {
                    CeScoringWicket(
                        player = it.optCleanString("player"),
                        how = it.optCleanString("how"),
                        next = it.optCleanString("next").ifEmpty { null },
                        incomingOnStrike = if (it.has("incomingOnStrike") && !it.isNull("incomingOnStrike")) {
                            it.optBoolean("incomingOnStrike")
                        } else null
                    )
                }
                CeScoringPlay.Ball(
                    CeScoringDelivery(
                        kind = kind,
                        runType = runType,
                        runs = runs,
                        boundary = boundary,
                        wicket = wicket
                    )
                )
            }
            "bowler" -> CeScoringPlay.Bowler(player = obj.optCleanString("player"))
            "swap" -> CeScoringPlay.Swap
            "replaceBatter" -> CeScoringPlay.ReplaceBatter(
                outgoing = obj.optCleanString("outgoing"),
                incoming = obj.optCleanString("incoming")
            )
            "adjustRuns" -> CeScoringPlay.AdjustRuns(runs = obj.optInt("runs", 0))
            "closeInnings" -> CeScoringPlay.CloseInnings
            else -> error("Unsupported scoring event: ${obj.optCleanString("type")}")
        }
    }

    private fun JSONObject.optCleanString(key: String): String {
        if (!has(key) || isNull(key)) return ""
        val raw = optString(key, "")
        return if (raw == "null") "" else raw.trim()
    }
}
