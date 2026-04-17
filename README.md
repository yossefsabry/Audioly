# Audioly

Android app that accepts YouTube URLs, extracts audio with [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), plays it in the background via Media3 ExoPlayer, and displays synced scrollable subtitles. Tracks, playlists, and cached audio are stored for offline replay.

## Features

- **Paste or share a YouTube URL** — from any app (YouTube, browser) via Android share sheet, or paste directly
- **Background playback** — audio keeps playing when the app is minimized; notification controls included
- **Synced subtitles** — scrollable subtitle list auto-scrolls to the active cue; tap any cue to seek
- **Offline cache** — audio is cached via ExoPlayer `SimpleCache`; cached tracks play without a network connection
- **History** — recently played tracks sorted by last played time
- **Playlists** — create, rename, delete playlists; add/remove tracks
- **Dark / Light / System theme** — Material You dynamic color on Android 12+

## Supported URLs

| Format | Example |
|--------|---------|
| Standard watch | `https://www.youtube.com/watch?v=dQw4w9WgXcQ` |
| Short | `https://youtu.be/dQw4w9WgXcQ` |
| Mobile | `https://m.youtube.com/watch?v=dQw4w9WgXcQ` |
| Music | `https://music.youtube.com/watch?v=dQw4w9WgXcQ` |

## Share flow

1. Open YouTube (or any app with a YouTube link).
2. Tap **Share → Audioly**.
3. Audioly launches, extracts the audio stream, and begins playback immediately.

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
    ├── screens/    Home, Player, Library (History/Playlists/Cached), Settings
    └── components/ UrlInput, SubtitleView, MiniPlayer, TrackItem, PlaylistItem
```

## Known limits

- **Stream URL expiry** — NewPipe-extracted stream URLs expire (typically ~6 hours). Audioly does not yet auto-re-extract on expiry; if playback stalls on a cached track whose stream URL has expired, re-paste the URL to refresh it.
- **SRT subtitles** — only VTT is parsed. SRT from NewPipe is not yet supported.
- **Playlists** — playlist detail screen (track list view) is not yet implemented; tracks can be added to a playlist from the Library screen.
- **Age-restricted videos** — NewPipe cannot extract age-restricted streams without a logged-in account cookie, which is not supported.

## Build

```bash
./build.sh          # assembleDebug + install
./build-release.sh  # assembleRelease (requires keystore env vars)
./dev.sh            # incremental build + install + launch
```

### Run unit tests

```bash
./gradlew testDebugUnitTest
```

### Run instrumentation tests (requires connected device or emulator)

```bash
./gradlew connectedDebugAndroidTest
```

## Dependencies

| Library | Purpose |
|---------|---------|
| NewPipe Extractor 0.24.2 | YouTube stream + subtitle extraction |
| Media3 ExoPlayer + Session | Audio playback + MediaSession |
| Room | Local track/playlist/subtitle cache database |
| DataStore Preferences | User settings persistence |
| Compose Material3 | UI |
| Coil | Thumbnail image loading |
| OkHttp | HTTP downloader for NewPipe |
