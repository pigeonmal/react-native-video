# @pigeonmal/react-native-video

**Android-only** video player with **custom Cronet v143.0.7499.146** (DoH Cloudflare enabled), **Media3 1.8.0**, and **FFmpeg fallback** and more !

[![npm version](https://img.shields.io/npm/v/@pigeonmal/react-native-video/beta.svg)](https://www.npmjs.com/package/@pigeonmal/react-native-video)
[![Last Commit](https://img.shields.io/badge/Last%20Commit-605feed68a4be9ff8fcfa2f288d4f0570f044699-brightgreen)](https://github.com/pigeonmal/react-native-video/commit/605feed68a4be9ff8fcfa2f288d4f0570f044699)
[![Last Sync](https://img.shields.io/badge/Last%20Sync-19%2F12%2F2025-blue.svg)](https://github.com/pigeonmal/react-native-video)

## 🚀 Quick Start

### New Install (Custom Cronet - Recommended)
npm install @pigeonmal/react-native-video@beta @pigeonmal/react-native-nitro-fetch

Add to **`android/settings.gradle`**:
def cronetReleasePath = new File(["node", "--print", "require.resolve('@pigeonmal/react-native-nitro-fetch/package.json')"].execute(null, rootDir).text.trim(), "../android/cronet-release")
include ':cronet-release'
project(':cronet-release').projectDir = file(cronetReleasePath)

### Old Install (Play Store Cronet +0.1MB APK)
npm i @pigeonmal/react-native-video@7.0.0-beta.17 @pigeonmal/react-native-nitro-fetch@0.1.9

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| **Custom Cronet** | v143.0.7499.146 with **DoH Cloudflare** enabled |
| **Modern Media3** | **1.8.0** ExoPlayer integration |
| **FFmpeg Fallback** | Video (non-TV devices only) + Audio |
| **VideoConfig** | `externalAudios`, `forceType`, `forceOkhttp`, `initialSubtitleDelay`, `startPosition` |
| **Player Controls** | `subtitleDelay`, `getAllPlayerTracks()`, `selectTrackById/Index()`, `resetForReuse()` |
| **Performance** | `progressEventInterval` customization |
| **Bug Fixes** | External subs, HLS/DASH, onloadstart, playback exceptions |

## 📱 VideoConfig Options

const videoConfig = {
  externalAudios: [/* Array of ExternalAudio  */],
  forceType: ExternalForcedType.m3u8 || ExternalForcedType.mpd, // Force HLS/DASH if no extension
  forceOkhttp: true, // Use Cronet by default (OkHttp still works)
  initialSubtitleDelay: 500, // ms (positive/negative)
  startPosition: 10000, // ms
};

## 🎮 Player API

- `new VideoPlayer(undefined)` now supported (nullable source)

| Method/Property | Description |
|-----------------|-------------|
| `player.subtitleDelay` | Subtitle delay adjustment (ms, positive/negative) |
| `player.getAllPlayerTracks()` | Get all current tracks (audio/video/text) |
| `player.selectTrackById(type, trackId)` | Select track by ID string |
| `player.selectTrackByIndex(type, index)` | Select track by index number |
| `player.resetForReuse()` | Stop + clear tracks for reuse |
| `player.progressEventInterval` | Customize progress event frequency |

**Note**: `player.selectTextTrack()` → `player.selectTrackById(TrackType.TEXT, trackId)`

## 📊 Event Data

| Event | New Data |
|-------|----------|
| `onLoadData` | `allPlayerTracks` included |
| `onProgressData` | `seekableDuration` (video duration) |

## 🐛 Fixed Issues

- External subtitles in HLS/DASH streams
- Added language tags to external subtitles
- `onloadstart` event firing
- `onError` now catches `'player/playback-exception'`

## 🔄 Breaking Changes

- `TextTrack` → `PlayerTrack`
- `onTrackChange` event **removed**

---
*Last sync: Dec 19, 2025 [605feed68a4be9ff8fcfa2f288d4f0570f044699](https://github.com/pigeonmal/react-native-video/commit/605feed68a4be9ff8fcfa2f288d4f0570f044699)*