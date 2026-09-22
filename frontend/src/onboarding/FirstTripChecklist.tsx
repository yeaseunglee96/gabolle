// 동백이 첫 여행 체크리스트 — 시안 5 의 01d. 온보딩을 거친 사람의 홈 맨 위에 뜬다.
//
// 코치마크가 「어디서 시작하나」를 말했다면 이 카드는 「그다음 셋」을 말한다 — 여행 만들기 ·
// 동행 초대 · 첫 기록. 하나 할 때마다 ✓, 셋 다 하면 사라진다. 닫기(X)도 있다 — 안 하고
// 싶은 사람에게 매일 보이는 카드는 잔소리다.
//
// 「첫 여행 만들기」는 서버가 아는 사실(내 여행이 있다)로도 ✓ 가 된다 — 기기 표시가 없어도
// 여행이 있으면 한 것이다.
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import Svg, { Path } from 'react-native-svg';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import type { ChecklistState, ChecklistStep } from '@/onboarding/firstRun';

const mascot = require('../../assets/mascot/dongbaek-idle.png');

function Check() {
  return <Svg width={15} height={15} viewBox="0 0 24 24" fill="none"><Path d="M5 12.5l4.5 4.5L19 7.5" stroke={color.text.onAction} strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" /></Svg>;
}
function Chevron() {
  return <Svg width={16} height={16} viewBox="0 0 24 24" fill="none"><Path d="M9 5l7 7-7 7" stroke={color.text.muted} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" /></Svg>;
}
function Close() {
  return <Svg width={18} height={18} viewBox="0 0 24 24" fill="none"><Path d="M6 6l12 12M18 6L6 18" stroke={color.text.muted} strokeWidth={2} strokeLinecap="round" /></Svg>;
}

export function FirstTripChecklist({ state, hasTrip, onDismiss }: { state: ChecklistState; hasTrip: boolean; onDismiss: () => void }) {
  const router = useRouter();
  const { tx } = useI18n();
  const done: Record<ChecklistStep, boolean> = { ...state.done, trip: state.done.trip || hasTrip };
  const steps: Array<{ key: ChecklistStep; label: string; sub: string; href: string }> = [
    { key: 'trip', label: tx('첫 여행 만들기', 'Plan your first trip'), sub: tx('출발지·날짜·인원만 고르면 시작이에요', 'Just pick where you start, dates and who is coming'), href: '/plan' },
    { key: 'invite', label: tx('동행 초대하기', 'Invite a companion'), sub: tx('링크 하나면 같이 보고 고칠 수 있어요', 'One link — plan and edit it together'), href: '/trips' },
    { key: 'story', label: tx('첫 기록 남기기', 'Post your first record'), sub: tx('여행 중 사진 한 장, 한 줄이면 돼요', 'A photo and one line from the trip is enough'), href: '/feed/compose' },
  ];
  const doneCount = steps.filter((step) => done[step.key]).length;
  if (!state.active || state.dismissed || doneCount === steps.length) return null;
  const remaining = steps.length - doneCount;

  return (
    <View style={styles.card}>
      <View style={styles.head}>
        <Image source={mascot} resizeMode="contain" style={styles.mascot} accessibilityIgnoresInvertColors />
        <View style={styles.headCopy}>
          <Text variant="micro" weight="bold" color={color.text.eyebrow}>{tx('동백이의 첫 여행 준비', 'Dongbaek’s first-trip checklist')}</Text>
          <Text variant="title" weight="bold">
            {doneCount === 0
              ? tx('세 가지만 하면 준비 끝', 'Three steps and you’re set')
              : tx(`${doneCount}개 했어요 · ${remaining}개 남았어요`, `${doneCount} done · ${remaining} to go`)}
          </Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('체크리스트 닫기', 'Close checklist')} hitSlop={8} onPress={onDismiss} style={({ pressed }) => [styles.close, pressed && styles.pressed]}><Close /></Pressable>
      </View>
      <View style={styles.bar}><View style={[styles.barFill, { width: `${Math.round((doneCount / steps.length) * 100)}%` }]} /></View>
      {steps.map((step, index) => {
        const isDone = done[step.key];
        return (
          <Pressable
            key={step.key}
            accessibilityRole="button"
            accessibilityState={{ checked: isDone }}
            disabled={isDone}
            onPress={() => router.push(step.href as never)}
            style={({ pressed }) => [styles.row, index > 0 && styles.rowBorder, pressed && styles.pressed]}
          >
            <View style={[styles.dot, isDone && styles.dotDone]}>{isDone ? <Check /> : <View style={styles.dotInner} />}</View>
            <View style={styles.rowCopy}>
              <Text variant="body" weight="bold" color={isDone ? color.text.muted : color.text.heading} style={isDone && styles.strike}>{step.label}</Text>
              <Text variant="caption" color={color.text.muted}>{step.sub}</Text>
            </View>
            {isDone ? null : <Chevron />}
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  // 홈의 다른 카드와 같은 문법(선·그림자 없음) — 진행 중 링만 붉게, tokens.ts 의 예외 그대로.
  card: { marginHorizontal: spacing[4], marginBottom: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 2, borderColor: color.action.outline, gap: spacing[2] },
  head: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  mascot: { width: 44, height: 44 },
  headCopy: { flex: 1, gap: 2 },
  close: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  bar: { height: 5, borderRadius: radius.full, backgroundColor: color.surface.soft, overflow: 'hidden' },
  barFill: { height: '100%', borderRadius: radius.full, backgroundColor: color.state.success },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingVertical: spacing[2] },
  rowBorder: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border },
  rowCopy: { flex: 1, gap: 1 },
  dot: { width: 26, height: 26, borderRadius: radius.full, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' },
  dotDone: { backgroundColor: color.state.success },
  dotInner: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.surface.field },
  strike: { textDecorationLine: 'line-through' },
  pressed: { opacity: 0.75 },
});
