import { useVideoPlayer, VideoView } from '@pigeonmal/react-native-video';
import { StyleSheet, View } from 'react-native';
export default function App() {
  const player = useVideoPlayer(
    {
      uri: 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
    },
    (player) => {
      player.play();
    }
  );
  return (
    <View style={{ flex: 1 }}>
      <VideoView player={player} style={styles.player} />
    </View>
  );
}

const styles = StyleSheet.create({
  player: {
    width: '100%',
    height: 300,
    backgroundColor: 'black',
  },
});
