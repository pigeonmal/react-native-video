import type { NativeExternalSubtitle } from './VideoConfig';

export interface DownloadRequest {
  uri: string;
  title?: string;
  description?: string;
  headers?: Record<string, string>;
  externalSubtitles?: NativeExternalSubtitle[];
}

export type DownloadState =
  | 'queued'
  | 'downloading'
  | 'paused'
  | 'completed'
  | 'failed'
  | 'removing'
  | 'restarting'
  | 'deleted';

export interface DownloadProgress {
  uri: string;
  state: DownloadState;
  percent: number; // 0-100
  bytesDownloaded: number;
  contentLength: number;
  error?: string;
}
