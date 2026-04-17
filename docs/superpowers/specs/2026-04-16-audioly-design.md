# Audioly - YouTube Audio Player for Android

## Overview

Audioly is a Kotlin Android app that lets users listen to YouTube videos as audio with synced, scrollable subtitles. Users share a YouTube URL (via share intent or paste), and the app extracts the audio stream, plays it with full background playback support, and displays synchronized subtitles. All audio and subtitle data is cached for offline replay.

## Goals

- **Audio-only YouTube playback** with background audio (like a podcast/music app)
- **Synced subtitle display** with scrollable, tappable subtitle cues
- **Offline caching** of audio files and subtitles
- **Modern Material You UI** with dark/light mode support
- **Share intent** integration so users can share from YouTube directly

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose + Material3 (Material You) |
| Audio extraction | NewPipe Extractor |
| Audio playback | Media3 ExoPlayer + MediaSession |
| Database | Room |
| Preferences | DataStore |
| Async | Kotlin Coroutines + Flow |
| Navigation | Navigation Compose |
| Min SDK | 24 |
| Target SDK | 34 |

## Architecture

Single-module monolithic app with clean package separation:

```
com.audioly.app/
├── AudiolyApp.kt                    # Application class
├── MainActivity.kt                  # Single activity, hosts Compose nav
├── data/
│   ├── db/
│   │   ├── AudiolyDatabase.kt       # Room database
│   │   ├── entities/                 # Track, Playlist, PlaylistTrack, SubtitleCache
│   │   └── dao/                      # TrackDao, PlaylistDao, SubtitleDao
│   ├── cache/
│   │   ├── AudioCacheManager.kt      # Manages audio file cache on disk
│   │   └── SubtitleCacheManager.kt   # Manages subtitle file cache
│   └── repository/
│       ├── TrackRepository.kt        # CRUD for tracks + history
│       ├── PlaylistRepository.kt     # CRUD for playlists
│       └── CacheRepository.kt        # Cache stats, cleanup
├── extraction/
│   ├── YouTubeExtractor.kt           # NewPipe Extractor wrapper
│   ├── StreamInfo.kt                 # Extracted stream data model
│   └── SubtitleExtractor.kt          # Subtitle track extraction
├── player/
│   ├── AudioService.kt               # Foreground service for background playback
│   ├── AudioPlayer.kt                # ExoPlayer wrapper with MediaSession
│   ├── SubtitleManager.kt            # Subtitle loading, timing, sync
│   └── PlayerState.kt                # Playback state model
├── ui/
│   ├── theme/
│   │   ├── Theme.kt                  # Material You theme (dark + light)
│   │   ├── Color.kt                  # Color definitions
│   │   └── Type.kt                   # Typography
│   ├── navigation/
│   │   └── NavGraph.kt               # Navigation routes
│   ├── screens/
│   │   ├── home/                     # Home screen (URL input + recent)
│   │   ├── player/                   # Player screen (audio + subtitles)
│   │   ├── library/                  # History + playlists
│   │   ├── settings/                 # App settings
│   │   └── cache/                    # Cache management
│   └── components/
│       ├── MiniPlayer.kt            # Bottom mini player bar
│       ├── SubtitleView.kt          # Scrollable synced subtitle display
│       ├── UrlInput.kt              # URL paste input component
│       └── TrackItem.kt             # Track list item component
└── util/
    ├── UrlValidator.kt               # YouTube URL validation/normalization
    └── TimeFormatter.kt              # Duration formatting
```

## Data Flow

### URL Input → Playback
1. User shares YouTube URL via share intent or pastes in app
2. `UrlValidator` normalizes the URL, extracts video ID
3. Check Room DB: if track exists and audio is cached, load from cache
4. Otherwise, `YouTubeExtractor` uses NewPipe Extractor to fetch:
   - Title, thumbnail URL, duration
   - Best audio stream URL
   - Available subtitle tracks (languages)
5. Save track metadata to Room DB
6. Navigate to player screen

### Audio Playback
1. `AudioPlayer` loads audio stream URL into ExoPlayer
2. If cached: `CacheDataSource` serves from local file
3. If not cached: stream from URL + simultaneously write to cache via ExoPlayer's `SimpleCache`
4. `AudioService` starts as foreground service with notification controls
5. `MediaSession` publishes state for system integration (lock screen, Bluetooth, notification)
6. On app minimize: audio continues via foreground service
7. Audio focus handling: duck/pause for calls and notifications

