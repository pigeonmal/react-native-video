import { useVideoPlayer, VideoView } from "@pigeonmal/react-native-video";
import { StyleSheet, View } from "react-native";
export default function App() {
  const player = useVideoPlayer(
    {
      uri: "https://github.com/chthomos/video-media-samples/raw/refs/heads/master/big-buck-bunny-1080p-60fps-30sec.mp4",
    },
    (pl) => {
      pl.play();
    },
  );
  return (
    <View style={styles.page}>
      <VideoView player={player} style={styles.player} />
    </View>
  );
}

const styles = StyleSheet.create({
  page: {
    flex: 1,
  },
  player: {
    width: "100%",
    height: 300,
    backgroundColor: "black",
  },
});
