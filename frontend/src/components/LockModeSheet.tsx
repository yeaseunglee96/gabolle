// 고정 방식 고르기 — 「순서 고정」·「시각 고정」 (S15P21E201-2017).
//
// 🔴 전에는 「고정」 하나였고, 앞 장소를 빼면 고정한 곳의 시각이 당겨졌다(12:47 → 10:14). 안내 문구는 떴지만 사용자는
//    「고정했는데 왜 움직이나」로 읽었다. 이제 둘로 나누고, 고르기 전에 무엇이 지켜지는지 한 줄씩 말한다.
import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { txf } from '@/i18n/format';
import { useI18n } from '@/i18n';
import type { ItineraryLockMode } from '@/plan/itinerary';

export function LockModeSheet({ visible, placeName, current, busy = false, onPick, onUnlock, onClose }: {
  visible: boolean;
  placeName: string;
  /** 지금 고정 방식. 고정이 아니면 null. */
  current: ItineraryLockMode | null;
  busy?: boolean;
  onPick: (mode: ItineraryLockMode) => void;
  onUnlock: () => void;
  onClose: () => void;
}) {
  const { tx } = useI18n();
  const options: { mode: ItineraryLockMode; title: string; body: string }[] = [
    {
      mode: 'ORDER',
      title: tx('순서 고정', 'Keep order'),
      body: tx('다시 계산해도 빠지지 않고 이 자리를 지켜요. 앞 일정이 바뀌면 시각은 함께 움직여요.', 'Stays in this spot when the day is recalculated. Its time moves with the stops before it.'),
    },
    {
      mode: 'TIME',
      title: tx('시각 고정', 'Keep time'),
      body: tx('시작 시각도 그대로 지켜요. 앞 장소를 빼거나 줄이면 그 사이는 자유 시간이 돼요.', 'Also keeps its start time. If you remove or shorten earlier stops, the gap becomes free time.'),
    },
  ];
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card} testID="lock-mode-sheet">
          <Text variant="title" weight="bold">{txf(tx, '\'%s\' 고정하기', 'Pin \'%s\'', placeName)}</Text>
          {options.map((option) => {
            const selected = current === option.mode;
            return (
              <Pressable
                key={option.mode}
                testID={`lock-mode-${option.mode}`}
                accessibilityRole="radio"
                accessibilityState={{ selected, disabled: busy }}
                disabled={busy}
                onPress={() => onPick(option.mode)}
                style={({ pressed }) => [styles.option, selected && styles.optionSelected, (pressed || busy) && styles.dim]}
              >
                <Text weight="bold" color={selected ? color.text.onAction : color.text.heading}>{option.title}</Text>
                <Text variant="caption" color={selected ? color.text.onDarkMuted : color.text.body}>{option.body}</Text>
              </Pressable>
            );
          })}
          <View style={styles.footer}>
            {current ? (
              <Pressable testID="lock-mode-unlock" accessibilityRole="button" disabled={busy} onPress={onUnlock} style={({ pressed }) => [styles.footerButton, (pressed || busy) && styles.dim]}>
                <Text weight="bold" color={color.state.danger}>{tx('고정 풀기', 'Unpin')}</Text>
              </Pressable>
            ) : <View />}
            <Pressable testID="lock-mode-close" accessibilityRole="button" onPress={onClose} style={({ pressed }) => [styles.footerButton, pressed && styles.dim]}>
              <Text weight="bold" color={color.brand.navy}>{tx('닫기', 'Close')}</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 420, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  option: { gap: spacing[1], padding: spacing[4], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  optionSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  footer: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  footerButton: { minHeight: 44, paddingHorizontal: spacing[3], justifyContent: 'center' },
  dim: { opacity: 0.6 },
});
