import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { loadSavedTrips } from '@/trip/tripLibrary';

function InfoRow({ label, value, onPress, disabled = false }: { label: string; value: string; onPress?: () => void; disabled?: boolean }) {
  return <Pressable accessibilityRole={onPress ? 'button' : undefined} accessibilityState={{ disabled }} disabled={disabled || !onPress} onPress={onPress} style={({ pressed }) => [styles.row, pressed && styles.rowPressed, disabled && styles.rowDisabled]}><Text weight="bold">{label}</Text><Text variant="caption" color={disabled ? color.text.muted : color.text.body}>{value}</Text></Pressable>;
}

export default function Me() {
  const { user, signOut, updateProfile, deleteAccount } = useAuth();
  const { language, tx } = useI18n();
  const [editing, setEditing] = useState(false);
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [profileLanguage, setProfileLanguage] = useState<'KO' | 'EN'>(language === 'ko' ? 'KO' : 'EN');
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{ danger: boolean; text: string } | null>(null);
  const [deleteStep, setDeleteStep] = useState<0 | 1 | 2>(0);
  const [savedTripCount, setSavedTripCount] = useState(0);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
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
  async function openDeletion() {
    setSavedTripCount((await loadSavedTrips()).length);
    setDeletePassword('');
    setDeleteError(null);
    setDeleteStep(1);
  }
  function closeDeletion() { if (!deleting) { setDeleteStep(0); setDeletePassword(''); setDeleteError(null); } }
  async function confirmDeletion() {
    if (!deletePassword || deleting) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteAccount(deletePassword);
    } catch (cause) {
      const incorrect = cause instanceof ApiClientError && (cause.status === 401 || cause.code === 'INVALID_CREDENTIALS');
      setDeleteError(incorrect ? tx('비밀번호가 올바르지 않아요. 다시 입력해 주세요.', 'The password is incorrect. Try again.') : cause instanceof ApiClientError ? cause.message : tx('계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not delete the account. Try again later.'));
      setDeleting(false);
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
    <View style={styles.dangerZone}><Text variant="caption" weight="bold" color={color.state.danger}>{tx('계정 관리', 'Account')}</Text><Text variant="caption" color={color.text.body}>{tx('계정과 개인 데이터를 영구적으로 삭제할 수 있어요.', 'Permanently delete your account and personal data.')}</Text><Pressable accessibilityRole="button" onPress={() => void openDeletion()} style={({ pressed }) => [styles.deleteEntry, pressed && styles.rowPressed]}><Text weight="bold" color={color.state.danger}>{tx('계정 삭제', 'Delete account')}</Text><Text variant="title" color={color.state.danger}>›</Text></Pressable></View>
  </Screen><TabBar active="me" />
    <Modal visible={deleteStep > 0} transparent animationType="fade" onRequestClose={closeDeletion}>
      <View style={styles.modalBackdrop}><View accessibilityViewIsModal style={styles.modalCard}>
        {deleteStep === 1 ? <>
          <Text variant="caption" weight="bold" color={color.state.danger}>{tx('1 / 2 · 삭제 내용 확인', '1 / 2 · Review deletion')}</Text>
          <Text variant="display" weight="bold">{tx('삭제되는 내용을 확인해 주세요', 'Review what will be deleted')}</Text>
          <View style={styles.impactList}>
            <View style={styles.impactRow}><Text variant="title" weight="bold" color={color.text.accent}>{savedTripCount}</Text><Text style={styles.impactCopy}>{tx('이 기기에 저장된 여행과 서버의 일정·추천 데이터가 삭제돼요.', 'Saved trips on this device and server itinerary data will be deleted.')}</Text></View>
            <View style={styles.impactRow}><Text variant="title" weight="bold" color={color.text.accent}>0</Text><Text style={styles.impactCopy}>{tx('현재 기록 기능이 연결되지 않아 삭제할 여행 기록은 없어요.', 'Travel records are not connected yet, so there are no records to delete.')}</Text></View>
          </View>
          <View style={styles.reviewNotice}><Text weight="bold">{tx('리뷰는 익명으로 남아요', 'Reviews remain anonymous')}</Text><Text variant="caption" color={color.text.body}>{tx('리뷰 기능이 연결되면 작성자 정보만 제거하고 내용은 익명으로 유지해요.', 'When reviews are connected, author details are removed while content remains anonymous.')}</Text></View>
          <Text accessibilityRole="alert" weight="bold" color={color.state.danger}>{tx('계정 삭제는 되돌릴 수 없습니다.', 'Account deletion cannot be undone.')}</Text>
          <View style={styles.modalActions}><Button label={tx('취소', 'Cancel')} variant="ghost" onPress={closeDeletion} containerStyle={styles.modalAction} /><Button label={tx('계속', 'Continue')} onPress={() => setDeleteStep(2)} containerStyle={styles.modalAction} /></View>
        </> : <>
          <Text variant="caption" weight="bold" color={color.state.danger}>{tx('2 / 2 · 본인 확인', '2 / 2 · Verify identity')}</Text>
          <Text variant="display" weight="bold">{tx('비밀번호를 다시 입력해 주세요', 'Enter your password again')}</Text>
          <Text color={color.text.body}>{tx('비밀번호가 맞아야 계정과 데이터가 삭제됩니다.', 'Your account is deleted only after the password is verified.')}</Text>
          <TextInput accessibilityLabel={tx('계정 삭제 확인 비밀번호', 'Password to confirm account deletion')} secureTextEntry autoFocus value={deletePassword} onChangeText={(value) => { setDeletePassword(value); setDeleteError(null); }} onSubmitEditing={() => void confirmDeletion()} placeholder={tx('비밀번호', 'Password')} placeholderTextColor={color.text.muted} style={[styles.input, deleteError && styles.inputError]} />
          {deleteError ? <Text accessibilityRole="alert" color={color.state.danger}>{deleteError}</Text> : null}
          <View style={styles.modalActions}><Button label={tx('이전', 'Back')} variant="ghost" disabled={deleting} onPress={() => { setDeleteStep(1); setDeleteError(null); }} containerStyle={styles.modalAction} /><Pressable accessibilityRole="button" accessibilityState={{ disabled: !deletePassword || deleting }} disabled={!deletePassword || deleting} onPress={() => void confirmDeletion()} style={[styles.deleteConfirm, (!deletePassword || deleting) && styles.deleteConfirmDisabled]}><Text weight="bold" color={color.text.onAction}>{tx('계정 영구 삭제', 'Delete permanently')}</Text></Pressable></View>
        </>}
      </View></View>
    </Modal>
    {deleting ? <View accessibilityRole="alert" accessibilityLiveRegion="assertive" style={styles.blockingOverlay}><ActivityIndicator size="large" color={color.brand.orange} /><Text variant="title" weight="bold" color={color.text.onAction}>{tx('계정과 데이터를 삭제하고 있어요', 'Deleting your account and data')}</Text><Text color={color.text.onAction}>{tx('완료될 때까지 화면을 닫지 마세요.', 'Keep this screen open until deletion finishes.')}</Text></View> : null}
  </View>;
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
  dangerZone: { gap: spacing[2], marginBottom: spacing[4], padding: spacing[4], borderWidth: 1, borderColor: color.state.dangerBg, borderRadius: radius.lg, backgroundColor: color.state.dangerBg },
  deleteEntry: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card },
  modalBackdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  modalCard: { width: '100%', maxWidth: 560, gap: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  impactList: { gap: spacing[2] }, impactRow: { minHeight: 64, flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, impactCopy: { flex: 1 },
  reviewNotice: { gap: spacing[1], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },
  modalActions: { flexDirection: 'row', gap: spacing[2] }, modalAction: { flex: 1 },
  deleteConfirm: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.state.danger }, deleteConfirmDisabled: { opacity: 0.4 },
  blockingOverlay: { ...StyleSheet.absoluteFill, zIndex: 100, alignItems: 'center', justifyContent: 'center', gap: spacing[3], padding: spacing[6], backgroundColor: color.brand.navy },
});
