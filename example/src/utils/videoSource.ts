import type { VideoConfig } from '@pigeonmal/react-native-video';

export type VideoType = 'hls' | 'mp4';

export const getVideoSource = (type: VideoType): VideoConfig => {
  const HLS = 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8';
  const MP4 =
    'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_30MB.mp4';

  return {
    uri: type === 'hls' ? HLS : MP4,
    externalSubtitles: [
      {
        label: 'External',
        uri: 'https://gist.githubusercontent.com/samdutton/ca37f3adaf4e23679957b8083e061177/raw/e19399fbccbc069a2af4266e5120ae6bad62699a/sample.vtt',
        language: 'en',
        type: 'vtt',
      },
    ],
    metadata: {
      title: 'Big Buck Bunny',
      artist: 'Blender Foundation',
      imageUri:
        'https://peach.blender.org/wp-content/uploads/title_anouncement.jpg',
      subtitle: 'By the Blender Institute',
      description:
        'Big Buck Bunny is a short computer-animated comedy film by the Blender Institute, part of the Blender Foundation. It was made using Blender, a free and open-source 3D creation suite.',
    },
  } as VideoConfig;
};
