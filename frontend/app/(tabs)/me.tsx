import { Pressable, StyleSheet, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

function InfoRow({ label, value, onPress, disabled = false }: { label: string; value: string; onPress?: () => void; disabled?: boolean }) {
  return <Pressable accessibilityRole={onPress ? 'button' : undefined} accessibilityState={{ disabled }} disabled={disabled || !onPress} onPress={onPress} style={({ pressed }) => [styles.row, pressed && styles.rowPressed, disabled && styles.rowDisabled]}><Text weight="bold">{label}</Text><Text variant="caption" color={disabled ? color.text.muted : color.text.body}>{value}</Text></Pressable>;
}

export default function Me() {
  const { user, signOut } = useAuth();
  const { language, setLanguage, tx } = useI18n();
  return <View style={styles.shell}><Screen style={styles.screen}>
    <View style={styles.heading}><Text variant="caption" weight="bold" color={color.brand.orange}>MY PAGE</Text><Text variant="display" weight="bold">{tx('마이페이지', 'My page')}</Text></View>
    <View style={styles.profile}><View style={styles.avatar}><Text variant="title" weight="bold" color={color.text.onAction}>{(user?.displayName || '여행자').slice(0, 1)}</Text></View><View style={styles.profileCopy}><Text variant="title" weight="bold">{user?.displayName || tx('여행자', 'Traveler')}</Text><Text variant="caption" color={color.text.muted}>{user?.email || tx('계정 정보를 불러오지 못했어요', 'Account information is unavailable')}</Text></View></View>
    <View style={styles.group}>
      <InfoRow label={tx('언어', 'Language')} value={language === 'ko' ? '한국어' : 'English'} onPress={() => setLanguage(language === 'ko' ? 'en' : 'ko')} />
      <InfoRow label={tx('여행 조건 관리', 'Trip preferences')} value={tx('여행 만들기에서 수정', 'Edit while planning')} disabled />
      <InfoRow label={tx('연결 계정', 'Connected accounts')} value={tx('서버 기능 준비 중', 'Server feature pending')} disabled />
      <InfoRow label={tx('개인화 데이터 관리', 'Personalization data')} value={tx('서버 기능 준비 중', 'Server feature pending')} disabled />
    </View>
    <Text variant="caption" color={color.text.muted} style={styles.notice}>{tx('완료 여행·저장 장소·리뷰 수는 실제 조회 API가 연결된 뒤 표시합니다.', 'Trip, saved-place, and review counts will appear after their APIs are connected.')}</Text>
    <Button label={tx('로그아웃', 'Sign out')} variant="ghost" onPress={() => void signOut()} containerStyle={styles.logout} />
  </Screen><TabBar active="me" /></View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory }, screen: { flex: 1, backgroundColor: color.brand.ivory },
  heading: { gap: spacing[2], marginBottom: spacing[6] },
  profile: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  avatar: { width: 56, height: 56, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.orange },
  profileCopy: { flex: 1, gap: spacing[1] },
  group: { marginTop: spacing[4], overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card },
  row: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#e8e4dd' },
  rowPressed: { opacity: 0.7, backgroundColor: color.surface.tint }, rowDisabled: { opacity: 0.58 },
  notice: { marginTop: spacing[4], lineHeight: 20 }, logout: { marginTop: 'auto', marginBottom: spacing[4], borderColor: color.brand.orange },
});
