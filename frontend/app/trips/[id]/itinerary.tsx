import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import {
  loadItinerary,
  loadItineraryVersions,
  pollItineraryJob,
  recalculateItineraryDay,
  removeItineraryItem,
  revertItinerary,
  setItineraryItemLocked,
  type ItineraryDto,
  type ItineraryItemDto,
  type ItineraryLoadResult,
  type ItineraryVersionEntryDto,
} from '@/plan/itinerary';
import { rememberItinerary } from '@/trip/tripLibrary';
import { useI18n } from '@/i18n';

const WARNING_LABEL: Record<string, [string, string]> = {
  RECALC_NO_CANDIDATE: ['뺀 자리를 채울 다른 장소를 찾지 못해 비워 뒀어요.', "We couldn't find another place to fill the removed spot, so it's left empty."],
  RECALC_TIMES_RESHUFFLED: ['다시 계산하면서 고정된 장소의 시각도 함께 조정됐어요.', 'Recalculating also adjusted the times of locked places.'],
};

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value.slice(11, 16) || value : date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });
}

function formatDate(value: string, index: number) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? `DAY ${index + 1}` : `${date.getMonth() + 1}.${date.getDate()} · DAY ${index + 1}`;
}

function ItemCard({ item, canEdit, lockBusy, excludeBusy, onLock, onExclude }: { item: ItineraryItemDto; canEdit: boolean; lockBusy: boolean; excludeBusy: boolean; onLock: () => void; onExclude: () => void }) {
  const { tx } = useI18n();
  const disabled = !canEdit || lockBusy || excludeBusy;
  return <View style={styles.itemRow}><Text variant="body" weight="bold" color={color.brand.orange} style={styles.time}>{formatTime(item.startsAt)}</Text><View style={styles.itemCard}><View style={styles.itemTitleRow}><View style={styles.grow}><Text variant="body" weight="bold">{item.title}</Text>{item.description ? <Text variant="caption" color={color.text.body}>{item.description}</Text> : null}</View><View style={styles.itemActions}><Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} ${item.locked ? '고정 해제' : '고정'}`, `${item.title} ${item.locked ? 'unlock' : 'lock'}`)} accessibilityState={{ selected: item.locked, busy: lockBusy, disabled }} disabled={disabled} onPress={onLock} style={[styles.lockButton, item.locked && styles.lockButtonActive, disabled && styles.actionDisabled]}><Text variant="caption" weight="bold" color={item.locked ? color.text.onAction : color.brand.navy}>{lockBusy ? tx('처리 중', 'Processing') : item.locked ? tx('고정됨', 'Locked') : tx('고정', 'Lock')}</Text></Pressable><Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 제외`, `Exclude ${item.title}`)} accessibilityState={{ busy: excludeBusy, disabled }} disabled={disabled} onPress={onExclude} style={[styles.excludeButton, disabled && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.state.danger}>{excludeBusy ? tx('처리 중', 'Processing') : tx('제외', 'Exclude')}</Text></Pressable></View></View><View style={styles.metaRow}><Text variant="caption" color={color.text.muted}>{item.walkingMeters == null ? tx('도보 미확인', 'Walking distance unconfirmed') : tx(`도보 ${item.walkingMeters.toLocaleString()}m`, `${item.walkingMeters.toLocaleString()}m walk`)}</Text><Text variant="caption" color={color.text.muted}>{item.estimatedCostKrw == null ? tx('비용 미확인', 'Cost unconfirmed') : tx(`${item.estimatedCostKrw.toLocaleString()}원`, `${item.estimatedCostKrw.toLocaleString()} KRW`)}</Text></View></View></View>;
}

