// 내 기록을 못 불러왔을 때 — 「다시 시도」와 함께 (S15P21E201-1681).
//
// 마이페이지 폰·넓은 화면 두 자리가 같이 쓴다. 전에는 「기록을 불러오지 못했어요.」 한 줄만 있어서 다시 할 길이 없었고,
// 그 줄이 위의 새 기록 카드에 바짝 붙어 있었다. 못 불러온 것을 「없다」로 바꾸지 않는 것은 그대로다.
import { StyleSheet, View } from 'react-native';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, spacing } from '@/design/tokens';

type Tx = (ko: string, en: string) => string;

export function RecordsLoadFailed({ onRetry, tx }: { onRetry: () => void; tx: Tx }) {
  return (
    <View style={styles.row}>
      <Text variant="caption" color={color.text.muted} style={styles.copy}>{tx('기록을 불러오지 못했어요.', "We couldn't load your records.")}</Text>
      <Button label={tx('다시 시도', 'Try again')} variant="tertiary" compact onPress={onRetry} />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { marginTop: spacing[3], flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  copy: { flexShrink: 1 },
});
