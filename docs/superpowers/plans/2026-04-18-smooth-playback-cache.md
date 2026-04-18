# Smooth Playback And Cache Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Audioly playback feel like normal audio player by hardening background playback, smoothing subtitle rendering, and improving cache accuracy and reuse.

**Architecture:** Keep current Android-only app structure. Harden service-owned playback and cache reporting in `player/` and `data/cache/`, then simplify UI by making subtitle parsing/background updates more reactive and less janky. Avoid schema migrations and large rewrites.

**Tech Stack:** Kotlin, Android Media3 ExoPlayer, MediaSessionService, Jetpack Compose, Room, Coroutines/Flow.

---

## Chunk 1: Playback Lifecycle And Cache State

### Task 1: Harden service-owned playback lifecycle

**Files:**
- Modify: `app/src/main/java/com/audioly/app/player/AudioService.kt`
- Modify: `app/src/main/java/com/audioly/app/player/AudioPlayer.kt`
- Modify: `app/src/main/java/com/audioly/app/player/PlaybackController.kt`
- Modify: `app/src/main/java/com/audioly/app/player/PlayerState.kt`
- Modify: `app/src/main/java/com/audioly/app/MainActivity.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/components/MiniPlayer.kt`

**Test:**
- Run: `./gradlew assembleDebug`

- [ ] **Step 1: Add playback-state fields needed by service and UI**

Add cache-related fields to `PlayerState` and keep them updated from `AudioPlayer` snapshots.

- [ ] **Step 2: Harden `AudioPlayer` configuration**

Reuse cache-aware data source factory, enable wake mode for network audio, and update ticker/state rules so cache progress and buffering are reflected cleanly.

- [ ] **Step 3: Harden `AudioService` lifecycle rules**

Record playback start from real player state, report cache progress back to cache manager, and keep or stop service correctly on task removal.

- [ ] **Step 4: Start service in foreground-safe way**

Update `PlaybackController` to start service with foreground-safe API before binding.

- [ ] **Step 5: Surface normal player UX in root scaffold**

Integrate `MiniPlayer` into `MainActivity` so playback state remains visible outside full player screen.

- [ ] **Step 6: Verify build**

Run: `./gradlew assembleDebug`
Expected: build succeeds with hardened service/player wiring and mini player integration.

## Chunk 2: Subtitle Smoothness

### Task 2: Smooth subtitle parsing and scrolling

**Files:**
- Modify: `app/src/main/java/com/audioly/app/player/VttParser.kt`
- Modify: `app/src/main/java/com/audioly/app/player/SubtitleManager.kt`
- Modify: `app/src/main/java/com/audioly/app/player/SubtitleCue.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/screens/player/PlayerScreen.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/components/SubtitleView.kt`
- Modify: `app/src/test/java/com/audioly/app/player/VttParserTest.kt`

**Test:**
- Run: `./gradlew assembleDebug`

- [ ] **Step 1: Extend subtitle parser for real-world inputs**

Support `.vtt` and `.srt`, normalize timestamp parsing, and keep cue output sorted and allocation-light.

- [ ] **Step 2: Keep cue activation stable**

Tighten cue-active rules so edge transitions are predictable and current cue index does not flap.

- [ ] **Step 3: Move parsing work off main thread**

Update `PlayerScreen` so downloaded subtitle text is parsed on background dispatcher before UI state updates.

- [ ] **Step 4: Remove jumpy auto-scroll behavior**

Update `SubtitleView` so programmatic scroll does not mark itself as user scroll and only scrolls when active cue leaves visible range.

- [ ] **Step 5: Verify build**

Run: `./gradlew assembleDebug`
Expected: build succeeds with smoother subtitle UI and broader subtitle-format support.

## Chunk 3: Smarter Cache Decisions

### Task 3: Make cache state accurate and fast

**Files:**
- Modify: `app/src/main/java/com/audioly/app/data/cache/AudioCacheManager.kt`
- Modify: `app/src/main/java/com/audioly/app/data/repository/CacheRepository.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/screens/library/CachedTab.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/components/TrackItem.kt`

**Test:**
- Run: `./gradlew assembleDebug`

- [ ] **Step 1: Add explicit cache status model**

Expose `cachedBytes`, `contentLength`, `hasCache`, and `isFullyCached` from `AudioCacheManager`.

- [ ] **Step 2: Add cache change notifications**

Expose lightweight cache-version state so cache-driven screens can refresh when playback fills cache or when entries are deleted.

- [ ] **Step 3: Reuse cache status in playback entry paths**

Update `HomeScreen` cache replay decisions to use shared status helpers and cleaner fallback logging.

- [ ] **Step 4: Make cached library reflect real cache state**

Update `CachedTab` and track badges to rely on actual cache state instead of stale DB-only markers.

- [ ] **Step 5: Verify build**

Run: `./gradlew assembleDebug`
Expected: build succeeds and cache-aware UI compiles against new cache status helpers.

## Chunk 4: Final Verification

### Task 4: Final build verification and docs sync

**Files:**
- Modify: `README.md` if behavior notes need update

**Test:**
- Run: `./gradlew assembleDebug`

- [ ] **Step 1: Re-read design and confirm scope stayed minimal**

Verify changes stayed inside playback, subtitles, cache, and small UX glue only.

- [ ] **Step 2: Update README only if needed**

Document subtitle-format support or cache behavior only if user-visible behavior changed.

- [ ] **Step 3: Run final build**

Run: `./gradlew assembleDebug`
Expected: debug APK assembles successfully from final worktree state.

- [ ] **Step 4: Report verification limits clearly**

State that `assembleDebug` verifies compile/package success, not complete runtime proof.
