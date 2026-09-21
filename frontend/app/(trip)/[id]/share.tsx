import { useState } from 'react';
import { Pressable, Share as NativeShare, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { createCompanionInvite, type CompanionInvite, type CompanionRole } from '@/trip/collaboration';
import { issueShareLink, type ShareLinkIssued } from '@/share/sharedItinerary';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { markChecklistStep } from '@/onboarding/firstRun';
import { localizeMessage } from '@/i18n/messages';

const ROLES: { value: CompanionRole; titleKo: string; titleEn: string; descriptionKo: string; descriptionEn: string }[] = [
  { value: 'EDITOR', titleKo: '함께 편집', titleEn: 'Edit together', descriptionKo: '일정의 장소와 순서를 같이 바꿀 수 있어요.', descriptionEn: 'Can change places and order in the itinerary together.' },
  { value: 'VIEWER', titleKo: '보기만 허용', titleEn: 'View only', descriptionKo: '일정을 변경하지 않고 확인만 할 수 있어요.', descriptionEn: 'Can view the itinerary without changing it.' },
];

export default function TripShare() {
  const router = useRouter();
  const { tx, locale } = useI18n();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const { accessToken, ready } = useAuth();
  const [role, setRole] = useState<CompanionRole>('EDITOR');
  const [invite, setInvite] = useState<CompanionInvite | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');
  const [readLink, setReadLink] = useState<ShareLinkIssued | null>(null);
  const [issuingReadLink, setIssuingReadLink] = useState(false);
  const [readLinkError, setReadLinkError] = useState('');

  async function createInvite() {
    if (!id || !accessToken || creating) return;
    setCreating(true);
    setError('');
    try {
      const created = await createCompanionInvite(id, role, accessToken);
      setInvite(created);
      void markChecklistStep('invite');
      await NativeShare.share({ title: tx('가볼래 부산 여행 초대', 'GABOLLE Busan trip invite'), message: txf(tx, '부산 여행 일정에 초대할게요.\n%s', 'You\'re invited to a Busan trip itinerary.\n%s', created.inviteUrl), url: created.inviteUrl });
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : tx('초대 링크를 만들지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not create the invite link. Please try again shortly.'));
    } finally {
      setCreating(false);
    }
  }

  async function createReadLink() {
    if (!id || !accessToken || issuingReadLink) return;
    setIssuingReadLink(true);
    setReadLinkError('');
    try {
      setReadLink(await issueShareLink(id, accessToken));
    } catch (cause) {
      setReadLinkError(cause instanceof ApiClientError ? cause.message : tx('공유 링크를 만들지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not create the share link. Please try again shortly.'));
    } finally {
      setIssuingReadLink(false);
    }
  }

  return <Screen scroll wide style={styles.screen}>
    <View style={styles.topBar}><Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/trips')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}><Text variant="title" weight="bold">‹</Text></Pressable><BrandLogoLink href="/home" imageStyle={styles.logo} /><View style={styles.spacer} /></View>
    <View style={styles.heading}><Eyebrow>{tx('함께하는 여행', 'Trip together')}</Eyebrow><Text variant="display" weight="bold">{tx('동행자를 초대해요', 'Invite a companion')}</Text><Text color={color.text.body}>{tx('역할을 먼저 고르면 7일 동안 사용할 수 있는 초대 링크를 만들어요.', 'Pick a role first, and we’ll create an invite link valid for 7 days.')}</Text></View>

    {!ready && <View accessibilityLiveRegion="polite" style={styles.stateCard}><Text weight="bold">{tx('로그인 상태를 확인하고 있어요.', 'Checking sign-in status.')}</Text></View>}
    {ready && !accessToken && <View style={styles.stateCard}><Text variant="title" weight="bold">{tx('로그인이 필요한 기능이에요', 'Sign-in required for this feature')}</Text><Text color={color.text.body}>{tx('초대 링크는 여행 소유자와 권한을 확인한 뒤 만들 수 있어요.', "We'll verify the trip owner and permissions before creating the invite link.")}</Text><Button label={tx('로그인하기', 'Sign in')} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: `/${id}/share` } })} /></View>}

    {ready && accessToken && <>
      <View accessibilityRole="radiogroup" accessibilityLabel={tx('초대할 동행자의 역할', 'Role for the companion to invite')} style={styles.roleList}>{ROLES.map((item) => { const selected = item.value === role; return <Pressable key={item.value} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => { setRole(item.value); setInvite(null); setError(''); }} style={({ pressed }) => [styles.roleCard, selected && styles.roleSelected, pressed && styles.pressed]}><View style={[styles.radio, selected && styles.radioSelected]}>{selected && <View style={styles.radioDot} />}</View><View style={styles.roleCopy}><Text variant="title" weight="bold">{tx(item.titleKo, item.titleEn)}</Text><Text color={color.text.body}>{tx(item.descriptionKo, item.descriptionEn)}</Text></View></Pressable>; })}</View>
      <View style={styles.notice}><Text variant="caption" weight="bold">{tx('초대 전 확인', 'Before you invite')}</Text><Text variant="caption" color={color.text.body}>{tx('링크를 받은 사람만 참여할 수 있어요. 링크는 7일 뒤 만료돼요.', 'Only people with the link can join. It expires in 7 days.')}</Text></View>
      <Button label={tx('참여자 목록·역할 관리', 'Manage participants and roles')} variant="tertiary" onPress={() => router.push(`/${id}/collaborate`)} containerStyle={styles.manageButton} />
      <Button label={creating ? tx('초대 링크 만드는 중…', 'Creating invite link…') : txf(tx, '%s 초대 링크 만들기', 'Create %s invite link', role === 'EDITOR' ? tx('편집자', 'editor') : tx('열람자', 'viewer'))} disabled={creating || !id} onPress={() => void createInvite()} />
      {error && <View accessibilityRole="alert" style={styles.errorCard}><Text weight="bold" color={color.state.danger}>{tx('초대 링크를 만들지 못했습니다', 'Could not create the invite link')}</Text><Text color={color.text.body}>{localizeMessage(tx, error)}</Text><Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void createInvite()} /></View>}
      {invite && <View accessibilityLiveRegion="polite" style={styles.successCard}><Text weight="bold" color={color.state.success}>{tx('초대 링크를 만들었어요', 'Invite link created')}</Text><Text selectable color={color.text.body}>{invite.inviteUrl}</Text><Text variant="caption" color={color.text.muted}>{txf(tx, '만료: %s', 'Expires: %s', new Date(invite.expiresAt).toLocaleString(locale))}</Text><Button label={tx('공유 창 다시 열기', 'Reopen share sheet')} variant="tertiary" onPress={() => void NativeShare.share({ message: invite.inviteUrl, url: invite.inviteUrl })} /></View>}

      <View style={styles.divider} />

      <View style={styles.heading}><Eyebrow>{tx('구경만 시키기', 'Just show it off')}</Eyebrow><Text variant="title" weight="bold">{tx('읽기 전용 링크로 공유해요', 'Share a read-only link')}</Text><Text color={color.text.body}>{tx('가볼래 계정이 없어도 볼 수 있어요. 날짜별 일정만 보이고 출발지·연락처·예산·인원은 공유되지 않아요. 30일 뒤 만료돼요.', "Viewable without a GABOLLE account. Only the day-by-day itinerary is shown — starting point, contact info, budget, and party size aren't shared. Expires in 30 days.")}</Text></View>
      <Button label={issuingReadLink ? tx('링크 만드는 중…', 'Creating link…') : tx('읽기 전용 링크 만들기', 'Create read-only link')} variant="tertiary" disabled={issuingReadLink || !id} onPress={() => void createReadLink()} />
      {readLinkError && <View accessibilityRole="alert" style={styles.errorCard}><Text weight="bold" color={color.state.danger}>{tx('링크를 만들지 못했습니다', 'Could not create the link')}</Text><Text color={color.text.body}>{readLinkError}</Text><Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void createReadLink()} /></View>}
      {readLink && <View accessibilityLiveRegion="polite" style={styles.successCard}><Text weight="bold" color={color.state.success}>{tx('읽기 전용 링크를 만들었어요', 'Read-only link created')}</Text><Text selectable color={color.text.body}>{readLink.shareUrl}</Text><Text variant="caption" color={color.text.muted}>{txf(tx, '만료: %s', 'Expires: %s', new Date(readLink.expiresAt).toLocaleString(locale))}</Text><Button label={tx('공유 창 열기', 'Open share sheet')} variant="tertiary" onPress={() => void NativeShare.share({ message: readLink.shareUrl, url: readLink.shareUrl })} /></View>}
    </>}
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas }, topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, pressed: { opacity: 0.72, transform: [{ scale: 0.97 }] }, logo: { width: 154, height: 28 }, spacer: { width: 44 },
  heading: { gap: spacing[2], marginTop: spacing[4], marginBottom: spacing[6] }, stateCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card }, roleList: { gap: spacing[3], marginBottom: spacing[4] }, roleCard: { minHeight: 92, flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card }, roleSelected: { borderWidth: 1.5, borderColor: color.action.secondary, backgroundColor: color.surface.tint }, radio: { width: 24, height: 24, borderWidth: 2, borderColor: color.text.muted, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' }, radioSelected: { borderColor: color.action.secondary }, radioDot: { width: 12, height: 12, borderRadius: radius.full, backgroundColor: color.action.secondary }, roleCopy: { flex: 1, gap: spacing[1] }, notice: { gap: spacing[2], marginBottom: spacing[4], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.state.warningBg }, errorCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.dangerBg }, successCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.successBg }, manageButton: { marginTop: spacing[3] }, divider: { height: 1, marginVertical: spacing[6], backgroundColor: color.surface.border },
});
