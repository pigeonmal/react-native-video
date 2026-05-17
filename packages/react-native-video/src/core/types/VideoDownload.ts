import type { NativeVideoConfig } from "./VideoConfig";

export type VideoDownloadState =
  | "queued"
  | "downloading"
  | "paused"
  | "completed"
  | "failed"
  | "removing";

export interface VideoDownloadTask {
  id: string;
  uri: string;
  state: VideoDownloadState;
  bytesDownloaded: number;
  bytesTotal: number;
  percentDownloaded: number;
  failureReason?: string;
  updatedAtMs: number;
}

export interface VideoDownloadOptions {
  /**
   * Stable id of the download. If empty, uri/offlineDownloadId are used.
   */
  downloadId: string;
  /**
   * If true, external subtitle files are downloaded and later remapped to local files.
   */
  downloadExternalSubtitles: boolean;
}

export interface VideoDownloadManagerBase {
  enqueueDownload(
    config: NativeVideoConfig,
    options: VideoDownloadOptions,
  ): Promise<VideoDownloadTask>;
  pauseDownload(downloadId: string): Promise<void>;
  resumeDownload(downloadId: string): Promise<void>;
  removeDownload(downloadId: string): Promise<void>;
  getDownload(downloadId: string): Promise<VideoDownloadTask>;
  getDownloads(): Promise<VideoDownloadTask[]>;
  resolveSourceForOffline(
    config: NativeVideoConfig,
    downloadId: string,
  ): Promise<NativeVideoConfig>;
  clearDownloads(): Promise<void>;
}
