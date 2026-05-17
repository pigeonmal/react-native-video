import type { DownloadProgress } from '@pigeonmal/react-native-video';
import {
  DownloadManager,
  useVideoPlayer,
  VideoView,
} from '@pigeonmal/react-native-video';
import { useEffect, useState } from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

const TEST_URI = 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8';

export default function App() {
  const [downloads, setDownloads] = useState<DownloadProgress[]>([]);

  useEffect(() => {
    setDownloads(DownloadManager.getAllDownloads());
    const subscription = DownloadManager.addOnDownloadChangedListener(
      (progress) => {
        console.log('Download progress:', progress);
        setDownloads((prev) => {
          const index = prev.findIndex((d) => d.uri === progress.uri);
          if (index === -1) return [...prev, progress];
          const next = [...prev];
          next[index] = progress;
          return next;
        });
      },
    );
    return () => subscription.remove();
  }, []);

  const player = useVideoPlayer(
    {
      uri: TEST_URI,
      externalSubtitles: [
        {
          uri: 'https://raw.githubusercontent.com/andreyvit/subtitle-tools/master/sample.srt',
          label: 'English (SRT)',
          type: 'srt',
          language: 'en',
        },
      ],
      forceType: 'm3u8',
    },
    (pl) => {
      pl.play();
    },
  );

  const startDownload = () => {
    DownloadManager.download({
      uri: TEST_URI,
      title: 'Sintel',
      description: 'HLS multi-track download test',
      externalSubtitles: [
        {
          uri: 'https://raw.githubusercontent.com/andreyvit/subtitle-tools/master/sample.srt',
          label: 'English (SRT)',
          type: 'srt',
          language: 'en',
        },
      ],
    });
  };

  return (
    <View style={styles.page}>
      <VideoView player={player} style={styles.player} controls={true} />

      <TouchableOpacity style={styles.button} onPress={startDownload}>
        <Text style={styles.buttonText}>Download Video</Text>
      </TouchableOpacity>

      <ScrollView style={styles.downloadsList}>
        <Text style={styles.sectionTitle}>Downloads</Text>
        {downloads.map((d) => (
          <View key={d.uri} style={styles.downloadItem}>
            <Text style={styles.downloadUri} numberOfLines={1}>
              {d.uri}
            </Text>
            <Text style={styles.downloadStatus}>
              {d.state} - {d.percent.toFixed(1)}% (
              {(d.bytesDownloaded / 1024 / 1024).toFixed(1)} MB)
            </Text>
            <View style={styles.actions}>
              <TouchableOpacity onPress={() => DownloadManager.pause(d.uri)}>
                <Text style={styles.actionText}>Pause</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={() => DownloadManager.resume(d.uri)}>
                <Text style={styles.actionText}>Resume</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={() => DownloadManager.remove(d.uri)}>
                <Text style={styles.actionText}>Remove</Text>
              </TouchableOpacity>
            </View>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  page: {
    flex: 1,
    paddingTop: 50,
    backgroundColor: '#f5f5f5',
  },
  player: {
    width: '100%',
    height: 250,
    backgroundColor: 'black',
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 15,
    margin: 20,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonText: {
    color: 'white',
    fontWeight: 'bold',
  },
  downloadsList: {
    flex: 1,
    padding: 20,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  downloadItem: {
    backgroundColor: 'white',
    padding: 15,
    borderRadius: 8,
    marginBottom: 10,
    elevation: 2,
  },
  downloadUri: {
    fontSize: 14,
    color: '#333',
  },
  downloadStatus: {
    fontSize: 12,
    color: '#666',
    marginTop: 5,
  },
  actions: {
    flexDirection: 'row',
    marginTop: 10,
    gap: 20,
  },
  actionText: {
    color: '#007AFF',
    fontSize: 14,
  },
});
