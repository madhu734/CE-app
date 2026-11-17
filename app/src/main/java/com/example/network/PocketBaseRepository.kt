package com.example.network

import com.example.domain.CricketCalculations
import com.example.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TournamentSnapshot(
    val seasons: List<CeSeason>,
    val activeSeason: CeSeason?,
    val teams: List<CeTeam>,
    val players: List<CePlayer>,
    val matches: List<CeMatch>,
    val legacyStatsByMatchId: Map<String, List<PlayerMatchStat>>,
    val history: List<CeSeasonHistory>,
    val fetchedAtMillis: Long = System.currentTimeMillis()
)

data class MatchScoringResult(
    val matchId: String,
    val updated: String,
    val scoring: CeScoringLog?
)

interface CricketRepository {
    suspend fun fetchFullTournament(preferredSeasonId: String? = null): Result<TournamentSnapshot>
    suspend fun fetchMatchesForSeason(seasonId: String): Result<List<CeMatch>>
    suspend fun fetchMatchScoring(matchId: String): Result<MatchScoringResult>
    suspend fun fetchSeasonHistory(): Result<List<CeSeasonHistory>>
}

class PocketBaseRepository(
    private val baseUrl: String = CricketCalculations.POCKETBASE_BASE_URL,
    private val client: OkHttpClient = defaultOkHttpClient()
) : CricketRepository {

    companion object {
        const val MATCH_LIST_FIELDS =
            "id,season,team_a,team_b,starts_at,stage,overs,status,published,winner,scorecard,updated"
        const val MATCH_SCORING_FIELDS = "id,scoring,updated"

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun fetchFullTournament(preferredSeasonId: String?): Result<TournamentSnapshot> =
        withContext(Dispatchers.IO) {
            networkResult {
                coroutineScope {
                    val history = async { loadHistory() }
                    val seasons = fetchAllRecords(
                        collection = "ce_seasons",
                        sort = "-number",
                        itemParser = PocketBaseJsonParser::parseSeason
                    )
                    val activeSeason = if (preferredSeasonId != null) {
                        seasons.firstOrNull { it.id == preferredSeasonId }
                            ?: CricketCalculations.selectSeason(seasons)
                    } else {
                        CricketCalculations.selectSeason(seasons)
                    }

                    if (activeSeason == null) {
                        return@coroutineScope TournamentSnapshot(
                            seasons = seasons,
                            activeSeason = null,
                            teams = emptyList(),
                            players = emptyList(),
                            matches = emptyList(),
                            legacyStatsByMatchId = emptyMap(),
                            history = history.await()
                        )
                    }

                    val seasonFilter = "season = \"${escapeFilterValue(activeSeason.id)}\""
                    val teams = async {
                        fetchAllRecords(
                            collection = "ce_teams",
                            filter = seasonFilter,
                            sort = "name",
                            itemParser = PocketBaseJsonParser::parseTeam
                        )
                    }

                    val players = async {
                        fetchAllRecords(
                            collection = "ce_players",
                            sort = "name",
                            itemParser = PocketBaseJsonParser::parsePlayer
                        )
                    }

                    val matches = async { fetchMatchesInternal(activeSeason.id) }

                    val legacyStats = async {
                        fetchAllRecords(
                            collection = "ce_player_match_stats",
                            filter = "match.season = \"${escapeFilterValue(activeSeason.id)}\"",
                            itemParser = { PocketBaseJsonParser.parsePlayerMatchStat(it) }
                        ).groupBy { it.match }
                    }

                    TournamentSnapshot(
                        seasons = seasons,
                        activeSeason = activeSeason,
                        teams = teams.await(),
                        players = players.await(),
                        matches = matches.await(),
                        legacyStatsByMatchId = legacyStats.await(),
                        history = history.await(),
                        fetchedAtMillis = System.currentTimeMillis()
                    )
                }
            }
        }

    override suspend fun fetchMatchesForSeason(seasonId: String): Result<List<CeMatch>> =
        withContext(Dispatchers.IO) {
            networkResult {
                fetchMatchesInternal(seasonId)
            }
        }

    override suspend fun fetchMatchScoring(matchId: String): Result<MatchScoringResult> =
        withContext(Dispatchers.IO) {
            networkResult {
                val safeId = CricketCalculations.encodePathSegment(matchId)
                val url = "${baseUrl.trimEnd('/')}/api/collections/ce_matches/records/$safeId"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("fields", MATCH_SCORING_FIELDS)
                    .build()

                val body = executeGet(url.toString())
                val json = JSONObject(body)
                val id = json.optString("id", matchId)
                val updated = json.optString("updated", "")
                val scoringObj = json.optJSONObject("scoring")
                val scoringLog = scoringObj?.let { PocketBaseJsonParser.parseScoringLog(it) }
                MatchScoringResult(
                    matchId = id,
                    updated = updated,
                    scoring = scoringLog
                )
            }
        }

    override suspend fun fetchSeasonHistory(): Result<List<CeSeasonHistory>> =
        withContext(Dispatchers.IO) { networkResult { loadHistory() } }

    private suspend fun loadHistory() = fetchAllRecords(
        collection = "ce_season_history",
        filter = "published = true",
        sort = "-season_number",
        itemParser = PocketBaseJsonParser::parseSeasonHistory
    )

    private suspend fun fetchMatchesInternal(seasonId: String): List<CeMatch> {
        val filter = "season = \"${escapeFilterValue(seasonId)}\" && published = true"
        return fetchAllRecords(
            collection = "ce_matches",
            filter = filter,
            sort = "starts_at",
            fields = MATCH_LIST_FIELDS,
            itemParser = PocketBaseJsonParser::parseMatch
        )
    }

    /**
     * Fetches all pages from a PocketBase collection endpoint until page >= totalPages.
     */
    private suspend fun <T> fetchAllRecords(
        collection: String,
        filter: String? = null,
        sort: String? = null,
        fields: String? = null,
        perPage: Int = 100,
        itemParser: (JSONObject) -> T
    ): List<T> {
        val allItems = mutableListOf<T>()
        var currentPage = 1
        var totalPages = 1

        while (currentPage <= totalPages) {
            val safeCollection = CricketCalculations.encodePathSegment(collection)
            val urlBuilder = "${baseUrl.trimEnd('/')}/api/collections/$safeCollection/records"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("page", currentPage.toString())
                .addQueryParameter("perPage", perPage.toString())

            if (!filter.isNullOrBlank()) {
                urlBuilder.addQueryParameter("filter", filter)
            }
            if (!sort.isNullOrBlank()) {
                urlBuilder.addQueryParameter("sort", sort)
            }
            if (!fields.isNullOrBlank()) {
                urlBuilder.addQueryParameter("fields", fields)
            }

            val responseBody = executeGet(urlBuilder.build().toString())
            val pageResult = PocketBaseJsonParser.parseListResponse(responseBody, itemParser)
            allItems.addAll(pageResult.items)
            totalPages = maxOf(1, pageResult.totalPages)
            if (pageResult.items.isEmpty()) break
            currentPage += 1
        }

        return allItems
    }

    private suspend fun executeGet(url: String): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code} for $url")
                        it.body?.string() ?: throw IOException("Empty response body for $url")
                    }
                })
            }
        })
    }

    private inline fun <T> networkResult(action: () -> T): Result<T> =
        runCatching(action).onFailure { if (it is CancellationException) throw it }

    private fun escapeFilterValue(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
