import type { IgnoreSilentSwitchMode } from './IgnoreSilentSwitchMode';
import type { MixAudioMode } from './MixAudioMode';
import type { VideoPlayerSourceBase } from './VideoPlayerSourceBase';
import type { VideoPlayerStatus } from './VideoPlayerStatus';

// eslint-disable-next-line @typescript-eslint/no-unused-vars
import type { VideoConfig } from './VideoConfig';
import type { AllPlayerTracks, PlayerTrack, TrackType } from './PlayerTrack';

export interface VideoPlayerBase {
  /**
   * The source of the video.
   * Source is immutable. To change the source, you need to call {@link replaceSourceAsync} method.
   * see {@link VideoPlayerSourceBase}
   */
  readonly source: VideoPlayerSourceBase;

  /**
   * The status of the player.
   * @param idle - The player is idle (source is not loaded)
   * @param loading - The player is loading.
   * @param readyToPlay - The player is ready to play (source is loaded).
   * @param error - The player has an error.
   */
  readonly status: VideoPlayerStatus;

  /**
   * The current time of the video in seconds (1.0 = 1 sec).
   * Returns NaN if the current time is not available.
   */
  readonly duration: number;

  /**
   * The volume of the video (0.0 = 0%, 1.0 = 100%).
   * @note If the player is {@link muted}, the volume will be 0.0.
   */
  volume: number;

  /**
   * The duration of the video in seconds (1.0 = 1 sec).
   * Returns NaN if the duration is not available.
   */
  currentTime: number;

  /**
   * Modify the progress event interval (ms)
   * By default it's 250ms
   */
  progressEventInterval: number;

  /**
   * Subtitle delay (milliseconds) negative or positive number
   */
  subtitleDelay: bigint;

  /**
   * Whether the player is muted.
   */
  muted: boolean;

  /**
   * Whether the player is looped.
   */
  loop: boolean;

  /**
   * Controls the speed at which the player should play.
   * @note if rate is = 0, it will pause video.
   */
  rate: number;

  /**
   * Controls the audio mixing mode of the player.
   *
   * - `mixWithOthers` - Mix with other players.
   * - `doNotMix` - Do not mix with other players.
   * - `duckOthers` - Duck other players.
   * - `auto` - uses default behavior for player.
   *
   * default is `auto`.
   */
  mixAudioMode: MixAudioMode;

  /**
   * Controls the silent switch mode of the player.
   * @note This is only supported on iOS.
   *
   * - `auto` - uses default behavior for player.
   * - `ignore` - ignore the silent switch.
   * - `obey` - obey the silent switch.
   */
  ignoreSilentSwitchMode: IgnoreSilentSwitchMode;

  /**
   * Whether the player should play in background.
   *
   * - `true` - play in background.
   * - `false` - pause in background (default).
   *
   * @note this can override {@link playWhenInactive}.
   */
  playInBackground: boolean;

  /**
   * Whether the player should play when the app is inactive (user opened control center).
   *
   * - `true` - play when the app is inactive.
   * - `false` - pause when the app is inactive (default).
   *
   * @note this can be overridden by {@link playInBackground}.
   * @note This is only supported on iOS.
   */
  playWhenInactive: boolean;

  /**
   * Whether the player is playing.
   * @note This is a read-only property.
   * @note To pause/resume the player, you need to use {@link play} and {@link pause} methods.
   */
  readonly isPlaying: boolean;

  /**
   * Manually initialize the player. You don't need to call this method manually, unless you set `initializeOnCreation` to false in {@link VideoConfig}
   */
  initialize(): Promise<void>;

  /**
   * Preload the video.
   * This is useful to avoid delay when the user plays the video.
   * Preloading too many videos can lead to memory issues or performance issues.
   */
  preload(): Promise<void>;

  /**
   * Start playback of player.
   */
  play(): void;

  /**
   * Pause playback of player.
   */
  pause(): void;

  /**
   * Seek by given time.
   * If the time is negative, it will seek backward.
   * time will be clamped if it is out of range (0 ~ {@link duration}).
   * @param time - The time to seek from current time in seconds.
   */
  seekBy(time: number): void;

  /**
   * Seek to a specific time in the video.
   * @param time - The time to seek to in seconds.
   * @note This have same effect as {@link currentTime} setter.
   * @note time will be clamped if it is out of range (0 ~ {@link duration}).
   */
  seekTo(time: number): void;

  /**
   * Replace the current source of the player.
   * @param source - The new source of the video.
   * @note If you want to clear the source, you can pass null. It has the same effect as {@link release}.
   * see {@link VideoPlayerSourceBase}
   */
  replaceSourceAsync(source: VideoPlayerSourceBase | null): Promise<void>;

  /**
   * Get all available text tracks for the current source.
   * @returns Array of available text tracks
   */
  getAvailableTextTracks(): PlayerTrack[];

  /**
   * Get all tracks (audios, videos, texts) tracks for the current source.
   * @returns All tracks of the player
   */
  getAllPlayerTracks(): AllPlayerTracks;

  /**
   * Select a track (text, audio, video) to display.
   * @param type - TrackType (text, audio, video)
   * @param id - The id of the track to select.
   */
  selectTrackById(type: TrackType, id?: string): void;

  /**
   * Select a track (text, audio, video) to display.
   * @param type - TrackType (text, audio, video)
   * @param index - The index of the track to select.
   */
  selectTrackByIndex(type: TrackType, index?: number): void;

  /**
   * Stops playback and clears all media items so the Player can be safely reused.
   *
   * This function:
   * - Stops playback if currently playing or buffering
   * - Cancels any ongoing loading
   * - Clears the playlist/timeline
   * - Keeps the Player instance and its resources alive
   *
   * After calling this function, the Player will be in [Player.STATE_IDLE]
   * with an empty playlist.
   *
   * Use this when:
   * - Reusing a single Player instance for multiple media items
   * - Switching videos frequently (e.g. feeds, previews, RecyclerView)
   *
   * Do NOT use this when you are done with playback permanently.
   * In that case, call [Player.release] instead.
   */
  resetForReuse(): void;
  /**
   * Get the currently selected text track.
   * @returns The currently selected text track, or undefined if none is selected
   */
  readonly selectedTrack?: PlayerTrack;

  /**
   * Releases the player's native resources and releases native state.
   */
  release(): void;
}
