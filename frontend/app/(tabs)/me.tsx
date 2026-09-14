import { useEffect, useState } from 'react';
import { ActivityIndicator, Image, Modal, Pressable, StyleSheet, TextInput, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as ImagePicker from 'expo-image-picker';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { getAccountDeletionPreview, type AccountDeletionPreview, type OAuthProvider } from '@/auth/authApi';
import { linkOAuthProvider } from '@/auth/oauth';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { SocialProviderIcon } from '@/components/SocialProviderIcon';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { Toggle } from '@/components/Toggle';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useBehaviorConsent } from '@/personalization/behaviorConsent';
import { usePlan } from '@/plan/PlanProvider';

// 박재현 님 계약(S15P21E201-837) — 서버가 대소문자·앞뒤 공백까지 정확히 이 값과 비교한다.
// 언어별로 문구를 바꾸면 서버가 어느 언어인지 판정해야 해서, 화면은 안내만 각 언어로 하고
// 실제로 보내는 값은 이 하나로 고정한다.
const DELETE_CONFIRMATION_PHRASE = 'DELETE';

function InfoRow({ label, value, onPress, disabled = false }: { label: string; value: string; onPress?: () => void; disabled?: boolean }) {
  return <Pressable accessibilityRole={onPress ? 'button' : undefined} accessibilityState={{ disabled }} disabled={disabled || !onPress} onPress={onPress} style={({ pressed }) => [styles.row, pressed && styles.rowPressed, disabled && styles.rowDisabled]}><Text weight="bold">{label}</Text><Text variant="caption" color={disabled ? color.text.muted : color.text.body}>{value}</Text></Pressable>;
}

// 처음 켜는 자리는 첫 체크인 화면이고, 여기는 **언제든 끄는 자리**다. 끄는 길이 설정 안쪽
// 어딘가에만 있으면 사용자는 못 찾고, 못 찾으면 켠 적 없는 사람처럼 취급된다.
function ConsentRow({ label, description, value, onValueChange }: { label: string; description: string; value: boolean; onValueChange: (next: boolean) => void }) {
  return <View style={styles.consentRow}><View style={styles.consentCopy}><Text weight="bold">{label}</Text><Text variant="caption" color={color.text.muted}>{description}</Text></View><Toggle value={value} onValueChange={onValueChange} /></View>;
}

// 구글·카카오 아이콘은 그 자체가 다색이라 흰 바탕에 보이지만, 애플·네이버 아이콘은
// sign-in.tsx의 브랜드색 버튼 위에 놓일 흰색 그림이라(SocialProviderIcon.tsx) 이 화면의
// 흰 배경에서는 흰색 위에 흰색이 되어 안 보인다. 그 둘만 브랜드색 배지를 뒤에 깔아 준다.
const SOCIAL_BADGE_BG: Partial<Record<OAuthProvider, string>> = { apple: '#000000', naver: '#03c75a' };

// S15P21E201-832 — 연결 여부를 서버가 목록으로 돌려주지 않아(그런 조회 API가 없다) "연결됨"
// 배지는 못 띄운다. 눌렀을 때 결과(연결됨/이미 다른 계정에 연결됨)만 그 자리에서 보여준다 —
// 없는 상태를 지어내지 않는다.
function SocialLinkRow({ provider, name, busy, onPress }: { provider: OAuthProvider; name: string; busy: boolean; onPress: () => void }) {
  const { tx } = useI18n();
  const badgeBg = SOCIAL_BADGE_BG[provider];
  return <Pressable accessibilityRole="button" accessibilityLabel={tx(`${name} 계정 연결하기`, `Connect ${name} account`)} accessibilityState={{ disabled: busy }} disabled={busy} onPress={onPress} style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}>
    <View style={styles.socialLabel}><View style={[styles.socialLinkMark, badgeBg ? { backgroundColor: badgeBg } : null]}><SocialProviderIcon provider={provider} /></View><Text weight="bold">{name}</Text></View>
    <Text variant="caption" color={color.text.body}>{busy ? tx('연결하는 중…', 'Connecting…') : tx('연결하기', 'Connect')}</Text>
  </Pressable>;
}

