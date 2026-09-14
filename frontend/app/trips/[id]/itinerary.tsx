import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { PlaceReviewModal } from '@/components/PlaceReviewModal';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import {
  loadItinerary,
  loadItineraryPace,
  loadItineraryRhythm,
  loadItineraryVersions,
  pollItineraryJob,
  recalculateItineraryDay,
  recordItineraryItemActual,
  removeItineraryItem,
  replanItineraryDay,
  reorderItineraryDay,
  revertItinerary,
  setItineraryItemLocked,
  type ItineraryDto,
  type ItineraryItemDto,
  type ItineraryLoadResult,
  type ItineraryOpeningHoursNotChecked,
  type ItineraryOpeningHoursWarning,
  type ItineraryPaceDto,
  type ItineraryPaceItemDto,
  type ItineraryRhythmDto,
  type ItineraryVersionEntryDto,
} from '@/plan/itinerary';
import { loadPlaceReviews, submitPlaceReview } from '@/review/placeReviews';
import { useI18n } from '@/i18n';
import { ExcludeConfirmModal } from '@/components/ExcludeConfirmModal';

const WARNING_LABEL: Record<string, [string, string]> = {
  RECALC_NO_CANDIDATE: ['뺀 자리를 채울 다른 장소를 찾지 못해 비워 뒀어요.', "We couldn't find another place to fill the removed spot, so it's left empty."],
  RECALC_TIMES_RESHUFFLED: ['다시 계산하면서 고정된 장소의 시각도 함께 조정됐어요.', 'Recalculating also adjusted the times of locked places.'],
};

// 영업시간 경고(S15P21E201-268/-858) — 편집 다섯 갈래 중 넷(더하기 제외, 재계산은 비동기라
// 이 응답에 못 싣는다)이 warnings·notChecked를 함께 돌려준다. 되돌리기는 여러 날에 걸친
// 위반이 함께 올 수 있어 하루가 아니라 일정 전체에서 항목을 찾는다.
function describeOpeningHoursIssues(itinerary: ItineraryDto, warnings: ItineraryOpeningHoursWarning[], notChecked: ItineraryOpeningHoursNotChecked[], tx: (ko: string, en: string) => string): string[] {
  const itemsById = new Map(itinerary.days.flatMap((day) => day.items).map((item) => [item.id, item]));
  const closedMessages = warnings
    .filter((warning) => warning.code === 'OPENING_HOURS_CLOSED')
    .map((warning) => {
      const title = itemsById.get(warning.itemId)?.title ?? tx('이 장소', 'this place');
      return tx(`${title}은(는) 이 시각에 영업하지 않아요.`, `${title} is closed at this time.`);
    });
  const notCheckedMessages = notChecked.map((entry) => entry.reason === 'NOT_COLLECTED'
    ? tx('일부 장소는 영업시간 정보가 없어 확인하지 못했어요.', "We couldn't check opening hours for some places — no data yet.")
    : tx('시각이 없는 항목이 있어 일부는 확인하지 못했어요.', "Some items have no visit time, so we couldn't check them."));
  return [...closedMessages, ...notCheckedMessages];
}

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value.slice(11, 16) || value : date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });
}

function formatDate(value: string, index: number) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? `DAY ${index + 1}` : `${date.getMonth() + 1}.${date.getDate()} · DAY ${index + 1}`;
}

function formatDelay(delayMinutes: number, tx: (ko: string, en: string) => string) {
  if (delayMinutes === 0) return tx('정시', 'On time');
  const abs = Math.abs(Math.round(delayMinutes));
  return delayMinutes > 0 ? tx(`${abs}분 지연`, `${abs}m late`) : tx(`${abs}분 빠름`, `${abs}m early`);
}

