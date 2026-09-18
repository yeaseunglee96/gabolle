// 마이페이지 › 약관·고지 `(tabs)/me.tsx` 아래쪽에 있던 줄 넷을 그대로 옮겼다.
import { StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { InfoRow } from '@/me/InfoRow';
import { MyPageShell } from '@/me/MyPageShell';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

export default function MyPageTerms() {
  const router = useRouter();
  const { tx } = useI18n();
  const rows = [
    { path: '/legal/terms', ko: '이용약관', en: 'Terms of Service' },
    { path: '/legal/privacy', ko: '개인정보 처리방침', en: 'Privacy Policy' },
    { path: '/legal/open-source', ko: '오픈소스 고지', en: 'Open-source notices' },
    { path: '/legal/data-sources', ko: '공공데이터 출처', en: 'Public data sources' },
  ];
  return (
    <MyPageShell tab="terms" title={tx('약관·고지', 'Terms & notices')} description={tx('가볼래를 쓰실 때 적용되는 약관과, 이 앱이 쓰는 자료의 출처예요.', 'The terms that apply to Gabolle, and where the data in this app comes from.')}>
      <View style={styles.group}>
        {rows.map((row, index) => (
          <InfoRow key={row.path} label={tx(row.ko, row.en)} value="›" first={index === 0} onPress={() => router.push(row.path)} />
        ))}
      </View>
      <Text variant="caption" style={styles.note}>{tx('약관을 누르면 전문을 볼 수 있어요.', 'Open any item to read it in full.')}</Text>
    </MyPageShell>
  );
}

const styles = StyleSheet.create({
  group: { overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card },
  note: { marginTop: spacing[3] },
});
