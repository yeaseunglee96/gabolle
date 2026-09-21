// 기록 공동 작성 — 참여자 목록·초대·여행 동행자 추가·제거/나가기 화면. 서버는
// 이미 끝나 있었고 화면만 없었다 — S15P21E201-845. 구조는 여행 참여자 관리 화면
// ((trip)/[id]/collaborate.tsx,을 그대로 따른다 — 참여자 목록 + 초대 + 제거/나가기
// 라는 같은 모양의 문제라 화면도 같은 모양으로 푼다.
import { useCallback, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, Share as NativeShare, StyleSheet, View } from 'react-native';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { formatDateTime } from '@/i18n/datetime';
import { getStory, type StoryDto } from '@/social/stories';
import { addStoryCoauthors, createStoryInvite, listStoryCoauthors, removeStoryCoauthor, type StoryCoauthor } from '@/social/storyCoauthors';
import { listTripMembers, type TripMember } from '@/trip/collaboration';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';

type State =
  | { status: 'loading' }
  | { status: 'ready'; story: StoryDto; coauthors: StoryCoauthor[] }
  | { status: 'not-found' | 'forbidden' | 'error'; message: string };

export default function StoryCoauthors() {
  const router = useRouter();
  const { tx, locale } = useI18n();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const { accessToken, user, ready } = useAuth();
  const [state, setState] = useState<State>({ status: 'loading' });
  const [busyUserId, setBusyUserId] = useState<string | null>(null);
  const [actionError, setActionError] = useState('');
  const [inviting, setInviting] = useState(false);
  const [invite, setInvite] = useState<{ inviteUrl: string; expiresAt: string } | null>(null);
  const [pickerVisible, setPickerVisible] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setState({ status: 'loading' });
    const storyResult = await getStory(id, accessToken);
    if (storyResult.state === 'not-found') { setState({ status: 'not-found', message: '' }); return; }
    if (storyResult.state !== 'success') { setState({ status: 'error', message: storyResult.message }); return; }
    const coauthorsResult = await listStoryCoauthors(id, accessToken);
    if (coauthorsResult.state === 'forbidden') { setState({ status: 'forbidden', message: coauthorsResult.message }); return; }
    if (coauthorsResult.state !== 'success') { setState({ status: 'error', message: coauthorsResult.message }); return; }
    setState({ status: 'ready', story: storyResult.story, coauthors: coauthorsResult.coauthors });
  }, [id, accessToken]);

  useFocusEffect(useCallback(() => { if (ready) void load(); }, [ready, load]));

  const isAuthor = state.status === 'ready' && state.story.mine;

  async function makeInvite() {
    if (!id || inviting) return;
    setInviting(true);
    setActionError('');
    const result = await createStoryInvite(id, accessToken);
    setInviting(false);
    if (result.state === 'success') {
      setInvite({ inviteUrl: result.inviteUrl, expiresAt: result.expiresAt });
      await NativeShare.share({ title: tx('가볼래 기록 함께 쓰기', 'GABOLLE record co-writing'), message: txf(tx, '이 기록을 함께 써요.\n%s', 'Write this record together.\n%s', result.inviteUrl), url: result.inviteUrl });
    } else {
      setActionError(result.message);
    }
  }

  async function removeOne(target: StoryCoauthor) {
    if (!id || busyUserId) return;
    setBusyUserId(target.userId);
    setActionError('');
    const result = await removeStoryCoauthor(id, target.userId, accessToken);
    setBusyUserId(null);
    if (result.state === 'success') void load();
    else setActionError(result.message);
  }

  async function addFromTrip(userIds: string[]) {
    if (!id || userIds.length === 0) return;
    setActionError('');
    const result = await addStoryCoauthors(id, userIds, accessToken);
    if (result.state === 'success') { setPickerVisible(false); void load(); }
    else setActionError(result.message);
  }

  return <Screen scroll wide style={styles.screen}>
    <View style={styles.topBar}><Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace(`/feed/${id}`)} style={({ pressed }) => [styles.back, pressed && styles.pressed]}><Text variant="title" weight="bold">‹</Text></Pressable><BrandLogoLink href="/home" imageStyle={styles.logo} /><View style={styles.spacer} /></View>
    <View style={styles.heading}><Eyebrow>{tx('함께 쓰는 기록', 'Co-written record')}</Eyebrow><Text variant="display" weight="bold">{tx('참여자 관리', 'Manage participants')}</Text><Text color={color.text.body}>{tx('이 기록을 함께 쓰는 사람들이에요.', 'Everyone writing this record together.')}</Text></View>

    {state.status === 'loading' && <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.action.primary} /><Text weight="bold">{tx('참여자를 불러오고 있어요.', 'Loading participants.')}</Text></View>}
    {state.status === 'not-found' && <View style={styles.stateCard}><Text variant="title" weight="bold">{tx('기록을 찾을 수 없어요', 'Could not find this record')}</Text><Button label={tx('피드로 돌아가기', 'Back to feed')} variant="tertiary" onPress={() => router.replace('/feed')} /></View>}
    {state.status === 'forbidden' && <View style={styles.stateCard}><Text variant="title" weight="bold">{tx('이 기록을 볼 수 있는 사람만 볼 수 있어요', 'Only people who can see this record can view this')}</Text><Text color={color.text.body}>{localizeMessage(tx, state.message)}</Text></View>}
    {state.status === 'error' && <View style={styles.stateCard}><Text variant="title" weight="bold">{tx('참여자를 불러오지 못했어요', "Couldn't load participants")}</Text><Text color={color.text.body}>{localizeMessage(tx, state.message)}</Text><Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void load()} /></View>}

    {state.status === 'ready' && <>
      <View style={styles.list}>{state.coauthors.map((member) => {
        const busy = busyUserId === member.userId;
        const isMe = member.userId === user?.userId;
        const canRemove = (isAuthor && !member.isAuthor) || (isMe && !member.isAuthor);
        return <View key={member.userId} style={styles.memberCard}>
          <View style={styles.memberInfo}>
            <Text weight="bold">{member.displayName ?? tx('(탈퇴한 사용자)', '(deleted user)')}{isMe ? tx(' · 나', ' · You') : ''}</Text>
            {member.isAuthor && <View style={styles.roleBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('만든 사람', 'Author')}</Text></View>}
          </View>
          {canRemove && <Pressable accessibilityRole="button" accessibilityLabel={isMe ? tx('나가기', 'Leave') : txf(tx, '%s 제거', 'Remove %s', member.displayName ?? '')} accessibilityState={{ disabled: busy }} disabled={busy} onPress={() => void removeOne(member)} style={[styles.actionButtonDanger, busy && styles.actionDisabled]}>
            <Text variant="caption" weight="bold" color={color.state.danger}>{busy ? tx('처리 중…', 'Working…') : isMe ? tx('나가기', 'Leave') : tx('제거', 'Remove')}</Text>
          </Pressable>}
        </View>;
      })}</View>

      {actionError && <View accessibilityRole="alert" style={styles.errorCard}><Text color={color.state.danger}>{actionError}</Text></View>}

      {isAuthor && <>
        <Button label={inviting ? tx('초대 링크 만드는 중…', 'Creating invite link…') : tx('초대 링크 만들기', 'Create invite link')} disabled={inviting} onPress={() => void makeInvite()} containerStyle={styles.actionRowButton} />
        {invite && <View accessibilityLiveRegion="polite" style={styles.successCard}><Text weight="bold" color={color.state.success}>{tx('초대 링크를 만들었어요', 'Invite link created')}</Text><Text selectable color={color.text.body}>{invite.inviteUrl}</Text><Text variant="caption" color={color.text.muted}>{txf(tx, '만료: %s', 'Expires: %s', formatDateTime(invite.expiresAt, locale))}</Text></View>}
        {state.story.tripId && <Button label={tx('여행 동행자 추가', 'Add a trip companion')} variant="tertiary" onPress={() => setPickerVisible(true)} containerStyle={styles.actionRowButton} />}
      </>}
    </>}

    {state.status === 'ready' && state.story.tripId && <TripCompanionPicker
      visible={pickerVisible}
      tripId={state.story.tripId}
      accessToken={accessToken}
      excludeUserIds={state.coauthors.map((c) => c.userId)}
      onClose={() => setPickerVisible(false)}
      onAdd={(userIds) => void addFromTrip(userIds)}
    />}
  </Screen>;
}

