package com.example.domain

import com.example.model.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class MatchFilter {
    ALL, FIXTURES, RESULTS
}

data class PointsTableRow(
    val team: CeTeam,
    val played: Int,
    val won: Int,
    val lost: Int,
    val tiedOrNoResult: Int,
    val points: Int,
    val nrr: Double?,
    val nrrDisplay: String
)

data class PlayerLeaderboardRow(
    val player: CePlayer,
    val team: CeTeam?,
    val matches: Int,
    val runs: Int,
    val ballsFaced: Int,
    val fours: Int,
    val sixes: Int,
    val wickets: Int,
    val ballsBowled: Int,
    val runsConceded: Int,
    val strikeRateUnrounded: Double,
    val strikeRateDisplay: String
)

data class TournamentStatsSummary(
    val totalFours: Int,
    val totalSixes: Int,
    val mostRuns: List<PlayerLeaderboardRow>,
    val mostWickets: List<PlayerLeaderboardRow>,
    val highestStrikeRate: List<PlayerLeaderboardRow>
)

data class PlayerMatchBattingRow(
    val match: CeMatch,
    val opponent: CeTeam?,
    val runs: Int,
    val ballsFaced: Int,
    val fours: Int,
    val sixes: Int,
    val notOut: Boolean,
    val retired: Boolean
)

data class PlayerMatchBowlingRow(
    val match: CeMatch,
    val opponent: CeTeam?,
    val ballsBowled: Int,
    val oversDisplay: String,
    val runsConceded: Int,
    val wickets: Int
)

data class PlayerProfileSummary(
    val player: CePlayer,
    val team: CeTeam?,
    val matchCount: Int,
    // Batting
    val totalRuns: Int,
    val highestScoreRuns: Int?,
    val highestScoreNotOut: Boolean,
    val highestScoreDisplay: String,
    val totalFours: Int,
    val totalSixes: Int,
    val seasonStrikeRateDisplay: String,
    val battingRows: List<PlayerMatchBattingRow>,
    // Bowling
    val totalWickets: Int,
    val totalBallsBowled: Int,
    val totalOversDisplay: String,
    val bestBowlingWickets: Int?,
    val bestBowlingRuns: Int?,
    val bestBowlingDisplay: String,
    val seasonEconomyDisplay: String,
    val bowlingRows: List<PlayerMatchBowlingRow>
)

object CricketCalculations {

    const val POCKETBASE_BASE_URL = "https://pbdb2.duckdns.org"
    val DHAKA_ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

