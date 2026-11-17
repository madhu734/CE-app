package com.example.domain

import com.example.model.*

data class ReplayDeliveryRow(
    val eventIndex: Int,
    val overNumber: Int, // 1-indexed over group (1, 2, ...)
    val ballPosition: String, // floor(L/6).(L%6+1), e.g., "0.1"
    val resultChip: String, // e.g., "0", "wd", "nb+4", "2lb", "W", "nb+1b+W"
    val bowlerId: String?,
    val batterId: String?,
    val dismissalText: String?,
    val dismissedPlayerId: String?,
    val runningRuns: Int,
    val runningWickets: Int,
    val isWicket: Boolean,
    val isBoundaryFour: Boolean,
    val isBoundarySix: Boolean
)

data class ReplayAdjustmentRow(
    val eventIndex: Int,
    val deltaRuns: Int,
    val signedText: String,
    val runningRuns: Int,
    val runningWickets: Int,
    val oversLabel: String
)

data class ReplayOverGroup(
    val overNumber: Int, // 1-indexed (Over 1, Over 2...)
    val deliveries: List<ReplayDeliveryRow>,
    val endOfOverRuns: Int,
    val endOfOverWickets: Int
)

data class ReplayChartPoint(
    val eventIndex: Int,
    val legalBalls: Int,
    val xOvers: Double, // legalBalls / 6.0
    val runs: Int,
    val wickets: Int,
    val runRate: Double?, // null when legalBalls == 0
    val isWicket: Boolean,
    val isAdjustment: Boolean
)

data class ReplayedBatterStats(
    val playerId: String,
    val runs: Int,
    val ballsFaced: Int,
    val fours: Int,
    val sixes: Int,
    val dismissed: Boolean
)

data class ReplayedInnings(
    val inningsIndex: Int,
    val teamId: String,
    val totalRuns: Int,
    val totalWickets: Int,
    val legalBalls: Int,
    val oversDisplay: String,
    val closedEarly: Boolean,
    val allOut: Boolean,
    val deliveries: List<ReplayDeliveryRow>,
    val overGroupsNewestFirst: List<ReplayOverGroup>,
    val adjustments: List<ReplayAdjustmentRow>,
    val chartPoints: List<ReplayChartPoint>,
    val reconstructedBatters: Map<String, ReplayedBatterStats>,
    val currentStriker: String?,
    val currentPartner: String?,
    val currentBowler: String?
)

data class ReplayedMatchLog(
    val version: Int,
    val oversQuota: Int,
    val innings: List<ReplayedInnings>,
    val canCompareInnings: Boolean
)

object ScoringReplay {

