import { useEffect, useState } from 'react';
import { AccessibilityInfo, Image, type ImageSourcePropType, type StyleProp, StyleSheet, View, type ViewStyle } from 'react-native';
import { useVideoPlayer, VideoView } from 'expo-video';

type ScenicVideoProps = {
  poster: ImageSourcePropType;
  source: number;
  contentFit?: 'contain' | 'cover';
  style?: StyleProp<ViewStyle>;
};

export function ScenicVideo({ poster, source, contentFit = 'cover', style }: ScenicVideoProps) {
  const [reduceMotion, setReduceMotion] = useState(true);
  const [videoFailed, setVideoFailed] = useState(false);
  const player = useVideoPlayer(source, (instance) => {
    instance.loop = true;
    instance.muted = true;
  });

  useEffect(() => {
    let active = true;
    void AccessibilityInfo.isReduceMotionEnabled().then((enabled) => {
      if (active) setReduceMotion(enabled);
    });
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    return () => {
      active = false;
      subscription.remove();
    };
  }, []);

  useEffect(() => {
    if (reduceMotion) player.pause();
    else player.play();
  }, [player, reduceMotion]);

  // 자동재생이 막히거나(저사양 기기·데이터 절약 모드) 영상 자체를 못 받아 오면 player 가
  // 'error' 상태를 낸다. 그때는 영상을 아예 그리지 않아 아래 포스터 이미지가 그대로 보이게 한다.
  useEffect(() => {
    setVideoFailed(false);
    const subscription = player.addListener('statusChange', ({ status }) => {
      if (status === 'error') setVideoFailed(true);
    });
    return () => subscription.remove();
  }, [player, source]);

  return (
    <View accessible={false} importantForAccessibility="no-hide-descendants" style={[styles.fill, style]}>
      <Image source={poster} resizeMode="cover" style={styles.poster} />
      {!reduceMotion && !videoFailed && <VideoView player={player} nativeControls={false} contentFit={contentFit} style={styles.video} />}
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { position: 'absolute', inset: 0, width: '100%', height: '100%', overflow: 'hidden' },
  poster: { position: 'absolute', inset: 0, width: '100%', height: '100%' },
  video: { position: 'absolute', inset: 0, width: '100%', height: '100%' },
});