export default function Me() {
  const router = useRouter();
  const { preview } = useLocalSearchParams<{ preview?: string }>();
  const { user, accessToken, signOut, updateProfile, deleteAccount } = useAuth();
  const { language, tx } = useI18n();
  const plan = usePlan();
  const { enabled: behaviorPersonalization, setEnabled: setBehaviorPersonalization } = useBehaviorConsent();
  const visualPreview = __DEV__ && preview === 'ui';
  const profileOwner = user?.userId ?? (visualPreview ? 'preview' : null);
  const [editing, setEditing] = useState(visualPreview);
  const [displayName, setDisplayName] = useState(user?.displayName ?? (visualPreview ? '진미리' : ''));
  const [avatarUri, setAvatarUri] = useState<string | null>(null);
  const [pickingAvatar, setPickingAvatar] = useState(false);
  const [profileLanguage, setProfileLanguage] = useState<'KO' | 'EN'>(language === 'ko' ? 'KO' : 'EN');
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{ danger: boolean; text: string } | null>(null);
  const [deleteStep, setDeleteStep] = useState<0 | 1 | 2>(0);
  const [deletionPreview, setDeletionPreview] = useState<AccountDeletionPreview | null>(null);
  const [deleteConfirmation, setDeleteConfirmation] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [linkingProvider, setLinkingProvider] = useState<OAuthProvider | null>(null);
  const [linkFeedback, setLinkFeedback] = useState<{ danger: boolean; text: string } | null>(null);
  useEffect(() => { setDisplayName(user?.displayName ?? (visualPreview ? '진미리' : '')); setProfileLanguage(user?.language?.toUpperCase() === 'EN' ? 'EN' : 'KO'); }, [user, visualPreview]);
  useEffect(() => {
    if (!profileOwner) { setAvatarUri(null); return; }
    void AsyncStorage.getItem(`gabolle:profile-avatar:${profileOwner}`).then(setAvatarUri);
  }, [profileOwner]);
  const nameValid = displayName.trim().length >= 1 && displayName.trim().length <= 30;
  async function chooseAvatar() {
    if (!profileOwner || pickingAvatar) return;
    setPickingAvatar(true);
    setFeedback(null);
    try {
      const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 0.55, base64: true });
      if (result.canceled) return;
      const asset = result.assets[0];
      const nextUri = asset.base64 ? `data:${asset.mimeType ?? 'image/jpeg'};base64,${asset.base64}` : asset.uri;
      await AsyncStorage.setItem(`gabolle:profile-avatar:${profileOwner}`, nextUri);
      setAvatarUri(nextUri);
      setFeedback({ danger: false, text: tx('프로필 사진을 이 기기에 저장했어요.', 'Your profile photo was saved on this device.') });
    } catch {
      setFeedback({ danger: true, text: tx('사진을 불러오지 못했어요. JPG, PNG 또는 WebP 파일을 선택해 주세요.', 'Could not load the photo. Choose a JPG, PNG, or WebP file.') });
    } finally {
      setPickingAvatar(false);
    }
  }
  async function removeAvatar() {
    if (!profileOwner) return;
    await AsyncStorage.removeItem(`gabolle:profile-avatar:${profileOwner}`);
    setAvatarUri(null);
    setFeedback({ danger: false, text: tx('기본 프로필로 돌아왔어요.', 'Your default profile was restored.') });
  }
  async function saveProfile() {
    if ((!user && !visualPreview) || !nameValid || saving) return;
    setSaving(true);
    setFeedback(null);
    try {
      if (user) await updateProfile({ displayName: displayName.trim(), language: profileLanguage });
      setEditing(false);
      setFeedback({ danger: false, text: visualPreview && !user ? tx('미리보기에서 변경 모습을 확인했어요.', 'Preview changes are displayed.') : tx('프로필을 저장했어요.', 'Your profile was saved.') });
    } catch (cause) {
      setFeedback({ danger: true, text: cause instanceof ApiClientError ? cause.message : tx('프로필을 저장하지 못했어요.', 'Could not save your profile.') });
    } finally {
      setSaving(false);
    }
  }
  // S15P21E201-832 — 웹에서는 linkOAuthProvider가 현재 페이지를 제공자 화면으로 그대로
  // 넘긴다(S15P21E201-830과 같은 방식). 이 아래는 실행되지 않고, 결과는 착지 화면
  // (oauth/[provider]/callback.tsx)이 보여준 뒤 "설정으로 돌아가기"로 이 화면에 돌아온다.
  // 네이티브(앱)에서는 그 왕복 없이 여기서 바로 결과를 받는다.
  async function connectProvider(provider: OAuthProvider) {
    if (!accessToken || linkingProvider) return;
    setLinkingProvider(provider);
    setLinkFeedback(null);
    try {
      const result = await linkOAuthProvider(provider, accessToken, '/me');
      if (result.status === 'TAKEN') {
        setLinkFeedback({ danger: true, text: tx('이미 다른 계정에 연결된 소셜 계정이에요.', 'This social account is already connected to a different account.') });
      } else {
        setLinkFeedback({ danger: false, text: result.alreadyLinked ? tx('이미 연결되어 있어요.', 'Already connected.') : tx('계정을 연결했어요.', 'Account connected.') });
      }
    } catch (cause) {
      setLinkFeedback({ danger: true, text: cause instanceof ApiClientError ? cause.message : tx('연결하지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not connect. Please try again shortly.') });
    } finally {
      setLinkingProvider(null);
    }
  }
  async function openDeletion() {
    if (accessToken) {
      try { setDeletionPreview(await getAccountDeletionPreview(accessToken)); } catch { setDeletionPreview(null); }
    }
    setDeleteConfirmation('');
    setDeleteError(null);
    setDeleteStep(1);
  }
  function closeDeletion() { if (!deleting) { setDeleteStep(0); setDeleteConfirmation(''); setDeleteError(null); } }
  // S15P21E201-837 — 소셜로만 가입한 계정은 비밀번호가 없어 비밀번호로는 본인 확인을 할 수
  // 없다. 그래서 비밀번호 칸을 아예 안 그리고, 사용자가 직접 친 확인 값(DELETE)만 받는다 —
  // 두 종류 계정이 같은 화면을 쓴다(jaehyeon 님 권고). 서버와 정확히 같은 기준(대소문자·
  // 앞뒤 공백까지)으로 버튼을 잠가 둬야 사용자가 400을 먼저 만나지 않는다.
  const deleteConfirmed = deleteConfirmation === DELETE_CONFIRMATION_PHRASE;
  async function confirmDeletion() {
    if (!deleteConfirmed || deleting) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteAccount(deleteConfirmation);
      await plan.clear();
    } catch (cause) {
      const notConfirmed = cause instanceof ApiClientError && cause.code === 'DELETION_NOT_CONFIRMED';
      const unavailable = cause instanceof ApiClientError && cause.code === 'ACCOUNT_UNAVAILABLE';
      setDeleteError(
        notConfirmed ? tx('입력한 값이 달라요. 다시 입력해 주세요.', 'What you typed does not match. Try again.')
          : unavailable ? tx('이미 삭제된 계정이에요.', 'This account has already been deleted.')
          : cause instanceof ApiClientError ? cause.message : tx('계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not delete the account. Try again later.'),
      );
      setDeleting(false);
    }
  }
  return <View style={styles.shell}><Screen scroll withTabBar>
    <View style={styles.heading}><Eyebrow>{tx('내 계정', 'Account')}</Eyebrow><Text variant="display" weight="bold">{tx('마이페이지', 'My page')}</Text></View>
    <View style={styles.profile}><View style={styles.avatar}>{avatarUri ? <Image source={{ uri: avatarUri }} resizeMode="cover" accessibilityLabel={tx('현재 프로필 사진', 'Current profile photo')} style={styles.avatarPhoto} /> : <Text variant="title" weight="bold" color={color.text.onAction}>{(user?.displayName || displayName || tx('여행자', 'Traveler')).slice(0, 1)}</Text>}</View><View style={styles.profileCopy}><Text variant="title" weight="bold">{user?.displayName || displayName || tx('여행자', 'Traveler')}</Text><Text variant="caption" color={color.text.muted}>{user?.email || (visualPreview ? 'miri@example.com' : tx('계정 정보를 불러오지 못했어요', 'Account information is unavailable'))}</Text><Text variant="caption" color={color.text.muted}>{tx('사진은 현재 기기에, 이름과 언어는 계정에 저장돼요.', 'The photo is stored on this device; name and language are saved to your account.')}</Text></View>{(user || visualPreview) && <Pressable accessibilityRole="button" accessibilityState={{ expanded: editing }} onPress={() => { setEditing((value) => !value); setFeedback(null); }} style={({ pressed }) => [styles.editButton, pressed && styles.rowPressed]}><Text variant="caption" weight="bold" color={color.brand.orange}>{editing ? tx('취소', 'Cancel') : tx('프로필 편집', 'Edit profile')}</Text></Pressable>}</View>
    {editing && <View style={styles.editPanel}>
      <View style={styles.avatarEditor}><View style={styles.avatarLarge}>{avatarUri ? <Image source={{ uri: avatarUri }} resizeMode="cover" accessibilityLabel={tx('선택한 프로필 사진', 'Selected profile photo')} style={styles.avatarPhoto} /> : <Text variant="display" weight="bold" color={color.text.onAction}>{(displayName || tx('여행자', 'Traveler')).slice(0, 1)}</Text>}</View><View style={styles.avatarEditorCopy}><Text weight="bold">{tx('프로필 사진', 'Profile photo')}</Text><Text variant="caption" color={color.text.muted}>{tx('JPG, PNG, WebP · 정사각형으로 맞춰드려요', 'JPG, PNG, WebP · cropped to a square')}</Text><View style={styles.avatarActions}><Pressable accessibilityRole="button" disabled={pickingAvatar} onPress={() => void chooseAvatar()} style={({ pressed }) => [styles.photoButton, pressed && styles.rowPressed]}><Text variant="caption" weight="bold" color={color.brand.orange}>{pickingAvatar ? tx('불러오는 중…', 'Loading…') : tx('사진 선택', 'Choose photo')}</Text></Pressable>{avatarUri && <Pressable accessibilityRole="button" onPress={() => void removeAvatar()} style={({ pressed }) => [styles.photoButton, pressed && styles.rowPressed]}><Text variant="caption" weight="bold" color={color.text.body}>{tx('기본 이미지', 'Use default')}</Text></Pressable>}</View></View></View>
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
      {/* S15P21E201-847 — /user/[id] 화면은 이미 내 기록을 전부(공개·팔로워·나만 보기) 보여주고
          삭제까지 되는데, 이 설정 화면에서 거기로 가는 길이 없었다. 다른 사람 프로필을 보다가
          우연히 자기 자신일 때만 닿을 수 있었다. */}
      <InfoRow label={tx('내 기록', 'My records')} value="›" onPress={() => user && router.push(`/user/${user.userId}`)} disabled={!user} />
      <ConsentRow
        label={tx('행동으로 추천 다듬기', 'Tune recommendations from my activity')}
        description={tx('저장·제외·일정 수정·체크인 후기를 보고 추천 순서를 바꿔요. 이 선택은 이 기기에 저장돼요.', 'We reorder recommendations using your saves, exclusions, itinerary edits, and check-in reviews. This choice is stored on this device.')}
        value={behaviorPersonalization}
        onValueChange={setBehaviorPersonalization}
      />
    </View>
    <View style={styles.group}>
      <View style={styles.groupHeader}><Text weight="bold">{tx('연결된 소셜 계정', 'Connected social accounts')}</Text><Text variant="caption" color={color.text.muted}>{tx('다른 방식으로 로그인해도 같은 계정으로 이어가려면 연결해 두세요.', 'Connect these so signing in a different way still lands on this same account.')}</Text></View>
      {(['google', 'apple', 'kakao', 'naver'] as const).map((item) => <SocialLinkRow key={item} provider={item} name={item === 'google' ? 'Google' : item === 'apple' ? 'Apple' : item === 'kakao' ? 'Kakao' : 'Naver'} busy={linkingProvider === item} onPress={() => void connectProvider(item)} />)}
      {linkFeedback && <View accessibilityRole="alert" style={[styles.feedback, linkFeedback.danger && styles.feedbackDanger]}><Text variant="caption" weight="bold" color={linkFeedback.danger ? color.state.danger : color.state.success}>{linkFeedback.text}</Text></View>}
    </View>
    <View style={styles.group}>
      <InfoRow label={tx('이용약관', 'Terms of Service')} value="›" onPress={() => router.push('/legal/terms')} />
      <InfoRow label={tx('개인정보 처리방침', 'Privacy Policy')} value="›" onPress={() => router.push('/legal/privacy')} />
      <InfoRow label={tx('오픈소스 고지', 'Open-source notices')} value="›" onPress={() => router.push('/legal/open-source')} />
      <InfoRow label={tx('공공데이터 출처', 'Public data sources')} value="›" onPress={() => router.push('/legal/data-sources')} />
    </View>
    <Button label={tx('로그아웃', 'Sign out')} variant="ghost" onPress={() => void (async () => { await signOut(); await plan.clear(); })()} containerStyle={styles.logout} />
    <View style={styles.dangerZone}><Text variant="caption" weight="bold" color={color.state.danger}>{tx('계정 관리', 'Account')}</Text><Text variant="caption" color={color.text.body}>{tx('계정과 개인 데이터를 영구적으로 삭제할 수 있어요.', 'Permanently delete your account and personal data.')}</Text><Pressable accessibilityRole="button" onPress={() => void openDeletion()} style={({ pressed }) => [styles.deleteEntry, pressed && styles.rowPressed]}><Text weight="bold" color={color.state.danger}>{tx('계정 삭제', 'Delete account')}</Text><Text variant="title" color={color.state.danger}>›</Text></Pressable></View>
  </Screen><TabBar active="me" />
    <Modal visible={deleteStep > 0} transparent animationType="fade" onRequestClose={closeDeletion}>
      <View style={styles.modalBackdrop}><View accessibilityViewIsModal style={styles.modalCard}>
        {deleteStep === 1 ? <>
          <Text variant="caption" weight="bold" color={color.state.danger}>{tx('1 / 2 · 삭제 내용 확인', '1 / 2 · Review deletion')}</Text>
          <Text variant="display" weight="bold">{tx('삭제되는 내용을 확인해 주세요', 'Review what will be deleted')}</Text>
          <View style={styles.impactList}>
            <View style={styles.impactRow}><Text variant="title" weight="bold" color={color.text.accent}>{deletionPreview?.ownedTripCount ?? '—'}</Text><Text style={styles.impactCopy}>{tx('내가 만든 여행이 삭제돼요.', 'Trips you created will be deleted.')}</Text></View>
            <View style={styles.impactRow}><Text variant="title" weight="bold" color={color.text.accent}>{deletionPreview?.itineraryCount ?? '—'}</Text><Text style={styles.impactCopy}>{tx('그 여행들의 일정이 삭제돼요.', "Those trips' itineraries will be deleted.")}</Text></View>
            <View style={styles.impactRow}><Text variant="title" weight="bold" color={color.text.accent}>{deletionPreview?.recordCount ?? '—'}</Text><Text style={styles.impactCopy}>{tx('작성한 여행 기록이 삭제돼요.', 'Travel records you wrote will be deleted.')}</Text></View>
          </View>
          <View style={styles.reviewNotice}><Text weight="bold">{tx('리뷰는 익명으로 남아요', 'Reviews remain anonymous')}</Text><Text variant="caption" color={color.text.body}>{tx('리뷰 기능이 연결되면 작성자 정보만 제거하고 내용은 익명으로 유지해요.', 'When reviews are connected, author details are removed while content remains anonymous.')}</Text></View>
          <Text accessibilityRole="alert" weight="bold" color={color.state.danger}>{tx('계정 삭제는 되돌릴 수 없습니다.', 'Account deletion cannot be undone.')}</Text>
          <View style={styles.modalActions}><Button label={tx('취소', 'Cancel')} variant="ghost" onPress={closeDeletion} containerStyle={styles.modalAction} /><Button label={tx('계속', 'Continue')} onPress={() => setDeleteStep(2)} containerStyle={styles.modalAction} /></View>
        </> : <>
          <Text variant="caption" weight="bold" color={color.state.danger}>{tx('2 / 2 · 본인 확인', '2 / 2 · Verify identity')}</Text>
          <Text variant="display" weight="bold">{tx('삭제하려면 DELETE를 입력해 주세요', 'Type DELETE to confirm')}</Text>
          <Text color={color.text.body}>{tx('대문자 DELETE를 정확히 입력해야 계정과 데이터가 삭제됩니다.', 'Your account is deleted only after you type DELETE exactly.')}</Text>
          <TextInput accessibilityLabel={tx('계정 삭제 확인 입력', 'Text to confirm account deletion')} autoCapitalize="none" autoCorrect={false} autoFocus value={deleteConfirmation} onChangeText={(value) => { setDeleteConfirmation(value); setDeleteError(null); }} onSubmitEditing={() => void confirmDeletion()} placeholder="DELETE" placeholderTextColor={color.text.muted} style={[styles.input, deleteError && styles.inputError]} />
          {deleteError ? <Text accessibilityRole="alert" color={color.state.danger}>{deleteError}</Text> : null}
          <View style={styles.modalActions}><Button label={tx('이전', 'Back')} variant="ghost" disabled={deleting} onPress={() => { setDeleteStep(1); setDeleteError(null); }} containerStyle={styles.modalAction} /><Pressable accessibilityRole="button" accessibilityState={{ disabled: !deleteConfirmed || deleting }} disabled={!deleteConfirmed || deleting} onPress={() => void confirmDeletion()} style={[styles.deleteConfirm, (!deleteConfirmed || deleting) && styles.deleteConfirmDisabled]}><Text weight="bold" color={color.text.onAction}>{tx('계정 영구 삭제', 'Delete permanently')}</Text></Pressable></View>
        </>}
      </View></View>
    </Modal>
    {deleting ? <View accessibilityRole="alert" accessibilityLiveRegion="assertive" style={styles.blockingOverlay}><ActivityIndicator size="large" color={color.brand.orange} /><Text variant="title" weight="bold" color={color.text.onAction}>{tx('계정과 데이터를 삭제하고 있어요', 'Deleting your account and data')}</Text><Text color={color.text.onAction}>{tx('완료될 때까지 화면을 닫지 마세요.', 'Keep this screen open until deletion finishes.')}</Text></View> : null}
  </View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory },
  heading: { gap: spacing[2], marginBottom: spacing[6] },
  profile: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  avatar: { width: 56, height: 56, overflow: 'hidden', borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.orange },
  avatarPhoto: { width: '100%', height: '100%' },
  profileCopy: { flex: 1, gap: spacing[1] },
  editButton: { minWidth: 72, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  editPanel: { gap: spacing[2], marginTop: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  avatarEditor: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], paddingBottom: spacing[4], marginBottom: spacing[2], borderBottomWidth: 1, borderBottomColor: color.surface.border },
  avatarLarge: { width: 80, height: 80, overflow: 'hidden', borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.orange },
  avatarEditorCopy: { flex: 1, gap: spacing[2] },
  avatarActions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  photoButton: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.sm, backgroundColor: color.surface.card },
  input: { minHeight: 48, paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, color: color.text.heading, backgroundColor: color.brand.ivory },
  inputError: { borderColor: color.state.danger },
  languageRow: { flexDirection: 'row', gap: spacing[2], marginBottom: spacing[2] },
  languageButton: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md },
  languageSelected: { backgroundColor: color.brand.orange, borderColor: color.brand.orange },
  feedback: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.successBg },
  feedbackDanger: { backgroundColor: color.state.dangerBg },
  group: { marginTop: spacing[4], overflow: 'hidden', borderRadius: radius.lg, backgroundColor: color.surface.card },
  groupHeader: { gap: spacing[1], padding: spacing[4], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border },
  socialLabel: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  socialLinkMark: { width: 28, height: 28, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  row: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border },
  rowPressed: { opacity: 0.7, backgroundColor: color.surface.tint }, rowDisabled: { opacity: 0.58 },
  consentRow: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], paddingHorizontal: spacing[4], paddingVertical: spacing[3] },
  consentCopy: { flex: 1, gap: spacing[1] },
  logout: { marginTop: 'auto', marginBottom: spacing[4], borderColor: color.brand.orange },
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
