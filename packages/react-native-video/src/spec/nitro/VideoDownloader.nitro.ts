import type { HybridObject } from 'react-native-nitro-modules';
import type {
  DownloadProgress,
  DownloadRequest,
} from '../../core/types/DownloadTypes';
import type { ListenerSubscription } from '../../core/types/Events';

/**
 * A module to download videos for offline playback.
 */
export interface VideoDownloader extends HybridObject<{ android: 'kotlin' }> {
  /**
   * Start or resume a download.
   * If it's HLS, it will download all tracks (audio, video, subtitles).
   */
  download(request: DownloadRequest): void;

  /**
   * Pause a download.
   */
  pause(uri: string): void;

  /**
   * Resume a paused download.
   */
  resume(uri: string): void;

  /**
   * Stop and remove a download.
   */
  remove(uri: string): void;

  /**
   * Get all active and completed downloads.
   */
  getAllDownloads(): DownloadProgress[];

  /**
   * Adds a listener for download progress updates.
   * @param listener - The listener to add.
   * @returns A subscription object that can be used to remove the listener.
   */
  addOnDownloadChangedListener(
    listener: (progress: DownloadProgress) => void,
  ): ListenerSubscription;
}
