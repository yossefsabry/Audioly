# Smooth Playback, Subtitle, and Cache Hardening Design

## Goal

Make Audioly behave more like normal audio player: playback survives app minimize and task removal more reliably, subtitles update smoothly without jumpy UI work, and cache decisions become faster and more accurate.

## Current Problems

- `AudioService` keeps playback alive, but lifecycle policy is minimal. App unbinds on background and service does not make explicit keep-alive or idle-stop decisions.
- `PlayerScreen` downloads and parses subtitles inside Compose. Large subtitle files can parse on main thread and `SubtitleView` treats programmatic scroll as user scroll, which causes jumpy auto-scroll.
- Cache reads use `SimpleCache`, but cache status is not surfaced cleanly to UI. Cached library behavior depends on DB markers that are not kept in sync with `SimpleCache` state.
- History/playback bookkeeping is incomplete because playback start is not recorded from service-owned player state.

## Design Summary

Keep current Android-only architecture. Do not rewrite around KMP or a new playback stack. Harden existing `AudioService`, `AudioPlayer`, `AudioCacheManager`, and player UI with minimal focused changes.

## Architecture Changes

### 1. Playback lifecycle hardening

- Keep `AudioService` as single owner of `AudioPlayer` and `MediaSession`.
- Start service with foreground-safe startup from `PlaybackController`.
- Add explicit task-removal behavior: keep service alive while playback or buffering is active, stop only when idle.
- Hold network wake mode in `ExoPlayer` so streaming audio survives screen-off/background conditions better.
- Record playback start from service-side player state so history reflects real playback, not only extraction requests.

### 2. Subtitle pipeline smoothing

- Move subtitle parsing off main thread.
- Add format-aware subtitle parsing so `.vtt` and `.srt` both map into `SubtitleCue`.
- Keep `SubtitleManager` as active-cue lookup layer.
- Fix `SubtitleView` auto-scroll logic so programmatic scroll does not mark itself as user scroll.
- Only auto-scroll when active cue moves outside visible window. Avoid re-animating on every tick.

### 3. Smarter cache status

- Extend `AudioCacheManager` with cache-status helpers that expose `cachedBytes`, `contentLength`, `hasCache`, and `isFullyCached` from `SimpleCache` metadata.
- Reuse one cache-aware data source factory instead of rebuilding cache wiring ad hoc.
- Expose cache change version/state so UI can refresh actual cached-track lists from real cache state, not stale DB markers.
- Feed cache progress back from service/player ticker so cache-aware UI updates during playback.

### 4. Normal player UX polish

- Surface cache state in `PlayerState` so player UI can react without direct cache reads.
- Add in-app mini player to main scaffold when full player screen is hidden.
- Keep existing playback commands and screen structure. No large navigation rewrite.

## File Responsibilities

- `app/src/main/java/com/audioly/app/player/AudioService.kt`
  Service lifetime, playback bookkeeping, cache-progress reporting.
- `app/src/main/java/com/audioly/app/player/AudioPlayer.kt`
  ExoPlayer config, wake mode, cache-aware state snapshots.
- `app/src/main/java/com/audioly/app/player/PlayerState.kt`
  Add cache-related state needed by UI.
- `app/src/main/java/com/audioly/app/player/PlaybackController.kt`
  Foreground-safe service startup and binding.
- `app/src/main/java/com/audioly/app/data/cache/AudioCacheManager.kt`
  Cache status model, shared data source factory, cache change notifications.
- `app/src/main/java/com/audioly/app/ui/screens/player/PlayerScreen.kt`
  Background-safe reactive player UI, off-main subtitle parsing, subtitle picker polish.
- `app/src/main/java/com/audioly/app/ui/components/SubtitleView.kt`
  Smooth auto-scroll and user-scroll detection.
- `app/src/main/java/com/audioly/app/MainActivity.kt`
  Mini player host and root player-state integration.
- `app/src/main/java/com/audioly/app/ui/screens/library/CachedTab.kt`
  Show actual cached tracks from cache state, not stale persistence.

## Data Flow

1. `PlaybackController` starts and binds `AudioService`.
2. `AudioService` owns `AudioPlayer`, `MediaSession`, and ticker loop.
3. `AudioPlayer` publishes `PlayerState`, including cache state derived from `AudioCacheManager`.
4. `AudioService` records playback start and reports cache progress to `AudioCacheManager`.
5. `PlayerScreen` reacts to `PlayerRepository.state`, downloads subtitle text if needed, parses on background dispatcher, and renders cues via `SubtitleView`.
6. `CachedTab` combines track list with actual cache status so cache UI matches `SimpleCache` reality.

## Error Handling

- If cache metadata is missing or corrupt, fall back to network playback rather than blocking playback.
- If subtitle download succeeds but parse fails, keep audio playing and show no active subtitles.
- If task is removed while idle, stop service to avoid leaked resources.
- If task is removed while playing or buffering, keep service alive.

## Verification

- Primary required verification: `./gradlew assembleDebug`
- Secondary targeted checks if feasible: subtitle parser tests and cache helper tests.
- No claim of "100%" runtime certainty from build alone; goal is compile-clean code plus tighter runtime behavior in high-risk paths.