function TripCompanionPicker({ visible, tripId, accessToken, excludeUserIds, onClose, onAdd }: {
  visible: boolean;
  tripId: string;
  accessToken: string | null;
  excludeUserIds: string[];
  onClose: () => void;
  onAdd: (userIds: string[]) => void;
}) {
  const { tx } = useI18n();
  const [members, setMembers] = useState<TripMember[] | null>(null);
  const [loadError, setLoadError] = useState('');
  const [selected, setSelected] = useState<string[]>([]);

  useFocusEffect(useCallback(() => {
    if (!visible) return;
    setMembers(null);
    setSelected([]);
    setLoadError('');
    let active = true;
    (async () => {
      const result = await listTripMembers(tripId, accessToken);
      if (!active) return;
      if (result.state === 'success') setMembers(result.members.filter((m) => !excludeUserIds.includes(m.userId)));
      else setLoadError(result.message);
    })();
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, tripId, accessToken]));

  function toggle(userId: string) {
    setSelected((prev) => prev.includes(userId) ? prev.filter((v) => v !== userId) : [...prev, userId]);
  }

  return <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
    <View style={styles.backdrop}>
      <View accessibilityViewIsModal style={styles.pickerCard}>
        <View style={styles.pickerHeader}>
          <Text variant="title" weight="bold">{tx('여행 동행자 고르기', 'Choose trip companions')}</Text>
          <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={onClose} style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}><Text variant="title" weight="bold">✕</Text></Pressable>
        </View>
        {members === null && !loadError && <View style={styles.pickerLoading}><ActivityIndicator color={color.action.primary} /></View>}
        {loadError ? <Text color={color.state.danger}>{loadError}</Text> : null}
        {members !== null && members.length === 0 && <Text color={color.text.body}>{tx('추가할 수 있는 동행자가 없어요 — 이미 모두 참여 중이에요.', 'No companions left to add — everyone is already a participant.')}</Text>}
        {members !== null && members.length > 0 && <View style={styles.pickerList}>{members.map((member) => {
          const isSelected = selected.includes(member.userId);
          return <Pressable key={member.userId} accessibilityRole="checkbox" accessibilityState={{ checked: isSelected }} onPress={() => toggle(member.userId)} style={[styles.pickerOption, isSelected && styles.pickerOptionSelected]}>
            <Text variant="body" weight="bold" color={isSelected ? color.text.onAction : color.text.heading}>{member.displayName ?? tx('(탈퇴한 사용자)', '(deleted user)')}</Text>
          </Pressable>;
        })}</View>}
        <Button label={tx(`${selected.length}명 추가`, `Add ${selected.length}`)} disabled={selected.length === 0} onPress={() => onAdd(selected)} />
      </View>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas }, topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, pressed: { opacity: 0.72, transform: [{ scale: 0.97 }] }, logo: { width: 154, height: 28 }, spacer: { width: 44 },
  heading: { gap: spacing[2], marginTop: spacing[4], marginBottom: spacing[6] }, stateCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  list: { gap: spacing[3] }, memberCard: { gap: spacing[3], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  memberInfo: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  roleBadge: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy },
  actionButtonDanger: { minWidth: 88, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.state.dangerBg },
  actionDisabled: { opacity: 0.5 },
  errorCard: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.dangerBg },
  actionRowButton: { marginTop: spacing[4] },
  successCard: { gap: spacing[2], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.state.successBg },
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  pickerCard: { width: '100%', maxWidth: 420, maxHeight: '80%', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  pickerHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  closeButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card },
  pickerLoading: { alignItems: 'center', padding: spacing[4] },
  pickerList: { gap: spacing[2] },
  pickerOption: { minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  pickerOptionSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
});
