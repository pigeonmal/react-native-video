import { NitroModules } from 'react-native-nitro-modules';
import type { VideoDownloader as VideoDownloaderSpec } from './spec/nitro/VideoDownloader.nitro';

export { useEvent } from './core/hooks/useEvent';
export { useVideoPlayer } from './core/hooks/useVideoPlayer';
export type { BufferConfig } from './core/types/BufferConfig';
export type {
  DownloadProgress,
  DownloadRequest,
  DownloadState,
} from './core/types/DownloadTypes';
export * from './core/types/Events';
export type { IgnoreSilentSwitchMode } from './core/types/IgnoreSilentSwitchMode';
export type { MixAudioMode } from './core/types/MixAudioMode';
export { TrackType } from './core/types/PlayerTrack';
export type { PlayerTrack } from './core/types/PlayerTrack';
export type { ResizeMode } from './core/types/ResizeMode';
export type {
  ExternalAudio,
  ExternalForcedType,
  ExternalSubtitle,
  VideoConfig,
  VideoSource,
} from './core/types/VideoConfig';
export {
  type LibraryError,
  type PlayerError,
  type SourceError,
  type UnknownError,
  type VideoComponentError,
  type VideoError,
  type VideoErrorCode,
  type VideoRuntimeError,
  type VideoViewError,
} from './core/types/VideoError';
export type { VideoPlayerStatus } from './core/types/VideoPlayerStatus';
export {
  default as VideoView,
  type VideoViewProps,
  type VideoViewRef,
} from './core/video-view/VideoView';
export { VideoPlayer } from './core/VideoPlayer';
export const DownloadManager =
  NitroModules.createHybridObject<VideoDownloaderSpec>('VideoDownloader');
