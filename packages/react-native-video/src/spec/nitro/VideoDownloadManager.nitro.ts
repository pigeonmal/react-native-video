import type { HybridObject } from "react-native-nitro-modules";
import type { NativeVideoConfig } from "../../core/types/VideoConfig";
import type {
  VideoDownloadManagerBase,
  VideoDownloadOptions,
  VideoDownloadTask,
} from "../../core/types/VideoDownload";

export interface VideoDownloadManager
  extends HybridObject<{ android: "kotlin" }>,
    VideoDownloadManagerBase {
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

export interface VideoDownloadManagerFactory
  extends HybridObject<{ android: "kotlin" }> {
  createDownloadManager(): VideoDownloadManager;
}
