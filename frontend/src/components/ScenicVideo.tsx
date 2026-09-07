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

  return (
    <View accessible={false} importantForAccessibility="no-hide-descendants" style={[styles.fill, style]}>
      <Image source={poster} resizeMode="cover" style={styles.poster} />
      {!reduceMotion && <VideoView player={player} nativeControls={false} contentFit={contentFit} style={styles.video} />}
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { position: 'absolute', inset: 0, width: '100%', height: '100%', overflow: 'hidden' },
  poster: { position: 'absolute', inset: 0, width: '100%', height: '100%' },
  video: { position: 'absolute', inset: 0, width: '100%', height: '100%' },
});