    /**
     * Local read-only replay function for the public `scoring` JSON.
     * Produces delivery rows, over groups, adjustments, and chart points.
     */
    fun replay(log: CeScoringLog?, matchOversFallback: Int = 0): ReplayedMatchLog? {
        if (log == null || log.version != 1) return null
        val quotaOvers = if (log.overs > 0) log.overs else matchOversFallback
        val maxLegalBalls = if (quotaOvers > 0) quotaOvers * 6 else Int.MAX_VALUE

        val lineupSizeByTeam = log.lineups.associate { it.team to it.players.size }
        val replayedInningsList = mutableListOf<ReplayedInnings>()
        var firstInningsTotal: Int? = null

        for ((inningsIdx, innings) in log.innings.withIndex()) {
            val lineupSize = lineupSizeByTeam[innings.team]?.takeIf { it > 1 } ?: 11
            val allOutWickets = maxOf(1, lineupSize - 1)

            var striker: String? = innings.opening.striker
            var partner: String? = innings.opening.partner
            var bowler: String? = innings.opening.bowler

            var teamRuns = 0
            var wickets = 0
            var legalBalls = 0
            var closedEarly = false

            val deliveries = mutableListOf<ReplayDeliveryRow>()
            val adjustments = mutableListOf<ReplayAdjustmentRow>()
            val chartPoints = mutableListOf<ReplayChartPoint>()

            data class MutableBatter(
                var runs: Int = 0,
                var balls: Int = 0,
                var fours: Int = 0,
                var sixes: Int = 0,
                var dismissed: Boolean = false
            )
            val batterStats = mutableMapOf<String, MutableBatter>()

            for ((eventIdx, play) in innings.plays.withIndex()) {
                when (play) {
                    is CeScoringPlay.Ball -> {
                        val d = play.delivery
                        // Before processing a ball, save the current batter and bowler.
                        val currentBatter = striker
                        val currentBowler = bowler

                        // Label it using the current legal ball count L before the delivery.
                        val lBefore = legalBalls
                        val overIdx = lBefore / 6
                        val ballInOver = (lBefore % 6) + 1
                        val ballPos = "$overIdx.$ballInOver"
                        val overNumber = overIdx + 1

                        val isWide = d.kind.equals("wide", ignoreCase = true)
                        val isNoBall = d.kind.equals("noBall", ignoreCase = true)
                        val isLegal = d.kind.equals("legal", ignoreCase = true)

                        val penaltyRun = if (isWide || isNoBall) 1 else 0
                        teamRuns += d.runs + penaltyRun

                        if (isLegal) {
                            legalBalls += 1
                        }

                        // Reconstruct batting figures for replay tests:
                        // The current website counts balls faced for all delivery kinds except wide.
                        // Byes and leg byes are team extras; they do not add runs to a batter.
                        if (!currentBatter.isNullOrBlank()) {
                            val bStat = batterStats.getOrPut(currentBatter) { MutableBatter() }
                            if (!isWide) {
                                bStat.balls += 1
                            }
                            if (!isWide && d.runType.equals("bat", ignoreCase = true)) {
                                bStat.runs += d.runs
                                if (d.boundary == 4) bStat.fours += 1
                                if (d.boundary == 6) bStat.sixes += 1
                            }
                        }

                        // If the delivery is not marked as a boundary and has an odd number of recorded runs,
                        // swap the two batters. Use the recorded runs here, excluding the automatic wide or no-ball run.
                        val isBoundary = d.boundary == 4 || d.boundary == 6
                        if (!isBoundary && (d.runs % 2 != 0)) {
                            val tmp = striker
                            striker = partner
                            partner = tmp
                        }

                        // On a wicket, increase wickets and place next at the dismissed player's current end
                        // when the innings continues.
                        var dismissalDesc: String? = null
                        var dismissedPlayerId: String? = null
                        if (d.wicket != null) {
                            wickets += 1
                            val w = d.wicket
                            dismissedPlayerId = w.player.ifBlank { currentBatter }
                            if (!dismissedPlayerId.isNullOrBlank()) {
                                batterStats.getOrPut(dismissedPlayerId) { MutableBatter() }.dismissed = true
                            }
                            dismissalDesc = w.how.ifBlank { "Wicket" }

                            val nextBatter = w.next?.takeIf { it.isNotBlank() }
                            if (nextBatter != null && wickets < allOutWickets && legalBalls < maxLegalBalls) {
                                // Place next at the dismissed player's current end
                                val dismissedAtPartnerEnd = !w.player.isBlank() && w.player == partner
                                val survivor = if (dismissedAtPartnerEnd) striker else partner
                                if (dismissedAtPartnerEnd) {
                                    partner = nextBatter
                                } else {
                                    striker = nextBatter
                                }

                                // For Run out and Obstructing the field, position the incoming batter so that
                                // they are the striker before any over-end swap only if incomingOnStrike is true.
                                val isRunOutOrObstructing =
                                    w.how.equals("Run out", ignoreCase = true) ||
                                        w.how.equals("Obstructing the field", ignoreCase = true)
                                if (isRunOutOrObstructing) {
                                    if (w.incomingOnStrike == true) {
                                        striker = nextBatter
                                        partner = survivor
                                    } else {
                                        striker = survivor
                                        partner = nextBatter
                                    }
                                }
                            }
                        }

                        // At the end of a completed six-ball over, swap ends if the innings still has overs
                        // available and the side is not all out.
                        if (isLegal && legalBalls % 6 == 0) {
                            val hasOversAvailable = legalBalls < maxLegalBalls
                            val notAllOut = wickets < allOutWickets
                            if (hasOversAvailable && notAllOut && !closedEarly) {
                                val tmp = striker
                                striker = partner
                                partner = tmp
                                bowler = null
                            }
                        }

                        val chip = buildResultChip(d)

                        deliveries.add(
                            ReplayDeliveryRow(
                                eventIndex = eventIdx,
                                overNumber = overNumber,
                                ballPosition = ballPos,
                                resultChip = chip,
                                bowlerId = currentBowler,
                                batterId = currentBatter,
                                dismissalText = dismissalDesc,
                                dismissedPlayerId = dismissedPlayerId,
                                runningRuns = teamRuns,
                                runningWickets = wickets,
                                isWicket = d.wicket != null,
                                isBoundaryFour = d.boundary == 4,
                                isBoundarySix = d.boundary == 6
                            )
                        )

                        chartPoints.add(
                            ReplayChartPoint(
                                eventIndex = eventIdx,
                                legalBalls = legalBalls,
                                xOvers = legalBalls / 6.0,
                                runs = teamRuns,
                                wickets = wickets,
                                runRate = if (legalBalls > 0) (6.0 * teamRuns) / legalBalls.toDouble() else null,
                                isWicket = d.wicket != null,
                                isAdjustment = false
                            )
                        )
                    }

                    is CeScoringPlay.Bowler -> {
                        bowler = play.player
                    }

                    is CeScoringPlay.Swap -> {
                        val tmp = striker
                        striker = partner
                        partner = tmp
                    }

                    is CeScoringPlay.ReplaceBatter -> {
                        if (striker == play.outgoing) {
                            striker = play.incoming
                        } else if (partner == play.outgoing) {
                            partner = play.incoming
                        }
                    }

                    is CeScoringPlay.AdjustRuns -> {
                        teamRuns += play.runs
                        val signed = CricketCalculations.formatSignedAdjustment(play.runs)
                        adjustments.add(
                            ReplayAdjustmentRow(
                                eventIndex = eventIdx,
                                deltaRuns = play.runs,
                                signedText = signed,
                                runningRuns = teamRuns,
                                runningWickets = wickets,
                                oversLabel = CricketCalculations.formatOvers(legalBalls)
                            )
                        )
                        chartPoints.add(
                            ReplayChartPoint(
                                eventIndex = eventIdx,
                                legalBalls = legalBalls,
                                xOvers = legalBalls / 6.0,
                                runs = teamRuns,
                                wickets = wickets,
                                runRate = if (legalBalls > 0) (6.0 * teamRuns) / legalBalls.toDouble() else null,
                                isWicket = false,
                                isAdjustment = true
                            )
                        )
                    }

                    is CeScoringPlay.CloseInnings -> {
                        closedEarly = true
                    }
                }
            }

            if (inningsIdx == 0) {
                firstInningsTotal = teamRuns
            }

            // Group deliveries by over, latest over first. Within each over, preserve original delivery order.
            val overGroups = deliveries
                .groupBy { it.overNumber }
                .entries
                .sortedByDescending { it.key }
                .map { (overNum, rows) ->
                    val lastRow = rows.last()
                    ReplayOverGroup(
                        overNumber = overNum,
                        deliveries = rows,
                        endOfOverRuns = lastRow.runningRuns,
                        endOfOverWickets = lastRow.runningWickets
                    )
                }

            replayedInningsList.add(
                ReplayedInnings(
                    inningsIndex = inningsIdx,
                    teamId = innings.team,
                    totalRuns = teamRuns,
                    totalWickets = wickets,
                    legalBalls = legalBalls,
                    oversDisplay = CricketCalculations.formatOvers(legalBalls),
                    closedEarly = closedEarly,
                    allOut = wickets >= allOutWickets,
                    deliveries = deliveries,
                    overGroupsNewestFirst = overGroups,
                    adjustments = adjustments,
                    chartPoints = chartPoints,
                    reconstructedBatters = batterStats.mapValues { (pid, mb) ->
                        ReplayedBatterStats(
                            playerId = pid,
                            runs = mb.runs,
                            ballsFaced = mb.balls,
                            fours = mb.fours,
                            sixes = mb.sixes,
                            dismissed = mb.dismissed
                        )
                    },
                    currentStriker = striker,
                    currentPartner = partner,
                    currentBowler = bowler
                )
            )
        }

        val canCompare = replayedInningsList.size >= 2 &&
            replayedInningsList[0].deliveries.isNotEmpty() &&
            replayedInningsList[1].deliveries.isNotEmpty()

        return ReplayedMatchLog(
            version = log.version,
            oversQuota = quotaOvers,
            innings = replayedInningsList,
            canCompareInnings = canCompare
        )
    }

    /**
     * Generate the result chip by joining the applicable pieces with `+`.
     * Prefix `wd` or `nb` when applicable, then recorded runs with `b` for byes or `lb` for leg byes,
     * then `W` if there is a wicket. A legal dot ball is `0`.
     * Examples include `wd`, `nb+4`, `2lb`, `W`, and `nb+1b+W`.
     */
    fun buildResultChip(delivery: CeScoringDelivery): String {
        val parts = mutableListOf<String>()
        when {
            delivery.kind.equals("wide", ignoreCase = true) -> parts.add("wd")
            delivery.kind.equals("noBall", ignoreCase = true) -> parts.add("nb")
        }
        if (delivery.runs > 0) {
            val suffix = when {
                delivery.runType.equals("bye", ignoreCase = true) -> "b"
                delivery.runType.equals("legBye", ignoreCase = true) -> "lb"
                else -> ""
            }
            parts.add("${delivery.runs}$suffix")
        }
        if (delivery.wicket != null) {
            parts.add("W")
        }
        return if (parts.isEmpty()) "0" else parts.joinToString("+")
    }
}
