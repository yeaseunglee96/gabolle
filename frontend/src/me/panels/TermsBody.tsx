// 약관·고지 본문 — 화면(`app/me/terms.tsx`)과 마이페이지 패널이 같은 것을 쓴다.
import { StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { InfoRow } from '@/me/InfoRow';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

const ROWS = [
  { path: '/legal/terms', ko: '이용약관', en: 'Terms of Service' },
  { path: '/legal/privacy', ko: '개인정보 처리방침', en: 'Privacy Policy' },
  { path: '/legal/open-source', ko: '오픈소스 고지', en: 'Open-source notices' },
  { path: '/legal/data-sources', ko: '공공데이터 출처', en: 'Public data sources' },
];

export function TermsBody() {
  const router = useRouter();
  const { tx } = useI18n();
  return <>
    <View style={styles.group}>
      {ROWS.map((row, index) => (
        <InfoRow key={row.path} label={tx(row.ko, row.en)} value="›" first={index === 0} onPress={() => router.push(row.path)} />
      ))}
    </View>
    <Text variant="caption" style={styles.note}>{tx('약관을 누르면 전문을 볼 수 있어요.', 'Open any item to read it in full.')}</Text>
  </>;
}

const styles = StyleSheet.create({
  group: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card },
  note: { marginTop: spacing[3] },
});