export default function ItineraryScreen() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { tx } = useI18n();
  const itineraryId = id ?? '';
  const [result, setResult] = useState<ItineraryLoadResult>({ state: 'error', message: tx('일정 식별자가 없어요.', 'Missing itinerary identifier.') });
  const [loading, setLoading] = useState(Boolean(itineraryId));
  const [selectedDay, setSelectedDay] = useState(0);
  const [busyItemId, setBusyItemId] = useState<string | null>(null);
  const [excludingItemId, setExcludingItemId] = useState<string | null>(null);
  const [dayActionBusy, setDayActionBusy] = useState(false);
  const [revertBusy, setRevertBusy] = useState(false);
  const [conflict, setConflict] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [versions, setVersions] = useState<ItineraryVersionEntryDto[]>([]);

  const refreshVersions = useCallback(async (targetId: string) => {
    const next = await loadItineraryVersions(targetId, accessToken);
    if (next.state === 'success') setVersions(next.versions);
  }, [accessToken]);

  const reload = useCallback(async () => {
    if (!itineraryId) return;
    setLoading(true); setConflict(null); setActionMessage(null);
    const next = await loadItinerary(itineraryId, accessToken);
    setResult(next); setLoading(false);
    if (next.state === 'success') {
      setSelectedDay((current) => Math.min(current, Math.max(0, next.itinerary.days.length - 1)));
      void rememberItinerary(next.itinerary);
      void refreshVersions(next.itinerary.id);
    }
  }, [accessToken, itineraryId, refreshVersions]);

  useEffect(() => { void reload(); }, [reload]);

  const itinerary = result.state === 'success' ? result.itinerary : null;
  const day = itinerary?.days[selectedDay];
  const totals = useMemo(() => ({ cost: itinerary?.totalEstimatedCostKrw, walk: itinerary?.totalWalkingMeters }), [itinerary]);
  const canEdit = itinerary?.canEdit !== false;
  const latestWarnings = useMemo(() => versions[0]?.warningCodes?.map((code) => (WARNING_LABEL[code] ? tx(...WARNING_LABEL[code]) : code)) ?? [], [versions, tx]);

  const runJob = async (jobId: string): Promise<boolean> => {
    for (;;) {
      const poll = await pollItineraryJob(jobId, accessToken);
      if (poll.state === 'pending') { await new Promise((resolve) => setTimeout(resolve, 2000)); continue; }
      if (poll.state === 'succeeded') return true;
      if (poll.state === 'conflict') { setConflict(poll.message); return false; }
      setActionMessage(poll.message); return false;
    }
  };

  const toggleLock = async (item: ItineraryItemDto) => {
    if (!itinerary) return;
    setBusyItemId(item.id); setConflict(null); setActionMessage(null);
    const next = await setItineraryItemLocked({ itineraryId: itinerary.id, itemId: item.id, locked: !item.locked, baseVersion: itinerary.version, accessToken });
    setBusyItemId(null);
    if (next.state === 'success') setResult({ state: 'success', itinerary: next.itinerary });
    else if (next.state === 'conflict') setConflict(next.message);
    else setResult(next);
  };

  const excludeItem = async (item: ItineraryItemDto) => {
    if (!itinerary) return;
    setExcludingItemId(item.id); setConflict(null); setActionMessage(null);
    const accepted = await removeItineraryItem({ itineraryId: itinerary.id, itemId: item.id, baseVersion: itinerary.version, accessToken });
    if (accepted.state === 'accepted') { if (await runJob(accepted.jobId)) await reload(); }
    else if (accepted.state === 'conflict') setConflict(accepted.message);
    else setActionMessage(accepted.message);
    setExcludingItemId(null);
  };

  const recalculateDay = async () => {
    if (!itinerary) return;
    setDayActionBusy(true); setConflict(null); setActionMessage(null);
    const accepted = await recalculateItineraryDay({ itineraryId: itinerary.id, baseVersion: itinerary.version, dayIndex: selectedDay, accessToken });
    if (accepted.state === 'accepted') { if (await runJob(accepted.jobId)) await reload(); }
    else if (accepted.state === 'conflict') setConflict(accepted.message);
    else setActionMessage(accepted.message);
    setDayActionBusy(false);
  };

  const revert = async () => {
    if (!itinerary) return;
    setRevertBusy(true); setConflict(null); setActionMessage(null);
    const outcome = await revertItinerary({ itineraryId: itinerary.id, baseVersion: itinerary.version, accessToken });
    setRevertBusy(false);
    if (outcome.state === 'success') { setResult({ state: 'success', itinerary: outcome.itinerary }); void refreshVersions(outcome.itinerary.id); }
    else if (outcome.state === 'conflict') setConflict(outcome.message);
    else if (outcome.state === 'noOp') setActionMessage(outcome.message);
    else setResult(outcome);
  };

  return <View style={styles.shell}><Screen scroll wide style={styles.canvas}><View style={styles.header}><Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={styles.back}><Text variant="title">‹</Text></Pressable><View style={styles.headerTitle}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('나의 부산 여행', 'My Busan trip')}</Text><Text variant="display" weight="bold">{itinerary?.title ?? tx('여행 일정', 'Itinerary')}</Text></View>{itinerary ? <View style={styles.headerActions}><View style={styles.version}><Text variant="caption" weight="bold">v{itinerary.version}</Text></View>{canEdit && versions.length > 1 ? <Pressable accessibilityRole="button" accessibilityLabel={tx('되돌리기', 'Revert')} accessibilityState={{ busy: revertBusy }} disabled={revertBusy} onPress={() => void revert()} style={[styles.revertButton, revertBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{revertBusy ? tx('처리 중', 'Processing') : tx('되돌리기', 'Revert')}</Text></Pressable> : null}</View> : <View style={styles.headerSpacer} />}</View>
    {loading ? <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.brand.orange} /><Text variant="title" weight="bold">{tx('일정을 불러오고 있어요', 'Loading itinerary')}</Text></View> : null}
    {!loading && result.state !== 'success' ? <View style={styles.stateCard}><Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : result.state === 'unavailable' ? tx('일정 API를 기다리고 있어요', 'Waiting for the itinerary API') : tx('일정을 불러오지 못했어요', 'Could not load the itinerary')}</Text><Text color={color.text.body}>{result.message}</Text><Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void reload()} /></View> : null}
    {!loading && itinerary ? <><View style={styles.stats}><View style={styles.stat}><Text variant="title" weight="bold">{totals.cost == null ? tx('미확인', 'Unconfirmed') : tx(`${totals.cost.toLocaleString()}원`, `${totals.cost.toLocaleString()} KRW`)}</Text><Text variant="caption" color={color.text.muted}>{tx('예상 비용', 'Estimated cost')}</Text></View><View style={styles.stat}><Text variant="title" weight="bold">{totals.walk == null ? tx('미확인', 'Unconfirmed') : `${totals.walk.toLocaleString()}m`}</Text><Text variant="caption" color={color.text.muted}>{tx('총 도보', 'Total walking')}</Text></View><View style={styles.stat}><Text variant="title" weight="bold">{tx(`${itinerary.days.length}일`, `${itinerary.days.length} days`)}</Text><Text variant="caption" color={color.text.muted}>{tx('여행 기간', 'Trip length')}</Text></View></View>
      {!canEdit ? <View style={styles.viewerNotice}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('보기 전용 — 이 일정을 편집할 권한이 없어요.', "View only — you don't have permission to edit this itinerary.")}</Text></View> : null}
      {conflict ? <View accessibilityRole="alert" style={styles.conflict}><Text variant="body" weight="bold">{tx('최신 일정과 충돌했어요', 'Conflicted with the latest itinerary')}</Text><Text variant="caption" color={color.text.body}>{conflict}</Text><Button label={tx('최신 일정 불러오기', 'Load latest itinerary')} variant="ghost" onPress={() => void reload()} /></View> : null}
      {actionMessage ? <View accessibilityRole="alert" style={styles.actionNotice}><Text variant="caption" color={color.text.body}>{actionMessage}</Text></View> : null}
      {latestWarnings.length ? <View style={styles.warningNotice}>{latestWarnings.map((message, index) => <Text key={index} variant="caption" color={color.text.body}>{message}</Text>)}</View> : null}
      <View accessibilityRole="tablist" style={styles.dayTabs}>{itinerary.days.map((entry, index) => <Pressable key={`${entry.date}-${index}`} accessibilityRole="tab" accessibilityState={{ selected: selectedDay === index }} onPress={() => setSelectedDay(index)} style={[styles.dayTab, selectedDay === index && styles.dayTabActive]}><Text variant="caption" weight="bold" color={selectedDay === index ? color.text.onAction : color.text.body}>{formatDate(entry.date, index)}</Text></Pressable>)}</View>
      <View style={styles.sectionHeading}><Text variant="title" weight="bold">{tx('일정 요약', 'Itinerary summary')}</Text><View style={styles.sectionHeadingRight}><Text variant="caption" color={color.text.muted}>{tx(`${day?.items.length ?? 0}개 장소`, `${day?.items.length ?? 0} places`)}</Text>{canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={tx('이 날짜 다시 계산', 'Recalculate this day')} accessibilityState={{ busy: dayActionBusy }} disabled={dayActionBusy || !day?.items.length} onPress={() => void recalculateDay()} style={[styles.recalcButton, (dayActionBusy || !day?.items.length) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{dayActionBusy ? tx('계산 중', 'Calculating') : tx('다시 계산', 'Recalculate')}</Text></Pressable> : null}</View></View>
      {day?.items.length ? <View style={styles.timeline}>{day.items.map((item) => <ItemCard key={item.id} item={item} canEdit={canEdit} lockBusy={busyItemId === item.id} excludeBusy={excludingItemId === item.id} onLock={() => void toggleLock(item)} onExclude={() => void excludeItem(item)} />)}</View> : <View style={styles.empty}><Text variant="body" weight="bold">{tx('이 날짜에는 아직 장소가 없어요.', 'No places for this day yet.')}</Text></View>}
    </> : null}
  </Screen><TabBar active="map" /></View>;
}

