# Resume, Share, and History Playback Design

## Goal

Fix three related playback regressions in Audioly:

- app resume button should restore last loaded track after notification dismissal/service teardown
- library/history/cached taps should start playback the same way as home recent items
- sharing a YouTube link into Audioly should immediately trigger playback flow, even when app is already open

## Root Causes

### Share intent path

`MainActivity` uses `launchMode="singleTask"`, so later shares arrive through `onNewIntent()`. Current `onNewIntent()` ignores the new intent, and `AudiolyMainContent()` only receives a one-shot `initialUrl` from `onCreate()`. Result: share action opens app but does not start extraction/playback.

### Library/history playback path

`HomeScreen` owns the real playback entry path through `handleGo()`, `handleHistoryTap()`, and `tryPlayFromCache()`. `LibraryScreen` bypasses all of that and only navigates to player route with `videoId`. Result: player opens with no load request and stays on `Preparing audio...`.

### Resume after notification dismissal

`PlayerRepository` commands only forward to live `playerRef`. If the service/player has been torn down after notification dismissal, `play()` and `togglePlayPause()` become no-ops. There is no durable “last playable track” request that can be replayed when service binds again.

## Design Summary

Keep current architecture. Do not add a new service/controller layer. Introduce one shared playback launcher, add resumable pending playback state in `PlayerRepository`, and make `MainActivity` expose share URLs as mutable Compose state instead of one-shot startup data.

## Architecture Changes

### 1. Shared playback launcher

Move playback-entry logic out of `HomeScreen`-private helpers into a reusable file-level helper that can be called from both Home and Library. This helper owns:

- URL validation
- cache-first playback attempt
- extraction fallback
- subtitle reset/track setup
- player load request
- navigation to player screen

Home recent items and library/history/cached items will call the same path.

### 2. Resumable load request in `PlayerRepository`

Expand repository command behavior:

- keep latest successful/attempted `load()` request as resumable playback snapshot
- if `play()` or `togglePlayPause()` is called while detached, queue resume instead of no-op
- when `attach()` happens again, replay queued load or resume request automatically

This keeps normal audio-player behavior after notification dismissal without introducing persistent storage or DB migration.

### 3. Mutable share-intent state in `MainActivity`

Replace static `initialUrl` flow with `mutableStateOf<String?>` owned by activity. On create, seed from current intent. On new intent, call `setIntent(intent)` and update the share URL state. `HomeScreen` continues consuming one incoming URL at a time, but now receives new shares while app is already running.

## File Responsibilities

- `app/src/main/java/com/audioly/app/ui/playback/PlaybackLaunchers.kt`
  Shared playback-entry helpers for URL and cached-track playback.
- `app/src/main/java/com/audioly/app/player/PlayerRepository.kt`
  Resumable pending load/resume behavior.
- `app/src/main/java/com/audioly/app/MainActivity.kt`
  Mutable share-intent state, `onNewIntent()` handoff.
- `app/src/main/java/com/audioly/app/ui/screens/home/HomeScreen.kt`
  Use shared playback launcher instead of local private helpers.
- `app/src/main/java/com/audioly/app/ui/screens/library/LibraryScreen.kt`
  Route taps through shared playback launcher rather than navigation-only behavior.
- `app/src/test/java/com/audioly/app/player/PlayerRepositoryTest.kt`
  Regression tests for detached resume/load replay behavior.

## Data Flow

1. Share intent arrives in `MainActivity`.
2. Activity updates shared URL Compose state.
3. `HomeScreen` consumes URL and calls shared playback launcher.
4. Shared playback launcher tries cache-first replay, else extraction fallback, then calls `playerRepository.load()`.
5. `PlayerRepository` stores resumable snapshot and forwards load to live player or queues it for later attach.
6. If notification dismissal/service teardown happens, subsequent resume requests replay stored snapshot on next attach.

## Error Handling

- If shared URL is invalid, show same validation message as current home input flow.
- If cached track lacks stored `audioStreamUrl`, fallback to extraction.
- If resume is requested without any previous load snapshot, keep current no-op behavior.
- If app receives repeated share intents for same URL, latest one wins.

## Verification

- Add unit tests for repository detached resume behavior.
- Run `./gradlew assembleDebug` after implementation.
- Build verifies compilation/package only. Runtime share-intent and notification behavior still need device validation later.
