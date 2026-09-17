// 여행 핵심 화면의 CTA_다음 · CTA_로그인 · 수정 등 전폭 버튼을 하나로 통일한다.
// 기본 primary 는 피그마 APP/12~13의 길찾기·안내 CTA와 같은 브랜드 네이비를 쓴다.
//
// kakao variant 는 04 로그인에 있었지만 로그인 수단이 Google 하나로 확정되면서 걷어냈다
// (카카오는 지도·리뷰 API 로만 쓴다). 나중에 다시 필요해지면 그때 복원한다.
//
// secondary·field 는 16~23(여행 준비 이후 화면들) 실측에서 추가했다 — 그 화면들의 전폭 CTA 가
// action.primary 와 다른 파랑(action.secondary·action.field)을 쓴다(tokens.ts 주석 참고).
import { useMemo, useRef } from 'react';
import { AccessibilityInfo, Animated, Pressable, StyleSheet, type GestureResponderEvent, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Text } from './Text';

// 🔴 accent(주황)·danger(빨강)는 2026-09-17 에 올라왔다. 없어서 생긴 결함을 고친 것이다.
//
// 화면들이 이 색을 낼 방법이 없어서 `containerStyle` 에 backgroundColor 를 줬는데, 그건
// **바깥 껍데기(Animated.View)** 에 붙는다. 안쪽 Pressable 은 자기 색(남색)을 그대로 그리므로
// **버튼 뒤에 더 크고 더 둥근 도형이 하나 더** 남았다 — 실기기에서 「남색 버튼 아래로 주황색이
// 삐져나온다」로 보였다. containerStyle 로 넘어간 26곳 중 색이 다른 6곳이 그랬다
// (남색 13곳은 안쪽과 같은 색이라 안 보였을 뿐 같은 결함이다).
type ButtonVariant = 'primary' | 'secondary' | 'field' | 'ghost' | 'accent' | 'danger';

export type ButtonProps = Omit<PressableProps, 'style'> & {
  label: string;
  variant?: ButtonVariant;
  /**
   * 온보딩·연령확인의 큰 알약 모양 CTA (높이 54 · 완전 둥근 모서리).
   *
   * 🔴 화면에서 `containerStyle` 로 흉내내지 않는다. 그러면 껍데기만 커지고 안쪽 버튼은
   * 그대로라 뒤로 삐져나온다 — 이 prop 이 생긴 이유가 그것이다.
   */
  pill?: boolean;
  /** 버튼 자체의 모양은 안 바꾸고, 화면에서 위아래 여백만 줄 때 쓴다(예: marginTop). */
  containerStyle?: StyleProp<ViewStyle>;
};

const LABEL_COLOR: Record<ButtonVariant, string> = {
  primary: color.text.onAction,
  secondary: color.text.onAction,
  field: color.text.onAction,
  ghost: color.brand.navy,
  accent: color.text.onAction,
  danger: color.text.onAction,
};

// 사용자 요청(2026-09-16, 토스 참고): 예전엔 눌림 스타일이 Pressable의 pressed 값으로 순간
// 전환됐다(스타일이 그 프레임에 바로 바뀜) — 토스 버튼 특유의 "살짝 부드럽게 눌리는" 느낌이
// 없었다. RN 내장 Animated로 스케일·투명도에 시간(duration)을 줘서 부드럽게 만든다.
// 🔴 reanimated(react-native-worklets)는 이 저장소 Jest 설정에 목(mock)이 없어 Button을
// 쓰는 화면 테스트가 통째로 깨진다 — 그래서 별도 설정이 필요 없는 RN 내장 Animated를 쓴다.
// 🔴 reduce-motion(기기의 "동작 줄이기" 접근성 설정)은 **앱 전체에 하나뿐인 값**이라
// 모듈에서 한 번만 묻는다. 버튼 인스턴스마다 useState+useEffect 로 물으면 버튼을 그릴 때마다
// 비동기 조회와 그 결과로 인한 setState 가 따라붙는다. 값은 누르는 순간에만 읽으므로 이 값이
// 바뀌어도 다시 그릴 필요가 없다 — 그래서 상태가 아니라 모듈 변수가 맞다.
//
// 🔴 다만 **이것이 느림의 주범은 아니었다.** 처음엔 그렇게 짚었는데 이 수정만으로는
// 739ms→726ms 로 거의 안 줄었다(이예승 님 재측정, 2026-09-16). 주범은 아래 보간 객체다.
// 틀린 진단을 지우지 않고 남겨 둔다 — 다음 사람이 같은 곳을 다시 파지 않게.
let reducedMotion = false;
void Promise.resolve(AccessibilityInfo.isReduceMotionEnabled?.())
  .then((value) => { reducedMotion = Boolean(value); })
  .catch(() => { /* 조회할 수 없는 환경이면 애니메이션을 그대로 둔다 */ });
