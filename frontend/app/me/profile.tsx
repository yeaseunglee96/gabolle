// 마이페이지 › 프로필 (S15P21E201-965). 원래 `(tabs)/me.tsx` 한 화면에 쌓여 있던 것 중
// 「나」에 해당하는 것만 떼어 왔다 — 사진 · 닉네임 · 이메일 · 언어 · 회원 탈퇴.
//
// 🔴 닉네임 길이는 시안의 12자가 아니라 **기존 30자를 그대로 둔다.** 시안은 제안이고 서버가
// 받는 길이는 기존 화면이 쓰던 값이 근거다. 12로 조이면 이미 12자를 넘는 이름을 쓰는 사람이
// 저장을 못 하게 된다 — 줄이려면 서버 한도를 먼저 확인해야 한다.
//
// 🔴 시안의 「{제공자} 로그인 · 변경 불가」에서 제공자 이름은 **뺐다.** 지금 서버가 주는 계정
// 정보(AuthUser)에 가입 제공자 칸이 없어서 지어낼 수밖에 없기 때문이다.
import { useEffect, useState } from 'react';
import { ActivityIndicator, Image, Modal, Pressable, Share, StyleSheet, TextInput, View, useWindowDimensions } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as ImagePicker from 'expo-image-picker';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError, APP_WEB_BASE_URL } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { getAccountDeletionPreview, type AccountDeletionPreview } from '@/auth/authApi';
import { uploadStoryImage } from '@/social/stories';
import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { MyPageShell, useMyPageCounts } from '@/me/MyPageShell';
import { usePlan } from '@/plan/PlanProvider';

// 박재현 님 계약(S15P21E201-837) — 서버가 대소문자·앞뒤 공백까지 정확히 이 값과 비교한다.
const DELETE_CONFIRMATION_PHRASE = 'DELETE';
const NAME_MAX = 30;

