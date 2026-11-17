# Crick English — Spectator Android App

Native Android spectator client for the **Department of English Cricket Tournament**, built with Kotlin, Jetpack Compose, and Material 3.

## Public Backend & Architecture

- **PocketBase Base URL:** `https://pbdb2.duckdns.org`
- **Access Mode:** Read-only anonymous HTTPS client (`GET /api/collections/{collection}/records` and `GET /api/collections/{collection}/records/{id}`). Makes zero writes to PocketBase and requires no authentication or API keys.
- **Application ID:** `org.crickenglish.app` (installable alongside the scorer app `org.crickenglish.scorer`).
- **Time Zone:** All PocketBase timestamps are parsed as UTC instants and formatted in `Asia/Dhaka` time regardless of the device's local time zone.

## Building & Previewing

1. **In Google AI Studio:** The app compiles and streams automatically in the Android preview.
2. **Command-line Build & Verification:**
   - Assemble Debug APK: `gradle assembleDebug`
   - Run Unit, Replay, Robolectric UI, and Live API Verification Tests: `gradle :app:testDebugUnitTest`

## Verification Summary

All required checks passed (`gradle :app:testDebugUnitTest` & `compile_applet`):
- **Live PocketBase Integration:** Verified anonymous reads for `ce_seasons`, `ce_teams`, `ce_players`, `ce_matches` (including `scorecard` and detail `scoring` logs), `ce_player_match_stats`, and `ce_season_history`.
- **Overs Formatting & Math:** `17` legal balls display as `2.5` overs and use `17 / 6.0` in run rate, economy, and NRR calculations.
- **Highest Strike Rate Leaderboard:** Excludes batters with `< 10` cumulative balls (e.g., 9 balls excluded) while qualifying batters who reach `>= 10` balls across multiple matches (e.g., `6 + 4 = 10`), ranked by unrounded aggregate strike rate.
- **Single-Source Player Stats Rule:** Resolves `scorecard.playerStats` whenever present and non-null (including an authoritative empty `[]` array) and falls back to `ce_player_match_stats` only when absent or null, never double-counting. Live, private, and abandoned matches contribute no season rankings or boundary totals.
- **Points Table & NRR:** Uses full quota (`(innings.quota ?: match.overs) * 6`) for all-out innings and actual legal balls for innings ended early without being all out; abandoned league matches award 1 point and contribute no NRR data.
- **Ball-by-Ball Replay & Innings Comparison:** Reconstructs delivery rows, over groups (latest over first and open by default), result chips (`0`, `wd`, `nb+4`, `2lb`, `W`, `nb+1b+W`), wickets (`Run out` / `Obstructing the field` with `incomingOnStrike`), `replaceBatter`, `adjustRuns`, `closeInnings`, and dual-team comparison charts for Runs, Wickets, and Run rate.
- **State Preservation & Foreground Polling:** Preserves selected tabs, filters, and open matches across refreshes, rotation, and back navigation while polling published match summaries every 10 seconds when Home or Match details is in the foreground.
