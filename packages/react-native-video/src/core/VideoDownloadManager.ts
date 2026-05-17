import { Platform } from "react-native";
import { NitroModules } from "react-native-nitro-modules";
import type {
  VideoDownloadManagerFactory,
  VideoDownloadManager as VideoDownloadManagerImpl,
} from "../spec/nitro/VideoDownloadManager.nitro";
import type { NativeVideoConfig, VideoConfig } from "./types/VideoConfig";
import type {
  VideoDownloadOptions,
  VideoDownloadTask,
} from "./types/VideoDownload";
import {
  tryParseNativeVideoError,
  VideoRuntimeError,
} from "./types/VideoError";
import { createSourceFromVideoConfig } from "./utils/sourceFactory";

const NativeVideoDownloadManagerFactory =
  NitroModules.createHybridObject<VideoDownloadManagerFactory>(
    "VideoDownloadManagerFactory",
  );

const normalizeDownloadId = (
  config: NativeVideoConfig,
  options: Partial<VideoDownloadOptions> | undefined,
) => {
  const fromOptions = options?.downloadId?.trim();
  if (fromOptions) return fromOptions;

  const fromConfig = config.offlineDownloadId?.trim();
  if (fromConfig) return fromConfig;

  return config.uri;
};

const normalizeOptions = (
  config: NativeVideoConfig,
  options?: Partial<VideoDownloadOptions>,
): VideoDownloadOptions => ({
  downloadId: normalizeDownloadId(config, options),
  downloadExternalSubtitles: options?.downloadExternalSubtitles ?? true,
});

class VideoDownloadManager {
  private readonly manager: VideoDownloadManagerImpl;

  constructor() {
    if (Platform.OS !== "android") {
      throw new VideoRuntimeError(
        "library/method-not-supported",
        "VideoDownloadManager is currently only supported on Android",
      );
    }

    this.manager = NativeVideoDownloadManagerFactory.createDownloadManager();
  }

  private wrapPromise<T>(promise: Promise<T>) {
    return promise.catch((error) => {
      throw tryParseNativeVideoError(error);
    });
  }

  async enqueueDownload(
    config: VideoConfig & { uri: string },
    options?: Partial<VideoDownloadOptions>,
  ): Promise<VideoDownloadTask> {
    const source = createSourceFromVideoConfig(config);
    const nativeConfig = source.config;
    return this.wrapPromise(
      this.manager.enqueueDownload(
        nativeConfig,
        normalizeOptions(nativeConfig, options),
      ),
    );
  }

  pauseDownload(downloadId: string): Promise<void> {
    return this.wrapPromise(this.manager.pauseDownload(downloadId));
  }

  resumeDownload(downloadId: string): Promise<void> {
    return this.wrapPromise(this.manager.resumeDownload(downloadId));
  }

  removeDownload(downloadId: string): Promise<void> {
    return this.wrapPromise(this.manager.removeDownload(downloadId));
  }

  getDownload(downloadId: string): Promise<VideoDownloadTask> {
    return this.wrapPromise(this.manager.getDownload(downloadId));
  }

  getDownloads(): Promise<VideoDownloadTask[]> {
    return this.wrapPromise(this.manager.getDownloads());
  }

  async resolveSourceForOffline(
    config: VideoConfig & { uri: string },
    downloadId?: string,
  ): Promise<NativeVideoConfig> {
    const source = createSourceFromVideoConfig(config);
    const nativeConfig = source.config;
    return this.wrapPromise(
      this.manager.resolveSourceForOffline(
        nativeConfig,
        downloadId ?? normalizeDownloadId(nativeConfig, undefined),
      ),
    );
  }

  clearDownloads(): Promise<void> {
    return this.wrapPromise(this.manager.clearDownloads());
  }
}

const videoDownloadManager =
  Platform.OS === "android" ? new VideoDownloadManager() : null;

export { VideoDownloadManager, videoDownloadManager };