const styles = StyleSheet.create({ shell: { flex: 1, backgroundColor: color.brand.ivory }, canvas: { backgroundColor: color.brand.ivory }, header: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginBottom: spacing[6] }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, headerTitle: { flex: 1, gap: spacing[1] }, headerSpacer: { width: 44 }, headerActions: { alignItems: 'flex-end', gap: spacing[2] }, version: { minWidth: 44, minHeight: 32, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, revertButton: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center', borderWidth: 1, borderColor: color.surface.field }, stats: { flexDirection: 'row', gap: spacing[2] }, stat: { flex: 1, minHeight: 88, padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card, justifyContent: 'center', gap: spacing[1] }, viewerNotice: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, conflict: { gap: spacing[2], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.state.dangerBg }, actionNotice: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, warningNotice: { gap: spacing[1], marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint }, dayTabs: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[6] }, dayTab: { minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center' }, dayTabActive: { backgroundColor: color.brand.navy, borderColor: color.brand.navy }, sectionHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: spacing[6] }, sectionHeadingRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, recalcButton: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, timeline: { gap: spacing[3], marginTop: spacing[3] }, itemRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] }, time: { width: 48, paddingTop: spacing[4] }, itemCard: { flex: 1, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card }, itemTitleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2] }, grow: { flex: 1, gap: spacing[1] }, itemActions: { flexDirection: 'row', gap: spacing[2] }, lockButton: { minWidth: 64, minHeight: 44, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, lockButtonActive: { backgroundColor: color.brand.navy }, excludeButton: { minWidth: 56, minHeight: 44, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.state.dangerBg, alignItems: 'center', justifyContent: 'center' }, actionDisabled: { opacity: 0.5 }, metaRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] }, empty: { marginTop: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' } });
