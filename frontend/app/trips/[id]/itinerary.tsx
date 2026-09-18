import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { PlaceReviewModal } from '@/components/PlaceReviewModal';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, gutter, radius, spacing } from '@/design/tokens';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
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
import { nextSyncPollDelay, SYNC_POLL_BASE_MS } from '@/plan/syncPoll';
import { formatTravelLabel, itineraryStats, totalTravelMinutes } from '@/plan/itinerarySummary';
import { loadPlaceReviews, submitPlaceReview } from '@/review/placeReviews';
import { useI18n } from '@/i18n';
import { describeWarningCodes } from '@/plan/warningLabels';
import { ExcludeConfirmModal } from '@/components/ExcludeConfirmModal';

// 경고 문구는 src/plan/warningLabels.ts 로 옮겼다 — 시험이 붙들게 하려고

// 영업시간 경고/-858) — 편집 다섯 갈래 중 넷(더하기 제외, 재계산은 비동기라
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

// 1000m 이상은 km 한 자리로 (시안 1절). 「1200m」보다 「1.2km」가 걷는 거리로 읽힌다.
function formatWalk(meters: number) {
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)}km` : `${meters}m`;
}

// 「9월 19일 (금)」 — 시안 3.2. 날짜를 못 읽으면 지어내지 않고 「n일차」로만 적는다.
function formatDayHeading(value: string, index: number, tx: (ko: string, en: string) => string) {
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return tx(`${index + 1}일차`, `Day ${index + 1}`);
  const weekday = ['일', '월', '화', '수', '목', '금', '토'][date.getDay()];
  return tx(`${date.getMonth() + 1}월 ${date.getDate()}일 (${weekday})`, date.toLocaleDateString('en-US', { month: 'long', day: 'numeric', weekday: 'short' }));
}

function formatDate(value: string, index: number) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? `DAY ${index + 1}` : `${date.getMonth() + 1}.${date.getDate()} · DAY ${index + 1}`;
}

// 지도 대신 노선도 — 디자인 확정안 B안(09-디자인-인계-일정).
function RouteStrip({ items, times, tx }: { items: ItineraryItemDto[]; times: string[]; tx: (ko: string, en: string) => string }) {
  if (!items.length) return null;
  return <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.strip}>
    {items.map((item, index) => {
      const travel = [
        item.walkingMeters == null ? null : tx(`도보 ${formatWalk(item.walkingMeters)}`, `${formatWalk(item.walkingMeters)} walk`),
        formatTravelLabel(item, tx, index === 0),
      ].filter(Boolean).join(' · ');
      return <View key={item.id} style={styles.stripEntry}>
        {/* — 여태 index > 0 이라 그날 첫 구간이 통째로 안 그려졌다.
            서버는 순서 1번 구간(출발지 → 첫 장소)을 제대로 주는데 화면이 건너뛰었고
            그게 하루 중 제일 긴 구간이라(실측 38분·40분) 구간을 더한 값이 요약과 안 맞았다
            — 1일차 화면 3+3+1=7분 대 요약 45분. 값이 있으면 첫 칸에도 그린다.
        */}
        {index > 0 || travel ? <View style={styles.segment}>
          <View style={styles.segmentLine} />
          {/* 구간 라벨이 없을 수도 있다 — 좌표가 없어 못 잰 구간. 없으면 선만 긋는다. */}
          {travel ? <Text variant="caption" weight="bold" color={color.brand.navy}>{travel}</Text> : null}
        </View> : null}
        <View style={styles.stop}>
          <View style={[styles.node, index === 0 && styles.nodeFirst]}><Text variant="caption" weight="bold" color={color.text.onAction}>{index + 1}</Text></View>
          <Text variant="caption" weight="bold" numberOfLines={1} style={styles.stopName}>{item.title}</Text>
          <Text variant="caption" color={color.text.muted}>{formatTime(times[index] ?? item.startsAt)}</Text>
        </View>
      </View>;
    })}
  </ScrollView>;
}

// 정차 한 칸 — 시안 design_handoff_itinerary 2.5(넓은 화면) 3.3(폰).
function StopRow({ item, index, isLast, displayTime, wide, expanded, onToggleExpand, canEdit, lockBusy, excludeBusy, dayBusy, onLock, onExclude, reorderMode, canMoveUp, canMoveDown, moveBusy, onMoveUp, onMoveDown, pace, estimated, actualBusy, onRecordArrival, onRecordDeparture, accessToken }: { item: ItineraryItemDto; index: number; isLast: boolean; displayTime: string; wide: boolean; expanded: boolean; onToggleExpand: () => void; canEdit: boolean; lockBusy: boolean; excludeBusy: boolean; dayBusy: boolean; onLock: () => void; onExclude: () => void; reorderMode: boolean; canMoveUp: boolean; canMoveDown: boolean; moveBusy: boolean; onMoveUp: () => void; onMoveDown: () => void; pace?: ItineraryPaceItemDto; estimated?: boolean; actualBusy?: boolean; onRecordArrival?: () => void; onRecordDeparture?: () => void; accessToken: string | null }) {
  const { tx } = useI18n();
  const disabled = !canEdit || lockBusy || excludeBusy || dayBusy;

  // 다녀오셨나요 평가 — 방문 예정 시각이 지난 칸에만 띄운다.
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

  // 이 방문지 자료가 어디까지 확인된 것인가 (시안 1절). now.tsx·recommendations.tsx 와 같은
  // 문구를 쓴다 — 같은 값을 화면마다 다르게 부르면 안 된다.
  const STATUS_LABEL = { VERIFIED: tx('확인됨', 'Verified'), ESTIMATED: tx('추정', 'Estimated'), UNKNOWN: tx('미확인', 'Unconfirmed') } as const;
  const STATUS_CHIP = { VERIFIED: styles.statusVerified, ESTIMATED: styles.statusEstimated, UNKNOWN: styles.statusUnknown } as const;
  const STATUS_COLOR = { VERIFIED: color.state.success, ESTIMATED: color.state.warning, UNKNOWN: color.text.muted } as const;

  // 값이 없으면 칸을 만들지 않는다. 비용·도보는 서버에 자료가 없을 때가 있고, 그걸
  // 「미확인」이라고 적어 두면 빈 칸이 화면에서 제일 눈에 띈다.
  const walkLabel = item.walkingMeters == null ? null : tx(`도보 ${formatWalk(item.walkingMeters)}`, `${formatWalk(item.walkingMeters)} walk`);
  const facts = [
    walkLabel,
    formatTravelLabel(item, tx, index === 0),
    item.estimatedCostKrw == null ? null : item.estimatedCostKrw === 0 ? tx('무료', 'Free') : tx(`${item.estimatedCostKrw.toLocaleString()}원`, `${item.estimatedCostKrw.toLocaleString()} KRW`),
  ].filter((fact): fact is string => fact !== null);

  // 구간 라벨 — 이 값들은 이 방문지로 들어오는 구간이다(backend ItineraryQueryService
  // 의 incomingLeg). 다음 칸까지가 아니다. 그래서 노드 위에 그린다.
  const legLabel = [walkLabel, formatTravelLabel(item, tx, index === 0)].filter(Boolean).join(' · ');

  const lockControl = reorderMode
    ? (item.locked
      ? <View style={styles.lockBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('고정됨', 'Locked')}</Text></View>
      : <View style={styles.moveButtons}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 위로 이동`, `Move ${item.title} up`)} accessibilityState={{ disabled: !canMoveUp || moveBusy }} disabled={!canMoveUp || moveBusy} onPress={onMoveUp} style={[styles.moveButton, (!canMoveUp || moveBusy) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>▲</Text></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 아래로 이동`, `Move ${item.title} down`)} accessibilityState={{ disabled: !canMoveDown || moveBusy }} disabled={!canMoveDown || moveBusy} onPress={onMoveDown} style={[styles.moveButton, (!canMoveDown || moveBusy) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>▼</Text></Pressable>
      </View>)
    : canEdit
      ? <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} ${item.locked ? '고정 해제' : '고정'}`, `${item.title} ${item.locked ? 'unlock' : 'lock'}`)} accessibilityState={{ selected: item.locked, busy: lockBusy, disabled }} disabled={disabled} onPress={onLock} style={[styles.lockTouch, disabled && styles.actionDisabled]}><Text variant="body">{lockBusy ? '…' : item.locked ? '🔒' : '🔓'}</Text></Pressable>
      : item.locked ? <View style={styles.lockTouch}><Text variant="body">🔒</Text></View> : null;

  return <>
    {/* 구간 — 세로선과 「도보 1.2km」. 넓은 화면은 위쪽 가로 노선도가 같은 것을 보여주므로 생략한다. */}
    {/* — 첫 방문지에도 들어오는 구간이 있다 (출발지에서 온다). 위 주석 참고. */}
    {(index > 0 || legLabel) && !wide ? <View style={styles.segmentRow}>
      <View style={styles.rail}><View style={styles.railLine} /></View>
      {legLabel ? <View style={styles.segmentLabel}><Text variant="caption" weight="bold" color={color.brand.navy}>{legLabel}</Text></View> : null}
    </View> : null}
    <View style={[styles.stopRow, wide && styles.stopRowWide]}>
      <View style={styles.rail}>
        <View style={[styles.node, index === 0 && styles.nodeFirst, wide && styles.nodeWide]}><Text variant="caption" weight="bold" color={color.text.onAction}>{index + 1}</Text></View>
        {!isLast && !wide ? <View style={styles.railLine} /> : null}
      </View>
      <View style={[styles.stopBody, pace?.atRisk && styles.stopBodyAtRisk]}>
        <View style={styles.stopHead}>
          {/* 펼치는 손잡이는 제목 덩이에만 둔다. 행 전체를 Pressable 로 감싸면 그 안의
              자물쇠가 「버튼 안의 버튼」이 되고, 웹에서는 그게 허용되지 않는다.
          */}
          <Pressable accessibilityRole="button" accessibilityState={{ expanded }} accessibilityLabel={tx(`${item.title} ${expanded ? '접기' : '자세히'}`, `${item.title} ${expanded ? 'collapse' : 'details'}`)} onPress={onToggleExpand} style={styles.grow}>
            <View style={styles.titleLine}>
              <Text variant={wide ? 'title' : 'body'} weight="bold" style={styles.titleText}>{item.title}</Text>
              {item.dataStatus ? <View style={[styles.statusChip, STATUS_CHIP[item.dataStatus]]}><Text variant="caption" weight="bold" color={STATUS_COLOR[item.dataStatus]}>{STATUS_LABEL[item.dataStatus]}</Text></View> : null}
            </View>
            {/* 서버가 늘 null 로 주는 칸이다. 있으면 그리고 없으면 줄을 만들지 않는다. */}
            {item.description ? <Text variant="caption" color={color.text.body} numberOfLines={expanded ? undefined : 1}>{item.description}</Text> : null}
          </Pressable>
          <View style={[styles.stopRight, wide && styles.stopRightWide]}>
            <View style={wide ? styles.stopRightStack : undefined}>
              <Text variant={wide ? 'title' : 'body'} weight="bold" color={color.brand.navy}>{formatTime(displayTime)}</Text>
              {wide && item.estimatedCostKrw != null ? <Text variant="caption" color={color.text.muted}>{item.estimatedCostKrw === 0 ? tx('무료', 'Free') : tx(`${item.estimatedCostKrw.toLocaleString()}원`, `${item.estimatedCostKrw.toLocaleString()} KRW`)}</Text> : null}
            </View>
            {lockControl}
          </View>
        </View>
        {expanded ? <View style={styles.stopDetail}>
          {facts.length ? <View style={styles.metaRow}>{facts.map((fact) => <Text key={fact} variant="caption" color={color.text.muted}>{fact}</Text>)}</View> : null}
          {pace && !reorderMode ? <View style={styles.paceRow}>
            {pace.visited ? <Text variant="caption" weight="bold" color={color.state.success}>{tx(`도착 ${pace.predictedArrival ? formatTime(pace.predictedArrival) : '--:--'}${pace.predictedDeparture ? ` · 출발 ${formatTime(pace.predictedDeparture)}` : ''}`, `Arrived ${pace.predictedArrival ? formatTime(pace.predictedArrival) : '--:--'}${pace.predictedDeparture ? ` · Left ${formatTime(pace.predictedDeparture)}` : ''}`)}</Text>
              : <Text variant="caption" weight="bold" color={pace.atRisk ? color.state.danger : color.text.muted}>{tx(`예상 도착 ${pace.predictedArrival ? formatTime(pace.predictedArrival) : '--:--'}${estimated ? ' (추정)' : ''}`, `Est. arrival ${pace.predictedArrival ? formatTime(pace.predictedArrival) : '--:--'}${estimated ? ' (est.)' : ''}`)}{pace.atRisk ? ` · ${tx('하루를 넘길 위험', 'Risks running past the day')}` : ''}</Text>}
            {(onRecordArrival || onRecordDeparture) ? <View style={styles.actualButtons}>
              {!pace.visited ? <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 도착 찍기`, `Mark arrival at ${item.title}`)} accessibilityState={{ busy: actualBusy }} disabled={actualBusy} onPress={onRecordArrival} style={[styles.actualButton, actualBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('도착 찍기', 'Mark arrival')}</Text></Pressable>
                : !pace.predictedDeparture ? <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 출발 찍기`, `Mark departure at ${item.title}`)} accessibilityState={{ busy: actualBusy }} disabled={actualBusy} onPress={onRecordDeparture} style={[styles.actualButton, actualBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('출발 찍기', 'Mark departure')}</Text></Pressable> : null}
            </View> : null}
          </View> : null}
          {!reorderMode && canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 제외`, `Exclude ${item.title}`)} accessibilityState={{ busy: excludeBusy, disabled }} disabled={disabled} onPress={onExclude} style={[styles.excludeButton, disabled && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.state.danger}>{excludeBusy ? tx('처리 중', 'Processing') : tx('이 장소 제외', 'Remove this place')}</Text></Pressable> : null}
          {isPastVisit && !reorderMode ? (
            reviewStatus === 'reviewed' ? <View style={styles.reviewedBadge}><Text variant="caption" weight="bold" color={color.state.success}>{tx('평가함', 'Reviewed')}</Text></View>
            : reviewStatus === 'can-review' ? <Pressable accessibilityRole="button" accessibilityLabel={tx(`${item.title} 다녀오셨나요? 평가하기`, `Review your visit to ${item.title}`)} onPress={() => setReviewModalOpen(true)} style={styles.reviewButton}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('다녀오셨나요?', 'Did you visit?')}</Text></Pressable>
            : null
          ) : null}
        </View> : null}
      </View>
    </View>
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
  // 순서 바꾸기 응답에만 실려 오는 영업시간 경고/-852) — 활동 이력엔 안 남으므로
  // 그 자리에서 받은 문장을 이 상태에 직접 담아 둔다. 다음 편집을 시작하면 지운다.
  const [openingHoursNotice, setOpeningHoursNotice] = useState<string[]>([]);
  // ⋯ 패널과 펼친 정차. 둘 다 화면에만 있는 상태라 서버에 안 보낸다.
  const [menuOpen, setMenuOpen] = useState(false);
  const [expandedItemId, setExpandedItemId] = useState<string | null>(null);

  const refreshVersions = useCallback(async (targetId: string) => {
    const next = await loadItineraryVersions(targetId, accessToken);
    if (next.state === 'success') setVersions(next.versions);
  }, [accessToken]);

  // — 동기화 폴링 간격. reload 도 이 값을 되돌리므로 그보다 위에 둔다.
  const pollDelayRef = useRef(SYNC_POLL_BASE_MS);

  const reload = useCallback(async () => {
    if (!itineraryId) return;
    setLoading(true); setConflict(null); setActionMessage(null);
    // 내가 무엇이든 했으면 동기화를 다시 촘촘하게 본다
    pollDelayRef.current = SYNC_POLL_BASE_MS;
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

  // 동행자의 변경을 실시간으로 받아온다. 진행 중인 내 편집(잠금·제외
  // 순서 변경 등) 위에 서버 응답이 덮어써 충돌하지 않도록, 그런 조작이 도는 동안은
  // 이번 주기를 건너뛴다 — 편집이 끝나면 각 함수가 자체적으로 reload를 부른다.
  const pollBlockedRef = useRef(false);
  useEffect(() => {
    pollBlockedRef.current = Boolean(busyItemId) || excludingItemId !== null || excludeConfirming !== null || dayActionBusy || revertBusy || orderDraft !== null || reorderBusy || replanBusy || actualBusyItemId !== null;
  });
  const failureStreakRef = useRef(0);
  // — 간격이 고정 5초가 아니라 「안 바뀌면 늘어나는」 값이 된다.
  const resultRef = useRef(result);
  useEffect(() => { resultRef.current = result; });
  useEffect(() => {
    if (!itineraryId) return;
    let stopped = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const schedule = () => {
      if (stopped) return;
      timer = setTimeout(tick, pollDelayRef.current);
    };

    const tick = () => {
      if (stopped) return;
      // 내 편집이 도는 동안은 이번 차례를 건너뛴다. 간격은 그대로 두고 다시 잰다
      // 편집 중이라고 해서 동기화가 느려질 이유는 없다.
      if (pollBlockedRef.current) { schedule(); return; }
      void loadItinerary(itineraryId, accessToken).then((next) => {
        if (stopped) return;
        if (next.state === 'success') {
          failureStreakRef.current = 0;
          setSyncDisconnected(false);
          const prev = resultRef.current;
          const changed = !(prev.state === 'success' && prev.itinerary.version === next.itinerary.version);
          if (changed) setResult(next);
          pollDelayRef.current = nextSyncPollDelay(pollDelayRef.current, changed);
        } else {
          failureStreakRef.current += 1;
          if (failureStreakRef.current >= 3) setSyncDisconnected(true);
          // 실패도 「안 바뀐 것」으로 친다. 서버가 안 되는 동안 5초마다 두드리는 것은
          // 우리한테도 서버한테도 손해다.
          pollDelayRef.current = nextSyncPollDelay(pollDelayRef.current, false);
        }
        schedule();
      });
    };

    schedule();
    return () => { stopped = true; if (timer) clearTimeout(timer); };
  }, [accessToken, itineraryId]);

  const manualSyncRefresh = async () => {
    await reload();
    failureStreakRef.current = 0;
    pollDelayRef.current = SYNC_POLL_BASE_MS;
    setSyncDisconnected(false);
  };

  const itinerary = result.state === 'success' ? result.itinerary : null;
  const day = itinerary?.days[selectedDay];
  // 넓은 화면에서만 2단으로 나눈다. 저장소 반응형 표가 「1024~ 사이드바 + 본문」이라
  // 그 경계를 그대로 쓴다 — 여기서 숫자를 새로 정하지 않는다(layout/breakpoints.ts).
  const { width } = useLayout();
  const wide = isAtLeast(width, 'lg');
  // — 통계는 값이 있는 것만 만든다. 판정은 itinerarySummary.ts 에 있다.
  const stats = useMemo(() => (itinerary ? itineraryStats(itinerary, tx) : []), [itinerary, tx]);
  const canEdit = itinerary?.canEdit !== false;
  // — 모르는 코드는 안 그린다.
  const latestWarnings = useMemo(() => describeWarningCodes(versions[0]?.warningCodes, tx), [versions, tx]);
  const reorderMode = orderDraft !== null;
  const slotTimes = useMemo(() => day?.items.map((item) => item.startsAt) ?? [], [day]);
  const displayedItems = useMemo(() => {
    if (!day) return [];
    if (!orderDraft) return day.items;
    const itemsById = new Map(day.items.map((item) => [item.id, item]));
    return orderDraft.map((itemId) => itemsById.get(itemId)).filter((entry): entry is ItineraryItemDto => Boolean(entry));
  }, [day, orderDraft]);
  const dayTravelMinutes = useMemo(() => totalTravelMinutes(displayedItems), [displayedItems]);
  // 값이 없는 칸을 0 으로 세지 않는다. 자료가 있는 칸만 더하므로 이 합계는 「적어도 이만큼」이다.
  const dayWalkingMeters = useMemo(() => displayedItems.reduce((sum, item) => sum + (item.walkingMeters ?? 0), 0), [displayedItems]);
  // 비용 합계는 아는 칸이 몇 개인지 같이 말한다
  const dayCost = useMemo(() => {
    const known = displayedItems.filter((item) => typeof item.estimatedCostKrw === 'number');
    return { krw: known.reduce((sum, item) => sum + (item.estimatedCostKrw as number), 0), known: known.length, total: displayedItems.length };
  }, [displayedItems]);
  const dayFacts = useMemo(() => [
    dayWalkingMeters > 0 ? tx(`도보 ${formatWalk(dayWalkingMeters)}`, `${formatWalk(dayWalkingMeters)} on foot`) : null,
    dayCost.krw > 0
      ? dayCost.known === dayCost.total
        ? tx(`${dayCost.krw.toLocaleString()}원`, `${dayCost.krw.toLocaleString()} KRW`)
        : tx(`${dayCost.krw.toLocaleString()}원 (${dayCost.total}곳 중 ${dayCost.known}곳)`, `${dayCost.krw.toLocaleString()} KRW (${dayCost.known} of ${dayCost.total} places)`)
      : null,
  ].filter(Boolean).join(' · '), [dayWalkingMeters, dayCost, tx]);
  const canReorder = canEdit && (day?.items.filter((item) => !item.locked).length ?? 0) > 1;

  // 지연 경고·314). 날짜를 바꾸면 그 날짜 것을 새로 받는다 — 표본이
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

  // — 도착·출발 기록 PUT은 itinerary.version을 안 올린다(actualArrivedAt만
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

  // 방문지 수 — 모든 날의 정차를 합친다.
  const stopCount = itinerary ? itinerary.days.reduce((sum, day) => sum + day.items.length, 0) : 0;

  // 헤더 요약 — 값이 있는 것만 잇는다. 「미확인」이라고 적힌 칸은 정보가 아니라 잡음이다.
  const heroSummary = itinerary ? [
    itinerary.days.length > 0 ? tx(`${itinerary.days.length}일`, `${itinerary.days.length} days`) : null,
    stopCount > 0 ? tx(`${stopCount}곳`, `${stopCount} stops`) : null,
    typeof itinerary.totalWalkingMeters === 'number' && itinerary.totalWalkingMeters > 0
      ? tx(`도보 ${(itinerary.totalWalkingMeters / 1000).toFixed(1)}km`, `${(itinerary.totalWalkingMeters / 1000).toFixed(1)}km on foot`) : null,
    typeof itinerary.totalEstimatedCostKrw === 'number' && itinerary.totalEstimatedCostKrw > 0
      ? tx(`약 ${Math.round(itinerary.totalEstimatedCostKrw / 10000 * 10) / 10}만원`, `about ${itinerary.totalEstimatedCostKrw.toLocaleString()} KRW`) : null,
  ].filter(Boolean).join(' · ') : '';

  // ── 네이비 헤더 (시안 design_handoff_itinerary 2·3절) ──────────────────────
  return <View style={styles.shell}><Screen scroll wide withTabBar style={styles.canvas}>
    <View style={styles.hero}>
      <View style={styles.heroTop}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={styles.heroBack}><Text variant="title" color={color.text.onAction}>‹</Text></Pressable>
        {/* 「초안 v1 기본 추천」 배지를 뺐다 우리가 아는 것을 그대로
            내보인 말이지 사용자가 알아야 할 것이 아니었다 — 「초안」은 이미 저장된 여행에
            대고 아직 안 끝났다고 말하고, 「v1」은 편집할 때마다 올라가 불안만 주고
            「기본 추천」(fallbackMode=BASELINE)은 좋은 건지 나쁜 건지 알 수 없다.
            값을 버린 것이 아니다. version 은 되돌리기가 그대로 쓰고 fallbackMode 도
            응답에 남아 있다. 헤더에 안 그릴 뿐이다.
        */}
        {/* ⋯ — 시안 3.1. 늘 놓을 자리가 없는 것(통계·전체 일정·다시 계산·되돌리기)을 여기 담는다.
            화면에 다 늘어놓으면 정작 하루의 동선이 아래로 밀려 한 칸도 안 보인다.
        */}
        {itinerary ? <Pressable accessibilityRole="button" accessibilityLabel={tx('더 보기', 'More')} accessibilityState={{ expanded: menuOpen }} onPress={() => setMenuOpen((open) => !open)} style={styles.heroBack}><Text variant="title" color={color.text.onAction}>⋯</Text></Pressable> : <View style={styles.heroBackSpacer} />}
      </View>
      <Text variant="display" weight="bold" color={color.text.onAction} style={styles.heroTitle}>{itinerary?.title ?? tx('여행 일정', 'Itinerary')}</Text>
      {heroSummary ? <Text color={color.text.onDarkMuted}>{heroSummary}</Text> : null}
      {/* — 지도로 가는 문. 이 화면에는 지도로 가는 길이 아예 없었다.
          그래서 카카오 지도·경로선·3D 부산·그늘/휠체어 실측이 다 들어 있는 화면에 아무도
          못 들어갔다(주소를 직접 쳐야만 보였다). 「추천 다시 보기」는 서버가 여행 번호를
          실어 주는 판에서만 그린다 — 위 tripId 주석 참고.
      */}
      {itinerary ? <View style={styles.heroActions}>
        <Pressable accessibilityRole="button" onPress={() => router.push({ pathname: '/[id]/map', params: { id } })} style={styles.heroAction}>
          <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('지도 보기', 'View map')}</Text>
        </Pressable>
        {itinerary.tripId ? <Pressable accessibilityRole="button" onPress={() => router.push({ pathname: '/trips/[id]/recommendations', params: { id: itinerary.tripId as string } })} style={styles.heroAction}>
          <Text variant="caption" weight="bold" color={color.text.onAction}>{tx('추천 다시 보기', 'See recommendations')}</Text>
        </Pressable> : null}
      </View> : null}
      {/* 일차 탭은 헤더에 붙어 있다 (시안 2.3 3.1) — 탭이 헤더에서 떨어져 있으면
          어느 날을 보고 있는지가 제목과 따로 놀아서, 스크롤을 내리면 둘 다 안 보인다.
          하루짜리 여행에는 고를 것이 없으므로 안 그린다.
      */}
      {itinerary && viewMode === 'day' && itinerary.days.length > 1 ? <View accessibilityRole="tablist" style={styles.heroTabs}>
        {itinerary.days.map((entry, index) => <Pressable key={`hero-${entry.date}-${index}`} accessibilityRole="tab" accessibilityLabel={tx(`${index + 1}일차`, `Day ${index + 1}`)} accessibilityState={{ selected: selectedDay === index }} onPress={() => selectDay(index)} style={[styles.heroTab, selectedDay === index && styles.heroTabActive]}>
          <Text variant="caption" weight="bold" numberOfLines={1} color={selectedDay === index ? color.brand.navy : color.text.onDarkMuted}>{tx(`${index + 1}일차`, `Day ${index + 1}`)}</Text>
        </Pressable>)}
      </View> : null}
    </View>
    {loading ? <View accessibilityLabel={tx('일정을 불러오고 있어요', 'Loading itinerary')} style={styles.route}>{[0, 1, 2].map((key) => (
      <View key={key} style={styles.stopRow}>
        <View style={styles.rail}><Skeleton width={32} height={32} /></View>
        <View style={styles.stopBody}><Skeleton width="60%" height={16} /><View style={styles.metaRow}><Skeleton width="30%" height={12} /><Skeleton width="30%" height={12} /></View></View>
      </View>
    ))}</View> : null}
    {!loading && result.state !== 'success' ? <View style={styles.stateCard}><Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : result.state === 'unavailable' ? tx('일정 API를 기다리고 있어요', 'Waiting for the itinerary API') : tx('일정을 불러오지 못했어요', 'Could not load the itinerary')}</Text><Text color={color.text.body}>{result.message}</Text><Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void reload()} /></View> : null}
    {!loading && itinerary ? <>
      {menuOpen ? <View style={styles.menuPanel}>
        <View style={styles.stats}>{stats.map((stat) => <View key={stat.key} style={styles.stat}><Text variant="title" weight="bold">{stat.value}</Text><Text variant="caption" color={color.text.muted}>{stat.label}</Text></View>)}</View>
        {rhythm ? <Text variant="caption" color={color.text.body}>{tx(`하루 평균 ${rhythm.averageItemsPerDay}곳`, `${rhythm.averageItemsPerDay} places/day avg.`)}{rhythm.travelShare != null ? tx(` · 이동 비중 ${Math.round(rhythm.travelShare * 100)}%`, ` · ${Math.round(rhythm.travelShare * 100)}% travel time`) : ''}{rhythm.plannedVsActual != null ? tx(` · 계획 대비 실제 ${rhythm.plannedVsActual}배`, ` · ${rhythm.plannedVsActual}x planned pace`) : ''}</Text> : null}
        <View style={styles.menuActions}>
          <Pressable accessibilityRole="button" onPress={() => { selectView(viewMode === 'all' ? 'day' : 'all'); setMenuOpen(false); }} style={styles.recalcButton}><Text variant="caption" weight="bold" color={color.brand.navy}>{viewMode === 'all' ? tx('날짜별 보기', 'By day') : tx('전체 일정 보기', 'All days')}</Text></Pressable>
          {canReorder && !reorderMode ? <Pressable accessibilityRole="button" accessibilityLabel={tx('일정 순서 변경', 'Reorder itinerary')} accessibilityState={{ disabled: excludingItemId !== null }} disabled={excludingItemId !== null} onPress={() => { startReorder(); setMenuOpen(false); }} style={[styles.recalcButton, excludingItemId !== null && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('순서 변경', 'Reorder')}</Text></Pressable> : null}
          {canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={tx('이 날짜 다시 계산', 'Recalculate this day')} accessibilityState={{ busy: dayActionBusy }} disabled={dayActionBusy || !day?.items.length || excludingItemId !== null} onPress={() => void recalculateDay()} style={[styles.recalcButton, (dayActionBusy || !day?.items.length || excludingItemId !== null) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{dayActionBusy ? tx('계산 중', 'Calculating') : tx('다시 계산', 'Recalculate')}</Text></Pressable> : null}
          {canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={tx('남은 하루 다시 계획', 'Replan the rest of the day')} accessibilityState={{ busy: replanBusy }} disabled={replanBusy || !day?.items.length || excludingItemId !== null} onPress={() => { setReplanConfirming(true); setMenuOpen(false); }} style={[styles.recalcButton, (replanBusy || !day?.items.length || excludingItemId !== null) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('다시 계획', 'Replan')}</Text></Pressable> : null}
          {canEdit && versions.length > 1 ? <Pressable accessibilityRole="button" accessibilityLabel={tx('최근 변경 취소', 'Undo last change')} accessibilityState={{ busy: revertBusy, disabled: revertBusy }} disabled={revertBusy} onPress={() => void revert()} style={[styles.recalcButton, revertBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{revertBusy ? tx('처리 중', 'Processing') : tx('되돌리기', 'Undo')}</Text></Pressable> : null}
        </View>
      </View> : null}
      {!canEdit ? <View style={styles.viewerNotice}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('보기 전용 — 이 일정을 편집할 권한이 없어요.', "View only — you don't have permission to edit this itinerary.")}</Text></View> : null}
      {syncDisconnected ? <View accessibilityRole="alert" style={styles.conflict}><Text variant="body" weight="bold">{tx('실시간 동기화가 끊겼습니다', 'Live sync lost')}</Text><Text variant="caption" color={color.text.body}>{tx('네트워크 연결을 확인해 주세요. 보고 있는 화면은 최신이 아닐 수 있어요.', 'Please check your network connection. What you see may not be up to date.')}</Text><Button label={tx('새로고침', 'Refresh')} variant="ghost" onPress={() => void manualSyncRefresh()} /></View> : null}
      {conflict ? <View accessibilityRole="alert" style={styles.conflict}><Text variant="body" weight="bold">{tx('최신 일정과 충돌했어요', 'Conflicted with the latest itinerary')}</Text><Text variant="caption" color={color.text.body}>{conflict}</Text><Button label={tx('최신 일정 불러오기', 'Load latest itinerary')} variant="ghost" onPress={() => void reload()} /></View> : null}
      {actionMessage ? <View accessibilityRole="alert" style={styles.actionNotice}><Text variant="caption" color={color.text.body}>{actionMessage}</Text></View> : null}
      {latestWarnings.length ? <View style={styles.warningNotice}>{latestWarnings.map((message, index) => <Text key={index} variant="caption" color={color.text.body}>{message}</Text>)}</View> : null}
      {openingHoursNotice.length ? <View accessibilityRole="alert" style={styles.warningNotice}>{openingHoursNotice.map((message, index) => <Text key={index} variant="caption" color={color.text.body}>{message}</Text>)}</View> : null}
      {dayOutOfRange ? <View style={styles.actionNotice}><Text variant="caption" color={color.text.body}>{tx(`요청한 날짜가 없어서 1일차를 보여드려요. (전체 ${itinerary.days.length}일)`, `That day doesn't exist, so day 1 is shown instead. (${itinerary.days.length} days total)`)}</Text></View> : null}

      {viewMode === 'all' ? (
        <View style={styles.route}>
          {itinerary.days.map((entry, dayIndex) => (
            <View key={`${entry.date}-${dayIndex}`}>
              <Pressable accessibilityRole="button" accessibilityLabel={tx(`${dayIndex + 1}일차만 보기`, `View only day ${dayIndex + 1}`)} onPress={() => { selectView('day'); selectDay(dayIndex); }} style={styles.dayLine}>
                <Text variant="body" weight="bold">{formatDayHeading(entry.date, dayIndex, tx)}</Text>
                <Text variant="caption" color={color.text.muted}>{tx(`${entry.items.length}곳`, `${entry.items.length} stops`)}</Text>
              </Pressable>
              {entry.items.length ? entry.items.map((item, index) => (
                <StopRow key={item.id} item={item} index={index} isLast={index === entry.items.length - 1} displayTime={item.startsAt} wide={wide} expanded={expandedItemId === item.id} onToggleExpand={() => setExpandedItemId((current) => current === item.id ? null : item.id)} canEdit={false} lockBusy={false} excludeBusy={false} dayBusy={false} onLock={() => {}} onExclude={() => {}} reorderMode={false} canMoveUp={false} canMoveDown={false} moveBusy={false} onMoveUp={() => {}} onMoveDown={() => {}} accessToken={accessToken} />
              )) : <View style={styles.empty}><Text variant="caption" color={color.text.muted}>{tx('이 날짜에는 아직 장소가 없어요.', 'No places for this day yet.')}</Text></View>}
            </View>
          ))}
        </View>
      ) : (
        <>
          {/* 날짜 줄 — 시안 3.2. 왼쪽에 「9월 19일 (금)」, 오른쪽에 그날 합계. */}
          <View style={styles.dayLine}>
            <Text variant="body" weight="bold">{day ? formatDayHeading(day.date, selectedDay, tx) : tx(`${selectedDay + 1}일차`, `Day ${selectedDay + 1}`)}</Text>
            {dayFacts ? <Text variant="caption" color={color.text.muted}>{dayFacts}</Text> : null}
          </View>
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
          {reorderMode ? <View style={styles.reorderBar}>
            <Text variant="caption" color={color.text.body} style={styles.grow}>{tx('화살표로 순서를 바꾼 뒤 저장하세요. 고정된 장소는 자리를 옮길 수 없어요.', 'Use the arrows to reorder, then save. Locked places keep their spot.')}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('순서 변경 취소', 'Cancel reordering')} accessibilityState={{ disabled: reorderBusy }} disabled={reorderBusy} onPress={cancelReorder} style={[styles.recalcButton, reorderBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('취소', 'Cancel')}</Text></Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('순서 저장', 'Save order')} accessibilityState={{ busy: reorderBusy }} disabled={reorderBusy} onPress={() => void saveReorder()} style={[styles.recalcButtonPrimary, reorderBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.text.onAction}>{reorderBusy ? tx('저장 중', 'Saving') : tx('저장', 'Save')}</Text></Pressable>
          </View> : null}
          {/* 가로 노선도는 넓은 화면에만 — 폰에서는 아래 세로 노선이 같은 일을 더 잘한다.
              순서를 바꾸는 중에는 숨긴다: 아직 저장 안 된 순서를 확정된 동선처럼 그리면
              무엇이 진짜인지 헷갈린다.
          */}
          {!reorderMode && wide ? <RouteStrip items={displayedItems} times={slotTimes} tx={tx} /> : null}
          {displayedItems.length ? <View style={wide ? styles.wideGrid : undefined}>
            <View style={wide ? styles.timelineColumn : undefined}>
              <View style={styles.route}>{displayedItems.map((item, index) => <StopRow key={item.id} item={item} index={index} isLast={index === displayedItems.length - 1} displayTime={slotTimes[index] ?? item.startsAt} wide={wide} expanded={expandedItemId === item.id} onToggleExpand={() => setExpandedItemId((current) => current === item.id ? null : item.id)} canEdit={canEdit} lockBusy={busyItemId === item.id} excludeBusy={excludingItemId === item.id} dayBusy={dayActionBusy || excludingItemId !== null} onLock={() => void toggleLock(item)} onExclude={() => setExcludeConfirming(item)} reorderMode={reorderMode} canMoveUp={index > 0 && !item.locked && !displayedItems[index - 1].locked} canMoveDown={index < displayedItems.length - 1 && !item.locked && !displayedItems[index + 1].locked} moveBusy={reorderBusy} onMoveUp={() => moveDraftItem(index, -1)} onMoveDown={() => moveDraftItem(index, 1)} pace={paceByItemId.get(item.id)} estimated={paceEstimated} actualBusy={actualBusyItemId === item.id} onRecordArrival={() => void recordArrival(item)} onRecordDeparture={() => void recordDeparture(item)} accessToken={accessToken} />)}</View>
            </View>
            {/* 이동 요약 — 시안 p6 의 3칸(장소 이동 합계 수단). 폰에도 둔다
                「이 하루가 얼마나 걷는 하루인가」는 정차를 하나씩 봐서는 안 나오는 값이다.
            */}
            <View style={wide ? styles.aside : undefined}>
              {/* 시안의 3칸. 세 번째 칸(수단)은 서버가 안 준다 — 일정 응답에
                  구간 이동수단 칸이 없다(itinerary.ts 의 ItineraryItemDto). 「도보2·버스1」을
                  지어내지 않고 없다고 적는다. 칸을 지우지 않는 이유는, 자리가 비어 있어야
                  서버가 그 값을 싣는 날 여기에 들어온다는 것이 보이기 때문이다.
              */}
              <View style={styles.summaryRow}>
                <View style={styles.summaryCell}>
                  <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('장소', 'Stops')}</Text>
                  <Text variant="title" weight="bold">{tx(`${displayedItems.length}곳`, String(displayedItems.length))}</Text>
                </View>
                <View style={styles.summaryCell}>
                  <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('이동 합계', 'Travel')}</Text>
                  {dayTravelMinutes > 0
                    ? <Text variant="title" weight="bold">{tx(`${dayTravelMinutes}분`, `${dayTravelMinutes}m`)}</Text>
                    : <Text variant="caption" color={color.text.muted}>{tx('아직 없어요', 'Not yet')}</Text>}
                </View>
                <View style={styles.summaryCell}>
                  <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('수단', 'Modes')}</Text>
                  <Text variant="caption" color={color.text.muted}>{tx('아직 없어요', 'Not yet')}</Text>
                </View>
              </View>
              <View style={styles.asideCard}>
                <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx(`${selectedDay + 1}일차 이동 요약`, `Day ${selectedDay + 1} travel summary`)}</Text>
                {dayWalkingMeters > 0 ? <View accessibilityLabel={tx(`정차별 도보 비중`, 'Walking share per stop')} style={styles.shareBar}>
                  {displayedItems.map((item) => item.walkingMeters ? <View key={item.id} style={[styles.shareSlice, { flex: item.walkingMeters }]} /> : null)}
                </View> : null}
                {dayWalkingMeters > 0 ? <Text variant="caption" color={color.text.body}>{tx(`도보 ${formatWalk(dayWalkingMeters)}`, `${formatWalk(dayWalkingMeters)} on foot`)}</Text> : null}
                {dayTravelMinutes > 0
                  ? <Text variant="caption" color={color.text.body}>{tx(`이동 합계 ${dayTravelMinutes}분`, `${dayTravelMinutes}m travel in total`)}</Text>
                  : <Text variant="caption" color={color.text.muted}>{tx('이 날짜는 구간 이동 시간이 아직 없어요.', 'No leg travel times for this day yet.')}</Text>}
                {/* 대중교통은 업체가 정해지지 않아 부를 API 가 없다.
                    빈 값을 그리지 않고, 없다는 것을 그대로 적는다.
                */}
                <Text variant="caption" color={color.text.muted}>{tx('대중교통 안내 — 아직 없어요', 'Transit directions — not available yet')}</Text>
              </View>
            </View>
          </View> : <View style={styles.empty}><Text variant="body" weight="bold">{tx('이 날짜에는 아직 장소가 없어요.', 'No places for this day yet.')}</Text></View>}
        </>
      )}
    </> : null}
  </Screen>
  {/* 하단 고정 줄 — 시안 3.5.
      시안의 「저장」(초안을 내 여행으로 확정)은 안 만들었다. 부를 API 가 없다
      src/plan/itinerary.ts 에 확정 함수가 없고, 이 화면은 이미 「내 여행」에서 열리는
      저장된 일정이다. 누르면 아무 일도 안 나는 버튼은 없는 버튼보다 나쁘다.
  */}
  {!wide && itinerary && canReorder && !reorderMode ? <View style={styles.bottomBar}>
    <Button label={tx('순서 수정', 'Reorder')} variant="ghost" onPress={startReorder} />
  </View> : null}
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

