import { useVideoPlayer, VideoView } from "@pigeonmal/react-native-video";
import { StyleSheet, View } from "react-native";
export default function App() {
  const player = useVideoPlayer(
    {
      uri: "",
      forceType: "m3u8",
      useIvInjectDataSource: true,
      headers: {},
    },
    (pl) => {
      pl.play();
      setTimeout(() => {
        pl.seekTo(300);
      }, 6000);
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
  pressable: {
    width: 100,
    height: 100,
    backgroundColor: "red",
  },
  player: {
    width: "100%",
    height: 300,
    backgroundColor: "black",
  },
});
