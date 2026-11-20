package com.example.model

data class PbListResponse<T>(
    val page: Int = 1,
    val perPage: Int = 30,
    val totalItems: Int = 0,
    val totalPages: Int = 1,
    val items: List<T> = emptyList()
)

data class CeSeason(
    val id: String,
    val number: Int = 0,
    val name: String = "",
    val active: Boolean = false
)

data class CeTeam(
    val id: String,
    val collectionId: String = "ce_teams",
    val season: String = "",
    val name: String = "",
    val shortName: String = "",
    val color: String = "",
    val logo: String = "",
    val ownerName: String = "",
    val ownerPhoto: String = "",
    val managerName: String = "",
    val managerPhoto: String = "",
    val captain: String = ""
)

data class CePlayer(
    val id: String,
    val collectionId: String = "ce_players",
    val name: String = "",
    val batch: String = "",
    val role: String = "",
    val team: String = "",
    val photo: String = ""
)

data class CeMatch(
    val id: String,
    val season: String = "",
    val teamA: String = "",
    val teamB: String = "",
    val startsAt: String = "",
    val stage: String = "",
    val overs: Int = 0,
    val status: String = "",
    val published: Boolean = false,
    val winner: String = "",
    val scorecard: CeScorecard? = null,
    val updated: String = "",
    val scoring: CeScoringLog? = null,
    val playerOfMatch: String = ""
)

data class CeScorecard(
    val innings: List<CeInnings> = emptyList(),
    val result: String? = null,
    val recent: List<String> = emptyList(),
    val currentBatters: List<String> = emptyList(),
    val currentBowler: String? = null,
    val playingSides: List<CePlayingSide> = emptyList(),
    val playerStats: List<PlayerMatchStat>? = null
)

data class CeInnings(
    val team: String = "",
    val runs: Int = 0,
    val wickets: Int = 0,
    val balls: Int = 0,
    val allOut: Boolean = false,
    val quota: Int? = null,
    val extras: Int? = null,
    val extrasBreakdown: CeExtrasBreakdown? = null,
    val endedEarly: Boolean = false,
    val adjustedRuns: Int? = null
)

data class CeExtrasBreakdown(
    val wide: Int = 0,
    val noBall: Int = 0,
    val bye: Int = 0,
    val legBye: Int = 0
)

data class CePlayingSide(
    val team: String = "",
    val players: List<String> = emptyList()
)

data class PlayerMatchStat(
    val id: String = "",
    val match: String = "",
    val player: String = "",
    val team: String = "",
    val runs: Int = 0,
    val ballsFaced: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val wickets: Int = 0,
    val ballsBowled: Int = 0,
    val runsConceded: Int = 0,
    val batted: Boolean = false,
    val notOut: Boolean = false,
    val retired: Boolean = false
)

data class CeSeasonHistory(
    val id: String,
    val collectionId: String = "ce_season_history",
    val seasonNumber: Int = 0,
    val year: Int? = null,
    val champion: String = "",
    val runnerUp: String = "",
    val championLogo: String = "",
    val runnerUpLogo: String = "",
    val championCaptain: String = "",
    val runnerUpCaptain: String = "",
    val teamPhoto: String = "",
    val published: Boolean = false
)

// Ball-by-ball scoring log models
data class CeScoringLog(
    val version: Int = 1,
    val overs: Int = 0,
    val lineups: List<CeScoringLineup> = emptyList(),
    val innings: List<CeScoringInnings> = emptyList(),
    val outcome: String? = null
)

data class CeScoringLineup(
    val team: String = "",
    val players: List<String> = emptyList()
)

data class CeScoringInnings(
    val team: String = "",
    val opening: CeScoringOpening = CeScoringOpening(),
    val plays: List<CeScoringPlay> = emptyList()
)

data class CeScoringOpening(
    val striker: String? = null,
    val partner: String? = null,
    val bowler: String? = null
)

sealed class CeScoringPlay {
    data class Ball(val delivery: CeScoringDelivery) : CeScoringPlay()
    data class Bowler(val player: String) : CeScoringPlay()
    data object Swap : CeScoringPlay()
    data class ReplaceBatter(val outgoing: String, val incoming: String) : CeScoringPlay()
    data class AdjustRuns(val runs: Int) : CeScoringPlay()
    data object CloseInnings : CeScoringPlay()
}

data class CeScoringDelivery(
    val kind: String = "legal", // "legal", "wide", "noBall"
    val runType: String = "bat", // "bat", "bye", "legBye"
    val runs: Int = 0,
    val boundary: Int? = null, // 4 or 6 when present
    val wicket: CeScoringWicket? = null
)

data class CeScoringWicket(
    val player: String = "",
    val how: String = "",
    val next: String? = null,
    val incomingOnStrike: Boolean? = null
)
