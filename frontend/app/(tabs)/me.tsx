import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import { ApiClientError } from '@/api/client';
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
  const { user, signOut, updateProfile } = useAuth();
  const { language, tx } = useI18n();
  const [editing, setEditing] = useState(false);
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [profileLanguage, setProfileLanguage] = useState<'KO' | 'EN'>(language === 'ko' ? 'KO' : 'EN');
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{ danger: boolean; text: string } | null>(null);
  useEffect(() => { setDisplayName(user?.displayName ?? ''); setProfileLanguage(user?.language?.toUpperCase() === 'EN' ? 'EN' : 'KO'); }, [user]);
  const nameValid = displayName.trim().length >= 1 && displayName.trim().length <= 30;
  async function saveProfile() {
    if (!user || !nameValid || saving) return;
    setSaving(true);
    setFeedback(null);
    try {
      await updateProfile({ displayName: displayName.trim(), language: profileLanguage });
      setEditing(false);
      setFeedback({ danger: false, text: tx('프로필을 저장했어요.', 'Your profile was saved.') });
    } catch (cause) {
      setFeedback({ danger: true, text: cause instanceof ApiClientError ? cause.message : tx('프로필을 저장하지 못했어요.', 'Could not save your profile.') });
    } finally {
      setSaving(false);
    }
  }
  return <View style={styles.shell}><Screen scroll={editing} style={styles.screen}>
    <View style={styles.heading}><Text variant="caption" weight="bold" color={color.brand.orange}>MY PAGE</Text><Text variant="display" weight="bold">{tx('마이페이지', 'My page')}</Text></View>
    <View style={styles.profile}><View style={styles.avatar}><Text variant="title" weight="bold" color={color.text.onAction}>{(user?.displayName || '여행자').slice(0, 1)}</Text></View><View style={styles.profileCopy}><Text variant="title" weight="bold">{user?.displayName || tx('여행자', 'Traveler')}</Text><Text variant="caption" color={color.text.muted}>{user?.email || tx('계정 정보를 불러오지 못했어요', 'Account information is unavailable')}</Text><Text variant="caption" color={color.text.muted}>{tx('프로필 사진 변경은 서버 업로드 기능이 연결되면 제공해요.', 'Profile photos will be available after upload support is connected.')}</Text></View>{user && <Pressable accessibilityRole="button" accessibilityState={{ expanded: editing }} onPress={() => { setEditing((value) => !value); setFeedback(null); }} style={({ pressed }) => [styles.editButton, pressed && styles.rowPressed]}><Text variant="caption" weight="bold" color={color.brand.orange}>{editing ? tx('취소', 'Cancel') : tx('수정', 'Edit')}</Text></Pressable>}</View>
    {editing && <View style={styles.editPanel}>
      <Text variant="caption" weight="bold">{tx('표시 이름', 'Display name')}</Text>
      <TextInput accessibilityLabel={tx('표시 이름', 'Display name')} maxLength={30} value={displayName} onChangeText={setDisplayName} style={[styles.input, !nameValid && styles.inputError]} />
      <Text variant="caption" color={nameValid ? color.text.muted : color.state.danger}>{displayName.trim().length}/30{tx('자', ' characters')}</Text>
      <Text variant="caption" weight="bold">{tx('언어', 'Language')}</Text>
      <View accessibilityRole="radiogroup" style={styles.languageRow}>{(['KO', 'EN'] as const).map((value) => { const selected = profileLanguage === value; return <Pressable key={value} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => setProfileLanguage(value)} style={[styles.languageButton, selected && styles.languageSelected]}><Text weight="bold" color={selected ? color.text.onAction : color.text.heading}>{value === 'KO' ? '한국어' : 'English'}</Text></Pressable>; })}</View>
      <Button label={saving ? tx('저장 중…', 'Saving…') : tx('프로필 저장', 'Save profile')} disabled={!nameValid || saving} onPress={() => void saveProfile()} />
    </View>}
    {feedback && <View accessibilityRole="alert" style={[styles.feedback, feedback.danger && styles.feedbackDanger]}><Text variant="caption" weight="bold" color={feedback.danger ? color.state.danger : color.state.success}>{feedback.text}</Text></View>}
    <View style={styles.group}>
      <InfoRow label={tx('언어', 'Language')} value={language === 'ko' ? '한국어' : 'English'} disabled />
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
  editButton: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  editPanel: { gap: spacing[2], marginTop: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  input: { minHeight: 48, paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, color: color.text.heading, backgroundColor: color.brand.ivory },
  inputError: { borderColor: color.state.danger },
  languageRow: { flexDirection: 'row', gap: spacing[2], marginBottom: spacing[2] },
  languageButton: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md },
  languageSelected: { backgroundColor: color.brand.orange, borderColor: color.brand.orange },
  feedback: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.successBg },
  feedbackDanger: { backgroundColor: color.state.dangerBg },
  group: { marginTop: spacing[4], overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card },
  row: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#e8e4dd' },
  rowPressed: { opacity: 0.7, backgroundColor: color.surface.tint }, rowDisabled: { opacity: 0.58 },
  notice: { marginTop: spacing[4], lineHeight: 20 }, logout: { marginTop: 'auto', marginBottom: spacing[4], borderColor: color.brand.orange },
});