    private val fixtureFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.US)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    private val timeOnlyFormatter = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)

    /**
     * Select active season dynamically:
     * Sort by -number, use the first active season, and fall back to the highest-numbered season if none is active.
     */
    fun selectSeason(seasons: List<CeSeason>): CeSeason? {
        if (seasons.isEmpty()) return null
        val sorted = seasons.sortedByDescending { it.number }
        return sorted.firstOrNull { it.active } ?: sorted.first()
    }

    /**
     * For an innings, balls means legal deliveries.
     * Display 17 balls as 2.5 overs using integer quotient and remainder by six.
     */
    fun formatOvers(legalBalls: Int): String {
        val safeBalls = maxOf(0, legalBalls)
        val completedOvers = safeBalls / 6
        val remBalls = safeBalls % 6
        return "$completedOvers.$remBalls"
    }

    /**
     * For calculations, 17 balls means 17 / 6.0 overs.
     * The decimal-looking display string must never be parsed as a number.
     */
    fun oversAsDouble(legalBalls: Int): Double {
        return maxOf(0, legalBalls) / 6.0
    }

    /**
     * Calculate innings run rate or bowling economy as 6 * runs / legalBalls, displayed to two decimals.
     * Use "—" for a zero denominator.
     */
    fun formatRunRateOrEconomy(runs: Int, legalBalls: Int): String {
        if (legalBalls <= 0) return "—"
        val rate = (6.0 * runs) / legalBalls.toDouble()
        return String.format(Locale.US, "%.2f", rate)
    }

    /**
     * Calculate batting strike rate as 100 * runs / ballsFaced, displayed to one decimal.
     * Use "—" for a zero denominator.
     */
    fun formatStrikeRate(runs: Int, ballsFaced: Int): String {
        if (ballsFaced <= 0) return "—"
        val sr = (100.0 * runs) / ballsFaced.toDouble()
        return String.format(Locale.US, "%.1f", sr)
    }

    fun formatSignedAdjustment(adjustedRuns: Int): String {
        return if (adjustedRuns > 0) "+$adjustedRuns" else adjustedRuns.toString()
    }

    /**
     * Public status labels:
     * scheduled -> Upcoming, live -> Live, completed -> Result, abandoned -> Abandoned
     */
    fun statusLabel(status: String): String {
        return when (status.lowercase(Locale.US)) {
            "scheduled" -> "Upcoming"
            "live" -> "Live"
            "completed" -> "Result"
            "abandoned" -> "Abandoned"
            else -> status.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Stage labels:
     * league and legacy group -> League, semifinal -> Semi-final, final -> Final, friendly -> Friendly
     */
    fun stageLabel(stage: String): String {
        return when (stage.lowercase(Locale.US)) {
            "league", "group" -> "League"
            "semifinal" -> "Semi-final"
            "final" -> "Final"
            "friendly" -> "Friendly"
            else -> stage.replaceFirstChar { it.uppercase() }
        }
    }

    fun isLeagueStage(stage: String): Boolean {
        val s = stage.lowercase(Locale.US)
        return s == "league" || s == "group"
    }

    /**
     * Resolve images as /api/files/{collectionId}/{recordId}/{filename} on the PocketBase base URL.
     * URL-encode each path segment.
     */
    fun buildFileUrl(
        collectionId: String,
        recordId: String,
        filename: String,
        baseUrl: String = POCKETBASE_BASE_URL
    ): String? {
        if (collectionId.isBlank() || recordId.isBlank() || filename.isBlank()) return null
        val c = encodePathSegment(collectionId)
        val r = encodePathSegment(recordId)
        val f = encodePathSegment(filename)
        return "${baseUrl.trimEnd('/')}/api/files/$c/$r/$f"
    }

    fun encodePathSegment(segment: String): String {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
    }

    /**
     * Parse PocketBase timestamps as instants, including values like "2026-11-17 08:00:00.000Z".
     */
    fun parsePocketBaseInstant(raw: String): Instant? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val iso = if (trimmed.contains(' ') && !trimmed.contains('T')) {
                trimmed.replaceFirst(' ', 'T')
            } else {
                trimmed
            }
            Instant.parse(iso)
        } catch (_: Exception) {
            null
        }
    }

    fun formatDhakaDateTime(raw: String): String {
        val instant = parsePocketBaseInstant(raw) ?: return raw
        val zdt = ZonedDateTime.ofInstant(instant, DHAKA_ZONE)
        return zdt.format(fixtureFormatter)
    }

    fun formatDhakaShortDate(raw: String): String {
        val instant = parsePocketBaseInstant(raw) ?: return raw
        val zdt = ZonedDateTime.ofInstant(instant, DHAKA_ZONE)
        return zdt.format(shortDateFormatter)
    }

    fun formatDhakaTimeOnly(raw: String): String {
        val instant = parsePocketBaseInstant(raw) ?: return raw
        val zdt = ZonedDateTime.ofInstant(instant, DHAKA_ZONE)
        return zdt.format(timeOnlyFormatter) + " (Dhaka)"
    }

    /**
     * Filter and order matches for Home:
     * - All matches shows live matches first and keeps chronological order within the live and other groups.
     * - Fixtures shows scheduled matches in chronological order.
     * - Results shows completed and abandoned matches.
     */
    fun filterMatchesForHome(matches: List<CeMatch>, filter: MatchFilter): List<CeMatch> {
        val published = matches
            .sortedWith(compareBy<CeMatch> { parsePocketBaseInstant(it.startsAt) ?: Instant.MAX }.thenBy { it.startsAt })

        return when (filter) {
            MatchFilter.ALL -> {
                val (live, others) = published.filterNot { it.status.equals("scheduled", ignoreCase = true) }
                    .partition { it.status.equals("live", ignoreCase = true) }
                live + others
            }
            MatchFilter.FIXTURES -> {
                published.filter { it.status.equals("scheduled", ignoreCase = true) }
            }
            MatchFilter.RESULTS -> {
                published.filter {
                    it.status.equals("completed", ignoreCase = true) ||
                        it.status.equals("abandoned", ignoreCase = true)
                }
            }
        }
    }

    /**
     * Single-source rule:
     * Resolve player figures from scorecard.playerStats whenever that field is present and non-null.
     * Even an empty array is authoritative.
     * Fall back to ce_player_match_stats rows with the same match ID only when embedded figures are absent or null.
     */
    fun resolveMatchPlayerStats(
        match: CeMatch,
        legacyStatsByMatchId: Map<String, List<PlayerMatchStat>>
    ): List<PlayerMatchStat> {
        val embedded = match.scorecard?.playerStats
        if (embedded != null) {
            return embedded
        }
        return legacyStatsByMatchId[match.id] ?: emptyList()
    }

    /**
     * Calculate league Points Table and NRR.
     */
    fun computePointsTable(
        teams: List<CeTeam>,
        matches: List<CeMatch>
    ): List<PointsTableRow> {
        val leagueMatches = matches.filter { match ->
                isLeagueStage(match.stage) &&
                (match.status.equals("completed", ignoreCase = true) ||
                    match.status.equals("abandoned", ignoreCase = true))
        }

        return teams.map { team ->
            var played = 0
            var won = 0
            var lost = 0
            var tiedOrNoResult = 0
            var points = 0

            var totalRunsScored = 0
            var totalBallsFaced = 0
            var totalRunsConceded = 0
            var totalBallsBowled = 0

            for (match in leagueMatches) {
                if (match.teamA != team.id && match.teamB != team.id) continue
                val opponentId = if (match.teamA == team.id) match.teamB else match.teamA
                played += 1

                if (match.status.equals("abandoned", ignoreCase = true)) {
                    tiedOrNoResult += 1
                    points += 1
                    // Abandoned match contributes no NRR data.
                } else if (match.status.equals("completed", ignoreCase = true)) {
                    when {
                        match.winner == team.id -> {
                            won += 1
                            points += 2
                        }
                        match.winner == opponentId && opponentId.isNotEmpty() -> {
                            lost += 1
                        }
                        else -> {
                            // Tie or no winner recorded
                            tiedOrNoResult += 1
                            points += 1
                        }
                    }

                    // For NRR, use completed league matches with innings totals for both teams.
                    val inningsList = match.scorecard?.innings ?: emptyList()
                    val ownInnings = inningsList.firstOrNull { it.team == team.id }
                    val oppInnings = inningsList.firstOrNull { it.team == opponentId }

                    if (ownInnings != null && oppInnings != null) {
                        totalRunsScored += ownInnings.runs
                        totalBallsFaced += effectiveBallsForNrr(ownInnings, match.overs)

                        totalRunsConceded += oppInnings.runs
                        totalBallsBowled += effectiveBallsForNrr(oppInnings, match.overs)
                    }
                }
            }

            val nrrValue: Double? = if (totalBallsFaced > 0 && totalBallsBowled > 0) {
                6.0 * (
                    totalRunsScored.toDouble() / totalBallsFaced.toDouble() -
                        totalRunsConceded.toDouble() / totalBallsBowled.toDouble()
                    )
            } else {
                null
            }

            val nrrDisplay = formatNrr(nrrValue)

            PointsTableRow(
                team = team,
                played = played,
                won = won,
                lost = lost,
                tiedOrNoResult = tiedOrNoResult,
                points = points,
                nrr = nrrValue,
                nrrDisplay = nrrDisplay
            )
        }.sortedWith(
            compareByDescending<PointsTableRow> { it.points }
                .thenByDescending { it.won }
                .thenByDescending { it.nrr ?: 0.0 }
                .thenBy { it.team.name.lowercase(Locale.US) }
        )
    }

    /**
     * If a side was all out, use (innings.quota or match.overs) * 6 balls for that side.
     * For an innings ended early without being all out, use its actual legal balls.
     */
    fun effectiveBallsForNrr(innings: CeInnings, matchOvers: Int): Int {
        return if (innings.allOut) {
            val quotaOvers = innings.quota?.takeIf { it > 0 } ?: matchOvers
            maxOf(0, quotaOvers * 6)
        } else {
            maxOf(0, innings.balls)
        }
    }

    fun formatNrr(nrr: Double?): String {
        if (nrr == null) return "—"
        if (abs(nrr) < 0.0005) return "0.000"
        return if (nrr > 0.0) {
            String.format(Locale.US, "+%.3f", nrr)
        } else {
            String.format(Locale.US, "%.3f", nrr)
        }
    }

    /**
     * Tournament Stats aggregates player figures from every published, completed match
     * in the active season, across all stages. Use the single-source rule per match.
     * A live or abandoned match contributes no season ranking figures.
     */
    fun computeTournamentStats(
        matches: List<CeMatch>,
        players: List<CePlayer>,
        teams: List<CeTeam>,
        legacyStatsByMatchId: Map<String, List<PlayerMatchStat>>
    ): TournamentStatsSummary {
        val completedMatches = matches.filter {
            it.status.equals("completed", ignoreCase = true)
        }

        val playerById = players.associateBy { it.id }
        val teamById = teams.associateBy { it.id }

        var tournamentFours = 0
        var tournamentSixes = 0

        data class Accumulator(
            var matches: Int = 0,
            var runs: Int = 0,
            var ballsFaced: Int = 0,
            var fours: Int = 0,
            var sixes: Int = 0,
            var wickets: Int = 0,
            var ballsBowled: Int = 0,
            var runsConceded: Int = 0,
            var lastTeamId: String = ""
        )

        val accumulators = mutableMapOf<String, Accumulator>()

        for (match in completedMatches) {
            val matchStats = resolveMatchPlayerStats(match, legacyStatsByMatchId)
            val seenPlayersInMatch = mutableSetOf<String>()
            for (st in matchStats) {
                tournamentFours += st.fours
                tournamentSixes += st.sixes

                if (st.player.isBlank()) continue
                val acc = accumulators.getOrPut(st.player) { Accumulator() }
                if (seenPlayersInMatch.add(st.player)) {
                    acc.matches += 1
                }
                acc.runs += st.runs
                acc.ballsFaced += st.ballsFaced
                acc.fours += st.fours
                acc.sixes += st.sixes
                acc.wickets += st.wickets
                acc.ballsBowled += st.ballsBowled
                acc.runsConceded += st.runsConceded
                if (st.team.isNotBlank()) {
                    acc.lastTeamId = st.team
                }
            }
        }

        val allRows = accumulators.map { (playerId, acc) ->
            val player = playerById[playerId] ?: CePlayer(
                id = playerId,
                name = playerId,
                team = acc.lastTeamId
            )
            val teamId = player.team.ifBlank { acc.lastTeamId }
            val srUnrounded = if (acc.ballsFaced > 0) {
                (100.0 * acc.runs) / acc.ballsFaced.toDouble()
            } else {
                0.0
            }
            PlayerLeaderboardRow(
                player = player,
                team = teamById[teamId],
                matches = acc.matches,
                runs = acc.runs,
                ballsFaced = acc.ballsFaced,
                fours = acc.fours,
                sixes = acc.sixes,
                wickets = acc.wickets,
                ballsBowled = acc.ballsBowled,
                runsConceded = acc.runsConceded,
                strikeRateUnrounded = srUnrounded,
                strikeRateDisplay = formatStrikeRate(acc.runs, acc.ballsFaced)
            )
        }

        val mostRuns = allRows
            .filter { it.runs > 0 }
            .sortedWith(
                compareByDescending<PlayerLeaderboardRow> { it.runs }
                    .thenBy { it.player.name.lowercase(Locale.US) }
            )
            .take(10)

        val mostWickets = allRows
            .filter { it.wickets > 0 }
            .sortedWith(
                compareByDescending<PlayerLeaderboardRow> { it.wickets }
                    .thenBy { it.player.name.lowercase(Locale.US) }
            )
            .take(10)

        val highestStrikeRate = allRows
            .filter { it.ballsFaced >= 10 }
            .sortedWith(
                compareByDescending<PlayerLeaderboardRow> { it.strikeRateUnrounded }
                    .thenBy { it.player.name.lowercase(Locale.US) }
            )
            .take(10)

        return TournamentStatsSummary(
            totalFours = tournamentFours,
            totalSixes = tournamentSixes,
            mostRuns = mostRuns,
            mostWickets = mostWickets,
            highestStrikeRate = highestStrikeRate
        )
    }

    /**
     * Compute player profile stats from finalized published matches in the selected season.
     */
    fun computePlayerProfile(
        player: CePlayer,
        teams: List<CeTeam>,
        matches: List<CeMatch>,
        legacyStatsByMatchId: Map<String, List<PlayerMatchStat>>
    ): PlayerProfileSummary {
        val teamById = teams.associateBy { it.id }
        val completedMatches = matches
            .filter { it.status.equals("completed", ignoreCase = true) }
            .sortedWith(compareBy<CeMatch> { parsePocketBaseInstant(it.startsAt) ?: Instant.MAX }.thenBy { it.startsAt })

        var matchCount = 0
        val battingRows = mutableListOf<PlayerMatchBattingRow>()
        val bowlingRows = mutableListOf<PlayerMatchBowlingRow>()

        for (match in completedMatches) {
            val resolvedStats = resolveMatchPlayerStats(match, legacyStatsByMatchId)
            val playerStat = resolvedStats.firstOrNull { it.player == player.id }
            val inPlayingSide = match.scorecard?.playingSides?.any { player.id in it.players } == true

            if (playerStat != null || inPlayingSide) {
                matchCount += 1
            }

            if (playerStat != null) {
                val playerTeamId = playerStat.team.ifBlank { player.team }
                val opponentId = when (playerTeamId) {
                    match.teamA -> match.teamB
                    match.teamB -> match.teamA
                    else -> if (match.teamA == player.team) match.teamB else match.teamA
                }
                val opponent = teamById[opponentId]

                if (playerStat.batted) {
                    battingRows.add(
                        PlayerMatchBattingRow(
                            match = match,
                            opponent = opponent,
                            runs = playerStat.runs,
                            ballsFaced = playerStat.ballsFaced,
                            fours = playerStat.fours,
                            sixes = playerStat.sixes,
                            notOut = playerStat.notOut,
                            retired = playerStat.retired
                        )
                    )
                }

                if (playerStat.ballsBowled > 0 || playerStat.runsConceded > 0 || playerStat.wickets > 0) {
                    bowlingRows.add(
                        PlayerMatchBowlingRow(
                            match = match,
                            opponent = opponent,
                            ballsBowled = playerStat.ballsBowled,
                            oversDisplay = formatOvers(playerStat.ballsBowled),
                            runsConceded = playerStat.runsConceded,
                            wickets = playerStat.wickets
                        )
                    )
                }
            }
        }

        val totalRuns = battingRows.sumOf { it.runs }
        val totalBallsFaced = battingRows.sumOf { it.ballsFaced }
        val totalFours = battingRows.sumOf { it.fours }
        val totalSixes = battingRows.sumOf { it.sixes }

        // Highest score is chosen by runs, with not-out ahead when runs tie.
        val bestBatting = battingRows.maxWithOrNull(
            compareBy<PlayerMatchBattingRow> { it.runs }
                .thenBy { if (it.notOut) 1 else 0 }
        )
        val highestScoreDisplay = if (bestBatting != null) {
            "${bestBatting.runs}${if (bestBatting.notOut) "*" else ""}"
        } else {
            "—"
        }

        val totalWickets = bowlingRows.sumOf { it.wickets }
        val totalBallsBowled = bowlingRows.sumOf { it.ballsBowled }
        val totalRunsConceded = bowlingRows.sumOf { it.runsConceded }

        // Best bowling is chosen by most wickets, then fewest runs conceded.
        val bestBowling = bowlingRows.maxWithOrNull(
            compareBy<PlayerMatchBowlingRow> { it.wickets }
                .thenByDescending { it.runsConceded }
        )
        val bestBowlingDisplay = if (bestBowling != null) {
            "${bestBowling.wickets}/${bestBowling.runsConceded}"
        } else {
            "—"
        }

        return PlayerProfileSummary(
            player = player,
            team = teamById[player.team],
            matchCount = matchCount,
            totalRuns = totalRuns,
            highestScoreRuns = bestBatting?.runs,
            highestScoreNotOut = bestBatting?.notOut ?: false,
            highestScoreDisplay = highestScoreDisplay,
            totalFours = totalFours,
            totalSixes = totalSixes,
            seasonStrikeRateDisplay = formatStrikeRate(totalRuns, totalBallsFaced),
            battingRows = battingRows,
            totalWickets = totalWickets,
            totalBallsBowled = totalBallsBowled,
            totalOversDisplay = formatOvers(totalBallsBowled),
            bestBowlingWickets = bestBowling?.wickets,
            bestBowlingRuns = bestBowling?.runsConceded,
            bestBowlingDisplay = bestBowlingDisplay,
            seasonEconomyDisplay = formatRunRateOrEconomy(totalRunsConceded, totalBallsBowled),
            bowlingRows = bowlingRows
        )
    }

    /**
     * Extract up to 2 initials for a player name fallback avatar.
     */
    fun playerInitials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "?"
        if (parts.size == 1) return parts[0].take(2).uppercase(Locale.US)
        return "${parts[0].first()}${parts[1].first()}".uppercase(Locale.US)
    }
}
