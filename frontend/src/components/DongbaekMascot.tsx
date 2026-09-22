import { useEffect, useRef } from 'react';
import { Animated, Easing, Image, type ImageStyle, type StyleProp } from 'react-native';

import { useI18n } from '@/i18n';

const sources = {
  idle: require('../../assets/mascot/dongbaek-idle.png'),
  open: require('../../assets/mascot/dongbaek-open.png'),
  thinking: require('../../assets/mascot/dongbaek-thinking.png'),
  // 우는 동백이 — 연결이 끊기거나 불러오지 못했을 때. 움직이지 않는다(실패 화면에서 까불면 안 된다).
  sad: require('../../assets/mascot/dongbaek-sad.png'),
} as const;

export type GabolleMascotState = keyof typeof sources;

export function GabolleMascot({ state = 'idle', style, delay = 0, still = false }: { state?: GabolleMascotState; style?: StyleProp<ImageStyle>; delay?: number; /** 움직이지 않는다 — 메뉴 행·목록처럼 「살아 있는 단추」가 아닌 자리. */ still?: boolean }) {
  const { tx } = useI18n();
  const y = useRef(new Animated.Value(0)).current;
  const rotate = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    y.setValue(0);
    rotate.setValue(0);
    if (state === 'sad' || still) return;
    const animation = state === 'idle'
      ? Animated.loop(Animated.sequence([
          Animated.timing(y, { toValue: -5, duration: 1200, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
          Animated.timing(y, { toValue: 0, duration: 1200, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
        ]))
      : state === 'open'
        ? Animated.sequence([
            Animated.delay(delay),
            Animated.timing(y, { toValue: -9, duration: 150, easing: Easing.out(Easing.quad), useNativeDriver: true }),
            Animated.spring(y, { toValue: 0, speed: 18, bounciness: 10, useNativeDriver: true }),
            Animated.loop(Animated.sequence([
              Animated.timing(y, { toValue: -5, duration: 1050, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
              Animated.timing(y, { toValue: 1, duration: 1050, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
            ])),
          ])
        : Animated.loop(Animated.sequence([
            Animated.timing(rotate, { toValue: -1, duration: 90, useNativeDriver: true }),
            Animated.timing(rotate, { toValue: 1, duration: 180, useNativeDriver: true }),
            Animated.timing(rotate, { toValue: 0, duration: 90, useNativeDriver: true }),
            Animated.delay(380),
          ]));

    animation.start();
    return () => animation.stop();
  }, [delay, rotate, state, still, y]);

  return (
    <Animated.View style={{ transform: [{ translateY: y }, { rotate: rotate.interpolate({ inputRange: [-1, 1], outputRange: ['-2deg', '2deg'] }) }] }}>
      <Image accessibilityLabel={state === 'idle' ? tx('가볼래 대기 중', 'Gabolle waiting') : state === 'open' ? tx('가볼래 반갑게 인사하는 중', 'Gabolle greeting warmly') : state === 'sad' ? tx('가볼래 속상해하는 중', 'Gabolle feeling sad') : tx('가볼래 답변을 준비하는 중', 'Gabolle preparing an answer')} source={sources[state]} resizeMode="contain" style={style} />
    </Animated.View>
  );
}
