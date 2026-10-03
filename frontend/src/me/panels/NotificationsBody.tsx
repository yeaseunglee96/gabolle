// 알림 본문 — 화면(`app/notifications.tsx`)과 마이페이지 패널이 같은 것을 쓴다.
//
// 뒤로 가는 머리는 화면만 그린다. 패널은 껍데기가 이미 닫는 자리를 준다.
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, AppState, Image, Linking, Platform, Pressable, StyleSheet, View } from 'react-native';
import * as ExpoNotifications from 'expo-notifications';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { formatDayHeading } from '@/i18n/datetime';
import { groupLines, groupNotices, hasUnseen, loadActivityFeed, loadSeenAt, markSeenNow, noticeCopy, noticeKind, type ActivityNotice, type NoticeGroup } from '@/notifications/activityFeed';
import { NoticeIcon } from '@/components/NoticeIcon';
import { dismissAll, dismissNotices, loadDismissed, visibleNotices, type DismissedNotices } from '@/notifications/dismissedNotices';
import { txf } from '@/i18n/format';
import { relativeStoryTime } from '@/social/stories';

const bellIcon = require('../../../assets/icons/home/bell.png');


/**
 * 「오늘」 묶음 — 기기 현지 날짜로 가른다(S15P21E201-1771).
 * 🔴 전에는 UTC 날짜끼리 비교해서(toISOString().slice(0, 10)) 한국 새벽 0~9시에는 어제 알림이 「오늘」에 섞였다
 *    (Play 37 실기기, 9/27 04시에 9/26 13시 알림이 「오늘」).
 */
export function splitToday<T extends { at: string }>(items: T[], now: Date = new Date()): { today: T[]; earlier: T[] } {
  const key = (d: Date) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
  const todayKey = key(now);
  const today: T[] = [];
  const earlier: T[] = [];
  for (const item of items) {
    const at = new Date(item.at);
    (!Number.isNaN(at.getTime()) && key(at) === todayKey ? today : earlier).push(item);
  }
  return { today, earlier };
}

/**
 * 빈 화면이 「다 지워서 빈 것」인지 「원래 없는 것」인지(S15P21E201-1976).
 * 지운 것인데 「아직 도착한 알림이 없어요」라고 하면 알림이 사라진 줄로 안다.
 */
export function emptyNoticeState(receivedCount: number, shownCount: number): 'cleared' | 'none' {
  return receivedCount > 0 && shownCount === 0 ? 'cleared' : 'none';
}

