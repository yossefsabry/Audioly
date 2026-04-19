<p align="center">
  <img src="logo.svg" width="120" height="120" alt="Audioly logo" />
</p>

<h1 align="center">Audioly</h1>

<p align="center">
  YouTube audio player for Android with background playback, synced lyrics, and offline caching.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-24+-3DDC84?logo=android&logoColor=white" alt="API 24+" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="MIT License" />
</p>

---

## Features

- **Share or paste a YouTube URL** — from YouTube, a browser, or any app via the Android share sheet
- **Background playback** — audio continues when the app is minimized, with full notification controls
- **Synced scrollable lyrics** — subtitles auto-scroll to the active cue; tap any line to seek
- **Auto-translate to English** — when no English subtitles exist, auto-translated English is added automatically
- **Offline cache** — audio and subtitles are cached locally for playback without a network connection
- **History** — recently played tracks sorted by last played time
- **Playlists** — create, rename, and delete playlists; add or remove tracks
- **Queue management** — shuffle, repeat, skip, and reorder the play queue
- **Search** — search YouTube directly from within the app
- **Dark / Light / System theme** — Material You dynamic color on Android 12+

## Supported URLs

| Format | Example |
|--------|---------|
| Standard | `https://www.youtube.com/watch?v=dQw4w9WgXcQ` |
| Short | `https://youtu.be/dQw4w9WgXcQ` |
| Mobile | `https://m.youtube.com/watch?v=dQw4w9WgXcQ` |
| Music | `https://music.youtube.com/watch?v=dQw4w9WgXcQ` |

## How it works

1. Open YouTube (or any app with a YouTube link).
2. Tap **Share > Audioly**.
3. Audioly extracts the audio stream and begins playback immediately.
4. Subtitles are fetched in the background; lyrics appear automatically when available.

## Architecture

```
com.audioly.app
├── extraction/     NewPipe Extractor wrapper — YouTubeExtractor, SubtitleExtractor
├── player/         ExoPlayer (AudioPlayer), MediaSessionService (AudioService),
│                   VttParser, SubtitleManager, PlayerState Flow
├── data/
│   ├── db/         Room — Track, Playlist, PlaylistTrack, SubtitleCache
│   ├── cache/      AudioCacheManager (SimpleCache), SubtitleCacheManager (disk)
│   ├── preferences/ DataStore UserPreferences
│   └── repository/ TrackRepository, PlaylistRepository, CacheRepository
└── ui/
    ├── screens/    Home, Search, Player, Library (History/Playlists/Cached), Settings
    ├── viewmodel/  HomeViewModel, SearchViewModel, PlayerViewModel, LibraryViewModel
    └── components/ UrlInput, SubtitleView, MiniPlayer, TrackItem, PlaylistItem
```

## Build

```bash
./build.sh          # assembleDebug + install
./build-release.sh  # assembleRelease (requires keystore env vars)
./dev.sh            # incremental build + install + launch
```

### Run tests

```bash
./gradlew testDebugUnitTest              # unit tests
./gradlew connectedDebugAndroidTest      # instrumentation (requires device)
```

## Dependencies

| Library | Purpose |
|---------|---------|
| [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) 0.24.2 | YouTube stream + subtitle extraction |
| Media3 ExoPlayer + Session | Audio playback + MediaSession |
| Room | Local track / playlist / subtitle cache database |
| DataStore Preferences | User settings persistence |
| Jetpack Compose + Material3 | UI toolkit |
| Coil | Thumbnail image loading |
| OkHttp | HTTP client for NewPipe and subtitle downloads |

## Known limitations

- **Stream URL expiry** — extracted URLs expire after ~6 hours. If playback stalls on a stale URL, re-share or re-paste the link to refresh it.
- **SRT subtitles** — only WebVTT is parsed; SRT tracks from NewPipe are not yet converted.
- **Age-restricted videos** — NewPipe cannot extract these without a logged-in cookie, which is not supported.

## License

[MIT](LICENSE) &copy; 2025 Yossef Sabry
