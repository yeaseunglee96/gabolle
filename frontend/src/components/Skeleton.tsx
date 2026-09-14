// 로딩 중에 스피너 하나만 보여주는 대신, 곧 나올 내용의 모양을 미리 보여준다.
// 체감 속도가 빨라지고 화면이 덜 휑해 보인다 (moti·react-native-skeleton-placeholder 가
// 쓰는 것과 같은 발상 — 다만 새 의존성 없이 이미 있는 reanimated 만으로 만든다).
import { useEffect } from 'react';
import { type DimensionValue, type StyleProp, type ViewStyle } from 'react-native';
import Animated, { Easing, cancelAnimation, useAnimatedStyle, useReducedMotion, useSharedValue, withRepeat, withSequence, withTiming } from 'react-native-reanimated';

import { color, radius } from '@/design/tokens';

type SkeletonProps = {
  width?: DimensionValue;
  height?: DimensionValue;
  radius?: number;
  style?: StyleProp<ViewStyle>;
};

export function Skeleton({ width = '100%', height = 16, radius: cornerRadius = radius.sm, style }: SkeletonProps) {
  const reducedMotion = useReducedMotion();
  const opacity = useSharedValue(reducedMotion ? 0.7 : 0.5);

  useEffect(() => {
    if (reducedMotion) { opacity.value = 0.7; return; }
    opacity.value = withRepeat(
      withSequence(
        withTiming(1, { duration: 700, easing: Easing.inOut(Easing.ease) }),
        withTiming(0.5, { duration: 700, easing: Easing.inOut(Easing.ease) }),
      ),
      -1,
      false,
    );
    // reducedMotion 이 도중에 켜지거나 컴포넌트가 사라질 때 반복 애니메이션을 멈춘다.
    // 안 멈추면 화면에서 지운 뒤에도 UI 스레드에서 계속 돈다(AI 리뷰 지적).
    return () => cancelAnimation(opacity);
    // opacity 는 useSharedValue 참조라 렌더마다 안 바뀐다(ref와 같다) — 의존성에
    // 넣으면 "값이 바뀌면 다시 돈다" 는 뜻으로 읽혀 오해를 부른다. reducedMotion 이
    // 바뀔 때만 다시 돌면 된다.
  }, [reducedMotion]);

  const animatedStyle = useAnimatedStyle(() => ({ opacity: opacity.value }));

  return (
    <Animated.View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[{ width, height, borderRadius: cornerRadius, backgroundColor: color.surface.field }, animatedStyle, style]}
    />
  );
}