export default function MyPageProfile() {
  const { preview } = useLocalSearchParams<{ preview?: string }>();
  const { user, accessToken, updateProfile, deleteAccount } = useAuth();
  const { tx } = useI18n();
  const plan = usePlan();
  const router = useRouter();
  const { width } = useWindowDimensions();
  const desktop = isAtLeast(width, 'lg');

  // 🔴 S15P21E201-1180 — 인스타그램식 기록·팔로워·팔로잉 숫자. MyPageShell이 이미 같은
  // 프로필 질의를 하고 있어서(사이드 메뉴 숫자), 새로 부르지 않고 그 결과를 같이 쓴다 —
  // react-query 캐시 열쇠가 같아 요청이 하나로 합쳐진다.
  const { storyCount, followerCount, followingCount } = useMyPageCounts();

  async function shareProfile() {
    if (!user?.userId) return;
    const url = `${APP_WEB_BASE_URL}/user/${user.userId}`;
    try {
      await Share.share({ title: tx('가볼래 프로필 공유', 'Share GABOLLE profile'), message: tx(`제 가볼래 프로필을 확인해 보세요.\n${url}`, `Check out my GABOLLE profile.\n${url}`), url });
    } catch {
      // 사용자가 공유 시트를 닫은 것도 실패로 취급하지 않는다 — 별도 안내가 필요 없다.
    }
  }

  const visualPreview = __DEV__ && preview === 'ui';
  const profileOwner = user?.userId ?? (visualPreview ? 'preview' : null);
  const [displayName, setDisplayName] = useState(user?.displayName ?? (visualPreview ? '진미리' : ''));
  const [avatarUri, setAvatarUri] = useState<string | null>(null);
  const [pickingAvatar, setPickingAvatar] = useState(false);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{ danger: boolean; text: string } | null>(null);
  const [deleteStep, setDeleteStep] = useState<0 | 1 | 2>(0);
  const [deletionPreview, setDeletionPreview] = useState<AccountDeletionPreview | null>(null);
  const [deleteConfirmation, setDeleteConfirmation] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    setDisplayName(user?.displayName ?? (visualPreview ? '진미리' : ''));
  }, [user, visualPreview]);
  // S15P21E201-844 — 계정에 붙은 사진이 있으면 그것이 먼저다. 없을 때만 이 기기에 남아 있던
  // 옛 사진을 보여준다 — 기기에만 있던 시절에 고른 사진이 갑자기 사라지면 사용자는 지워진
  // 줄 안다. 다음에 사진을 고르면 그때 계정으로 올라간다.
  useEffect(() => {
    if (!profileOwner) { setAvatarUri(null); return; }
    if (user?.avatarUrl) { setAvatarUri(user.avatarUrl); return; }
    void AsyncStorage.getItem(`gabolle:profile-avatar:${profileOwner}`).then(setAvatarUri);
  }, [profileOwner, user?.avatarUrl]);

  const trimmed = displayName.trim();
  const nameValid = trimmed.length >= 1 && trimmed.length <= NAME_MAX;
  const unchanged = trimmed === (user?.displayName ?? '');

  async function chooseAvatar() {
    if (!profileOwner || pickingAvatar) return;
    setPickingAvatar(true);
    setFeedback(null);
    try {
      const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 0.55, base64: true });
      if (result.canceled) return;
      const asset = result.assets[0];
      const nextUri = asset.base64 ? `data:${asset.mimeType ?? 'image/jpeg'};base64,${asset.base64}` : asset.uri;
      // 고른 것을 먼저 보여준다 — 올리는 동안 빈 자리로 두면 안 고른 것처럼 보인다.
      setAvatarUri(nextUri);
      // S15P21E201-844 — 계정에 붙인다. 🔴 파일은 기존 업로드 자리로 올리고 그 주소만
      // 계정에 붙인다(인증 경로가 파일을 직접 받지 않는다). 로그인 안 한 상태(미리보기)면
      // 지금까지처럼 이 기기에만 둔다.
      if (accessToken && !visualPreview) {
        const uploaded = await uploadStoryImage({ uri: asset.uri, fileName: asset.fileName, mimeType: asset.mimeType }, accessToken);
        if (uploaded.state !== 'success') {
          setFeedback({ danger: true, text: uploaded.message });
          return;
        }
        await updateProfile({ avatarUrl: uploaded.imageUrl });
        setAvatarUri(uploaded.imageUrl);
        setFeedback({ danger: false, text: tx('프로필 사진을 계정에 저장했어요. 다른 기기에서도 보여요.', 'Saved to your account — it shows on your other devices too.') });
        return;
      }
      await AsyncStorage.setItem(`gabolle:profile-avatar:${profileOwner}`, nextUri);
      setFeedback({ danger: false, text: tx('프로필 사진을 이 기기에 저장했어요.', 'Your profile photo was saved on this device.') });
    } catch {
      setFeedback({ danger: true, text: tx('사진을 불러오지 못했어요. JPG, PNG 또는 WebP 파일을 선택해 주세요.', 'Could not load the photo. Choose a JPG, PNG, or WebP file.') });
    } finally {
      setPickingAvatar(false);
    }
  }
  async function removeAvatar() {
    if (!profileOwner) return;
    // 기기에 남은 옛 사진과 계정에 붙은 사진을 둘 다 뗀다 — 한쪽만 떼면 화면을 다시 열 때
    // 지운 사진이 되살아난다.
    await AsyncStorage.removeItem(`gabolle:profile-avatar:${profileOwner}`);
    setAvatarUri(null);
    if (accessToken && !visualPreview && user?.avatarUrl) {
      try {
        await updateProfile({ avatarUrl: null });
      } catch {
        setFeedback({ danger: true, text: tx('계정에서 사진을 떼지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not remove the photo from your account. Please try again shortly.') });
        return;
      }
    }
    setFeedback({ danger: false, text: tx('기본 프로필로 돌아왔어요.', 'Your default profile was restored.') });
  }
  async function saveProfile() {
    if ((!user && !visualPreview) || !nameValid || saving) return;
    setSaving(true);
    setFeedback(null);
    try {
      if (user) await updateProfile({ displayName: trimmed });
      setFeedback({ danger: false, text: visualPreview && !user ? tx('미리보기에서 변경 모습을 확인했어요.', 'Preview changes are displayed.') : tx('프로필을 저장했어요.', 'Your profile was saved.') });
    } catch (cause) {
      setFeedback({ danger: true, text: cause instanceof ApiClientError ? cause.message : tx('프로필을 저장하지 못했어요.', 'Could not save your profile.') });
    } finally {
      setSaving(false);
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
  // S15P21E201-837 — 소셜로만 가입한 계정은 비밀번호가 없어 비밀번호로 본인 확인을 할 수 없다.
  // 그래서 사용자가 직접 친 확인 값(DELETE)만 받고, 서버와 같은 기준으로 버튼을 잠근다.
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

  const initial = (user?.displayName || displayName || tx('여행자', 'Traveler')).slice(0, 1);
  const avatarSize = desktop ? 104 : 96;

  return (
    <MyPageShell tab="profile" title={tx('프로필', 'Profile')} description={tx('피드와 기록에 보이는 이름과 사진이에요.', 'This is the name and photo people see on your posts.')}>
      {user ? (
        <View style={styles.statCard}>
          <View style={styles.statRow}>
            <View style={styles.stat}><Text variant="title" weight="bold">{storyCount ?? '—'}</Text><Text variant="caption" color={color.text.muted}>{tx('기록', 'Records')}</Text></View>
            <Pressable accessibilityRole="button" onPress={() => router.push(`/user/${user.userId}/followers`)} style={styles.stat}><Text variant="title" weight="bold">{followerCount ?? '—'}</Text><Text variant="caption" color={color.text.muted}>{tx('팔로워', 'Followers')}</Text></Pressable>
            <Pressable accessibilityRole="button" onPress={() => router.push(`/user/${user.userId}/following`)} style={styles.stat}><Text variant="title" weight="bold">{followingCount ?? '—'}</Text><Text variant="caption" color={color.text.muted}>{tx('팔로잉', 'Following')}</Text></Pressable>
          </View>
          <View style={styles.statActions}>
            <Button label={tx('내 피드 보기', 'View my posts')} variant="ghost" compact onPress={() => router.push('/me/posts')} />
            <Button label={tx('프로필 공유', 'Share profile')} variant="ghost" compact onPress={() => void shareProfile()} />
          </View>
        </View>
      ) : null}

      <View style={[styles.card, desktop && styles.cardDesktop]}>
        <View style={styles.avatarColumn}>
          <View style={[styles.avatar, { width: avatarSize, height: avatarSize }]}>
            {avatarUri
              ? <Image source={{ uri: avatarUri }} resizeMode="cover" accessibilityLabel={tx('현재 프로필 사진', 'Current profile photo')} style={styles.avatarPhoto} />
              : <Text variant="display" weight="bold" color={color.text.onAction}>{initial}</Text>}
          </View>
          <Pressable accessibilityRole="button" disabled={pickingAvatar} onPress={() => void chooseAvatar()} style={({ pressed }) => [styles.photoButton, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={color.brand.navy}>{pickingAvatar ? tx('불러오는 중…', 'Loading…') : tx('사진 바꾸기', 'Change photo')}</Text>
          </Pressable>
          {avatarUri ? (
            <Pressable accessibilityRole="button" onPress={() => void removeAvatar()} style={({ pressed }) => [styles.photoReset, pressed && styles.pressed]}>
              <Text variant="caption" weight="medium">{tx('기본으로', 'Use default')}</Text>
            </Pressable>
          ) : null}
        </View>

        <View style={styles.fields}>
          <View style={styles.field}>
            <Text variant="caption" weight="bold" color={color.text.body}>{tx('닉네임', 'Nickname')}</Text>
            <TextInput accessibilityLabel={tx('닉네임', 'Nickname')} maxLength={NAME_MAX} value={displayName} onChangeText={(value) => { setDisplayName(value); setFeedback(null); }} style={[styles.input, !nameValid && styles.inputError]} />
            <Text variant="caption" color={nameValid ? color.text.muted : color.state.danger}>
              {nameValid
                ? tx(`${trimmed.length} / ${NAME_MAX} · 피드와 기록에 보여요`, `${trimmed.length} / ${NAME_MAX} · shown on your posts`)
                : tx('1자 이상 입력해 주세요', 'Enter at least 1 character')}
            </Text>
          </View>

          <View style={styles.field}>
            <Text variant="caption" weight="bold" color={color.text.body}>{tx('이메일', 'Email')}</Text>
            <View style={styles.readonly}>
              <Text numberOfLines={1} style={styles.readonlyValue}>{user?.email || (visualPreview ? 'miri@example.com' : tx('계정 정보를 불러오지 못했어요', 'Account information is unavailable'))}</Text>
              <Text variant="caption" weight="bold">{tx('변경 불가', 'Cannot be changed')}</Text>
            </View>
          </View>

          {/* 🔴 계정 언어(SignupLanguage = 'KO' | 'EN') 칩은 여기 없다 — 화면 언어는 다섯인데
              (LANGUAGE_OPTIONS, 마이페이지의 AppLanguageSetting) 여기서는 늘 둘만 보여줄 수
              있어서 같은 개념의 설정이 화면 두 곳에서 서로 다른 범위로 보였다. 마이페이지
              쪽이 이미 계정 값도 같이 갱신하므로(AppLanguageSetting.toAccountLanguage)
              여기서 따로 안 만진다. */}

          <View style={styles.saveRow}>
            {feedback ? <Text accessibilityRole="alert" variant="caption" weight="bold" color={feedback.danger ? color.state.danger : color.state.success} style={styles.feedback}>{feedback.text}</Text> : <View style={styles.feedback} />}
            <Button label={saving ? tx('저장 중…', 'Saving…') : tx('저장', 'Save')} disabled={!nameValid || saving || unchanged} onPress={() => void saveProfile()} containerStyle={styles.save} />
          </View>
        </View>
      </View>

      <View style={styles.dangerZone}>
        <View style={styles.dangerCopy}>
          <Text weight="bold" color={color.state.danger}>{tx('회원 탈퇴', 'Delete account')}</Text>
          <Text variant="caption">{tx('여행, 기록, 취향이 모두 지워지고 되돌릴 수 없어요.', 'Your trips, records, and preferences are all deleted. This cannot be undone.')}</Text>
        </View>
        <Pressable accessibilityRole="button" onPress={() => void openDeletion()} style={({ pressed }) => [styles.dangerButton, pressed && styles.pressed]}>
          <Text weight="bold" color={color.state.danger}>{tx('탈퇴하기', 'Delete')}</Text>
        </Pressable>
      </View>

      <Modal visible={deleteStep > 0} transparent animationType="fade" onRequestClose={closeDeletion}>
        <View style={styles.modalBackdrop}><View accessibilityViewIsModal style={styles.modalCard}>
          {deleteStep === 1 ? <>
            <Text variant="caption" weight="bold" color={color.state.danger}>{tx('1 / 2 · 삭제 내용 확인', '1 / 2 · Review deletion')}</Text>
            <Text variant="display" weight="bold">{tx('삭제되는 내용을 확인해 주세요', 'Review what will be deleted')}</Text>
            <View style={styles.impactList}>
              <View style={styles.impactRow}><Text variant="title" weight="bold" color={color.text.accent}>{deletionPreview ? tx(`${deletionPreview.ownedTripCount}개`, `${deletionPreview.ownedTripCount} items`) : '—'}</Text><Text style={styles.impactCopy}>{tx('내가 만든 여행이 삭제돼요.', 'Trips you created will be deleted.')}</Text></View>
              <View style={styles.impactRow}><Text variant="title" weight="bold" color={color.text.accent}>{deletionPreview ? tx(`${deletionPreview.itineraryCount}개`, `${deletionPreview.itineraryCount} items`) : '—'}</Text><Text style={styles.impactCopy}>{tx('그 여행들의 일정이 삭제돼요.', "Those trips' itineraries will be deleted.")}</Text></View>
              <View style={styles.impactRow}><Text variant="title" weight="bold" color={color.text.accent}>{deletionPreview ? tx(`${deletionPreview.recordCount}개`, `${deletionPreview.recordCount} items`) : '—'}</Text><Text style={styles.impactCopy}>{tx('작성한 여행 기록이 삭제돼요.', 'Travel records you wrote will be deleted.')}</Text></View>
            </View>
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
    </MyPageShell>
  );
}

const styles = StyleSheet.create({
  statCard: { gap: spacing[3], marginBottom: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  statRow: { flexDirection: 'row', justifyContent: 'space-around' },
  stat: { alignItems: 'center' },
  statActions: { flexDirection: 'row', gap: spacing[2], paddingTop: spacing[3], borderTopWidth: 1, borderTopColor: color.surface.border },
  card: { gap: spacing[6], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardDesktop: { flexDirection: 'row', alignItems: 'flex-start', padding: spacing[6], borderWidth: 1, borderColor: color.surface.border },
  avatarColumn: { alignItems: 'center', gap: spacing[2] },
  avatar: { overflow: 'hidden', borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  avatarPhoto: { width: '100%', height: '100%' },
  photoButton: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[3], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.full, backgroundColor: color.surface.card },
  photoReset: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[3] },
  pressed: { opacity: 0.7 },

  fields: { flex: 1, gap: spacing[6], minWidth: 0 },
  field: { gap: spacing[2] },
  input: { minHeight: 48, paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.md, color: color.text.heading, backgroundColor: color.brand.ivory },
  inputError: { borderColor: color.state.danger },
  readonly: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.surface.border, borderRadius: radius.md, backgroundColor: color.surface.soft },
  readonlyValue: { flex: 1, minWidth: 0 },
  saveRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingTop: spacing[4], borderTopWidth: 1, borderTopColor: color.surface.border },
  feedback: { flex: 1 },
  // Button 의 기본 스타일이 width:'100%' 라 minWidth 만으로는 안 줄어든다 — 명시적으로 푼다.
  save: { width: 'auto', minWidth: 120 },

  dangerZone: { flexDirection: 'row', alignItems: 'center', gap: spacing[4], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.dangerBg },
  dangerCopy: { flex: 1, gap: spacing[1] },
  dangerButton: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderWidth: 1, borderColor: color.state.danger, borderRadius: radius.md, backgroundColor: color.surface.card },

  modalBackdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  modalCard: { width: '100%', maxWidth: 560, gap: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  impactList: { gap: spacing[2] },
  impactRow: { minHeight: 64, flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  impactCopy: { flex: 1 },
  modalActions: { flexDirection: 'row', gap: spacing[2] },
  modalAction: { flex: 1 },
  deleteConfirm: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.state.danger },
  deleteConfirmDisabled: { opacity: 0.4 },
  blockingOverlay: { ...StyleSheet.absoluteFill, zIndex: 100, alignItems: 'center', justifyContent: 'center', gap: spacing[3], padding: spacing[6], backgroundColor: color.brand.navy },
});
