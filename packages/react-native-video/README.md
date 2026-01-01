Last commit sync : 605feed68a4be9ff8fcfa2f288d4f0570f044699
Date: 19/12/2025

Only android !!

New install (custom cronet) :
 npm install @pigeonmal/react-native-video@beta @pigeonmal/react-native-nitro-fetch

Add this in **settings.gradle**
include ':cronet-release'
project(':cronet-release').projectDir = file('../../node_modules/@pigeonmal/react-native-nitro-fetch/android/cronet-release')

Old install (playstore cronet +0.1mb apk) :
 npm i @pigeonmal/react-native-video@7.0.0-beta.17 @pigeonmal/react-native-nitro-fetch@0.1.9
---

Features:

- bring custom cronet v143.0.7499.146 with DOH cloudflare
- modern media3 1.8.0
- ffmpeg fallback (audios + videos only if device is not tv)
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
- bugfix: add langs to external subs
- bugfix: onloadstart
- bugfix: onError also catch playback exception 'player/playback-exception'
- nullable video player source : new VideoPlayer(undefined);
- TextTrack type replaced to PlayerTrack
- removed onTrackChange event
- allPlayerTracks in onLoadData event directly
- seekableDuration in onProgressData event (video duration)
