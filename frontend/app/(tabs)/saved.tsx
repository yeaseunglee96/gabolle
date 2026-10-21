// 저장한 장소 — 본문은 마이페이지 「저장한 장소」 패널과 같은 것을 쓴다(src/me/panels/SavedPlacesBody.tsx).
// 두 벌로 두면 한쪽만 고쳐지고, 그 차이는 두 길로 들어가 나란히 봐야만 보인다(S15P21E201-1969).
import { StyleSheet, View } from 'react-native';

import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { color, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { SavedPlacesBody } from '@/me/panels/SavedPlacesBody';

export default function Saved() {
  const { tx } = useI18n();
  return <View style={styles.shell}><Screen scroll withTabBar style={styles.screen}>
    <View style={styles.heading}><Eyebrow>{tx('저장 목록', 'Saved')}</Eyebrow><Text variant="display" weight="bold">{tx('저장한 장소', 'Saved places')}</Text></View>
    <SavedPlacesBody />
  </Screen><TabBar active="saved" /></View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.canvas },
  screen: { flex: 1, backgroundColor: color.canvas },
  heading: { gap: spacing[2], marginBottom: spacing[6] },
});
