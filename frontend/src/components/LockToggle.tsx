// 일정 한 곳을 «고정»하는 손잡이 — 글자로 적은 알약(UI 캔버스 ④).
//
// 🔴 전에는 🔓/🔒 그림 글자 하나였다. 열린 자물쇠가 «잠겨 있지 않다»인지 «누르면 잠근다»인지 안 읽혔고,
//    고정이 «다시 계산할 때 이 곳은 그대로 둔다»는 뜻이라는 것도 그림으로는 전해지지 않았다.
//    그래서 지금 상태를 말로 적는다 — 안 잡혔으면 「고정하기」(누를 것), 잡혔으면 「고정됨」(짙은 판).
//    핀 그림은 알림 화면의 「고정」 아이콘(NoticeIcon 'lock')과 같은 것이다.
//    `compact` 는 좁은 폰 카드용 — 안 잡힌 곳은 핀 동그라미만 둔다. 글자 알약이 제목 칸을 80 쯤 먹어
//    「12:41 · 130,000원」이 두 줄로 꺾였다. 잡힌 곳은 그대로 「고정됨」 글자다(상태는 글로 읽혀야 한다).
import { Pressable, StyleSheet, View } from 'react-native';

import { NoticeIcon } from '@/components/NoticeIcon';
import { Text } from '@/components/Text';
import { txf } from '@/i18n/format';
import { color, radius, spacing } from '@/design/tokens';

type Tx = (ko: string, en: string) => string;

export function LockToggle({ locked, name, busy = false, disabled = false, compact = false, onPress, tx }: {
  locked: boolean; name: string; busy?: boolean; disabled?: boolean; compact?: boolean;
  /** 없으면 누를 수 없는 표시로만 그린다 — 보기 전용이면 고정된 곳에만 「고정됨」. */
  onPress?: () => void; tx: Tx;
}) {
  const tint = locked ? color.text.onAction : color.text.body;
  const label = locked ? tx('고정됨', 'Locked') : tx('고정하기', 'Lock');
  const iconOnly = compact && !locked;
  const body = (
    <>
      <NoticeIcon kind="lock" tint={tint} size={iconOnly ? 16 : 14} />
      {iconOnly ? null : <Text variant="micro" weight="bold" color={tint} numberOfLines={1}>{busy ? '…' : label}</Text>}
    </>
  );
  if (!onPress) {
    return locked ? <View style={[styles.pill, styles.locked]}>{body}</View> : null;
  }
  const off = busy || disabled;
  return (
    // 누르는 칸은 44 높이, 보이는 알약은 그보다 작게 — 카드 제목 줄을 밀어내지 않는다.
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={locked ? txf(tx, '%s 고정 해제', 'Unlock %s', name) : txf(tx, '%s 고정', 'Lock %s', name)}
      accessibilityState={{ selected: locked, busy, disabled: off }}
      disabled={off}
      onPress={onPress}
      hitSlop={8}
      style={({ pressed }) => [styles.pill, locked ? styles.locked : styles.open, iconOnly && styles.round, (pressed || off) && styles.dim]}
    >
      {body}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  pill: { flexDirection: 'row', alignItems: 'center', gap: 4, minHeight: 30, paddingHorizontal: spacing[2], borderRadius: radius.full },
  open: { borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.canvas },
  locked: { backgroundColor: color.action.secondary },
  round: { width: 32, height: 32, minHeight: 32, paddingHorizontal: 0, justifyContent: 'center' },
  dim: { opacity: 0.6 },
});