const styles = StyleSheet.create({ shell: { flex: 1, backgroundColor: color.brand.ivory },
  // ── 네이비 헤더 (시안 design_handoff_itinerary 2·3절) ──────────────────────
  // Screen 이 좌우 gutter(24)와 위 spacing[6] 을 이미 넣으므로, 그만큼 음수 여백으로
  // 되밀어야 색이 띠처럼 화면 끝까지 간다. 안 그러면 네이비가 카드처럼 떠 보인다.
  hero: { backgroundColor: color.brand.navy, marginHorizontal: -gutter, marginTop: -spacing[6], paddingHorizontal: gutter, paddingTop: spacing[3], gap: spacing[1] },
  heroTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  heroBack: { width: 44, height: 44, marginLeft: -spacing[3], alignItems: 'center', justifyContent: 'center' },
  heroBackSpacer: { width: 44, height: 44 },
  heroGhost: { minHeight: 36, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: 'rgba(255, 255, 255, 0.4)', alignItems: 'center', justifyContent: 'center' },
  heroTitle: { marginTop: spacing[2] },
  // 탭은 헤더 바닥에 붙는다 — 위쪽만 둥글고 아래는 각져서 헤더와 한 덩이로 보인다.
  // 지도·추천으로 가는 문 일차 탭과 같은 반투명 흰색이라 헤더와 한 덩이로
  // 보이고, 탭보다 위에 놓아 「어느 날을 보나」와 「어디로 가나」가 안 섞인다.
  heroActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[3], flexWrap: 'wrap' },
  heroAction: { minHeight: 44, paddingHorizontal: spacing[3], justifyContent: 'center', borderRadius: radius.md, backgroundColor: 'rgba(255, 255, 255, 0.16)' },
  heroTabs: { flexDirection: 'row', gap: spacing[1], marginTop: spacing[4] },
  heroTab: { flex: 1, minHeight: 44, paddingHorizontal: spacing[2], borderTopLeftRadius: radius.md, borderTopRightRadius: radius.md, backgroundColor: 'rgba(255, 255, 255, 0.10)', alignItems: 'center', justifyContent: 'center' },
  heroTabActive: { backgroundColor: color.brand.ivory },
  // 방문지 제목 옆 상태 배지
  titleLine: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], flexWrap: 'wrap' },
  // ── 세로 노선도 (시안 3.3) ────────────────────────────────────────────────
  // 왼쪽 레일에 번호 원과 3px 세로선, 오른쪽에 내용. 선은 노드 아래에서 다음 노드까지
  // 끊기지 않아야 한다 — 끊기면 「이 다음에 저기」라는 말이 안 된다.
  route: { marginTop: spacing[3], marginBottom: spacing[4] },
  rail: { width: 32, alignItems: 'center' },
  railLine: { width: 3, flex: 1, minHeight: spacing[4], borderRadius: radius.full, backgroundColor: color.brand.navy },
  segmentRow: { flexDirection: 'row', alignItems: 'stretch', gap: spacing[3], minHeight: 44 },
  segmentLabel: { flex: 1, justifyContent: 'center' },
  stopRow: { flexDirection: 'row', gap: spacing[3], alignItems: 'stretch' },
  stopRowWide: { borderBottomWidth: 1, borderBottomColor: color.surface.border, paddingVertical: spacing[3] },
  stopBody: { flex: 1, minWidth: 0, gap: spacing[2], paddingBottom: spacing[3] },
  stopBodyAtRisk: { borderLeftWidth: 3, borderLeftColor: color.state.danger, paddingLeft: spacing[3] },
  stopHead: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2] },
  stopRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[1] },
  stopRightWide: { alignItems: 'flex-start' },
  stopRightStack: { alignItems: 'flex-end' },
  stopDetail: { gap: spacing[2], paddingTop: spacing[2], borderTopWidth: 1, borderTopColor: color.surface.border },
  lockTouch: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  nodeWide: { width: 40, height: 40 },
  // 날짜 줄 (시안 3.2) — 왼쪽 날짜, 오른쪽 그날 합계
  dayLine: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing[2], flexWrap: 'wrap', marginTop: spacing[4] },
  // ⋯ 패널 — 늘 놓을 자리가 없는 것들
  menuPanel: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  menuActions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  reorderBar: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  // 하단 고정 줄 — 탭바 위에 형제로 놓는다. absolute 로 띄우면 목록 끝이 그만큼 가린다.
  bottomBar: { paddingHorizontal: gutter, paddingBottom: spacing[2] },
  // 정차별 도보 비중 (시안 2.5 3.4)
  shareBar: { flexDirection: 'row', gap: 2, height: 6, borderRadius: radius.full, overflow: 'hidden' },
  shareSlice: { backgroundColor: color.brand.orange, borderRadius: radius.full },

  titleText: { flexShrink: 1 },
  statusChip: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full },
  statusVerified: { backgroundColor: color.state.successBg },
  statusEstimated: { backgroundColor: color.state.warningBg },
  statusUnknown: { backgroundColor: color.surface.soft },
 canvas: { backgroundColor: color.brand.ivory }, header: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginBottom: spacing[6] }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, headerTitle: { flex: 1, gap: spacing[1] }, headerSpacer: { width: 44 }, headerActions: { alignItems: 'flex-end', gap: spacing[2] }, version: { minWidth: 44, minHeight: 32, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, revertButton: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center', borderWidth: 1, borderColor: color.surface.field }, stats: { flexDirection: 'row', gap: spacing[2] }, stat: { flex: 1, minHeight: 88, padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card, justifyContent: 'center', gap: spacing[1] }, viewerNotice: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, conflict: { gap: spacing[2], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.state.dangerBg }, actionNotice: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, warningNotice: { gap: spacing[1], marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint }, dayTabs: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[6] }, dayTab: { minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center' }, dayTabActive: { backgroundColor: color.brand.navy, borderColor: color.brand.navy }, allDayHeading: { marginTop: spacing[6], marginBottom: spacing[1] }, sectionHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: spacing[6], flexWrap: 'wrap', gap: spacing[2] }, sectionHeadingRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], flexWrap: 'wrap' }, recalcButton: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, recalcButtonPrimary: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, reorderHint: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, moveButtons: { flexDirection: 'row', gap: spacing[2] }, moveButton: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, timeline: { gap: spacing[3], marginTop: spacing[3] },
  // 넓은 화면 2단 — 본문이 남는 폭을 가져가고 요약 칸은 고정이다. 비율로 나누면 넓어질수록
  // 요약 칸까지 늘어나 여백만 커진다(Split.tsx 가 같은 이유로 고정폭을 쓴다).
  wideGrid: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6] },
  timelineColumn: { flex: 1, minWidth: 0 },
  aside: { width: 360 },
  summaryRow: { flexDirection: 'row', gap: spacing[2], marginBottom: spacing[3] },
  summaryCell: { flex: 1, gap: 2, padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  asideCard: { gap: spacing[2], marginTop: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  // 노선도 — 정차 노드와 구간. 정차가 많으면 가로로 스크롤한다.
  strip: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2], paddingVertical: spacing[3] },
  stripEntry: { flexDirection: 'row', alignItems: 'center' },
  segment: { alignItems: 'center', gap: spacing[1], paddingHorizontal: spacing[2] },
  segmentLine: { width: 72, height: 3, borderRadius: radius.full, backgroundColor: color.brand.navy },
  stop: { width: 112, alignItems: 'center', gap: spacing[1] },
  stopName: { textAlign: 'center' },
  node: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  nodeFirst: { backgroundColor: color.brand.orange }, itemRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] }, time: { width: 48, paddingTop: spacing[4] }, itemCard: { flex: 1, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card }, itemTitleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2], flexWrap: 'wrap' }, grow: { flex: 1, gap: spacing[1], minWidth: 180 }, itemActions: { flexDirection: 'row', gap: spacing[2] }, lockButton: { minWidth: 64, minHeight: 44, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, lockButtonActive: { backgroundColor: color.brand.navy }, lockBadge: { minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, excludeButton: { minWidth: 56, minHeight: 44, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.state.dangerBg, alignItems: 'center', justifyContent: 'center' }, actionDisabled: { opacity: 0.5 }, metaRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] }, empty: { marginTop: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' }, rhythmCard: { gap: spacing[1], marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint }, itemCardAtRisk: { borderWidth: 1, borderColor: color.state.danger }, paceRow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], marginTop: spacing[1] }, actualButtons: { flexDirection: 'row', gap: spacing[2] }, actualButton: { minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, replanActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] }, reviewButton: { alignSelf: 'flex-start', minHeight: 44, paddingHorizontal: spacing[3], marginTop: spacing[1], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, reviewedBadge: { alignSelf: 'flex-start', minHeight: 28, paddingHorizontal: spacing[3], marginTop: spacing[1], borderRadius: radius.full, backgroundColor: color.state.successBg, alignItems: 'center', justifyContent: 'center' } });
