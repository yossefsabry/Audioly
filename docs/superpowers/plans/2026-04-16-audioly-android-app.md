# Audioly Android App Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace current `RaheyGaay` shell with `Audioly`, Android app that accepts shared or pasted YouTube URLs, extracts audio with NewPipe Extractor, plays in background with Media3, shows synced scrollable subtitles, persists offline cache, and supports history, playlists, dark mode, and light mode.

**Architecture:** Single `app` module. `MainActivity` handles launcher and share intents. `ui/` renders Compose screens. `player/` owns `MediaSessionService`, ExoPlayer, subtitle sync, and playback state. `extraction/` wraps NewPipe Extractor. `data/` owns Room, DataStore, and cache metadata. `SimpleCache` stores audio files; subtitle files live in app cache and stay linked through Room.

**Tech Stack:** Kotlin 1.9.22, Compose Material3, Navigation Compose, Media3 ExoPlayer, MediaSession, Room, DataStore, Coroutines/Flow, NewPipe Extractor, Coil, JUnit4, AndroidX instrumentation tests.

---

## Chunk 1: Rename and Foundation

### Task 1: Rename app identity and add core dependencies

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `build.sh`
- Modify: `build-release.sh`
- Modify: `dev.sh`
- Modify: `.env.example`

**Test:**
- Run: `./gradlew assembleDebug`

- [ ] **Step 1: Rename project and package identity**

Update names to `Audioly` and package to `com.audioly.app` in Gradle, manifest, strings, theme, shell scripts, and env example.

- [ ] **Step 2: Add missing repositories and libraries**

Add JitPack repo for NewPipe Extractor, then dependencies for Navigation Compose, lifecycle ViewModel Compose, Media3, Room, DataStore, Coil, WorkManager, and Kotlin coroutines test support.

- [ ] **Step 3: Add required permissions and manifest placeholders**

Add `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS` permissions. Prepare manifest for app class and media service.

- [ ] **Step 4: Update local scripts**

Change install and launch package names in `build.sh`, `build-release.sh`, and `dev.sh` to `com.audioly.app/.MainActivity`.

- [ ] **Step 5: Verify base build**

Run: `./gradlew assembleDebug`
Expected: debug APK builds, launcher label shows `Audioly`, package name becomes `com.audioly.app`.

### Task 2: Replace placeholder app shell with Audioly shell

**Files:**
- Delete: `app/src/main/java/com/raheygaay/app/MainActivity.kt`
- Create: `app/src/main/java/com/audioly/app/AudiolyApp.kt`
- Create: `app/src/main/java/com/audioly/app/MainActivity.kt`
- Create: `app/src/main/java/com/audioly/app/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/audioly/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/audioly/app/ui/theme/Type.kt`
- Create: `app/src/main/java/com/audioly/app/ui/navigation/NavGraph.kt`

**Test:**
- Run: `./gradlew assembleDebug`

- [ ] **Step 1: Add application class**

Create `AudiolyApp` for app-wide initialization points: Room, DataStore, cache, extractor, player wiring.

- [ ] **Step 2: Build Material You theme**

Create theme files with dynamic color support, explicit light/dark fallback palettes, and no old `RaheyGaay` branding.

- [ ] **Step 3: Build empty navigation shell**

Create `MainActivity` and `NavGraph` with bottom nav placeholders for `Home`, `Library`, `Settings`, plus mini-player host slot.

- [ ] **Step 4: Remove old package path**

Delete `com/raheygaay/app`, move source to `com/audioly/app`, update imports and manifest references.

- [ ] **Step 5: Verify branded shell**

Run: `./gradlew assembleDebug`
Expected: app opens into clean Audioly shell with empty tabs and Material3 theme.

## Chunk 2: URL Intake and Extraction

