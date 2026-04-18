# Wire User Preferences to Consuming Code

**Date:** 2026-04-19
**Status:** Approved

## Problem

6 of 7 user preferences in `UserPreferencesRepository` are saved to DataStore but never read by consuming code. Users change settings and see no effect.

## Changes

| Preference | Where wired | How |
|---|---|---|
| Default speed | `PlaybackLaunchers.kt` | After `load()`, read pref and call `setSpeed()` |
| Skip interval | `PlayerScreen.kt` | Collect pref, pass `intervalMs` to `skipForward/skipBack` |
| Subtitle font size | `PlayerScreen.kt` | Collect pref, pass to `SubtitleView` |
| Subtitle position | `PlayerScreen.kt` | Collect pref, reorder layout sections |
| Preferred subtitle lang | `PlayerScreen.kt` | Match preferred lang when auto-selecting |
| Max cache size | `AudiolyApp.kt` | Read pref at startup, pass to `AudioCacheManager` constructor |

## Cache size note

`SimpleCache` takes max bytes at construction. Changing the setting requires app restart. SettingsScreen shows "(restart required)" label.

## Files modified

- `PlaybackLaunchers.kt` — apply default speed after load
- `PlayerScreen.kt` — collect prefs for skip interval, font size, position, preferred language
- `AudiolyApp.kt` — reorder init: preferences first, then cache with pref value
- `SettingsScreen.kt` — add restart note on cache size
