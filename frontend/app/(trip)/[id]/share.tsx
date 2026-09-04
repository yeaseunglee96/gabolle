import { useState } from 'react';
import { Pressable, Share as NativeShare, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { ApiClientError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { createCompanionInvite, type CompanionInvite, type CompanionRole } from '@/trip/collaboration';

const ROLES: { value: CompanionRole; title: string; description: string }[] = [
  { value: 'EDITOR', title: '함께 편집', description: '일정의 장소와 순서를 같이 바꿀 수 있어요.' },
  { value: 'VIEWER', title: '보기만 허용', description: '일정을 변경하지 않고 확인만 할 수 있어요.' },
];

export default function TripShare() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const { accessToken, ready } = useAuth();
  const [role, setRole] = useState<CompanionRole>('EDITOR');
  const [invite, setInvite] = useState<CompanionInvite | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');

  async function createInvite() {
    if (!id || !accessToken || creating) return;
    setCreating(true);
    setError('');
    try {
      const created = await createCompanionInvite(id, role, accessToken);
      setInvite(created);
      await NativeShare.share({ title: '가볼래 부산 여행 초대', message: `부산 여행 일정에 초대할게요.\n${created.inviteUrl}`, url: created.inviteUrl });
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : '초대 링크를 만들지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setCreating(false);
    }
  }

  return <Screen scroll wide style={styles.screen}>
    <View style={styles.topBar}><Pressable accessibilityRole="button" accessibilityLabel="이전 화면으로 이동" onPress={() => router.canGoBack() ? router.back() : router.replace('/trips')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}><Text variant="title" weight="bold">‹</Text></Pressable><BrandLogoLink href="/home" imageStyle={styles.logo} /><View style={styles.spacer} /></View>
    <View style={styles.heading}><Text variant="eyebrow" weight="bold">TRIP TOGETHER</Text><Text variant="display" weight="bold">동행자를 초대해요</Text><Text color={color.text.body}>역할을 먼저 고르면 7일 동안 사용할 수 있는 초대 링크를 만들어요.</Text></View>

    {!ready && <View accessibilityLiveRegion="polite" style={styles.stateCard}><Text weight="bold">로그인 상태를 확인하고 있어요.</Text></View>}
    {ready && !accessToken && <View style={styles.stateCard}><Text variant="title" weight="bold">로그인이 필요한 기능이에요</Text><Text color={color.text.body}>초대 링크는 여행 소유자와 권한을 확인한 뒤 만들 수 있어요.</Text><Button label="로그인하기" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: `/${id}/share` } })} /></View>}

    {ready && accessToken && <>
      <View accessibilityRole="radiogroup" accessibilityLabel="초대할 동행자의 역할" style={styles.roleList}>{ROLES.map((item) => { const selected = item.value === role; return <Pressable key={item.value} accessibilityRole="radio" accessibilityState={{ selected }} onPress={() => { setRole(item.value); setInvite(null); setError(''); }} style={({ pressed }) => [styles.roleCard, selected && styles.roleSelected, pressed && styles.pressed]}><View style={[styles.radio, selected && styles.radioSelected]}>{selected && <View style={styles.radioDot} />}</View><View style={styles.roleCopy}><Text variant="title" weight="bold">{item.title}</Text><Text color={color.text.body}>{item.description}</Text></View></Pressable>; })}</View>
      <View style={styles.notice}><Text variant="caption" weight="bold">초대 전 확인</Text><Text variant="caption" color={color.text.body}>링크를 받은 사람만 참여할 수 있어요. 링크는 7일 뒤 만료되며, 참여자와 최근 변경 내용은 서버 협업 조회 API가 연결되면 표시합니다.</Text></View>
      <Button label={creating ? '초대 링크 만드는 중…' : `${role === 'EDITOR' ? '편집자' : '열람자'} 초대 링크 만들기`} disabled={creating || !id} onPress={() => void createInvite()} />
      {error && <View accessibilityRole="alert" style={styles.errorCard}><Text weight="bold" color={color.state.danger}>초대 링크를 만들지 못했습니다</Text><Text color={color.text.body}>{error}</Text><Button label="다시 시도" variant="ghost" onPress={() => void createInvite()} /></View>}
      {invite && <View accessibilityLiveRegion="polite" style={styles.successCard}><Text weight="bold" color={color.state.success}>초대 링크를 만들었어요</Text><Text selectable color={color.text.body}>{invite.inviteUrl}</Text><Text variant="caption" color={color.text.muted}>만료: {new Date(invite.expiresAt).toLocaleString('ko-KR')}</Text><Button label="공유 창 다시 열기" variant="ghost" onPress={() => void NativeShare.share({ message: invite.inviteUrl, url: invite.inviteUrl })} /></View>}
    </>}
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory }, topBar: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, pressed: { opacity: 0.72, transform: [{ scale: 0.97 }] }, logo: { width: 96, height: 28 }, spacer: { width: 44 },
  heading: { gap: spacing[2], marginTop: spacing[4], marginBottom: spacing[6] }, stateCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card }, roleList: { gap: spacing[3], marginBottom: spacing[4] }, roleCard: { minHeight: 92, flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], borderWidth: 1, borderColor: color.surface.field, borderRadius: radius.lg, backgroundColor: color.surface.card }, roleSelected: { borderWidth: 2, borderColor: color.brand.orange, backgroundColor: color.surface.tint }, radio: { width: 24, height: 24, borderWidth: 2, borderColor: color.text.muted, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' }, radioSelected: { borderColor: color.brand.orange }, radioDot: { width: 12, height: 12, borderRadius: radius.full, backgroundColor: color.brand.orange }, roleCopy: { flex: 1, gap: spacing[1] }, notice: { gap: spacing[2], marginBottom: spacing[4], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.state.warningBg }, errorCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.dangerBg }, successCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.successBg },
});