export function NotificationsBody() {
  const { tx, language, locale } = useI18n();
  const router = useRouter();
  const { accessToken, user } = useAuth();
  // 🔴 알림은 여행 활동에서 나온다(S15P21E201-1380) — 서버 알림 API 가 없어 늘 비어 있던 화면이었다.
  const [feed, setFeed] = useState<{ state: 'loading' } | { state: 'ready'; items: ActivityNotice[]; seenAt: string | null } | { state: 'error'; message: string }>({ state: 'loading' });
  // 지운 알림(이 기기·계정) — S15P21E201-1907.
  const [dismissed, setDismissed] = useState<DismissedNotices>({ clearedBefore: null, ids: [] });
  useEffect(() => { let alive = true; void loadDismissed(user?.userId).then((d) => { if (alive) setDismissed(d); }); return () => { alive = false; }; }, [user?.userId]);
  useEffect(() => {
    let active = true;
    if (!user) { setFeed({ state: 'ready', items: [], seenAt: null }); return undefined; }
    (async () => {
      const [result, seenAt] = await Promise.all([loadActivityFeed(accessToken, tx, locale), loadSeenAt()]);
      if (!active) return;
      setFeed(result.state === 'success' ? { state: 'ready', items: result.items, seenAt } : { state: 'error', message: result.message });
      // 목록을 본 순간부터는 읽은 것이다 — 점은 다음에 새것이 올 때만 다시 뜬다.
      if (result.state === 'success' && result.items.length) void markSeenNow();
    })();
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, user?.userId]);
  const [permission, setPermission] = useState<'loading' | 'granted' | 'denied' | 'undetermined' | 'unsupported'>('loading');
  const [busy, setBusy] = useState(false);

  const refreshPermission = useCallback(async () => {
    if (Platform.OS === 'web') { setPermission('unsupported'); return; }
    try {
      const result = await ExpoNotifications.getPermissionsAsync();
      setPermission(result.status);
    } catch {
      setPermission('unsupported');
    }
  }, []);

  useEffect(() => {
    void refreshPermission();
    const subscription = AppState.addEventListener('change', (state) => { if (state === 'active') void refreshPermission(); });
    return () => subscription.remove();
  }, [refreshPermission]);

  async function requestPermission() {
    if (busy) return;
    setBusy(true);
    try {
      const result = await ExpoNotifications.requestPermissionsAsync();
      setPermission(result.status);
    } finally {
      setBusy(false);
    }
  }

  const statusCopy = permission === 'granted'
    ? { label: tx('알림 허용됨', 'Notifications allowed'), body: tx('여행 일정 알림을 받을 준비가 됐어요.', "You're ready to receive trip schedule alerts."), tone: color.state.success }
    : permission === 'denied'
      ? { label: tx('알림 꺼짐', 'Notifications off'), body: tx('기기 설정에서 언제든 다시 허용할 수 있어요.', 'You can allow them again anytime in device settings.'), tone: color.state.danger }
      : permission === 'unsupported'
        ? { label: tx('앱에서 설정 가능', 'Available in the app'), body: tx('알림 권한 관리는 iOS·Android 앱에서 제공해요.', 'Notification permissions are managed in the iOS/Android app.'), tone: color.text.muted }
        : permission === 'loading'
          ? { label: tx('권한 확인 중', 'Checking permission'), body: tx('기기의 알림 설정을 확인하고 있어요.', "We're checking your device's notification settings."), tone: color.text.muted }
          : { label: tx('알림을 켜볼까요?', 'Turn on notifications?'), body: tx('허용 여부를 먼저 물어본 뒤에만 알림을 보내요.', "We'll only send notifications after you allow it."), tone: color.action.primary };

  const permissionCard = (
    <>
      <View accessibilityLiveRegion="polite" style={styles.permissionCard}>
        <View style={[styles.statusDot, { backgroundColor: statusCopy.tone }]} />
        <View style={styles.permissionCopy}><Text variant="body" weight="bold">{statusCopy.label}</Text><Text variant="caption" color={color.text.body}>{statusCopy.body}</Text></View>
      </View>
      {permission === 'undetermined' && <Button label={busy ? tx('확인 중…', 'Checking…') : tx('알림 허용하기', 'Allow notifications')} disabled={busy} onPress={() => void requestPermission()} containerStyle={styles.action} />}
      {permission === 'denied' && <Button label={tx('기기 알림 설정 열기', 'Open device notification settings')} variant="tertiary" onPress={() => void Linking.openSettings()} containerStyle={styles.action} />}
    </>
  );

  if (feed.state === 'loading') {
    return <View style={styles.empty}><ActivityIndicator color={color.action.primary} /></View>;
  }

  const shownItems = feed.state === 'ready' ? visibleNotices(feed.items, dismissed) : [];
  const removeGroup = (group: NoticeGroup) => { if (user) void dismissNotices(user.userId, dismissed, group.items.map((item) => item.id)).then(setDismissed); };
  const removeAll = () => {
    if (!user || !shownItems.length || feed.state !== 'ready') return;
    void dismissAll(user.userId, feed.items).then((next) => { if (next) setDismissed(next); });
  };

  if (feed.state === 'ready' && shownItems.length) {
    const unseenFrom = feed.seenAt;
    const isFresh = (item: ActivityNotice) => (unseenFrom ? item.at > unseenFrom : true);
    const groups = groupNotices(shownItems);
    // 🔴 동행이 바꾼 것 중 아직 안 본 것은 맨 위에 — 「장소가 빠졌어요」 한 줄이면 중요한지 모르고 지나갔다(UI 캔버스 ⑦).
    const companionFresh = groups.filter((group) => !group.latest.isMe && group.latest.operation !== 'CREATE' && group.items.some(isFresh));
    const rest = groups.filter((group) => !companionFresh.includes(group));
    const { today, earlier } = splitToday(rest.map((group) => ({ ...group, at: group.latest.at })));
    const open = (group: NoticeGroup) => router.push(`/trips/${group.latest.itineraryId}/itinerary`);
    const trip = (group: NoticeGroup) => txf(tx, '「%s」', '“%s”', group.latest.tripTitle);

    // 동행이 바꾼 일정 — 누가 · 무엇을 · 몇 곳을, 그리고 「일정에서 확인하기」.
    const companionCard = (group: NoticeGroup) => (
      <View key={group.id} style={styles.companionCard}>
        <View style={styles.companionHead}>
          <View style={styles.actorBadge}><Text weight="bold" color={color.text.onAction}>{(group.latest.actorName ?? '·').trim().slice(0, 1)}</Text></View>
          <View style={styles.rowBody}>
            <View style={styles.rowTitle}>
              <Text weight="bold" numberOfLines={2} style={styles.rowTitleText}>{group.latest.actorName ? txf(tx, '%s님이 일정을 바꿨어요', '%s changed your trip', group.latest.actorName) : tx('동행이 일정을 바꿨어요', 'A companion changed your trip')}</Text>
              <View style={styles.freshDot} />
            </View>
            <Text variant="caption" color={color.text.muted} numberOfLines={1}>{`${trip(group)} · ${relativeStoryTime(group.latest.at, tx)}`}</Text>
          </View>
        </View>
        <View style={styles.changeLines}>
          {groupLines(group, tx).map((line) => (
            <View key={line.kind} style={styles.changeLine}>
              <NoticeIcon kind={line.kind} tint={color.text.body} size={18} />
              <Text variant="caption" color={color.text.body}>{line.text}</Text>
            </View>
          ))}
        </View>
        <Button label={tx('일정에서 확인하기', 'Review in itinerary')} variant="secondary" compact onPress={() => open(group)} />
      </View>
    );

    // 한 장 — 하나면 전과 같은 문구, 여럿이면 「내가 한 변경 N건」·「○○님이 바꾼 것 N건」으로 접는다.
    const row = (group: NoticeGroup) => {
      const item = group.latest;
      const many = group.items.length > 1;
      const copy = noticeCopy(item, tx);
      const title = !many ? copy.title : item.isMe ? txf(tx, '내가 한 변경 %s건', '%s changes you made', String(group.items.length)) : item.actorName ? txf(tx, '%s님이 일정을 바꿨어요', '%s changed your trip', item.actorName) : tx('일정이 바뀌었어요', 'Your itinerary changed');
      const body = !many ? copy.body : `${trip(group)} · ${groupLines(group, tx).map((line) => line.text).join(' · ')}`;
      // 묶음이 한 종류뿐이면(고정 10건) 그 아이콘 — 네모(여러 종류)는 섞였을 때만.
      const lines = many ? groupLines(group, tx) : [];
      const kind = many ? (lines.length === 1 ? lines[0].kind : 'change') : noticeKind(item.operation);
      return (
        <View key={group.id} style={styles.rowWrap}>
        <Pressable accessibilityRole="button" accessibilityLabel={title} onPress={() => open(group)} style={({ pressed }) => [styles.row, styles.rowFlex, pressed && styles.pressed]}>
          <View style={[styles.rowIcon, kind === 'created' && styles.rowIconCreate]}>
            <NoticeIcon kind={kind} tint={kind === 'created' ? color.state.success : color.text.heading} />
          </View>
          <View style={styles.rowBody}>
            <View style={styles.rowTitle}>
              <Text weight="bold" numberOfLines={1} style={styles.rowTitleText}>{title}</Text>
              {group.items.some(isFresh) ? <View style={styles.freshDot} /> : null}
            </View>
            <Text variant="caption" color={color.text.body} numberOfLines={2}>{body}</Text>
            <Text variant="micro" color={color.text.muted}>{relativeStoryTime(item.at, tx)}</Text>
          </View>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 알림 지우기', 'Remove notification: %s', title)} hitSlop={8} onPress={() => removeGroup(group)} style={({ pressed }) => [styles.remove, pressed && styles.pressed]}>
          <Text variant="body" color={color.text.muted}>✕</Text>
        </Pressable>
        </View>
      );
    };
    return (
      <View style={styles.list}>
        <View style={styles.listHead}>
          <Pressable accessibilityRole="button" onPress={removeAll} style={({ pressed }) => [styles.clearAll, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={color.text.muted}>{tx('모두 지우기', 'Clear all')}</Text>
          </Pressable>
        </View>
        {companionFresh.length ? <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('동행이 바꾼 일정 · 확인해 주세요', 'Changed by companions · please review')}</Text> : null}
        {companionFresh.map(companionCard)}
        {today.length ? <Text variant="caption" weight="bold" color={color.text.eyebrow} style={companionFresh.length ? styles.sectionGap : undefined}>{tx('오늘', 'Today')}</Text> : null}
        {today.map(row)}
        {earlier.length ? <Text variant="caption" weight="bold" color={color.text.eyebrow} style={styles.sectionGap}>{tx('이전 알림', 'Earlier')}</Text> : null}
        {earlier.map(row)}
        <Text variant="caption" color={color.text.muted} style={styles.footnote}>{tx('일정이 만들어지거나 바뀌면 이곳에서 알려드려요. 동행이 바꾼 것도 보여요.', "We'll tell you here when a trip is created or changed — including changes by companions.")}</Text>
        <View style={styles.permissionWrap}>{permissionCard}</View>
      </View>
    );
  }

  const cleared = feed.state === 'ready' && emptyNoticeState(feed.items.length, shownItems.length) === 'cleared';
  return (
      <View style={styles.empty}>
        <View style={styles.icon}><Image source={bellIcon} resizeMode="contain" style={styles.iconImage} /></View>
        <Text variant="title" weight="bold">{cleared ? tx('알림을 모두 지웠어요', 'All notifications cleared') : tx('아직 도착한 알림이 없어요', 'No notifications yet')}</Text>
        <Text variant="body" color={color.text.muted} style={styles.description}>{cleared ? tx('새 알림이 오면 여기에 보여요.', 'New notifications will show up here.') : user ? tx('여행 일정이 만들어지거나 바뀌면 이곳에서 알려드려요.', "We'll let you know here when a trip is created or changed.") : tx('로그인하면 내 여행의 소식을 여기서 볼 수 있어요.', 'Sign in to see updates about your trips here.')}</Text>
        {!user ? <Button label={tx('로그인', 'Sign in')} variant="outline" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/notifications' } })} containerStyle={styles.action} /> : null}
        {feed.state === 'error' ? <Text variant="caption" color={color.state.danger}>{feed.message}</Text> : null}
        {permissionCard}
      </View>
  );
}

const styles = StyleSheet.create({
  rowWrap: { flexDirection: 'row', alignItems: 'center', gap: spacing[1] },
  rowFlex: { flex: 1, minWidth: 0 },
  remove: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  listHead: { flexDirection: 'row', justifyContent: 'flex-end' },
  clearAll: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[2] },
  list: { gap: spacing[2], paddingTop: spacing[3] },
  sectionGap: { marginTop: spacing[3] },
  row: { flexDirection: 'row', gap: spacing[3], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  rowIcon: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.soft },
  rowIconCreate: { backgroundColor: color.state.successBg },
  companionCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  companionHead: { flexDirection: 'row', gap: spacing[3], alignItems: 'center' },
  actorBadge: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  changeLines: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  changeLine: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  rowBody: { flex: 1, minWidth: 0, gap: 2 },
  rowTitle: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  rowTitleText: { flexShrink: 1 },
  freshDot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.action.outline },
  footnote: { textAlign: 'center', marginTop: spacing[3] },
  permissionWrap: { alignItems: 'center', gap: spacing[3] },
  pressed: { opacity: 0.85 },
  empty: { flex: 1, minHeight: 420, alignItems: 'center', justifyContent: 'center', gap: spacing[3], paddingHorizontal: spacing[6] },
  icon: { width: 72, height: 72, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.state.warningBg },
  iconImage: { width: 32, height: 32 },
  description: { textAlign: 'center', lineHeight: 23 },
  permissionCard: { width: '100%', maxWidth: 360, minHeight: 72, marginTop: spacing[4], padding: spacing[4], flexDirection: 'row', alignItems: 'center', gap: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  statusDot: { width: 10, height: 10, borderRadius: radius.full },
  permissionCopy: { flex: 1, gap: spacing[1] },
  // 가운데 정렬 안에서는 껍데기가 글자 폭으로 줄어 버튼이 쪼그라든다 — 폭을 적어야 360 까지 편다(S15P21E201-1524).
  action: { width: '100%', maxWidth: 360 },
});
