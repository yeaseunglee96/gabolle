// 「지금」 카드 — 어디로 가고 있고 얼마나 남았나 (시안 ⑤).
//
// 🔴 이 카드가 이 화면의 전부다. 일정표는 「오늘 무엇을 하나」를 말하지만 이 카드는
//    **「지금 무엇을 하고 있나」**를 말한다. 길 안내를 보는 사람이 실제로 읽는 것은 뒤쪽이다.
import { useEffect, useRef } from 'react';
import { Animated, Easing, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import type { ProgressStatus } from '@/plan/tripProgress';

export function NowCard({
  status,
  gpsUsable,
  title,
  detail,
  clock,
  driftText,
  progress,
  showManualArrival,
  onStart,
  onPause,
  onArrive,
  onSkip,
  tx,
}: {
  status: ProgressStatus;
  gpsUsable: boolean;
  /** 「동백섬 산책로로 이동 중」. */
  title: string;
  /** 「도보 400m · 약 6분 남음 · 해운대해변로 따라 직진」. 모르면 null. */
  detail: string | null;
  /** 「11:06」. */
  clock: string;
  /** 「예정보다 4분 빠름」. 모르면 null — 모르는 것을 「정시」로 적지 않는다. */
  driftText: string | null;
  /** 이 구간을 얼마나 왔나 (0~1). 모르면 null 이고 막대를 안 그린다. */
  progress: number | null;
  showManualArrival: boolean;
  onStart: () => void;
  onPause: () => void;
  onArrive: () => void;
  onSkip: () => void;
  tx: (ko: string, en: string) => string;
}) {
  const running = status === 'RUNNING';
  const pulse = useRef(new Animated.Value(0)).current;

  // 🔴 달릴 때만 맥이 뛴다. 멈췄는데 점이 계속 뛰면 「추적 중」으로 읽힌다.
  useEffect(() => {
    if (!running) { pulse.setValue(0); return; }
    const loop = Animated.loop(Animated.sequence([
      Animated.timing(pulse, { toValue: 1, duration: 800, easing: Easing.out(Easing.quad), useNativeDriver: true }),
      Animated.timing(pulse, { toValue: 0, duration: 800, easing: Easing.in(Easing.quad), useNativeDriver: true }),
    ]));
    loop.start();
    return () => loop.stop();
  }, [pulse, running]);

  const hint = status !== 'RUNNING'
    ? tx('출발을 누르면 위치 추적이 시작돼요', 'Tap start and we begin tracking your location')
    : gpsUsable
      ? tx('도착은 GPS로 자동 기록돼요', 'Arrivals are recorded automatically by GPS')
      : tx('GPS 신호가 약해요 · 직접 찍어 주세요', 'Weak GPS signal — please tap when you arrive');

  return (
    <View style={styles.card}>
      <View style={styles.top}>
        <View style={styles.live}>
          <View style={styles.dotWrap}>
            <Animated.View
              style={[styles.pulse, {
                opacity: pulse.interpolate({ inputRange: [0, 1], outputRange: [0.45, 0] }),
                transform: [{ scale: pulse.interpolate({ inputRange: [0, 1], outputRange: [1, 1.9] }) }],
              }]}
            />
            <View style={styles.dot} />
          </View>
          <Text variant="caption" weight="bold" color={color.action.primary}>
            {running ? tx('지금 · 위치 추적 중', 'Now · tracking') : tx('지금', 'Now')}
          </Text>
        </View>
        <Text variant="caption" color={color.text.onDarkMuted} numberOfLines={1}>
          {[clock, driftText].filter(Boolean).join(' · ')}
        </Text>
      </View>

      <Text variant="title" weight="bold" color={color.text.onAction} numberOfLines={2}>{title}</Text>
      {detail ? <Text color={color.text.onDarkMuted} numberOfLines={2}>{detail}</Text> : null}

      {/* 🔴 얼마나 왔는지 모르면 막대를 아예 안 그린다. 0% 짜리 막대는 「아직 출발도 안 했다」로
          읽히는데, 실제로는 모를 뿐이다. */}
      {progress !== null ? (
        <View style={styles.track}>
          <View style={[styles.fill, { width: `${Math.round(Math.max(0, Math.min(1, progress)) * 100)}%` }]} />
        </View>
      ) : null}

      <View style={styles.actions}>
        {running ? (
          <Pressable accessibilityRole="button" onPress={onPause} style={({ pressed }) => [styles.ghost, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('⏸ 일정 중지', '⏸ Pause')}</Text>
          </Pressable>
        ) : (
          <Pressable accessibilityRole="button" onPress={onStart} style={({ pressed }) => [styles.go, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('▶ 출발', '▶ Start')}</Text>
          </Pressable>
        )}

        {/* 🔴 「도착 찍기」는 GPS 가 약할 때만 나온다(인계 §10-4). 늘 보이면 사람은 그걸
            정상 절차로 알고 매번 누르게 되고, 자동 기록이 있으나 마나가 된다. */}
        {showManualArrival ? (
          <Pressable accessibilityRole="button" onPress={onArrive} style={({ pressed }) => [styles.outline, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('도착 찍기', 'I arrived')}</Text>
          </Pressable>
        ) : null}

        {running ? (
          <Pressable accessibilityRole="button" onPress={onSkip} style={({ pressed }) => [styles.ghost, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('건너뛰기', 'Skip')}</Text>
          </Pressable>
        ) : null}

        <Text variant="caption" color={color.text.onDarkMuted} numberOfLines={2} style={styles.hint}>{hint}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.navy },
  top: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  live: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  dotWrap: { width: 16, height: 16, alignItems: 'center', justifyContent: 'center' },
  dot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.state.dot },
  pulse: { position: 'absolute', width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.state.dot },

  track: { height: 6, borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.12)', overflow: 'hidden' },
  fill: { height: 6, borderRadius: radius.full, backgroundColor: color.state.dot },

  actions: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing[2], marginTop: spacing[1] },
  go: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.action.primary },
  ghost: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: 'rgba(255,255,255,0.16)' },
  outline: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.md, borderWidth: 1, borderColor: 'rgba(255,255,255,0.35)' },
  pressed: { opacity: 0.82 },
  hint: { flex: 1, minWidth: 140, textAlign: 'right' },
});