AccessibilityInfo.addEventListener?.('reduceMotionChanged', (value) => { reducedMotion = Boolean(value); });

export function Button({ label, variant = 'primary', pill = false, disabled, containerStyle, accessibilityRole, accessibilityState, onPressIn, onPressOut, ...rest }: ButtonProps) {
  const pressProgress = useRef(new Animated.Value(0)).current;
  // 🔴 보간 객체를 렌더마다 새로 만들지 않는다 — 이것이 느림의 **진짜 주범**이었다.
  //
  // interpolate() 는 부를 때마다 새 애니메이션 노드를 만들어 pressProgress 에 붙인다. 버튼이
  // 다시 그려질 때마다 노드가 하나씩 더 붙고, 값이 바뀔 때 갱신해야 할 노드가 계속 늘어난다.
  // 설문 화면은 질문을 넘길 때마다 버튼을 다시 그리므로 그게 누적된다.
  //
  // pressProgress 는 useRef 라 절대 안 바뀌므로 보간 노드는 버튼당 한 번만 만들어진다.
  // 애니메이션 동작과 느낌은 전혀 바뀌지 않는다.
  //
  // 실측 (이예승 님, 같은 기계·같은 명령, SpendProfileScreen 의 그 테스트):
  //   애니메이션 전 190ms · 애니메이션 추가 739ms · reduce-motion 모듈로 올림 726ms
  //   · 여기에 이 useMemo 까지 326ms
  const animatedStyle = useMemo(() => ({
    opacity: pressProgress.interpolate({ inputRange: [0, 1], outputRange: [1, 0.82] }),
    transform: [{ scale: pressProgress.interpolate({ inputRange: [0, 1], outputRange: [1, 0.98] }) }],
  }), [pressProgress]);
  const animateTo = (value: number, duration: number) => {
    Animated.timing(pressProgress, { toValue: value, duration: reducedMotion ? 0 : duration, useNativeDriver: true }).start();
  };
  const handlePressIn = (event: GestureResponderEvent) => { animateTo(1, 90); onPressIn?.(event); };
  const handlePressOut = (event: GestureResponderEvent) => { animateTo(0, 150); onPressOut?.(event); };

  return (
    <Animated.View style={[containerStyle, !disabled && animatedStyle]}>
      <Pressable
        {...rest}
        accessibilityRole={accessibilityRole ?? 'button'}
        accessibilityState={{ ...accessibilityState, disabled: Boolean(disabled) }}
        disabled={disabled}
        onPressIn={handlePressIn}
        onPressOut={handlePressOut}
        style={[
          styles.base,
          variant === 'primary' && styles.primary,
          variant === 'secondary' && styles.secondary,
          variant === 'field' && styles.field,
          variant === 'ghost' && styles.ghost,
          variant === 'accent' && styles.accent,
          variant === 'danger' && styles.danger,
          // pill 은 색 뒤에 둔다 — 모양(높이·모서리)만 덮어쓰고 색은 건드리지 않는다.
          pill && styles.pill,
          disabled && styles.disabled,
        ]}
      >
        <Text variant="body" weight="bold" color={LABEL_COLOR[variant]} style={styles.label}>
          {label}
        </Text>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  base: {
    width: '100%',
    minHeight: 48,
    borderRadius: radius.md,
    paddingVertical: spacing[3],
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: { textAlign: 'center' },
  primary: {
    backgroundColor: color.brand.navy,
  },
  secondary: {
    backgroundColor: color.action.secondary,
  },
  field: {
    backgroundColor: color.action.field,
  },
  ghost: {
    backgroundColor: color.surface.card,
    borderWidth: 1,
    borderColor: color.surface.field,
  },
  accent: {
    backgroundColor: color.brand.orange,
  },
  danger: {
    backgroundColor: color.state.danger,
  },
  pill: {
    minHeight: 54,
    borderRadius: radius.full,
  },
  disabled: {
    opacity: 0.4,
  },
});
