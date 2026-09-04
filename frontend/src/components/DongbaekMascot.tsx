import { useEffect, useRef } from 'react';
import { Animated, Easing, Image, type ImageStyle, type StyleProp } from 'react-native';

const sources = {
  idle: require('../../assets/mascot/dongbaek-idle.png'),
  open: require('../../assets/mascot/dongbaek-open.png'),
  thinking: require('../../assets/mascot/dongbaek-thinking.png'),
} as const;

export type DongbaekState = keyof typeof sources;

export function DongbaekMascot({ state = 'idle', style }: { state?: DongbaekState; style?: StyleProp<ImageStyle> }) {
  const y = useRef(new Animated.Value(0)).current;
  const rotate = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    y.setValue(0);
    rotate.setValue(0);
    const animation = state === 'idle'
      ? Animated.loop(Animated.sequence([
          Animated.timing(y, { toValue: -5, duration: 1200, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
          Animated.timing(y, { toValue: 0, duration: 1200, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
        ]))
      : state === 'open'
        ? Animated.sequence([
            Animated.timing(y, { toValue: -9, duration: 150, easing: Easing.out(Easing.quad), useNativeDriver: true }),
            Animated.spring(y, { toValue: 0, speed: 18, bounciness: 10, useNativeDriver: true }),
          ])
        : Animated.loop(Animated.sequence([
            Animated.timing(rotate, { toValue: -1, duration: 90, useNativeDriver: true }),
            Animated.timing(rotate, { toValue: 1, duration: 180, useNativeDriver: true }),
            Animated.timing(rotate, { toValue: 0, duration: 90, useNativeDriver: true }),
            Animated.delay(380),
          ]));

    animation.start();
    return () => animation.stop();
  }, [rotate, state, y]);

  return (
    <Animated.View style={{ transform: [{ translateY: y }, { rotate: rotate.interpolate({ inputRange: [-1, 1], outputRange: ['-2deg', '2deg'] }) }] }}>
      <Image accessibilityLabel={`동백이 ${state === 'idle' ? '대기 중' : state === 'open' ? '반갑게 인사하는 중' : '답변을 준비하는 중'}`} source={sources[state]} resizeMode="contain" style={style} />
    </Animated.View>
  );
}
