Last commit sync : 605feed68a4be9ff8fcfa2f288d4f0570f044699
Date: 19/12/2025

(Only android)
Install :

1. npm install @pigeonmal/react-native-video@beta react-native-nitro-fetch
2. Add this to android/app/settings.gradle

```
include ':media3-ffmpeg-decoder'
project(':media3-ffmpeg-decoder').projectDir = file('../node_modules/react-native-video/android/media3-ffmpeg-decoder')
```

3. create a patch for react-native-nitro-fetch like in the patches/react-native-nitro-fetch@x.patch

---

Features:

- ffmpeg fallback
- videoconfig: externalAudios (array of AudioTrack)
- videoconfig: forceType 'm3u8' or 'mpd' if url not have explicit extension
- videoconfig: forceOkhttp cronet by default but still okhttp work
- videoconfig: initialSubtitleDelay (ms positive or negative)
- videoconfig: startPosition (in ms)
- player.subtitleDelay for subtitle delay adjust (ms positive or negative)
- player.getAllPlayerTracks() for get all current tracks (audios, videos, texts)
- player.selectTrackById and selectTrackByIndex for select video or audio or text
- player.selectTextTrack removed use selectTrackById(TrackType.TEXT, trackId)
- player.resetForReuse() stop playback and clear tracks
- player.progressEventInterval change the progress event interval
- bugfix: external subtitles in hls/dash
- nullable video player source : new VideoPlayer(undefined);
- TextTrack type replaced to PlayerTrack
- removed onTrackChange event
- allPlayerTracks in onLoadData event directly
- seekableDuration in onProgressData event (video duration)