### Task 3: Add URL validation, paste flow, and share intent entry

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/audioly/app/util/UrlValidator.kt`
- Create: `app/src/main/java/com/audioly/app/ui/screens/home/HomeScreen.kt`
- Create: `app/src/main/java/com/audioly/app/ui/components/UrlInput.kt`
- Modify: `app/src/main/java/com/audioly/app/MainActivity.kt`
- Create: `app/src/test/java/com/audioly/app/util/UrlValidatorTest.kt`

**Test:**
- Run: `./gradlew testDebugUnitTest`

- [ ] **Step 1: Implement YouTube URL normalization**

Support `youtube.com/watch?v=...`, `youtu.be/...`, `m.youtube.com`, `music.youtube.com`. Strip fragments and invalid query noise. Reject non-YouTube URLs.

- [ ] **Step 2: Add home screen input UI**

Create URL field with paste button, clear button, and `Go` action. Show recent failures or validation message inline.

- [ ] **Step 3: Add Android share intent support**

Handle `ACTION_SEND` with `text/plain` in manifest and `MainActivity`. When app receives shared YouTube URL, route directly into extraction flow.

- [ ] **Step 4: Add unit tests for URL rules**

Cover valid full URLs, short URLs, mobile URLs, invalid hosts, missing video IDs, and malformed strings.

- [ ] **Step 5: Verify entry paths**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: paste and share routes both resolve normalized video IDs.

### Task 4: Implement extraction layer with NewPipe Extractor

**Files:**
- Create: `app/src/main/java/com/audioly/app/extraction/StreamInfo.kt`
- Create: `app/src/main/java/com/audioly/app/extraction/SubtitleTrack.kt`
- Create: `app/src/main/java/com/audioly/app/extraction/YouTubeExtractor.kt`
- Create: `app/src/main/java/com/audioly/app/extraction/SubtitleExtractor.kt`
- Create: `app/src/main/java/com/audioly/app/extraction/ExtractionResult.kt`
- Create: `app/src/test/java/com/audioly/app/extraction/YouTubeExtractorTest.kt`

**Test:**
- Run: `./gradlew testDebugUnitTest`

- [ ] **Step 1: Define extraction models**

Add `StreamInfo`, `SubtitleTrack`, and result wrappers containing video ID, title, duration, thumbnail URL, best audio stream URL, and available subtitles.

- [ ] **Step 2: Wrap NewPipe Extractor**

Implement `YouTubeExtractor` with one entry point: `suspend fun extract(url: String): ExtractionResult`. Keep library-specific calls isolated in this package.

- [ ] **Step 3: Add subtitle track discovery**

Collect manual and auto subtitle tracks, normalize language codes, prefer manual tracks first, but expose full language list for picker.

- [ ] **Step 4: Add failure mapping**

Translate extractor/network exceptions into app-friendly errors: invalid URL, unavailable video, extraction failed, age restriction, no subtitles.

- [ ] **Step 5: Verify extractor contract**

Run: `./gradlew testDebugUnitTest`
Expected: extractor layer compiles behind fake inputs; tests cover mapping and language ordering without live network dependency.

## Chunk 3: Persistence and Cache

### Task 5: Add Room schema for tracks, playlists, history, subtitle cache

**Files:**
- Create: `app/src/main/java/com/audioly/app/data/db/AudiolyDatabase.kt`
- Create: `app/src/main/java/com/audioly/app/data/db/entities/TrackEntity.kt`
- Create: `app/src/main/java/com/audioly/app/data/db/entities/PlaylistEntity.kt`
- Create: `app/src/main/java/com/audioly/app/data/db/entities/PlaylistTrackEntity.kt`
- Create: `app/src/main/java/com/audioly/app/data/db/entities/SubtitleCacheEntity.kt`
- Create: `app/src/main/java/com/audioly/app/data/db/dao/TrackDao.kt`
- Create: `app/src/main/java/com/audioly/app/data/db/dao/PlaylistDao.kt`
- Create: `app/src/main/java/com/audioly/app/data/db/dao/SubtitleCacheDao.kt`
- Create: `app/src/main/java/com/audioly/app/data/repository/TrackRepository.kt`
- Create: `app/src/main/java/com/audioly/app/data/repository/PlaylistRepository.kt`
- Create: `app/src/androidTest/java/com/audioly/app/data/db/AudiolyDatabaseTest.kt`

**Test:**
- Run: `./gradlew connectedDebugAndroidTest`

- [ ] **Step 1: Create entities and DAOs**

Match approved schema: tracks, playlists, playlist tracks, subtitle cache. Add DAO queries for recent tracks, cached tracks, playlist contents, and subtitle lookup by video ID + language.

- [ ] **Step 2: Create database and repository wiring**

Build `AudiolyDatabase`, repository methods, and simple mappers between DB entities and domain models.

- [ ] **Step 3: Add history update rules**

On playback start or successful extraction, upsert track, increment play count, and update `lastPlayedAt`.

- [ ] **Step 4: Add database instrumentation tests**

Verify insert, upsert, playlist ordering, recent history sorting, and subtitle cache lookup.

- [ ] **Step 5: Verify local persistence**

Run: `./gradlew connectedDebugAndroidTest`
Expected: Room schema stable, history and playlist queries behave correctly.

### Task 6: Add cache managers and user settings storage

**Files:**
- Create: `app/src/main/java/com/audioly/app/data/cache/AudioCacheManager.kt`
- Create: `app/src/main/java/com/audioly/app/data/cache/SubtitleCacheManager.kt`
- Create: `app/src/main/java/com/audioly/app/data/repository/CacheRepository.kt`
- Create: `app/src/main/java/com/audioly/app/data/preferences/UserPreferences.kt`
- Create: `app/src/main/java/com/audioly/app/data/preferences/UserPreferencesRepository.kt`
- Create: `app/src/test/java/com/audioly/app/data/cache/SubtitleCacheManagerTest.kt`

**Test:**
- Run: `./gradlew testDebugUnitTest`

- [ ] **Step 1: Initialize ExoPlayer `SimpleCache` wrapper**

Create one cache instance under app cache dir. Add size lookup, clear-all, and delete-by-video-id helpers.

- [ ] **Step 2: Add subtitle file cache**

Store subtitle files by `videoId/language`, track file path in Room, and clean orphan files when track cache is deleted.

- [ ] **Step 3: Add DataStore preferences**

Persist theme mode, default playback speed, preferred subtitle language, subtitle font size, subtitle position, and max cache size.

- [ ] **Step 4: Add tests for cache path and preference defaults**

Verify stable path generation, overwrite behavior, orphan cleanup, and default preference values.

- [ ] **Step 5: Verify persistence layer**

Run: `./gradlew testDebugUnitTest`
Expected: cache managers and DataStore wrappers behave deterministically.

## Chunk 4: Playback and Subtitle Sync

### Task 7: Implement Media3 playback service and player state

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/audioly/app/player/PlayerState.kt`
- Create: `app/src/main/java/com/audioly/app/player/AudioPlayer.kt`
- Create: `app/src/main/java/com/audioly/app/player/AudioService.kt`
- Create: `app/src/main/java/com/audioly/app/player/PlayerRepository.kt`
- Create: `app/src/main/java/com/audioly/app/player/PlaybackController.kt`
- Create: `app/src/test/java/com/audioly/app/player/PlayerStateTest.kt`

