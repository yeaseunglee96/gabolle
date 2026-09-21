// 동행자 공동 편집 화면 — 참여자·역할 관리·최근 변경 (상세설계서 Part II P-22) — S15P21E201-327.
// 초대 링크 만들기는 share.tsx가 이미 하므로 여기서는 그 화면으로 보내는 진입점만 둔다.
import { useCallback, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { changeTripMemberRole, getTripActivity, listTripMembers, removeTripMember, type CompanionRole, type TripActivityEntry, type TripActivityOperation, type TripMember, type TripMembersView } from '@/trip/collaboration';
import { relativeStoryTime } from '@/social/stories';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';

const ROLE_LABEL: Record<TripMember['role'], [string, string]> = {
  OWNER: ['소유자', 'Owner'],
  EDITOR: ['편집자', 'Editor'],
  VIEWER: ['열람자', 'Viewer'],
};

// 계약: 이 목록은 일정의 판(itinerary_versions)을 읽은 것이라 장소 이름은
// 안 실려 온다(누가 무엇을 "바꿨는지"까지지 "무엇으로" 바꿨는지는 없다) — 지어내지 않는다.
const OPERATION_LABEL: Record<TripActivityOperation, [string, string]> = {
  CREATE: ['일정을 만들었어요', 'created the itinerary'],
  REGENERATE: ['일정을 전체 다시 만들었어요', 'regenerated the whole itinerary'],
  REGENERATE_DAY: ['하루 일정을 다시 만들었어요', 'regenerated a day'],
  REPLACE_ITEM: ['장소를 바꿨어요', 'replaced a place'],
  REMOVE_ITEM: ['장소를 뺐어요', 'removed a place'],
  LOCK_ITEM: ['장소 고정 상태를 바꿨어요', 'changed a place’s lock'],
  REORDER: ['순서를 바꿨어요', 'reordered items'],
  REVERT: ['이전 버전으로 되돌렸어요', 'reverted to an earlier version'],
  ADD_ITEM: ['장소를 추가했어요', 'added a place'],
};

type State = { status: 'loading' } | { status: 'ready'; view: TripMembersView } | { status: 'forbidden' | 'error'; message: string };
type ActivityState = { status: 'loading' | 'hidden' } | { status: 'ready'; entries: TripActivityEntry[] } | { status: 'error'; message: string };

export default function TripCollaborate() {
  const router = useRouter();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const { accessToken, ready } = useAuth();
  const [state, setState] = useState<State>({ status: 'loading' });
  const [activity, setActivity] = useState<ActivityState>({ status: 'loading' });
  const [busyUserId, setBusyUserId] = useState<string | null>(null);
  const [actionError, setActionError] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    setState({ status: 'loading' });
    const result = await listTripMembers(id, accessToken);
    if (result.state === 'success') setState({ status: 'ready', view: { members: result.members, myRole: result.myRole, canEdit: result.canEdit } });
    else setState({ status: result.state, message: result.message });
  }, [id, accessToken]);

  // 참여자 목록과 별개 요청이다 — 최근 변경이 늦게 오거나 실패해도 참여자 목록은 그대로
  // 보여야 한다(하나가 죽었다고 둘 다 죽을 이유가 없다).
  const loadActivity = useCallback(async () => {
    if (!id) return;
    setActivity({ status: 'loading' });
    const result = await getTripActivity(id, accessToken);
    if (result.state === 'success') setActivity({ status: 'ready', entries: result.entries });
    else if (result.state === 'forbidden') setActivity({ status: 'hidden' });
    else setActivity({ status: 'error', message: result.message });
  }, [id, accessToken]);

  useFocusEffect(useCallback(() => { if (ready) { void load(); void loadActivity(); } }, [ready, load, loadActivity]));

  async function changeRole(member: TripMember, role: CompanionRole) {
    if (!id || busyUserId) return;
    setBusyUserId(member.userId);
    setActionError('');
    const result = await changeTripMemberRole(id, member.userId, role, accessToken);
    setBusyUserId(null);
    if (result.state === 'success') void load();
    else setActionError(result.message);
  }

  async function remove(member: TripMember) {
    if (!id || busyUserId) return;
    setBusyUserId(member.userId);
    setActionError('');
    const result = await removeTripMember(id, member.userId, accessToken);
    setBusyUserId(null);
    if (result.state === 'success') void load();
    else setActionError(result.message);
  }

  const isOwner = state.status === 'ready' && state.view.myRole === 'OWNER';

  return <Screen scroll wide style={styles.screen}>
    <View style={styles.topBar}><Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/trips')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}><Text variant="title" weight="bold">‹</Text></Pressable><BrandLogoLink href="/home" imageStyle={styles.logo} /><View style={styles.spacer} /></View>
    <View style={styles.heading}><Eyebrow>{tx('함께하는 여행', 'Trip together')}</Eyebrow><Text variant="display" weight="bold">{tx('참여자 관리', 'Manage participants')}</Text><Text color={color.text.body}>{tx('이 여행을 함께 보는 사람과 각자의 역할이에요.', "Everyone who can see this trip, and each person's role.")}</Text></View>

    {state.status === 'loading' && <View accessibilityLiveRegion="polite" style={styles.stateCard}><Text weight="bold">{tx('참여자를 불러오고 있어요.', 'Loading participants.')}</Text></View>}
    {state.status === 'forbidden' && <View style={styles.stateCard}><Text variant="title" weight="bold">{tx('이 여행의 참여자만 볼 수 있어요', 'Only participants of this trip can view this')}</Text><Text color={color.text.body}>{localizeMessage(tx, state.message)}</Text></View>}
    {state.status === 'error' && <View style={styles.stateCard}><Text variant="title" weight="bold">{tx('참여자를 불러오지 못했어요', "Couldn't load participants")}</Text><Text color={color.text.body}>{localizeMessage(tx, state.message)}</Text><Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void load()} /></View>}

    {state.status === 'ready' && <>
      <View style={styles.list}>{state.view.members.map((member) => {
        const busy = busyUserId === member.userId;
        return <View key={member.userId} style={styles.memberCard}>
          <View style={styles.memberInfo}>
            <Text weight="bold">{member.displayName ?? tx('(탈퇴한 사용자)', '(deleted user)')}{member.isMe ? tx(' · 나', ' · You') : ''}</Text>
            <View style={styles.roleBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx(...ROLE_LABEL[member.role])}</Text></View>
          </View>
          {isOwner && member.role !== 'OWNER' && <View style={styles.memberActions}>
            <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 역할을 %s로 바꾸기', "Change %s's role to %s", member.displayName ?? '', member.role === 'EDITOR' ? tx('열람자', 'viewer') : tx('편집자', 'editor'))} accessibilityState={{ disabled: busy }} disabled={busy} onPress={() => void changeRole(member, member.role === 'EDITOR' ? 'VIEWER' : 'EDITOR')} style={[styles.actionButton, busy && styles.actionDisabled]}>
              <Text variant="caption" weight="bold" color={color.brand.navy}>{tx(member.role === 'EDITOR' ? '열람자로 변경' : '편집자로 변경', member.role === 'EDITOR' ? 'Make viewer' : 'Make editor')}</Text>
            </Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 참여자 제거', 'Remove %s', member.displayName ?? '')} accessibilityState={{ disabled: busy }} disabled={busy} onPress={() => void remove(member)} style={[styles.actionButtonDanger, busy && styles.actionDisabled]}>
              <Text variant="caption" weight="bold" color={color.state.danger}>{busy ? tx('처리 중…', 'Working…') : tx('제거', 'Remove')}</Text>
            </Pressable>
          </View>}
        </View>;
      })}</View>

      {actionError && <View accessibilityRole="alert" style={styles.errorCard}><Text color={color.state.danger}>{actionError}</Text></View>}

      {activity.status !== 'hidden' && <View style={styles.activitySection}>
        <Text variant="title" weight="bold">{tx('최근 변경', 'Recent changes')}</Text>
        {activity.status === 'loading' && <Text variant="caption" color={color.text.muted}>{tx('불러오는 중…', 'Loading…')}</Text>}
        {activity.status === 'error' && <Text variant="caption" color={color.text.muted}>{tx('최근 변경을 불러오지 못했어요.', "Couldn't load recent changes.")}</Text>}
        {activity.status === 'ready' && activity.entries.length === 0 && <Text variant="caption" color={color.text.muted}>{tx('아직 바뀐 내용이 없어요.', 'No changes yet.')}</Text>}
        {activity.status === 'ready' && activity.entries.map((entry) => (
          <View key={`${entry.itineraryId}-${entry.version}`} style={styles.activityRow}>
            <Text variant="caption" color={color.text.body}>
              {txf(tx, '%s%s님이 ', '%s%s ', entry.actorName ?? tx('(탈퇴한 사용자)', '(deleted user)'), entry.isMe ? tx('(나)', ' (you)') : '')}
              {tx(...OPERATION_LABEL[entry.operation])}
              {` · ${relativeStoryTime(entry.at, tx)}`}
            </Text>
          </View>
        ))}
      </View>}

      {isOwner && <Button label={tx('동행자 초대하기', 'Invite a companion')} onPress={() => router.push(`/${id}/share`)} containerStyle={styles.inviteButton} />}
    </>}
  </Screen>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas }, topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card }, pressed: { opacity: 0.72, transform: [{ scale: 0.97 }] }, logo: { width: 154, height: 28 }, spacer: { width: 44 },
  heading: { gap: spacing[2], marginTop: spacing[4], marginBottom: spacing[6] }, stateCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  list: { gap: spacing[3] }, memberCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  memberInfo: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  roleBadge: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.brand.navy },
  memberActions: { flexDirection: 'row', gap: spacing[2] },
  actionButton: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy },
  actionButtonDanger: { minWidth: 88, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.state.dangerBg },
  actionDisabled: { opacity: 0.5 },
  errorCard: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.dangerBg },
  activitySection: { gap: spacing[2], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  activityRow: { paddingVertical: spacing[1] },
  inviteButton: { marginTop: spacing[4] },
});
