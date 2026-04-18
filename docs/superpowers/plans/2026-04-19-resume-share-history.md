# Resume Share History Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Audioly resume playback after notification dismissal, start library/history taps correctly, and process new shared YouTube URLs while app is already running.

**Architecture:** Keep existing service/repository/navigation structure. Centralize playback launch behavior in shared helpers, then add minimal resumable load state in `PlayerRepository` and mutable share-intent state in `MainActivity`.

**Tech Stack:** Kotlin, Android Media3, Jetpack Compose, Coroutines/Flow.

---

## Chunk 1: Resumable Repository Behavior

### Task 1: Add detached resume replay in `PlayerRepository`

**Files:**
- Modify: `app/src/main/java/com/audioly/app/player/PlayerRepository.kt`
- Create: `app/src/test/java/com/audioly/app/player/PlayerRepositoryTest.kt`

**Test:**
- Run: `./gradlew testDebugUnitTest --tests com.audioly.app.player.PlayerRepositoryTest`

- [ ] **Step 1: Write failing test for queued load replay**

Assert that `load()` while detached stores pending load and replays it when attached.

- [ ] **Step 2: Run test to verify it fails for right reason**

Run: `./gradlew testDebugUnitTest --tests com.audioly.app.player.PlayerRepositoryTest`
Expected: FAIL on missing detached resume behavior or missing test scaffolding.

- [ ] **Step 3: Write failing test for detached resume request**

Assert that `togglePlayPause()` or `play()` after a prior `load()` queues replay when detached.

- [ ] **Step 4: Implement minimal resumable snapshot support**

Store last load request, queue detached resume, replay on `attach()`.

- [ ] **Step 5: Run targeted tests**

Run: `./gradlew testDebugUnitTest --tests com.audioly.app.player.PlayerRepositoryTest`
Expected: PASS.

## Chunk 2: Shared Playback Launcher

### Task 2: Unify playback entry path for Home and Library

**Files:**
- Create: `app/src/main/java/com/audioly/app/ui/playback/PlaybackLaunchers.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/screens/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/audioly/app/ui/screens/library/HistoryTab.kt` if callback shape changes
- Modify: `app/src/main/java/com/audioly/app/ui/screens/library/CachedTab.kt` if callback shape changes

**Test:**
- Run: `./gradlew assembleDebug`

- [ ] **Step 1: Extract current home playback helpers into shared file**

Move URL/cache/extraction/load logic to shared functions without changing behavior.

- [ ] **Step 2: Update HomeScreen to call shared helpers**

Keep current UX and messages intact.

- [ ] **Step 3: Update LibraryScreen to launch playback, not only navigate**

Make history/cached taps use same shared playback path before navigation.

- [ ] **Step 4: Run build**

Run: `./gradlew assembleDebug`
Expected: build succeeds and library/home both compile against shared launcher.

## Chunk 3: Share Intent Delivery

### Task 3: Make new shares work while app is already running

**Files:**
- Modify: `app/src/main/java/com/audioly/app/MainActivity.kt`

**Test:**
- Run: `./gradlew assembleDebug`

- [ ] **Step 1: Replace one-shot startup URL with mutable activity state**

Keep latest shared URL in Compose-visible state.

- [ ] **Step 2: Wire `onNewIntent()` to update state**

Call `setIntent(intent)` and publish new shared URL value.

- [ ] **Step 3: Keep HomeScreen consume-once behavior intact**

Ensure repeated share intents still trigger playback flow once each.

- [ ] **Step 4: Run build**

Run: `./gradlew assembleDebug`
Expected: build succeeds with updated activity/share flow.

## Chunk 4: Final Verification

### Task 4: Final regression build

**Files:**
- Modify: `docs/superpowers/specs/2026-04-19-resume-share-history-design.md` only if implementation deviates

**Test:**
- Run: `./gradlew assembleDebug`

- [ ] **Step 1: Re-read spec and confirm all three bugs are covered**

- [ ] **Step 2: Run final build**

Run: `./gradlew assembleDebug`
Expected: debug APK assembles successfully.

- [ ] **Step 3: Report remaining runtime-only verification gaps**

Document that notification-dismiss and share-to-running-app still need device QA beyond build.