**Test:**
- Run: `./gradlew testDebugUnitTest assembleDebug`

- [ ] **Step 1: Define player state contract**

Track current media item, position, duration, play/pause state, buffering state, playback speed, selected subtitle language, and current subtitle index.

- [ ] **Step 2: Build ExoPlayer wrapper**

Load cached audio if present; otherwise use extracted audio URL through cache-aware data source. Support play, pause, seek, skip +/-15s, and speed changes.

- [ ] **Step 3: Build foreground media service**

Create `MediaSessionService` with notification controls, audio focus handling, and persistent playback after app minimize.

- [ ] **Step 4: Expose state as `Flow`**

Publish player state to Compose screens and mini-player through repository/controller layer.

- [ ] **Step 5: Verify background playback**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: service registers, build succeeds, player API stable for UI integration.

### Task 8: Implement subtitle parsing, sync, and player screen UI

**Files:**
- Create: `app/src/main/java/com/audioly/app/player/SubtitleCue.kt`
- Create: `app/src/main/java/com/audioly/app/player/SubtitleManager.kt`
- Create: `app/src/main/java/com/audioly/app/player/VttParser.kt`
- Create: `app/src/main/java/com/audioly/app/ui/components/SubtitleView.kt`
- Create: `app/src/main/java/com/audioly/app/ui/components/MiniPlayer.kt`
- Create: `app/src/main/java/com/audioly/app/ui/screens/player/PlayerScreen.kt`
- Create: `app/src/test/java/com/audioly/app/player/VttParserTest.kt`

**Test:**
- Run: `./gradlew testDebugUnitTest assembleDebug`

- [ ] **Step 1: Parse subtitle files into cues**