function ItemCard({ item, displayTime, canEdit, lockBusy, excludeBusy, dayBusy, onLock, onExclude, reorderMode, canMoveUp, canMoveDown, moveBusy, onMoveUp, onMoveDown, pace, estimated, actualBusy, onRecordArrival, onRecordDeparture, accessToken }: { item: ItineraryItemDto; displayTime: string; canEdit: boolean; lockBusy: boolean; excludeBusy: boolean; dayBusy: boolean; onLock: () => void; onExclude: () => void; reorderMode: boolean; canMoveUp: boolean; canMoveDown: boolean; moveBusy: boolean; onMoveUp: () => void; onMoveDown: () => void; pace?: ItineraryPaceItemDto; estimated?: boolean; actualBusy?: boolean; onRecordArrival?: () => void; onRecordDeparture?: () => void; accessToken: string | null }) {
  const { tx } = useI18n();
  const disabled = !canEdit || lockBusy || excludeBusy || dayBusy;

  // 다녀오셨나요 평가(S15P21E201-406) — 방문 예정 시각이 지난 카드에만 띄운다.
  // 이미 평가했는지는 그 장소의 리뷰 목록에서 mine 표시로 안다(카드마다 한 번씩 확인).
  const isPastVisit = useMemo(() => new Date(item.startsAt).getTime() < Date.now(), [item.startsAt]);
  const [reviewStatus, setReviewStatus] = useState<'checking' | 'can-review' | 'reviewed'>('checking');
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  useEffect(() => {
    if (!isPastVisit) return;
    let cancelled = false;
    void loadPlaceReviews(item.placeId, accessToken).then((result) => {
      if (cancelled) return;
      setReviewStatus(result.state === 'success' && result.mine ? 'reviewed' : 'can-review');
    });
    return () => { cancelled = true; };
  }, [isPastVisit, item.placeId, accessToken]);
  const submitReview = async (scores: { food: 'LOW' | 'MID' | 'HIGH' | null; price: 'LOW' | 'MID' | 'HIGH' | null; accessibility: 'LOW' | 'MID' | 'HIGH' | null; onsite: 'LOW' | 'MID' | 'HIGH' | null }, body: string) => {
    const result = await submitPlaceReview({ placeId: item.placeId, ...scores, body, accessToken });
    if (result.state === 'success') { setReviewStatus('reviewed'); return true; }
    return false;
  };
  return <><View style={styles.itemRow}><Text variant="body" weight="bold" color={color.brand.orange} style={styles.time}>{formatTime(displayTime)}</Text><View style={[styles.itemCard, pace?.atRisk && styles.itemCardAtRisk]}><View style={styles.itemTitleRow}><View style={styles.grow}><Text variant="body" weight="bold">{item.title}</Text>{item.description ? <Text variant="caption" color={color.text.body}>{item.description}</Text> : null}</View><View style={styles.itemActions}>{reorderMode ? (item.locked ? <View style={styles.lockBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('고정됨', 'Locked')}</Text></View> : <View style={styles.moveButtons}><Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 위로 이동`, `Move ${item.title} up`)} accessibilityState={{ disabled: !canMoveUp || moveBusy }} disabled={!canMoveUp || moveBusy} onPress={onMoveUp} style={[styles.moveButton, (!canMoveUp || moveBusy) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>▲</Text></Pressable><Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 아래로 이동`, `Move ${item.title} down`)} accessibilityState={{ disabled: !canMoveDown || moveBusy }} disabled={!canMoveDown || moveBusy} onPress={onMoveDown} style={[styles.moveButton, (!canMoveDown || moveBusy) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>▼</Text></Pressable></View>) : <>{canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} ${item.locked ? '고정 해제' : '고정'}`, `${item.title} ${item.locked ? 'unlock' : 'lock'}`)} accessibilityState={{ selected: item.locked, busy: lockBusy, disabled }} disabled={disabled} onPress={onLock} style={[styles.lockButton, item.locked && styles.lockButtonActive, disabled && styles.actionDisabled]}><Text variant="caption" weight="bold" color={item.locked ? color.text.onAction : color.brand.navy}>{lockBusy ? tx('처리 중', 'Processing') : item.locked ? tx('고정됨', 'Locked') : tx('고정', 'Lock')}</Text></Pressable> : item.locked ? <View style={styles.lockBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('고정됨', 'Locked')}</Text></View> : null}<Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 제외`, `Exclude ${item.title}`)} accessibilityState={{ busy: excludeBusy, disabled }} disabled={disabled} onPress={onExclude} style={[styles.excludeButton, disabled && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.state.danger}>{excludeBusy ? tx('처리 중', 'Processing') : tx('제외', 'Exclude')}</Text></Pressable></>}</View></View><View style={styles.metaRow}><Text variant="caption" color={color.text.muted}>{item.walkingMeters == null ? tx('도보 미확인', 'Walking distance unconfirmed') : tx(`도보 ${item.walkingMeters.toLocaleString()}m`, `${item.walkingMeters.toLocaleString()}m walk`)}</Text><Text variant="caption" color={color.text.muted}>{item.estimatedCostKrw == null ? tx('비용 미확인', 'Cost unconfirmed') : tx(`${item.estimatedCostKrw.toLocaleString()}원`, `${item.estimatedCostKrw.toLocaleString()} KRW`)}</Text></View>
    {pace && !reorderMode ? <View style={styles.paceRow}>
      {pace.visited ? <Text variant="caption" weight="bold" color={color.state.success}>{tx(`도착 ${pace.predictedArrival ? formatTime(pace.predictedArrival) : '--:--'}${pace.predictedDeparture ? ` · 출발 ${formatTime(pace.predictedDeparture)}` : ''}`, `Arrived ${pace.predictedArrival ? formatTime(pace.predictedArrival) : '--:--'}${pace.predictedDeparture ? ` · Left ${formatTime(pace.predictedDeparture)}` : ''}`)}</Text>
        : <Text variant="caption" weight="bold" color={pace.atRisk ? color.state.danger : color.text.muted}>{tx(`예상 도착 ${pace.predictedArrival ? formatTime(pace.predictedArrival) : '--:--'}${estimated ? ' (추정)' : ''}`, `Est. arrival ${pace.predictedArrival ? formatTime(pace.predictedArrival) : '--:--'}${estimated ? ' (est.)' : ''}`)}{pace.delayMinutes != null ? ` · ${formatDelay(pace.delayMinutes, tx)}` : ''}{pace.atRisk ? ` · ${tx('하루를 넘길 위험', 'Risks running past the day')}` : ''}</Text>}
      {(onRecordArrival || onRecordDeparture) ? <View style={styles.actualButtons}>
        {!pace.visited ? <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 도착 찍기`, `Mark arrival at ${item.title}`)} accessibilityState={{ busy: actualBusy }} disabled={actualBusy} onPress={onRecordArrival} style={[styles.actualButton, actualBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('도착 찍기', 'Mark arrival')}</Text></Pressable>
          : !pace.predictedDeparture ? <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 출발 찍기`, `Mark departure at ${item.title}`)} accessibilityState={{ busy: actualBusy }} disabled={actualBusy} onPress={onRecordDeparture} style={[styles.actualButton, actualBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('출발 찍기', 'Mark departure')}</Text></Pressable> : null}
      </View> : null}
    </View> : null}
    {isPastVisit && !reorderMode ? (
      reviewStatus === 'reviewed' ? <View style={styles.reviewedBadge}><Text variant="caption" weight="bold" color={color.state.success}>{tx('평가함', 'Reviewed')}</Text></View>
      : reviewStatus === 'can-review' ? <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 다녀오셨나요? 평가하기`, `Review your visit to ${item.title}`)} onPress={() => setReviewModalOpen(true)} style={styles.reviewButton}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('다녀오셨나요?', 'Did you visit?')}</Text></Pressable>
      : null
    ) : null}
  </View></View>
  {isPastVisit ? <PlaceReviewModal visible={reviewModalOpen} placeTitle={item.title} onClose={() => setReviewModalOpen(false)} onSubmit={submitReview} /> : null}
  </>;
}

type ViewMode = 'day' | 'all';

export default function ItineraryScreen() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { id, day: dayParam, view: viewParam } = useLocalSearchParams<{ id: string; day?: string; view?: string }>();
  const { tx } = useI18n();
  const itineraryId = id ?? '';
  const [result, setResult] = useState<ItineraryLoadResult>({ state: 'error', message: tx('일정 식별자가 없어요.', 'Missing itinerary identifier.') });
  const [loading, setLoading] = useState(Boolean(itineraryId));
  // 날짜는 사람이 보는 주소에서는 1일차부터 세지만(?day=2), 내부 배열 인덱스는 0부터다.
  const [selectedDay, setSelectedDay] = useState(() => { const requested = Number(dayParam); return Number.isInteger(requested) && requested >= 1 ? requested - 1 : 0; });
  const [dayOutOfRange, setDayOutOfRange] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>(viewParam === 'all' ? 'all' : 'day');
  const [busyItemId, setBusyItemId] = useState<string | null>(null);
  const [excludingItemId, setExcludingItemId] = useState<string | null>(null);
  const [excludeConfirming, setExcludeConfirming] = useState<ItineraryItemDto | null>(null);
  const [dayActionBusy, setDayActionBusy] = useState(false);
  const [revertBusy, setRevertBusy] = useState(false);
  const [conflict, setConflict] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [versions, setVersions] = useState<ItineraryVersionEntryDto[]>([]);
  const [orderDraft, setOrderDraft] = useState<string[] | null>(null);
  const [reorderBusy, setReorderBusy] = useState(false);
  const [pace, setPace] = useState<ItineraryPaceDto | null>(null);
  const [rhythm, setRhythm] = useState<ItineraryRhythmDto | null>(null);
  const [actualBusyItemId, setActualBusyItemId] = useState<string | null>(null);
  const [replanConfirming, setReplanConfirming] = useState(false);
  const [replanBusy, setReplanBusy] = useState(false);
  const [replanOverflowIds, setReplanOverflowIds] = useState<string[] | null>(null);
  const [syncDisconnected, setSyncDisconnected] = useState(false);
  // 순서 바꾸기 응답에만 실려 오는 영업시간 경고(S15P21E201-268/-852) — 활동 이력엔 안 남으므로
  // 그 자리에서 받은 문장을 이 상태에 직접 담아 둔다. 다음 편집을 시작하면 지운다.
  const [openingHoursNotice, setOpeningHoursNotice] = useState<string[]>([]);

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
      setSelectedDay((current) => {
        const lastDay = Math.max(0, next.itinerary.days.length - 1);
        if (current > lastDay) { setDayOutOfRange(true); return 0; }
        return current;
      });
      void refreshVersions(next.itinerary.id);
    }
  }, [accessToken, itineraryId, refreshVersions]);

  useEffect(() => { void reload(); }, [reload]);

  // 동행자의 변경을 실시간으로 받아온다(S15P21E201-323). 진행 중인 내 편집(잠금·제외·
  // 순서 변경 등) 위에 서버 응답이 덮어써 충돌하지 않도록, 그런 조작이 도는 동안은
  // 이번 주기를 건너뛴다 — 편집이 끝나면 각 함수가 자체적으로 reload()를 부른다.
  const pollBlockedRef = useRef(false);
  useEffect(() => {
    pollBlockedRef.current = Boolean(busyItemId) || excludingItemId !== null || excludeConfirming !== null || dayActionBusy || revertBusy || orderDraft !== null || reorderBusy || replanBusy || actualBusyItemId !== null;
  });
  const failureStreakRef = useRef(0);
  useEffect(() => {
    if (!itineraryId) return;
    const timer = setInterval(() => {
      if (pollBlockedRef.current) return;
      void loadItinerary(itineraryId, accessToken).then((next) => {
        if (next.state === 'success') {
          failureStreakRef.current = 0;
          setSyncDisconnected(false);
          setResult((prev) => (prev.state === 'success' && prev.itinerary.version === next.itinerary.version) ? prev : next);
        } else {
          failureStreakRef.current += 1;
          if (failureStreakRef.current >= 3) setSyncDisconnected(true);
        }
      });
    }, 5000);
    return () => clearInterval(timer);
  }, [accessToken, itineraryId]);

  const manualSyncRefresh = async () => {
    await reload();
    failureStreakRef.current = 0;
    setSyncDisconnected(false);
  };

  const itinerary = result.state === 'success' ? result.itinerary : null;
  const day = itinerary?.days[selectedDay];
  const totals = useMemo(() => ({ cost: itinerary?.totalEstimatedCostKrw, walk: itinerary?.totalWalkingMeters }), [itinerary]);
  const lockedCount = useMemo(() => itinerary?.days.flatMap((entry) => entry.items).filter((entry) => entry.locked).length ?? 0, [itinerary]);
  const canEdit = itinerary?.canEdit !== false;
  const latestWarnings = useMemo(() => versions[0]?.warningCodes?.map((code) => (WARNING_LABEL[code] ? tx(...WARNING_LABEL[code]) : code)) ?? [], [versions, tx]);
  const reorderMode = orderDraft !== null;
  const slotTimes = useMemo(() => day?.items.map((item) => item.startsAt) ?? [], [day]);
  const displayedItems = useMemo(() => {
    if (!day) return [];
    if (!orderDraft) return day.items;
    const itemsById = new Map(day.items.map((item) => [item.id, item]));
    return orderDraft.map((itemId) => itemsById.get(itemId)).filter((entry): entry is ItineraryItemDto => Boolean(entry));
  }, [day, orderDraft]);
  const canReorder = canEdit && (day?.items.filter((item) => !item.locked).length ?? 0) > 1;

  // 지연 경고(S15P21E201-96·314). 날짜를 바꾸면 그 날짜 것을 새로 받는다 — 표본이
  // 모자라면 paceFactor가 null로 오고, 그건 1.0(계획대로)과 다른 뜻이라 그대로 둔다.
  useEffect(() => {
    if (!itinerary) { setPace(null); return; }
    let cancelled = false;
    void loadItineraryPace(itinerary.id, selectedDay, accessToken).then((next) => {
      if (!cancelled) setPace(next.state === 'success' ? next.pace : null);
    });
    return () => { cancelled = true; };
  }, [itinerary?.id, itinerary?.version, selectedDay, accessToken]);

  // 리듬 요약은 여행 전체 값이라 날짜와 무관하게 한 번만 받는다.
  useEffect(() => {
    if (!itinerary) { setRhythm(null); return; }
    let cancelled = false;
    void loadItineraryRhythm(itinerary.id, accessToken).then((next) => {
      if (!cancelled) setRhythm(next.state === 'success' ? next.rhythm : null);
    });
    return () => { cancelled = true; };
  }, [itinerary?.id, itinerary?.version, accessToken]);

  const paceByItemId = useMemo(() => new Map((pace?.items ?? []).map((entry) => [entry.itemId, entry] as const)), [pace]);
  const paceEstimated = pace?.paceFactor == null;

  // S15P21E201-911 — 도착·출발 기록 PUT은 itinerary.version을 안 올린다(actualArrivedAt만
  // 바뀐다, 실측 확인). pace를 불러오는 effect는 version 변화로만 재실행되므로, 그 effect에
  // 기대서는 "도착 찍기" 직후 화면이 영영 안 바뀐다 — 여기서 명시적으로 다시 불러온다.
  const refreshPaceAfterActual = async () => {
    if (!itinerary) return;
    const next = await loadItineraryPace(itinerary.id, selectedDay, accessToken);
    if (next.state === 'success') setPace(next.pace);
  };

  const recordArrival = async (item: ItineraryItemDto) => {
    if (!itinerary) return;
    setActualBusyItemId(item.id);
    const outcome = await recordItineraryItemActual({ itineraryId: itinerary.id, itemId: item.id, arrivedAt: new Date().toISOString(), departedAt: null, accessToken });
    setActualBusyItemId(null);
    if (outcome.state === 'success') { setResult({ state: 'success', itinerary: outcome.itinerary }); void refreshPaceAfterActual(); }
    else if (outcome.state !== 'conflict') setActionMessage(outcome.message);
  };

  const recordDeparture = async (item: ItineraryItemDto) => {
    if (!itinerary) return;
    // PUT은 보낸 것이 최종 상태다(부분 갱신이 아니다) — 이미 기록된 도착 시각을
    // 함께 실어 보내지 않으면 출발만 남고 도착이 null로 지워진다.
    const existingArrival = paceByItemId.get(item.id)?.predictedArrival ?? null;
    setActualBusyItemId(item.id);
    const outcome = await recordItineraryItemActual({ itineraryId: itinerary.id, itemId: item.id, arrivedAt: existingArrival, departedAt: new Date().toISOString(), accessToken });
    setActualBusyItemId(null);
    if (outcome.state === 'success') { setResult({ state: 'success', itinerary: outcome.itinerary }); void refreshPaceAfterActual(); }
    else if (outcome.state !== 'conflict') setActionMessage(outcome.message);
  };

  const confirmReplan = async () => {
    if (!itinerary) return;
    setReplanBusy(true); setConflict(null); setActionMessage(null); setReplanOverflowIds(null); setOpeningHoursNotice([]);
    const outcome = await replanItineraryDay({ itineraryId: itinerary.id, dayIndex: selectedDay, baseVersion: itinerary.version, accessToken });
    setReplanBusy(false); setReplanConfirming(false);
    if (outcome.state === 'success') {
      setResult({ state: 'success', itinerary: outcome.itinerary });
      setActionMessage(tx('남은 일정을 다시 계획했어요.', 'Replanned the rest of the day.'));
      setOpeningHoursNotice(describeOpeningHoursIssues(outcome.itinerary, outcome.warnings, outcome.notChecked, tx));
    }
    else if (outcome.state === 'conflict') setConflict(outcome.message);
    else if (outcome.state === 'overflow') { setReplanOverflowIds(outcome.itemIds); setActionMessage(outcome.message); }
    else setActionMessage(outcome.message);
  };

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
    setBusyItemId(item.id); setConflict(null); setActionMessage(null); setOpeningHoursNotice([]);
    const next = await setItineraryItemLocked({ itineraryId: itinerary.id, itemId: item.id, locked: !item.locked, baseVersion: itinerary.version, accessToken });
    setBusyItemId(null);
    if (next.state === 'success') { setResult({ state: 'success', itinerary: next.itinerary }); setOpeningHoursNotice(describeOpeningHoursIssues(next.itinerary, next.warnings, next.notChecked, tx)); }
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
    if (accepted.state === 'accepted') {
      if (await runJob(accepted.jobId)) { await reload(); setActionMessage(tx('이 날짜를 다시 계산했어요.', 'Recalculated this day.')); }
    }
    else if (accepted.state === 'conflict') setConflict(accepted.message);
    else setActionMessage(accepted.message);
    setDayActionBusy(false);
  };

  const revert = async () => {
    if (!itinerary) return;
    setRevertBusy(true); setConflict(null); setActionMessage(null); setOpeningHoursNotice([]);
    const outcome = await revertItinerary({ itineraryId: itinerary.id, baseVersion: itinerary.version, accessToken });
    setRevertBusy(false);
    if (outcome.state === 'success') {
      setResult({ state: 'success', itinerary: outcome.itinerary });
      void refreshVersions(outcome.itinerary.id);
      setActionMessage(tx('최근 변경을 되돌렸어요.', 'Reverted your last change.'));
      setOpeningHoursNotice(describeOpeningHoursIssues(outcome.itinerary, outcome.warnings, outcome.notChecked, tx));
    }
    else if (outcome.state === 'conflict') setConflict(outcome.message);
    else if (outcome.state === 'noOp') setActionMessage(outcome.message);
    else setResult(outcome);
  };

  const startReorder = () => {
    if (!day) return;
    setOrderDraft(day.items.map((item) => item.id));
    setConflict(null); setActionMessage(null); setOpeningHoursNotice([]);
  };

  const cancelReorder = () => setOrderDraft(null);

  // 날짜를 바꿀 때마다 주소도 같이 바꾼다 — router.setParams는 다시 불러오지 않고
  // 주소창만 갱신한다(완료 기준: "탭 전환 시 네트워크 요청이 발생하지 않는다").
  // 그래서 "2일차를 보고 있다"는 링크를 그대로 복사해 보낼 수 있다.
  const selectDay = (index: number) => {
    setOrderDraft(null);
    setDayOutOfRange(false);
    setSelectedDay(index);
    router.setParams({ day: String(index + 1) });
  };

  const selectView = (mode: ViewMode) => {
    setOrderDraft(null);
    setViewMode(mode);
    router.setParams({ view: mode });
  };

  const moveDraftItem = (index: number, delta: -1 | 1) => {
    if (!day || !orderDraft) return;
    const targetIndex = index + delta;
    if (targetIndex < 0 || targetIndex >= orderDraft.length) return;
    const itemsById = new Map(day.items.map((item) => [item.id, item]));
    const current = itemsById.get(orderDraft[index]);
    const target = itemsById.get(orderDraft[targetIndex]);
    if (!current || !target || current.locked || target.locked) return;
    const next = [...orderDraft];
    [next[index], next[targetIndex]] = [next[targetIndex], next[index]];
    setOrderDraft(next);
  };

  const saveReorder = async () => {
    if (!itinerary || !orderDraft) return;
    setReorderBusy(true); setConflict(null); setActionMessage(null); setOpeningHoursNotice([]);
    const outcome = await reorderItineraryDay({ itineraryId: itinerary.id, dayIndex: selectedDay, itemKeys: orderDraft, baseVersion: itinerary.version, accessToken });
    setReorderBusy(false);
    if (outcome.state === 'success') {
      setResult({ state: 'success', itinerary: outcome.itinerary });
      setOrderDraft(null);
      setActionMessage(tx('순서를 저장했어요.', 'Saved the new order.'));
      setOpeningHoursNotice(describeOpeningHoursIssues(outcome.itinerary, outcome.warnings, outcome.notChecked, tx));
    } else if (outcome.state === 'conflict') setConflict(outcome.message);
    else setActionMessage(outcome.message);
  };

  return <View style={styles.shell}><Screen scroll wide withTabBar style={styles.canvas}><View style={styles.header}><Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={styles.back}><Text variant="title">‹</Text></Pressable><View style={styles.headerTitle}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx('나의 부산 여행', 'My Busan trip')}</Text><Text variant="display" weight="bold">{itinerary?.title ?? tx('여행 일정', 'Itinerary')}</Text></View>{itinerary ? <View style={styles.headerActions}><View style={styles.version}><Text variant="caption" weight="bold">v{itinerary.version}</Text></View>{canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={tx('최근 변경 취소', 'Undo last change')} accessibilityState={{ busy: revertBusy, disabled: revertBusy || versions.length <= 1 }} disabled={revertBusy || versions.length <= 1} onPress={() => void revert()} style={[styles.revertButton, (revertBusy || versions.length <= 1) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{revertBusy ? tx('처리 중', 'Processing') : tx('최근 변경 취소', 'Undo last change')}</Text></Pressable> : null}</View> : <View style={styles.headerSpacer} />}</View>
    {loading ? <View accessibilityLabel={tx('일정을 불러오고 있어요', 'Loading itinerary')} style={styles.timeline}>{[0, 1, 2].map((key) => (
      <View key={key} style={styles.itemRow}>
        <Skeleton width={40} height={16} style={styles.time} />
        <View style={styles.itemCard}><Skeleton width="60%" height={16} /><View style={styles.metaRow}><Skeleton width="30%" height={12} /><Skeleton width="30%" height={12} /></View></View>
      </View>
    ))}</View> : null}
    {!loading && result.state !== 'success' ? <View style={styles.stateCard}><Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : result.state === 'unavailable' ? tx('일정 API를 기다리고 있어요', 'Waiting for the itinerary API') : tx('일정을 불러오지 못했어요', 'Could not load the itinerary')}</Text><Text color={color.text.body}>{result.message}</Text><Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void reload()} /></View> : null}
    {!loading && itinerary ? <><View style={styles.stats}><View style={styles.stat}><Text variant="title" weight="bold">{totals.cost == null ? tx('미확인', 'Unconfirmed') : tx(`${totals.cost.toLocaleString()}원`, `${totals.cost.toLocaleString()} KRW`)}</Text><Text variant="caption" color={color.text.muted}>{tx('예상 비용', 'Estimated cost')}</Text></View><View style={styles.stat}><Text variant="title" weight="bold">{totals.walk == null ? tx('미확인', 'Unconfirmed') : `${totals.walk.toLocaleString()}m`}</Text><Text variant="caption" color={color.text.muted}>{tx('총 도보', 'Total walking')}</Text></View><View style={styles.stat}><Text variant="title" weight="bold">{tx(`${itinerary.days.length}일`, `${itinerary.days.length} days`)}</Text><Text variant="caption" color={color.text.muted}>{tx('여행 기간', 'Trip length')}</Text></View><View style={styles.stat}><Text variant="title" weight="bold">{tx(`고정 ${lockedCount}곳`, `${lockedCount} pinned`)}</Text><Text variant="caption" color={color.text.muted}>{tx('고정된 장소', 'Pinned places')}</Text></View></View>
      {!canEdit ? <View style={styles.viewerNotice}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('보기 전용 — 이 일정을 편집할 권한이 없어요.', "View only — you don't have permission to edit this itinerary.")}</Text></View> : null}
      {rhythm ? <View style={styles.rhythmCard}>
        <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('여행 리듬', 'Trip rhythm')}</Text>
        <Text variant="caption" color={color.text.body}>{tx(`하루 평균 ${rhythm.averageItemsPerDay}곳`, `${rhythm.averageItemsPerDay} places/day avg.`)}{rhythm.travelShare != null ? tx(` · 이동 비중 ${Math.round(rhythm.travelShare * 100)}%`, ` · ${Math.round(rhythm.travelShare * 100)}% travel time`) : tx(' · 이동 비중 미확인', ' · travel share unconfirmed')}{rhythm.plannedVsActual != null ? tx(` · 계획 대비 실제 ${rhythm.plannedVsActual}배`, ` · ${rhythm.plannedVsActual}x planned pace`) : ''}</Text>
      </View> : null}
      {syncDisconnected ? <View accessibilityRole="alert" style={styles.conflict}><Text variant="body" weight="bold">{tx('실시간 동기화가 끊겼습니다', 'Live sync lost')}</Text><Text variant="caption" color={color.text.body}>{tx('네트워크 연결을 확인해 주세요. 보고 있는 화면은 최신이 아닐 수 있어요.', 'Please check your network connection. What you see may not be up to date.')}</Text><Button label={tx('새로고침', 'Refresh')} variant="ghost" onPress={() => void manualSyncRefresh()} /></View> : null}
      {conflict ? <View accessibilityRole="alert" style={styles.conflict}><Text variant="body" weight="bold">{tx('최신 일정과 충돌했어요', 'Conflicted with the latest itinerary')}</Text><Text variant="caption" color={color.text.body}>{conflict}</Text><Button label={tx('최신 일정 불러오기', 'Load latest itinerary')} variant="ghost" onPress={() => void reload()} /></View> : null}
      {actionMessage ? <View accessibilityRole="alert" style={styles.actionNotice}><Text variant="caption" color={color.text.body}>{actionMessage}</Text></View> : null}
      {latestWarnings.length ? <View style={styles.warningNotice}>{latestWarnings.map((message, index) => <Text key={index} variant="caption" color={color.text.body}>{message}</Text>)}</View> : null}
      {openingHoursNotice.length ? <View accessibilityRole="alert" style={styles.warningNotice}>{openingHoursNotice.map((message, index) => <Text key={index} variant="caption" color={color.text.body}>{message}</Text>)}</View> : null}
      {dayOutOfRange ? <View style={styles.actionNotice}><Text variant="caption" color={color.text.body}>{tx(`요청한 날짜가 없어서 1일차를 보여드려요. (전체 ${itinerary.days.length}일)`, `That day doesn't exist, so day 1 is shown instead. (${itinerary.days.length} days total)`)}</Text></View> : null}

      <View accessibilityRole="tablist" style={styles.dayTabs}>
        <Pressable accessibilityRole="tab" accessibilityState={{ selected: viewMode === 'all' }} onPress={() => selectView('all')} style={[styles.dayTab, viewMode === 'all' && styles.dayTabActive]}><Text variant="caption" weight="bold" color={viewMode === 'all' ? color.text.onAction : color.text.body}>{tx('전체 일정', 'All days')}</Text></Pressable>
        <Pressable accessibilityRole="tab" accessibilityState={{ selected: viewMode === 'day' }} onPress={() => selectView('day')} style={[styles.dayTab, viewMode === 'day' && styles.dayTabActive]}><Text variant="caption" weight="bold" color={viewMode === 'day' ? color.text.onAction : color.text.body}>{tx('날짜별 보기', 'By day')}</Text></Pressable>
      </View>

      {viewMode === 'all' ? (
        <View style={styles.timeline}>
          {itinerary.days.map((entry, dayIndex) => (
            <View key={`${entry.date}-${dayIndex}`}>
              <Pressable accessibilityRole="button" accessibilityLabel={tx(`${dayIndex + 1}일차만 보기`, `View only day ${dayIndex + 1}`)} onPress={() => { selectView('day'); selectDay(dayIndex); }} style={styles.allDayHeading}>
                <Text variant="title" weight="bold">{formatDate(entry.date, dayIndex)}</Text>
              </Pressable>
              {entry.items.length ? entry.items.map((item) => (
                <ItemCard key={item.id} item={item} displayTime={item.startsAt} canEdit={false} lockBusy={false} excludeBusy={false} dayBusy={false} onLock={() => {}} onExclude={() => {}} reorderMode={false} canMoveUp={false} canMoveDown={false} moveBusy={false} onMoveUp={() => {}} onMoveDown={() => {}} accessToken={accessToken} />
              )) : <View style={styles.empty}><Text variant="caption" color={color.text.muted}>{tx('이 날짜에는 아직 장소가 없어요.', 'No places for this day yet.')}</Text></View>}
            </View>
          ))}
        </View>
      ) : (
        <>
          <View accessibilityRole="tablist" style={styles.dayTabs}>{itinerary.days.map((entry, index) => <Pressable key={`${entry.date}-${index}`} accessibilityRole="tab" accessibilityState={{ selected: selectedDay === index }} onPress={() => selectDay(index)} style={[styles.dayTab, selectedDay === index && styles.dayTabActive]}><Text variant="caption" weight="bold" color={selectedDay === index ? color.text.onAction : color.text.body}>{formatDate(entry.date, index)}</Text></Pressable>)}</View>
          <View style={styles.sectionHeading}><Text variant="title" weight="bold">{tx('일정 요약', 'Itinerary summary')}</Text><View style={styles.sectionHeadingRight}><Text variant="caption" color={color.text.muted}>{tx(`${day?.items.length ?? 0}개 장소`, `${day?.items.length ?? 0} places`)}</Text>{reorderMode ? <><Pressable accessibilityRole="button" accessibilityLabel={tx('순서 변경 취소', 'Cancel reordering')} accessibilityState={{ disabled: reorderBusy }} disabled={reorderBusy} onPress={cancelReorder} style={[styles.recalcButton, reorderBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('취소', 'Cancel')}</Text></Pressable><Pressable accessibilityRole="button" accessibilityLabel={tx('순서 저장', 'Save order')} accessibilityState={{ busy: reorderBusy }} disabled={reorderBusy} onPress={() => void saveReorder()} style={[styles.recalcButtonPrimary, reorderBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.text.onAction}>{reorderBusy ? tx('저장 중', 'Saving') : tx('저장', 'Save')}</Text></Pressable></> : <>{canReorder ? <Pressable accessibilityRole="button" accessibilityLabel={tx('일정 순서 변경', 'Reorder itinerary')} accessibilityState={{ disabled: excludingItemId !== null }} disabled={excludingItemId !== null} onPress={startReorder} style={[styles.recalcButton, excludingItemId !== null && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('순서 변경', 'Reorder')}</Text></Pressable> : null}{canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={tx('이 날짜 다시 계산', 'Recalculate this day')} accessibilityState={{ busy: dayActionBusy }} disabled={dayActionBusy || !day?.items.length || excludingItemId !== null} onPress={() => void recalculateDay()} style={[styles.recalcButton, (dayActionBusy || !day?.items.length || excludingItemId !== null) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{dayActionBusy ? tx('계산 중', 'Calculating') : tx('다시 계산', 'Recalculate')}</Text></Pressable> : null}{canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={tx('남은 하루 다시 계획', 'Replan the rest of the day')} accessibilityState={{ busy: replanBusy }} disabled={replanBusy || !day?.items.length || excludingItemId !== null} onPress={() => setReplanConfirming(true)} style={[styles.recalcButton, (replanBusy || !day?.items.length || excludingItemId !== null) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('다시 계획', 'Replan')}</Text></Pressable> : null}</>}</View></View>
          {pace?.atRiskItemIds.length ? <View accessibilityRole="alert" style={styles.warningNotice}><Text variant="caption" weight="bold" color={color.state.danger}>{tx(`${pace.atRiskItemIds.length}곳이 하루를 넘길 위험이 있어요.${paceEstimated ? ' (기록이 적어 추정값이에요)' : ''}`, `${pace.atRiskItemIds.length} place(s) risk running past the day.${paceEstimated ? ' (estimated — few records yet)' : ''}`)}</Text></View> : null}
          {replanConfirming ? <View accessibilityRole="alert" style={styles.actionNotice}>
            <Text variant="body" weight="bold">{tx('남은 방문지의 시각을 다시 매길까요?', 'Retime the remaining visits?')}</Text>
            <Text variant="caption" color={color.text.body}>{tx('지나간 방문지는 그대로 두고, 안 간 방문지의 시각만 지금 시각 기준으로 다시 매겨요.', "Past visits stay as they are — only the times of the visits you haven't made yet are recalculated from now.")}</Text>
            <View style={styles.replanActions}>
              <Pressable accessibilityRole="button" accessibilityLabel={tx('다시 계획 취소', 'Cancel replan')} disabled={replanBusy} onPress={() => setReplanConfirming(false)} style={[styles.recalcButton, replanBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('취소', 'Cancel')}</Text></Pressable>
              <Pressable accessibilityRole="button" accessibilityLabel={tx('다시 계획 확인', 'Confirm replan')} accessibilityState={{ busy: replanBusy }} disabled={replanBusy} onPress={() => void confirmReplan()} style={[styles.recalcButtonPrimary, replanBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.text.onAction}>{replanBusy ? tx('처리 중', 'Working') : tx('확인', 'Confirm')}</Text></Pressable>
            </View>
          </View> : null}
          {replanOverflowIds?.length ? <View accessibilityRole="alert" style={styles.warningNotice}><Text variant="caption" color={color.text.body}>{tx(`넘치는 방문지 ${replanOverflowIds.length}곳 — 하루 안에 다 들어가지 않아요.`, `${replanOverflowIds.length} visit(s) overflow — they don't fit in the day.`)}</Text></View> : null}
          {reorderMode ? <View style={styles.reorderHint}><Text variant="caption" color={color.text.body}>{tx('화살표로 순서를 바꾼 뒤 저장하세요. 고정된 장소는 자리를 옮길 수 없어요.', 'Use the arrows to reorder, then save. Locked places keep their spot.')}</Text></View> : null}
          {displayedItems.length ? <View style={styles.timeline}>{displayedItems.map((item, index) => <ItemCard key={item.id} item={item} displayTime={slotTimes[index] ?? item.startsAt} canEdit={canEdit} lockBusy={busyItemId === item.id} excludeBusy={excludingItemId === item.id} dayBusy={dayActionBusy || excludingItemId !== null} onLock={() => void toggleLock(item)} onExclude={() => setExcludeConfirming(item)} reorderMode={reorderMode} canMoveUp={index > 0 && !item.locked && !displayedItems[index - 1].locked} canMoveDown={index < displayedItems.length - 1 && !item.locked && !displayedItems[index + 1].locked} moveBusy={reorderBusy} onMoveUp={() => moveDraftItem(index, -1)} onMoveDown={() => moveDraftItem(index, 1)} pace={paceByItemId.get(item.id)} estimated={paceEstimated} actualBusy={actualBusyItemId === item.id} onRecordArrival={() => void recordArrival(item)} onRecordDeparture={() => void recordDeparture(item)} accessToken={accessToken} />)}</View> : <View style={styles.empty}><Text variant="body" weight="bold">{tx('이 날짜에는 아직 장소가 없어요.', 'No places for this day yet.')}</Text></View>}
        </>
      )}
    </> : null}
  </Screen>
  <ExcludeConfirmModal
    visible={excludeConfirming !== null}
    placeTitle={excludeConfirming?.title ?? ''}
    busy={excludingItemId !== null}
    onCancel={() => setExcludeConfirming(null)}
    onConfirm={() => {
      const target = excludeConfirming;
      setExcludeConfirming(null);
      if (target) void excludeItem(target);
    }}
  />
  <TabBar active="map" /></View>;
}

const styles = StyleSheet.create({ shell: { flex: 1, backgroundColor: color.brand.ivory }, canvas: { backgroundColor: color.brand.ivory }, header: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginBottom: spacing[6] }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, headerTitle: { flex: 1, gap: spacing[1] }, headerSpacer: { width: 44 }, headerActions: { alignItems: 'flex-end', gap: spacing[2] }, version: { minWidth: 44, minHeight: 32, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, revertButton: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center', borderWidth: 1, borderColor: color.surface.field }, stats: { flexDirection: 'row', gap: spacing[2] }, stat: { flex: 1, minHeight: 88, padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card, justifyContent: 'center', gap: spacing[1] }, viewerNotice: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, conflict: { gap: spacing[2], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.state.dangerBg }, actionNotice: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, warningNotice: { gap: spacing[1], marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint }, dayTabs: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[6] }, dayTab: { minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center' }, dayTabActive: { backgroundColor: color.brand.navy, borderColor: color.brand.navy }, allDayHeading: { marginTop: spacing[6], marginBottom: spacing[1] }, sectionHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: spacing[6] }, sectionHeadingRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, recalcButton: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, recalcButtonPrimary: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, reorderHint: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, moveButtons: { flexDirection: 'row', gap: spacing[2] }, moveButton: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, timeline: { gap: spacing[3], marginTop: spacing[3] }, itemRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] }, time: { width: 48, paddingTop: spacing[4] }, itemCard: { flex: 1, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card }, itemTitleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2] }, grow: { flex: 1, gap: spacing[1] }, itemActions: { flexDirection: 'row', gap: spacing[2] }, lockButton: { minWidth: 64, minHeight: 44, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, lockButtonActive: { backgroundColor: color.brand.navy }, lockBadge: { minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, excludeButton: { minWidth: 56, minHeight: 44, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.state.dangerBg, alignItems: 'center', justifyContent: 'center' }, actionDisabled: { opacity: 0.5 }, metaRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] }, empty: { marginTop: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' }, rhythmCard: { gap: spacing[1], marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint }, itemCardAtRisk: { borderWidth: 1, borderColor: color.state.danger }, paceRow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], marginTop: spacing[1] }, actualButtons: { flexDirection: 'row', gap: spacing[2] }, actualButton: { minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, replanActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] }, reviewButton: { alignSelf: 'flex-start', minHeight: 44, paddingHorizontal: spacing[3], marginTop: spacing[1], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, reviewedBadge: { alignSelf: 'flex-start', minHeight: 28, paddingHorizontal: spacing[3], marginTop: spacing[1], borderRadius: radius.full, backgroundColor: color.state.successBg, alignItems: 'center', justifyContent: 'center' } });