### Subtitles
1. `SubtitleExtractor` fetches available subtitle tracks from NewPipe Extractor
2. User picks language (or auto-select from saved preference)
3. Download subtitle file (VTT) → cache to disk
4. `SubtitleManager` parses cues (start time, end time, text)
5. `SubtitleView` displays all cues as a scrollable list
6. Current cue highlighted and auto-scrolled to center
7. Tap any cue → audio seeks to that timestamp
8. Manual scroll → pause auto-scroll (resume button appears)

### Caching
- **Audio**: ExoPlayer `SimpleCache` with LRU eviction, configurable max size (default 500MB)
- **Subtitles**: stored as VTT files in app's cache directory, linked to track in Room DB
- **Metadata**: always persisted in Room DB (tiny footprint)
- **Cache management**: settings screen shows usage, per-track sizes, delete individual or clear all

## Database Schema

### Track
| Column | Type | Description |
|---|---|---|
| videoId (PK) | String | YouTube video ID |
| title | String | Video title |
| thumbnailUrl | String | Thumbnail URL |
| durationMs | Long | Duration in milliseconds |
| audioStreamUrl | String | Audio stream URL (may expire) |
| cachedAudioPath | String? | Local file path if cached |
| lastPlayedAt | Long | Timestamp for history |
| playCount | Int | Play count |
| createdAt | Long | First added timestamp |

### Playlist
| Column | Type | Description |
|---|---|---|
| id (PK, auto) | Long | Playlist ID |
| name | String | Playlist name |
| createdAt | Long | Creation timestamp |
| updatedAt | Long | Last modified timestamp |

### PlaylistTrack (join table)
| Column | Type | Description |
|---|---|---|
| playlistId (PK) | Long | FK to Playlist |
| videoId (PK) | String | FK to Track |
| position | Int | Order within playlist |
| addedAt | Long | When added |

### SubtitleCache
| Column | Type | Description |
|---|---|---|
| id (PK, auto) | Long | Subtitle cache ID |
| videoId | String | FK to Track |
| language | String | Language code (e.g., "en") |
| isAutoGenerated | Boolean | Manual vs auto-generated |
| cachedFilePath | String | Local VTT file path |
| createdAt | Long | Cache timestamp |

## UI Screens

### Home Screen
- App bar: "Audioly" title + settings gear icon
- URL input field with paste button + "Go" button
- "Recent" section: last 5-10 played tracks (thumbnail, title, duration)
- Tap recent track → player with cached data
- Bottom nav bar: Home | Library | Settings
- Mini player overlay when audio is playing

### Player Screen (full-screen)
- Back arrow + track title + overflow menu (share, add to playlist)
- Top: thumbnail/artwork
- Center/bottom: scrollable subtitle view
  - All cues listed, current cue highlighted with accent color
  - Auto-scrolls to current cue
  - Tap cue → seek to timestamp
  - Manual scroll → pause auto-scroll with resume button
- Bottom controls:
  - Seekable progress bar with current/total time
  - Skip back 15s | Play/Pause | Skip forward 15s
  - Speed selector (0.5x-3.0x)
  - Subtitle language picker
  - Subtitle settings (font size, position)

### Library Screen
- Tabs: History | Playlists | Cached
- History: chronological list of played tracks
- Playlists: user-created playlists, tap to view/edit
- Cached: tracks with file sizes, swipe to delete

### Settings Screen
- Theme: System / Light / Dark
- Cache: max size slider, current usage, clear all
- Subtitle defaults: preferred language, font size, font style
- Playback: default speed, skip interval (10s/15s/30s)
- About: version, licenses

## Player State Model

```kotlin
data class PlayerState(
    val track: Track?,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val subtitles: List<SubtitleCue> = emptyList(),
    val currentSubtitleIndex: Int = -1,
    val selectedSubtitleLanguage: String? = null,
    val isBuffering: Boolean = false,
    val error: String? = null
)

data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)
```

## Share Intent

AndroidManifest declares an intent filter for `ACTION_SEND` with `text/plain` MIME type. When a YouTube URL is shared to Audioly, `MainActivity` receives it, validates the URL, and navigates directly to extraction → player.

## Background Playback

`AudioService` extends `MediaSessionService` (Media3):
- Foreground notification with: track title, thumbnail, play/pause, skip controls
- MediaSession for lock screen and Bluetooth integration
- Audio focus handling (duck for notifications, pause for calls)
- Stops when user explicitly stops or dismisses notification

## Error Handling

- **Expired stream URLs**: Re-extract via NewPipe Extractor when playback fails
- **No internet + no cache**: Show clear error with retry button
- **No subtitles available**: Show message, continue audio-only
- **Extraction failure**: Show error with URL and retry option
- **Cache full**: LRU eviction (oldest unused tracks removed first)
