import {
  useEvent,
  useVideoPlayer,
  videoDownloadManager,
  VideoView,
  type VideoConfig,
} from "@pigeonmal/react-native-video";
import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";

const HLS_SOURCE: VideoConfig & { uri: string } = {
  uri: "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
  forceType: "m3u8",
  externalSubtitles: [
    {
      uri: "https://raw.githubusercontent.com/andreyvit/subtitle-tool/master/sample.srt",
      label: "English (SRT)",
      type: "srt",
      language: "en",
    },
  ],
  offlineDownloadId: "example-tears-of-steel",
};

export default function App() {
  const [isOfflineMode, setIsOfflineMode] = useState(false);
  const [downloadStatus, setDownloadStatus] = useState("Not started");
  const [downloadId] = useState("example-tears-of-steel");

  const player = useVideoPlayer(HLS_SOURCE, (pl) => {
    pl.play();
  });

  useEvent(player, "onError", (error) => {
    Alert.alert("Player Error", `[${error.code}] ${error.message}`);
  });

  const canUseDownloadApi = useMemo(
    () => Platform.OS === "android" && videoDownloadManager != null,
    [],
  );

  const refreshStatus = async () => {
    if (!canUseDownloadApi || videoDownloadManager == null) return;
    try {
      const task = await videoDownloadManager.getDownload(downloadId);
      const percent =
        task.percentDownloaded >= 0
          ? `${task.percentDownloaded.toFixed(1)}%`
          : "N/A";
      setDownloadStatus(
        `${task.state} | ${percent} | ${Math.round(task.bytesDownloaded / (1024 * 1024))}MB`,
      );
    } catch {
      setDownloadStatus("Not downloaded");
    }
  };

  useEffect(() => {
    if (!canUseDownloadApi) return;
    refreshStatus();
    const timer = setInterval(refreshStatus, 1000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canUseDownloadApi]);

  const onStartDownload = async () => {
    if (!canUseDownloadApi || videoDownloadManager == null) {
      Alert.alert("Android only", "Offline download is available on Android.");
      return;
    }
    try {
      const ldrr = await videoDownloadManager.enqueueDownload(HLS_SOURCE, {
        downloadId,
        downloadExternalSubtitles: true,
      });
      console.log("Download started", ldrr);
      await refreshStatus();
      console.log("Download enqueued");
    } catch (error) {
      Alert.alert("Download failed", String(error));
    }
  };

  const onPauseDownload = async () => {
    if (!videoDownloadManager) return;
    await videoDownloadManager.pauseDownload(downloadId);
    await refreshStatus();
  };

  const onResumeDownload = async () => {
    if (!videoDownloadManager) return;
    await videoDownloadManager.resumeDownload(downloadId);
    await refreshStatus();
  };

  const onDeleteDownload = async () => {
    if (!videoDownloadManager) return;
    await videoDownloadManager.removeDownload(downloadId);
    setIsOfflineMode(false);
    await player.replaceSourceAsync(HLS_SOURCE);
    await refreshStatus();
  };

  const onPlayOffline = async () => {
    if (!videoDownloadManager) return;
    try {
      const offlineSource = await videoDownloadManager.resolveSourceForOffline(
        HLS_SOURCE,
        downloadId,
      );
      await player.replaceSourceAsync(offlineSource);
      setIsOfflineMode(true);
      player.play();
    } catch (error) {
      Alert.alert("Offline source failed", String(error));
    }
  };

  const onPlayOnline = async () => {
    await player.replaceSourceAsync(HLS_SOURCE);
    setIsOfflineMode(false);
    player.play();
  };

  return (
    <SafeAreaView style={styles.root}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>React Native Video - Download Example</Text>
        <Text style={styles.subtitle}>
          Mode: {isOfflineMode ? "Offline playback" : "Online playback"}
        </Text>
        <Text style={styles.subtitle}>Download: {downloadStatus}</Text>

        <View style={styles.playerContainer}>
          <VideoView player={player} style={styles.player} controls />
        </View>

        <View style={styles.row}>
          <ActionButton text="Start Download" onPress={onStartDownload} />
          <ActionButton text="Pause" onPress={onPauseDownload} />
          <ActionButton text="Resume" onPress={onResumeDownload} />
        </View>
        <View style={styles.row}>
          <ActionButton text="Play Offline" onPress={onPlayOffline} />
          <ActionButton text="Play Online" onPress={onPlayOnline} />
          <ActionButton text="Delete" onPress={onDeleteDownload} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function ActionButton({
  text,
  onPress,
}: {
  text: string;
  onPress: () => void | Promise<void>;
}) {
  return (
    <Pressable style={styles.button} onPress={onPress}>
      <Text style={styles.buttonText}>{text}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: "#0f1115" },
  content: { padding: 16, gap: 12 },
  title: { color: "#fff", fontSize: 20, fontWeight: "700" },
  subtitle: { color: "#c6ccda", fontSize: 14 },
  playerContainer: {
    width: "100%",
    height: 220,
    borderRadius: 12,
    overflow: "hidden",
    backgroundColor: "#000",
  },
  player: { width: "100%", height: "100%" },
  row: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  button: {
    backgroundColor: "#2b6cff",
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 12,
  },
  buttonText: { color: "#fff", fontWeight: "600" },
});