Support VTT first. Parse start/end timestamps and multi-line cue text. Keep parser small and deterministic.

- [ ] **Step 2: Sync cues to playback position**

Given current player position, compute active cue index and expose highlighted subtitle to UI.

- [ ] **Step 3: Build subtitle list UI**

Render scrollable list with current cue highlight, tap-to-seek, manual scroll pause, and resume auto-scroll action.

- [ ] **Step 4: Build full player and mini-player**

Add title, artwork, progress, skip controls, play/pause, speed selector, subtitle language picker, subtitle font size and position controls.

- [ ] **Step 5: Verify subtitle UX**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: parser tests pass; player screen compiles with subtitle state, seeking, and style controls.

## Chunk 5: Library, Settings, and Polish

### Task 9: Implement history, playlists, cached items, and settings screens

**Files:**
- Create: `app/src/main/java/com/audioly/app/ui/screens/library/LibraryScreen.kt`
- Create: `app/src/main/java/com/audioly/app/ui/screens/library/HistoryTab.kt`
- Create: `app/src/main/java/com/audioly/app/ui/screens/library/PlaylistsTab.kt`
- Create: `app/src/main/java/com/audioly/app/ui/screens/library/CachedTab.kt`
- Create: `app/src/main/java/com/audioly/app/ui/screens/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/audioly/app/ui/components/TrackItem.kt`
- Create: `app/src/main/java/com/audioly/app/ui/components/PlaylistItem.kt`
- Create: `app/src/test/java/com/audioly/app/ui/library/LibraryViewModelTest.kt`

**Test:**
- Run: `./gradlew testDebugUnitTest assembleDebug`

- [ ] **Step 1: Build history tab**

Show recent tracks sorted by `lastPlayedAt`, including title, duration, artwork, cache badge, and quick resume.

- [ ] **Step 2: Build playlist flows**

Allow create, rename, delete playlist, add track to playlist, reorder playlist tracks, and play playlist items.

- [ ] **Step 3: Build cached tab**

Show per-track cache size, delete one item, or clear all cached audio and subtitles.

- [ ] **Step 4: Build settings screen**

Add theme mode selector, cache size limit, default subtitle language, playback speed default, skip interval, subtitle font size, and subtitle vertical position.

- [ ] **Step 5: Verify library/settings flows**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: library and settings screens compile and connect to repositories.

### Task 10: Final QA, errors, and release-readiness

**Files:**
- Modify: `app/src/main/java/com/audioly/app/MainActivity.kt`
- Modify: `app/src/main/java/com/audioly/app/player/AudioService.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/screens/player/PlayerScreen.kt`
- Modify: `README.md`
- Create: `app/src/androidTest/java/com/audioly/app/share/ShareIntentTest.kt`

**Test:**
- Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug`

- [ ] **Step 1: Add error handling and empty states**

Cover invalid URLs, extraction failure, expired audio URLs, missing subtitles, offline-without-cache, and notification permission denial.

- [ ] **Step 2: Add final share-intent and smoke tests**

Verify app launches from shared URL, routes to player, and keeps playback alive in background service.

- [ ] **Step 3: Add README setup notes**

Document supported URLs, share flow, playback features, cache behavior, and known limits of NewPipe Extractor stream expiration.

- [ ] **Step 4: Run full verification**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug`
Expected: unit tests pass, instrumentation tests pass, debug APK builds.

- [ ] **Step 5: Manual QA checklist**

Verify on device:
1. Share YouTube URL from YouTube app into Audioly.
2. Paste URL from clipboard and start playback.
3. Minimize app; audio keeps playing.
4. Reopen app; player state restored.
5. Cached track replays offline.
6. Subtitle tap seeks audio.
7. Theme switches between system, light, dark.

## Execution Notes

- Keep impl minimal. No DI framework unless repeated constructor wiring becomes painful.
- Keep NewPipe-specific code inside `extraction/` only.
- Prefer one source of truth for player state: `Flow<PlayerState>`.
- Do not trust extracted stream URLs long-term. Re-extract on playback failure before showing fatal error.
- Start with VTT subtitle support only. Add SRT only if NewPipe delivers it in practice.
- Use internal app storage for cache. Avoid shared storage complexity.
- Keep first milestone focused on single-track playback. Playlist playback can reuse same player queue after base player is stable.
