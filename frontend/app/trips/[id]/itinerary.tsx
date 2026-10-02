import { enCount, txf } from '@/i18n/format';
import { formatClock, formatDayHeading as formatLocaleDayHeading } from '@/i18n/datetime';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Animated, Easing, Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { TripNameSheet } from '@/trip/TripNameSheet';
import { NowCard } from '@/plan/NowCard';
import {
  EMPTY_PROGRESS, arrive as arriveAt, drift, loadProgress, needsManualArrival,
  isToday, localDateKey, pause as pauseRun, saveProgress, saysStartsIn, skip as skipStop, start as startRun, stepStates,
  type TripProgress,
} from '@/plan/tripProgress';
import {
  arriveProgress, fetchProgress, pauseProgress, skipProgress, startProgress, type ProgressResult,
} from '@/plan/tripProgressApi';
import { arrivedAtOf } from '@/trip/page/actualTime';
import { ImpressionView, useImpressionTracker } from '@/analytics/impressions';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { PlaceReviewModal } from '@/components/PlaceReviewModal';
import { Screen } from '@/components/Screen';
import { Skeleton } from '@/components/Skeleton';
import { TabBar, bottomBarClearance } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { LockToggle } from '@/components/LockToggle';
import { color, gutter, radius, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';
import { TripPageDesktop } from '@/trip/page/TripPageDesktop';
import { TripPageMobile } from '@/trip/page/TripPageMobile';
import { formatManwon, tripPageKind } from '@/trip/page/tripPageModel';
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
  stopClock,
} from '@/plan/itinerary';
import { nextSyncPollDelay, SYNC_POLL_BASE_MS } from '@/plan/syncPoll';
import { formatTravelLabel, itineraryStats, totalTravelMinutes } from '@/plan/itinerarySummary';
import { loadPlaceReviews, submitPlaceReview } from '@/review/placeReviews';
import { stopNameForLanguage } from '@/discovery/romanize';
import { itemOtherName } from '@/plan/itineraryItemName';
import { useI18n } from '@/i18n';
import { takeItineraryHint } from '@/onboarding/firstRun';
import { ItineraryHint } from '@/onboarding/ItineraryHint';
import { describeWarningCodes } from '@/plan/warningLabels';
import { ExcludeConfirmModal, type ExcludeReason } from '@/components/ExcludeConfirmModal';
import { localizeMessage } from '@/i18n/messages';
import { koreanToward } from '@/i18n/korean';
import { humanTripTitle, tripNameOrDates } from '@/trip/tripNaming';
import { categoryGlyph, loadPlacePhoto, loadPlacePhotos, type PlacePhoto } from '@/plan/placePhotos';
import { summarizeItineraryBudget, type BudgetCategoryKey, type BudgetSummary } from '@/plan/itineraryBudget';
import { loadTripBudget } from '@/trip/tripBudget';
import { initialDayIndex } from '@/trip/openDay';

// 경고 문구는 src/plan/warningLabels.ts 로 옮겼다 — 시험이 붙들게 하려고.

// 영업시간 경고/-858) — 편집 다섯 갈래 중 넷(더하기 제외, 재계산은 비동기라
// 이 응답에 못 싣는다)이 warnings·notChecked를 함께 돌려준다. 되돌리기는 여러 날에 걸친
// 위반이 함께 올 수 있어 하루가 아니라 일정 전체에서 항목을 찾는다.
function describeOpeningHoursIssues(itinerary: ItineraryDto, warnings: ItineraryOpeningHoursWarning[], notChecked: ItineraryOpeningHoursNotChecked[], tx: (ko: string, en: string) => string): string[] {
  const itemsById = new Map(itinerary.days.flatMap((day) => day.items).map((item) => [item.id, item]));
  const closedMessages = warnings
    .filter((warning) => warning.code === 'OPENING_HOURS_CLOSED')
    .map((warning) => {
      const title = itemsById.get(warning.itemId)?.title ?? tx('이 장소', 'this place');
      return txf(tx, '%s은(는) 이 시각에 영업하지 않아요.', '%s is closed at this time.', title);
    });
  const notCheckedMessages = notChecked.map((entry) => entry.reason === 'NOT_COLLECTED'
    ? tx('일부 장소는 영업시간 정보가 없어 확인하지 못했어요.', "We couldn't check opening hours for some places — no data yet.")
    : tx('시각이 없는 항목이 있어 일부는 확인하지 못했어요.', "Some items have no visit time, so we couldn't check them."));
  return [...closedMessages, ...notCheckedMessages];
}

// 🔴 'ko-KR' 이 박혀 있었다 — 어떤 언어를 골라도 한국식으로 나왔다 (S15P21E201-1355).
function formatTime(value: string, locale: string) {
  return formatClock(value, locale);
}

// 일정 칸의 시각. 시각이 없는 항목(startsAt null)은 null — 부르는 쪽이 「미정」을 그린다. 1970년 시각을 만들지 않는다.
function formatStopTime(value: string | null, locale: string): string | null {
  return value ? formatClock(value, locale) : null;
}

// 1000m 이상은 km 한 자리로 (시안 1절). 「1200m」보다 「1.2km」가 걷는 거리로 읽힌다.
function formatWalk(meters: number) {
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)}km` : `${meters}m`;
}

// 「9월 19일 (금)」 — 시안 3.2. 날짜를 못 읽으면 지어내지 않고 「n일차」로만 적는다.
//
// 🔴 한국어면 손으로 만들고 그 밖이면 «무조건 en-US» 였다 (S15P21E201-1355).
//    일본어·중국어 사용자가 자기 언어로 고른 화면에서 「September 20 (Sat)」를 봤다.
//    이제는 운영체제에 맡긴다 — ja 「9月20日(土)」 · zh 「9月20日周六」.
function formatDayHeading(value: string, index: number, tx: (ko: string, en: string) => string, locale: string) {
  return formatLocaleDayHeading(value, locale) ?? txf(tx, '%s일차', 'Day %s', index + 1);
}

// 지도 대신 노선도 — 디자인 확정안 B안(09-디자인-인계-일정).
function RouteStrip({ items, times, tx, locale }: { items: ItineraryItemDto[]; times: (string | null)[]; tx: (ko: string, en: string) => string; locale: string }) {
  const { language } = useI18n();
  if (!items.length) return null;
  return <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.strip}>
    {items.map((item, index) => {
      const travel = [
        item.walkingMeters == null ? null : txf(tx, '도보 %s', '%s walk', formatWalk(item.walkingMeters)),
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
          <Text variant="caption" weight="bold" numberOfLines={1} style={styles.stopName}>{stopNameForLanguage(item.title, itemOtherName(item, null, language), language)}</Text>
          <Text variant="caption" color={color.text.muted}>{formatStopTime(times[index] ?? item.startsAt, locale) ?? tx('미정', 'TBD')}</Text>
        </View>
      </View>;
    })}
  </ScrollView>;
}

// 예산 명세 — 시안 design_handoff_itinerary 7절 (S15P21E201-1432).
//
// 🔴 **이 카드는 지금 「미정」투성이로 뜨는 것이 정상이다.** 서버가 항목 비용을 하나도
//    안 준다 — 장소 표에 가격 칸이 없어서다. 버그로 보고 고치러 가지 마라.
//    그래도 카드를 그리는 이유는, **자리가 비어 있어야 값이 들어올 곳이 보이기** 때문이다
//    (이 화면의 「수단 — 아직 없어요」 칸과 같은 이유).
function BudgetCard({ summary }: { summary: BudgetSummary }) {
  const { tx } = useI18n();

  const LABEL: Record<BudgetCategoryKey, string> = {
    FOOD: tx('식비', 'Food'),
    CAFE: tx('카페', 'Cafés'),
    ADMISSION: tx('입장·체험', 'Admission'),
    TRANSIT: tx('교통 (추정)', 'Transit (est.)'),
  };
  const SLICE: Record<BudgetCategoryKey, object> = {
    FOOD: styles.budgetSliceFood,
    CAFE: styles.budgetSliceCafe,
    ADMISSION: styles.budgetSliceAdmission,
    TRANSIT: styles.budgetSliceTransit,
  };

  // 「10.2만원」 — 헤더 요약과 같은 자릿수 규칙. 영어는 만 단위가 없어 원 단위 그대로.
  const manwon = (won: number) => tx(`${Math.round(won / 1000) / 10}만원`, `₩${won.toLocaleString()}`);

  // 🔴 **아는 비용이 하나라도 있는가.** 이 값이 카드 전체의 말투를 가른다 —
  //    없으면 숫자도 막대도 안 그리고, 「예산에 맞아요」 같은 판정도 안 한다.
  const priced = summary.known > 0;
  const over = summary.remainingKrw != null && summary.remainingKrw < 0;
  const comparable = priced && summary.budgetKrw != null && summary.budgetKrw > 0;
  const usedRatio = comparable ? Math.min(1, summary.krw / (summary.budgetKrw as number)) : 0;
  const maxDayKrw = summary.days.reduce((most, day) => Math.max(most, day.krw), 0);

  return <View style={styles.budgetCard}>
    <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('예산 대비', 'Against budget')}</Text>

    <View style={styles.budgetHead}>
      <View style={styles.budgetHeadLeft}>
        {priced
          ? <Text variant="display" weight="bold">{txf(tx, '%s원', '₩%s', summary.krw.toLocaleString())}</Text>
          : <Text variant="body" weight="bold" color={color.text.muted}>{tx('아직 계산할 수 없어요', "Can't add this up yet")}</Text>}
        {summary.unpriced > 0 ? <Text variant="caption" color={color.text.muted}>{txf(tx, '%s곳 비용 미정', '%s place(s) unpriced', summary.unpriced)}</Text> : null}
      </View>
      {summary.budgetKrw != null ? <View style={styles.budgetHeadRight}>
        <Text variant="caption" color={color.text.muted}>{txf(tx, '예산 %s원', 'Budget ₩%s', summary.budgetKrw.toLocaleString())}</Text>
        {/* 🔴 아는 비용이 없을 때 「예산에 맞아요」라고 적지 않는다. 그건 맞는 상태가
            아니라 **맞는지 모르는 상태**다. 12곳 중 0곳의 값으로 예산을 통과시키면,
            사람은 통과한 줄 알고 그 예산으로 떠난다. */}
        {priced
          ? <Text variant="caption" weight="bold" color={over ? color.state.danger : color.state.success}>
              {over
                ? txf(tx, '예산을 %s 넘어요', '%s over budget', manwon(-(summary.remainingKrw as number)))
                : txf(tx, '예산에 맞아요 · %s 남음', 'Within budget · %s left', manwon(summary.remainingKrw as number))}
            </Text>
          : <Text variant="caption" color={color.text.muted}>{tx('비교할 비용 자료가 아직 없어요', 'No cost data to compare yet')}</Text>}
      </View> : null}
    </View>

    {/* 총액 대 예산. 비교할 것이 없으면 안 그린다 — 빈 막대는 「0을 썼다」로 읽힌다. */}
    {comparable ? <View accessibilityLabel={tx('예산 사용량', 'Budget used')} style={styles.budgetTotalTrack}>
      {/* 🔴 넘쳤을 때 state.danger 를 **채움으로 쓰지 않는다.** 배색 검사가 그것을 막고
          있고(위험은 채우지 않는다), 이 자리는 글자가 없는 표시라 state.dot 자리다. */}
      <View style={[styles.budgetTotalFill, over && styles.budgetTotalFillOver, { flex: usedRatio }]} />
      <View style={{ flex: 1 - usedRatio }} />
    </View> : null}

    {/* 갈래 분해. 아는 값이 있을 때만 — 없으면 아래 목록이 「미정」으로 같은 말을 한다. */}
    {priced && summary.krw > 0 ? <View accessibilityLabel={tx('갈래별 비중', 'Share by category')} style={styles.budgetSplit}>
      {summary.categories.filter((entry) => entry.krw > 0).map((entry) => (
        <View key={entry.key} style={[SLICE[entry.key], { flex: entry.krw }]} />
      ))}
    </View> : null}

    <View style={styles.budgetRows}>
      {summary.categories.map((entry) => <View key={entry.key} style={styles.budgetRow}>
        <View style={SLICE[entry.key] ? [styles.budgetDot, SLICE[entry.key]] : styles.budgetDot} />
        <Text variant="caption" style={styles.budgetRowLabel}>{LABEL[entry.key]}</Text>
        <Text variant="caption" color={color.text.muted}>
          {entry.key === 'TRANSIT'
            ? tx('구간 거리 기준', 'By leg distance')
            : txf(tx, '%s곳', '%s place(s)', entry.count)}
        </Text>
        {entry.known > 0
          ? <Text variant="caption" weight="bold" style={styles.budgetRowValue}>{txf(tx, '%s원', '₩%s', entry.krw.toLocaleString())}</Text>
          : <Text variant="caption" color={color.text.muted} style={styles.budgetRowValue}>{tx('미정', 'Unpriced')}</Text>}
      </View>)}
    </View>

    {/* 일차별. 하루짜리 여행에는 나눌 것이 없다. */}
    {summary.days.length > 1 ? <View style={styles.budgetDays}>
      <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('일차별', 'By day')}</Text>
      {summary.days.map((day) => <View key={`${day.date}-${day.dayIndex}`} style={styles.budgetDayRow}>
        <Text variant="caption" color={color.text.muted} style={styles.budgetDayLabel}>{tx(`${day.dayIndex + 1}일차`, `Day ${day.dayIndex + 1}`)}</Text>
        <View style={styles.budgetDayTrack}>
          {maxDayKrw > 0 ? <><View style={[styles.budgetDayFill, { flex: day.krw }]} /><View style={{ flex: maxDayKrw - day.krw }} /></> : null}
        </View>
        {day.known > 0
          ? <Text variant="caption" weight="bold" style={styles.budgetDayValue}>{txf(tx, '%s원', '₩%s', day.krw.toLocaleString())}</Text>
          : <Text variant="caption" color={color.text.muted} style={styles.budgetDayValue}>{tx('미정', 'Unpriced')}</Text>}
      </View>)}
    </View> : null}

    {/* 🔴 각주는 장식이 아니다. 「교통 22,000원」이 어디서 나온 값인지 안 적으면,
        사람은 그것을 실제 요금표에서 받은 값으로 읽는다. */}
    <Text variant="caption" color={color.text.muted}>
      {tx('교통은 구간 거리로 추정한 값이에요 · 입장료·식비는 장소 자료 기준',
        'Transit is estimated from leg distance · admission and food come from place data')}
    </Text>
  </View>;
}

// 정차 한 칸 — 시안 design_handoff_itinerary 2.5(넓은 화면) · 3.3(폰).
function StopPhoto({ placeId, wide }: { placeId: string; wide: boolean }) {
  // 🔴 정차지 사진 — S15P21E201-1378. 일정 항목에는 사진이 없어 장소 상세에서 받아 온다. 없으면 갈래 아이콘.
  const [photo, setPhoto] = useState<PlacePhoto | null>(null);
  useEffect(() => { let active = true; void loadPlacePhoto(placeId).then((next) => { if (active) setPhoto(next); }); return () => { active = false; }; }, [placeId]);
  if (photo?.photoUrl) return <Image source={{ uri: photo.photoUrl }} resizeMode="cover" accessibilityLabel="" style={[styles.stopPhoto, wide && styles.stopPhotoWide]} />;
  return <View style={[styles.stopPhoto, styles.stopPhotoEmpty, wide && styles.stopPhotoWide]}><Text variant={wide ? 'title' : 'body'}>{categoryGlyph(photo?.category)}</Text></View>;
}

function StopRow({ item, index, isLast, displayTime, wide, expanded, onToggleExpand, canEdit, lockBusy, excludeBusy, dayBusy, onLock, onExclude, reorderMode, canMoveUp, canMoveDown, moveBusy, onMoveUp, onMoveDown, pace, estimated, actualBusy, onRecordArrival, onRecordDeparture, accessToken, stepState }: { item: ItineraryItemDto; index: number; isLast: boolean; displayTime: string | null; wide: boolean; expanded: boolean; onToggleExpand: () => void; canEdit: boolean; lockBusy: boolean; excludeBusy: boolean; dayBusy: boolean; onLock: () => void; onExclude: () => void; reorderMode: boolean; canMoveUp: boolean; canMoveDown: boolean; moveBusy: boolean; onMoveUp: () => void; onMoveDown: () => void; pace?: ItineraryPaceItemDto; estimated?: boolean; actualBusy?: boolean; onRecordArrival?: () => void; onRecordDeparture?: () => void; accessToken: string | null;
  /** 시안 ⑤ — 다녀옴 · 현재 · 다음 · 이후. 모르면 안 준다(진행을 안 켠 화면). */
  stepState?: 'done' | 'current' | 'next' | 'later' }) {
  const { tx, locale, language } = useI18n();
  const disabled = !canEdit || lockBusy || excludeBusy || dayBusy;

  // 다녀오셨나요 평가 — 방문 예정 시각이 지난 칸에만 띄운다.
  // 시각이 없는 곳은 지났다고 보지 않는다 — new Date(null) 은 1970년이라 가지도 않은 곳에 후기를 물었다.
  const isPastVisit = useMemo(() => item.startsAt != null && new Date(item.startsAt).getTime() < Date.now(), [item.startsAt]);
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
  const walkLabel = item.walkingMeters == null ? null : txf(tx, '도보 %s', '%s walk', formatWalk(item.walkingMeters));
  const facts = [
    walkLabel,
    formatTravelLabel(item, tx, index === 0),
    item.estimatedCostKrw == null ? null : item.estimatedCostKrw === 0 ? tx('무료', 'Free') : txf(tx, '%s원', '₩%s', item.estimatedCostKrw.toLocaleString()),
  ].filter((fact): fact is string => fact !== null);

  // 구간 라벨 — 이 값들은 이 방문지로 들어오는 구간이다(backend ItineraryQueryService
  // 의 incomingLeg). 다음 칸까지가 아니다. 그래서 노드 위에 그린다.
  const legLabel = [walkLabel, formatTravelLabel(item, tx, index === 0)].filter(Boolean).join(' · ');

  const lockControl = reorderMode
    ? (item.locked
      ? <View style={styles.lockBadge}><Text variant="caption" weight="bold" color={color.text.onAction}>{tx('고정됨', 'Locked')}</Text></View>
      : <View style={styles.moveButtons}>
        <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 위로 이동', 'Move %s up', item.title)} accessibilityState={{ disabled: !canMoveUp || moveBusy }} disabled={!canMoveUp || moveBusy} onPress={onMoveUp} style={[styles.moveButton, (!canMoveUp || moveBusy) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>▲</Text></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 아래로 이동', 'Move %s down', item.title)} accessibilityState={{ disabled: !canMoveDown || moveBusy }} disabled={!canMoveDown || moveBusy} onPress={onMoveDown} style={[styles.moveButton, (!canMoveDown || moveBusy) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>▼</Text></Pressable>
      </View>)
    : canEdit
      // 🔓/🔒 그림 글자 대신 상태를 말로 적는 알약 — 폰 여행 화면과 같은 부품(UI 캔버스 ④).
      ? <LockToggle locked={item.locked} name={item.title} busy={lockBusy} disabled={disabled} onPress={onLock} tx={tx} />
      : <LockToggle locked={item.locked} name={item.title} tx={tx} />;

  return <>
    {/* 구간 — 세로선과 「도보 1.2km」. 넓은 화면은 위쪽 가로 노선도가 같은 것을 보여주므로 생략한다. */}
    {/* — 첫 방문지에도 들어오는 구간이 있다 (출발지에서 온다). 위 주석 참고. */}
    {(index > 0 || legLabel) && !wide ? <View style={styles.segmentRow}>
      <View style={styles.rail}><View style={styles.railLine} /></View>
      {legLabel ? <View style={styles.segmentLabel}><Text variant="caption" weight="bold" color={color.brand.navy}>{legLabel}</Text></View> : null}
    </View> : null}
    <View style={[styles.stopRow, wide && styles.stopRowWide]}>
      <View style={styles.rail}>
        {/* 🔴 노드가 「어디까지 왔나」를 말한다(시안 ⑤). 다녀옴 ✓ 초록 · 현재 ● 오렌지 ·
            다음 네이비 번호 · 이후 흐린 번호. 순서 수정 중에는 셋 다 ≡ 손잡이가 되어
            **끌 수 있다는 것**을 그림으로 말한다 — 글로 적으면 아무도 안 읽는다. */}
        <View style={[
          styles.node,
          wide && styles.nodeWide,
          stepState === 'done' && styles.nodeDone,
          stepState === 'current' && styles.nodeCurrent,
          stepState === 'later' && styles.nodeLater,
          !stepState && index === 0 && styles.nodeFirst,
        ]}>
          <Text variant="caption" weight="bold" color={stepState === 'later' ? color.text.muted : color.text.onAction}>
            {reorderMode ? '≡' : stepState === 'done' ? '✓' : stepState === 'current' ? '●' : index + 1}
          </Text>
        </View>
        {!isLast && !wide ? <View style={styles.railLine} /> : null}
      </View>
      {/* 🔴 위험은 세로줄이 아니라 말로 — 왼쪽 붉은 3px 줄은 뜻이 그 자리에 없어 「왜 빨간 줄이 있지」로 읽혔다(2026-09-21 실기, S15P21E201-1400). */}
      <View style={styles.stopBody}>
        <View style={styles.stopHead}>
          <StopPhoto placeId={item.placeId} wide={wide} />
          {/* 펼치는 손잡이는 제목 덩이에만 둔다. 행 전체를 Pressable 로 감싸면 그 안의
              자물쇠가 「버튼 안의 버튼」이 되고, 웹에서는 그게 허용되지 않는다.
          */}
          <Pressable accessibilityRole="button" accessibilityState={{ expanded }} accessibilityLabel={(expanded ? txf(tx, '%s 접기', 'Collapse %s', item.title) : txf(tx, '%s 자세히', 'Details for %s', item.title))} onPress={onToggleExpand} style={styles.stopTitle}>
            <View style={styles.titleLine}>
              <Text variant={wide ? 'title' : 'body'} weight="bold" style={styles.titleText}>{stopNameForLanguage(item.title, itemOtherName(item, null, language), language)}</Text>
              {item.dataStatus ? <View style={[styles.statusChip, STATUS_CHIP[item.dataStatus]]}><Text variant="caption" weight="bold" color={STATUS_COLOR[item.dataStatus]}>{STATUS_LABEL[item.dataStatus]}</Text></View> : null}
              {pace?.atRisk ? <View style={[styles.statusChip, styles.riskChip]}><Text variant="caption" weight="bold" color={color.state.danger}>{tx('하루 넘길 위험', 'May run past the day')}</Text></View> : null}
            </View>
            {/* 서버가 늘 null 로 주는 칸이다. 있으면 그리고 없으면 줄을 만들지 않는다. */}
            {item.description ? <Text variant="caption" color={color.text.body} numberOfLines={expanded ? undefined : 1}>{item.description}</Text> : null}
          </Pressable>
          <View style={[styles.stopRight, wide && styles.stopRightWide]}>
            <View style={wide ? styles.stopRightStack : undefined}>
              <Text variant={wide ? 'title' : 'body'} weight="bold" color={color.brand.navy}>{formatStopTime(displayTime, locale) ?? tx('미정', 'TBD')}</Text>
              {wide && item.estimatedCostKrw != null ? <Text variant="caption" color={color.text.muted}>{item.estimatedCostKrw === 0 ? tx('무료', 'Free') : txf(tx, '%s원', '₩%s', item.estimatedCostKrw.toLocaleString())}</Text> : null}
            </View>
            {lockControl}
          </View>
        </View>
        {expanded ? <View style={styles.stopDetail}>
          {facts.length ? <View style={styles.metaRow}>{facts.map((fact) => <Text key={fact} variant="caption" color={color.text.muted}>{fact}</Text>)}</View> : null}
          {pace && !reorderMode ? <View style={styles.paceRow}>
            {pace.visited ? <Text variant="caption" weight="bold" color={color.state.success}>{txf(tx, '도착 %s', 'Arrived %s', pace.predictedArrival ? formatTime(pace.predictedArrival, locale) : '--:--') + (pace.predictedDeparture ? ` · ${txf(tx, '출발 %s', 'Left %s', formatTime(pace.predictedDeparture, locale))}` : '')}</Text>
              : <Text variant="caption" weight="bold" color={pace.atRisk ? color.state.danger : color.text.muted}>{txf(tx, '예상 도착 %s%s', 'Est. arrival %s%s', pace.predictedArrival ? formatTime(pace.predictedArrival, locale) : '--:--', estimated ? tx(' (추정)', ' (est.)') : '')}{pace.atRisk ? ` · ${tx('하루를 넘길 위험', 'Risks running past the day')}` : ''}</Text>}
            {(onRecordArrival || onRecordDeparture) ? <View style={styles.actualButtons}>
              {onRecordArrival && !pace.visited ? <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 도착 찍기', 'Mark arrival at %s', item.title)} accessibilityState={{ busy: actualBusy }} disabled={actualBusy} onPress={onRecordArrival} style={[styles.actualButton, actualBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('도착 찍기', 'Mark arrival')}</Text></Pressable>
                : onRecordDeparture && !pace.visited ? <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 출발 찍기', 'Mark departure at %s', item.title)} accessibilityState={{ busy: actualBusy }} disabled={actualBusy} onPress={onRecordDeparture} style={[styles.actualButton, actualBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('출발 찍기', 'Mark departure')}</Text></Pressable> : null}
            </View> : null}
          </View> : null}
          {!reorderMode && canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 제외', 'Exclude %s', item.title)} accessibilityState={{ busy: excludeBusy, disabled }} disabled={disabled} onPress={onExclude} style={[styles.excludeButton, disabled && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.state.danger}>{excludeBusy ? tx('처리 중', 'Processing') : tx('이 장소 제외', 'Remove this place')}</Text></Pressable> : null}
          {isPastVisit && !reorderMode ? (
            reviewStatus === 'reviewed' ? <View style={styles.reviewedBadge}><Text variant="caption" weight="bold" color={color.state.success}>{tx('평가함', 'Reviewed')}</Text></View>
            : reviewStatus === 'can-review' ? <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 다녀오셨나요? 평가하기', 'Review your visit to %s', item.title)} onPress={() => setReviewModalOpen(true)} style={styles.reviewButton}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('다녀오셨나요?', 'Did you visit?')}</Text></Pressable>
            : null
          ) : null}
        </View> : null}
      </View>
    </View>
    {isPastVisit ? <PlaceReviewModal visible={reviewModalOpen} placeTitle={item.title} onClose={() => setReviewModalOpen(false)} onSubmit={submitReview} /> : null}
  </>;
}

type ViewMode = 'day' | 'all';

/**
 * 넓은 화면(데스크톱 판정 — useLayout)은 여행 페이지 통합 화면을 연다 — 추천 코스 + 일정을 한 화면에 (S15P21E201-1535, 시안
 * frontend/docs/design_handoff_trip_page/). 🔴 `?classic=1` 이면 지금까지의 화면 그대로다 — 새 화면의
 * ⋯ 「일정 편집」이 이리로 온다. 순서·고정·제외·다시 계산·되돌리기는 여기에만 있다.
 *
 * 폰은 같은 통합 화면의 폰 판이다(통합 2단계, TripPageMobile). 아래 ItineraryClassic 은 `?classic=1` 로만 온다
 * — 어느 판을 여는지의 규칙은 tripPageKind(src/trip/page/tripPageModel.ts)가 갖는다.
 */
export default function ItineraryScreen() {
  const { desktop } = useLayout();
  const { id, name, classic } = useLocalSearchParams<{ id: string; name?: string; classic?: string }>();
  const page = id ? tripPageKind(desktop, classic === '1') : 'classic';
  if (id && page === 'desktop') return <TripPageDesktop source={{ kind: 'itinerary', itineraryId: id }} askName={name === '1'} />;
  if (id && page === 'mobile') return <TripPageMobile source={{ kind: 'itinerary', itineraryId: id }} askName={name === '1'} />;
  return <ItineraryClassic />;
}

function ItineraryClassic() {
  const router = useRouter();
  const { accessToken } = useAuth();
  // 🔴 `name=1` 은 ③ 코스 고르기에서 넘어왔다는 뜻이다. 그때만 이름 묻기가 **열린 채로**
  //    들어온다(시안 ④). 내 여행에서 다시 들어오면 안 연다 — 열 때마다 물으면 건너뛸 수
  //    있다고 말해 놓고 안 놓아주는 것이다.
  const { id, day: dayParam, view: viewParam, name: nameParam, reorder: reorderParam } = useLocalSearchParams<{ id: string; day?: string; view?: string; name?: string; reorder?: string }>();
  const { tx, locale } = useI18n();
  const itineraryId = id ?? '';
  const [result, setResult] = useState<ItineraryLoadResult>({ state: 'error', message: tx('일정 식별자가 없어요.', 'Missing itinerary identifier.') });
  const [loading, setLoading] = useState(Boolean(itineraryId));
  // 날짜는 사람이 보는 주소에서는 1일차부터 세지만(?day=2), 내부 배열 인덱스는 0부터다.
  const [selectedDay, setSelectedDay] = useState(() => { const requested = Number(dayParam); return Number.isInteger(requested) && requested >= 1 ? requested - 1 : 0; });
  const [dayOutOfRange, setDayOutOfRange] = useState(false);
  // 처음 받아 왔을 때 한 번만 — 여행 중이면 오늘 칸을 연다(S15P21E201-1921). 그 뒤 고친 칸은 건드리지 않는다.
  const openedDayRef = useRef(false);
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
  // 이 화면에서 방금 찍은 도착 — 진행 기록(서버의 도착 표에서 읽는다)은 다시 받기 전까지 모른다(S15P21E201-1690).
  const [arrivalsHere, setArrivalsHere] = useState<Record<string, string>>({});
  const [replanConfirming, setReplanConfirming] = useState(false);
  const [replanBusy, setReplanBusy] = useState(false);
  const [replanOverflowIds, setReplanOverflowIds] = useState<string[] | null>(null);
  const [syncDisconnected, setSyncDisconnected] = useState(false);
  // 순서 바꾸기 응답에만 실려 오는 영업시간 경고/-852) — 활동 이력엔 안 남으므로
  // 그 자리에서 받은 문장을 이 상태에 직접 담아 둔다. 다음 편집을 시작하면 지운다.
  const [openingHoursNotice, setOpeningHoursNotice] = useState<string[]>([]);
  // ⋯ 패널과 펼친 정차. 둘 다 화면에만 있는 상태라 서버에 안 보낸다.
  const [menuOpen, setMenuOpen] = useState(false);
  // ③ 에서 넘어온 직후에는 열린 채로 들어온다. 그 뒤로는 「이름 바꾸기」로만 연다.
  const [naming, setNaming] = useState(nameParam === '1');

  // ── 일정 진행 (시안 ⑤) ──────────────────────────────────────────────────
  //
  // 🔴 **서버가 정본이다**(S15P21E201-1325). 화면은 사건을 보내고 서버가 돌려준 상태를 그린다.
  //    상태를 통째로 보내면 두 기기가 서로의 상태를 덮어쓴다.
  //
  // 🔴 서버에 그 자리가 없는 판(404·501)에서는 **기기에만** 남는다. 그 판정은 화면을 열 때
  //    한 번만 하고 그대로 간다 — 돌다가 갈아타면 눌러 놓은 것이 어디에 남았는지 알 수 없다.
  const [progress, setProgress] = useState<TripProgress>(EMPTY_PROGRESS);
  const [deviceOnly, setDeviceOnly] = useState<boolean | null>(null);
  const [progressError, setProgressError] = useState<string | null>(null);
  // 🔴 위치 권한·정확도를 아직 안 잰다. 그래서 「GPS 를 쓸 수 있다」로 두고, 손으로 찍는
  //    단추를 기본으로 감춘다 — 늘 보이면 사람이 그걸 정상 절차로 안다.
  const gpsUsable = true;
  useEffect(() => {
    if (!itineraryId) return;
    let alive = true;
    void (async () => {
      const outcome = await fetchProgress(itineraryId, accessToken);
      if (!alive) return;
      if (outcome.state === 'success') { setDeviceOnly(false); setProgress(outcome.progress); return; }
      // 실패도 기기에 남은 것으로 이어 간다 — 통신이 잠깐 안 되는 것과 서버에 자리가
      // 없는 것은 사용자에게 같은 뜻이다. 다만 실패는 말해 준다.
      setDeviceOnly(true);
      if (outcome.state === 'error') setProgressError(outcome.message);
      setProgress(await loadProgress(itineraryId));
    })();
    return () => { alive = false; };
  }, [accessToken, itineraryId]);

  /**
   * 사건 하나를 보낸다.
   *
   * 🔴 화면을 **먼저** 바꾸고 보낸다. 안 그러면 눌렀는데 한 박자 뒤에 반응해서 사람이
   *    두 번 누르고, 그러면 같은 사건이 두 번 간다. 서버가 돌려준 것으로 다시 맞춘다.
   */
  const sendProgress = (optimistic: TripProgress, send: () => Promise<ProgressResult>) => {
    setProgress(optimistic);
    setProgressError(null);
    if (deviceOnly !== false) { void saveProgress(itineraryId, optimistic); return; }
    void send().then((outcome) => {
      if (outcome.state === 'success') { setProgress(outcome.progress); return; }
      if (outcome.state === 'error') setProgressError(outcome.message);
      // 🔴 서버가 안 받았으면 화면을 되돌린다. 안 되돌리면 사용자는 기록된 줄 아는데
      //    다른 기기에는 없다 — 그게 조용히 어긋나는 시작점이다.
      void fetchProgress(itineraryId, accessToken).then((again) => {
        if (again.state === 'success') setProgress(again.progress);
      });
    });
  };
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
    // 내가 무엇이든 했으면 동기화를 다시 촘촘하게 본다.
    pollDelayRef.current = SYNC_POLL_BASE_MS;
    const next = await loadItinerary(itineraryId, accessToken);
    setResult(next); setLoading(false);
    if (next.state === 'success') {
      if (!openedDayRef.current) { openedDayRef.current = true; setSelectedDay(initialDayIndex(next.itinerary.days, dayParam)); }
      setSelectedDay((current) => {
        const lastDay = Math.max(0, next.itinerary.days.length - 1);
        if (current > lastDay) { setDayOutOfRange(true); return 0; }
        return current;
      });
      void refreshVersions(next.itinerary.id);
    }
  }, [accessToken, itineraryId, refreshVersions, dayParam]);

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
  // 🔴 손으로 찍는 것은 오늘 방문지만(S15P21E201-1690, 조율 세션 결정). 서버는 날짜를 일부러 검사하지 않는다 — 앱이 지킨다.
  const recordToday = isToday(day?.date, localDateKey(new Date()));
  // 추천 노출 — 카드가 실제로 화면에 보일 때만 보낸다(S15P21E201-1698, !1691 과 같은 기준).
  const impressions = useImpressionTracker({ accessToken, sourceScreen: 'TRIP_ITINERARY', active: Boolean(itinerary) });
  /** 적힌 실제 도착. 도착만 적힌 곳은 서버가 「다녀옴」으로 안 봐서 속도(pace)에는 예측값만 온다 — 여기서 읽는다. */
  const arrivalOf = (itemId: string) => arrivalsHere[itemId] ?? arrivedAtOf(progress.outcomes, itemId);

  // ── 「지금」 카드가 쓰는 값들 ────────────────────────────────────────────
  const dayStops = day?.items ?? [];
  const dayStopIds = dayStops.map((item) => item.id);
  const currentStopId = dayStopIds[Math.min(progress.currentStopIndex, Math.max(0, dayStopIds.length - 1))] ?? null;
  const currentStop = dayStops.find((item) => item.id === currentStopId) ?? null;
  const nowClock = new Date().toTimeString().slice(0, 5);
  const nowDriftValue = drift(new Date().toISOString(), currentStop?.startsAt ?? null);
  // 「582분 빠름」은 읽는 사람이 나눗셈을 해야 한다(2026-09-21 실측, S15P21E201-1372) — 한 시간이 넘으면 시간·분으로.
  const driftSpan = nowDriftValue
    ? nowDriftValue.minutes >= 60
      ? nowDriftValue.minutes % 60 === 0
        ? txf(tx, '%s시간', '%s h', Math.floor(nowDriftValue.minutes / 60))
        : txf(tx, '%s시간 %s분', '%s h %s min', Math.floor(nowDriftValue.minutes / 60), nowDriftValue.minutes % 60)
      : txf(tx, '%s분', '%s min', nowDriftValue.minutes)
    : null;
  // 🔴 시작하지도 않은 여행에 「예정보다 빠름」이라고 하지 않는다 — S15P21E201-1489(B-13).
  //    왜 그런지는 saysStartsIn 의 주석이 소유한다(src/plan/tripProgress.ts).
  const nowDrift = nowDriftValue && driftSpan
    ? saysStartsIn(progress.status, nowDriftValue)
      ? txf(tx, '%s 뒤 시작', 'starts in %s', driftSpan)
      : txf(tx, '예정보다 %s %s', '%s %s', driftSpan, nowDriftValue.early ? tx('빠름', 'early') : tx('늦음', 'late'))
    : null;
  const nowTitle = progress.status === 'DONE'
    ? tx('오늘 일정을 다 돌았어요', 'You finished today')
    : progress.status === 'RUNNING' && currentStop
      ? txf(tx, `%s${koreanToward(currentStop.title)} 이동 중`, 'Heading to %s', currentStop.title)
      : currentStop
        ? txf(tx, '다음은 %s', 'Next: %s', currentStop.title)
        : tx('오늘 갈 곳이 없어요', 'Nothing planned today');
  // 🔴 없는 안내를 지어내지 않는다. 서버가 구간 안내를 안 주므로 설명 칸이 있으면 그것을, 없으면
  //    「4곳 중 1곳 다녀옴 · 다음 12:30」(시안 5 Itinerary)을 적는다 — 어디까지 왔는지는 지어내는 것이 아니라 세는 것이다.
  const doneCount = dayStopIds.filter((stopId) => progress.outcomes[stopId]).length;
  const nowProgressLine = dayStops.length && progress.status !== 'PLANNED'
    ? txf(tx, '%s곳 중 %s곳 다녀옴', '%s stops · %s done', dayStops.length, doneCount)
      + (progress.status !== 'DONE' && stopClock(currentStop?.startsAt) ? ` · ${txf(tx, '다음 %s', 'next %s', stopClock(currentStop?.startsAt) ?? '')}` : '')
    : null;
  const nowDetail = currentStop?.description ?? nowProgressLine;
  const nowProgressRatio = dayStops.length && progress.status !== 'PLANNED' ? doneCount / dayStops.length : null;
  // 넓은 화면에서만 2단으로 나눈다. 저장소 반응형 표가 「1024~ 사이드바 + 본문」이라
  // 그 경계를 그대로 쓴다 — 여기서 숫자를 새로 정하지 않는다(layout/breakpoints.ts).
  const { desktop } = useLayout();
  // 데스크톱 판인가 — 폭만이 아니라 폴드 펼침 가로까지, 판정은 useLayout 한 곳(S15P21E201-1563).
  const wide = desktop;
  const insets = useSafeAreaInsets();
  // — 통계는 값이 있는 것만 만든다. 판정은 itinerarySummary.ts 에 있다.
  const stats = useMemo(() => (itinerary ? itineraryStats(itinerary, tx) : []), [itinerary, tx]);
  const canEdit = itinerary?.canEdit !== false;
  // 첫 일정을 연 사람에게 한 번 — 「초안이니 고정·제외·다시 계산으로 고쳐라」 (S15P21E201-1361)
  const [hint, setHint] = useState(false);
  useEffect(() => { let alive = true; void takeItineraryHint().then((show) => { if (alive && show) setHint(true); }); return () => { alive = false; }; }, []);
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
  // 🔴 **그리는 목록의 순서**로 센다. 순서 수정 중에는 초안 순서가 원본과 달라서,
  //    원본 순서로 세면 초록 ✓ 가 엉뚱한 줄에 붙는다.
  const dayStepStates = stepStates(displayedItems.map((item) => item.id), progress);
  const dayTravelMinutes = useMemo(() => totalTravelMinutes(displayedItems), [displayedItems]);
  // 🔴 도보도 비용과 같은 모양으로 센다 (S15P21E201-1466). 예전에는 `?? 0` 으로 더해서
  //    **「모른다」가 「0미터」가 됐고**, 그 뒤 `> 0` 검사가 그것을 「표시하지 않음」으로 바꿨다.
  //    그래서 대중교통 여행은 도보 표시가 통째로 사라졌다 — 오류도 안내도 없이.
  //
  //    서버는 규칙대로다. 이동수단이 WALK 일 때만 도보 거리를 채운다 — 지하철 구간에
  //    직선거리를 넣으면 「지하철로 이만큼 걸었다」가 되어 틀린 답이 되기 때문이다.
  //    운영 실측(2026-09-22): 구간 812개 중 대중교통이 796개(98%)다. 즉 **거의 모든
  //    사용자가** 도보 표시를 못 보고 있었다.
  const dayWalking = useMemo(() => {
    const known = displayedItems.filter((item) => typeof item.walkingMeters === 'number');
    return { meters: known.reduce((sum, item) => sum + (item.walkingMeters as number), 0), known: known.length, total: displayedItems.length };
  }, [displayedItems]);
  const dayWalkingMeters = dayWalking.meters;
  // 비용 합계는 아는 칸이 몇 개인지 같이 말한다.
  const dayCost = useMemo(() => {
    const known = displayedItems.filter((item) => typeof item.estimatedCostKrw === 'number');
    return { krw: known.reduce((sum, item) => sum + (item.estimatedCostKrw as number), 0), known: known.length, total: displayedItems.length };
  }, [displayedItems]);
  const dayFacts = useMemo(() => [
    // 아는 칸이 하나도 없으면 숫자를 짓지 않고 **왜 없는지**를 적는다. 조용히 사라지면
    // 사용자는 그 기능이 있는 줄도 모른다.
    dayWalking.known === 0
      ? (displayedItems.length ? tx('도보 거리 미집계', 'Walking distance not measured') : null)
      : dayWalking.known === dayWalking.total
        ? txf(tx, '도보 %s', '%s on foot', formatWalk(dayWalkingMeters))
        : txf(tx, '도보 %s (%s곳 중 %s곳)', '%s on foot (%s places, %s measured)', formatWalk(dayWalkingMeters), dayWalking.total, dayWalking.known),
    dayCost.krw > 0
      ? dayCost.known === dayCost.total
        ? txf(tx, '%s원', '₩%s', dayCost.krw.toLocaleString())
        : txf(tx, '%s원 (%s곳 중 %s곳)', '%s KRW (%s places, %s priced)', dayCost.krw.toLocaleString(), dayCost.total, dayCost.known)
      : null,
  ].filter(Boolean).join(' · '), [dayWalking, dayWalkingMeters, dayCost, displayedItems.length, tx]);
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
    const arrivedAt = new Date().toISOString();
    const outcome = await recordItineraryItemActual({ itineraryId: itinerary.id, itemId: item.id, arrivedAt, departedAt: null, accessToken });
    setActualBusyItemId(null);
    if (outcome.state === 'success') {
      setArrivalsHere((current) => ({ ...current, [item.id]: arrivedAt }));
      setResult({ state: 'success', itinerary: outcome.itinerary });
      void refreshPaceAfterActual();
    }
    else if (outcome.state !== 'conflict') setActionMessage(outcome.message);
  };

  const recordDeparture = async (item: ItineraryItemDto) => {
    if (!itinerary) return;
    // PUT은 보낸 것이 최종 상태다(부분 갱신이 아니다) — 이미 기록된 도착 시각을
    // 함께 실어 보내지 않으면 출발만 남고 도착이 null로 지워진다.
    // 🔴 실제 도착을 싣는다(S15P21E201-1690). 여기에 서버의 «예측» 도착(pace.predictedArrival)을 실었었다 — 도착만 적힌 곳은
    //    서버가 「다녀옴」으로 안 봐서 예측값을 주고, 그 값이 실제 도착을 덮었다.
    const existingArrival = arrivalOf(item.id);
    if (!existingArrival) return;
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

  const excludeItem = async (item: ItineraryItemDto, reason: ExcludeReason | null) => {
    if (!itinerary) return;
    setExcludingItemId(item.id); setConflict(null); setActionMessage(null);
    // 고른 이유(S15P21E201-1695). 건너뛰면 칸을 비운다.
    const accepted = await removeItineraryItem({ itineraryId: itinerary.id, itemId: item.id, baseVersion: itinerary.version, operationalReason: reason ?? undefined, accessToken });
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

  // 통합 화면의 「순서 수정」에서 왔으면(?reorder=1) 일정이 보이는 대로 한 번 순서 수정을 연다 — 두 번 누르게 하지 않는다.
  const reorderOpened = useRef(false);
  useEffect(() => {
    if (reorderParam !== '1' || reorderOpened.current || !day || !canReorder) return;
    reorderOpened.current = true;
    startReorder();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reorderParam, day, canReorder]);

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

  // ── 일차 탭 알약 (시안 1절) ──────────────────────────────────────────────
  // 🔴 한 칸의 너비는 **폭을 재야 안다.** 일차 수가 둘이냐 다섯이냐에 따라 달라지고,
  //    폴드 기기는 앱이 켜진 채로 폭이 바뀐다. 상수로 박으면 그때 알약이 어긋난다.
  const [dayTrackWidth, setDayTrackWidth] = useState(0);
  const dayCount = itinerary?.days.length ?? 0;
  const daySlotWidth = dayCount > 0 && dayTrackWidth > 0 ? (dayTrackWidth - spacing[1] * 2) / dayCount : 0;
  const dayPillX = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    if (!daySlotWidth) return;
    Animated.timing(dayPillX, {
      toValue: selectedDay * daySlotWidth,
      duration: 450,
      easing: Easing.bezier(0.22, 1, 0.36, 1),
      // 위치만 옮기므로 그리기를 기다리지 않고 바로 굴린다.
      useNativeDriver: true,
    }).start();
  }, [selectedDay, daySlotWidth, dayPillX]);

  // ── 예산 명세 (시안 7절) ─────────────────────────────────────────────────
  // 🔴 예산은 **일정 응답에 없다.** 여행 상세를 한 번 더 부른다(src/trip/tripBudget.ts).
  //    못 받으면 null 이고, 그러면 카드가 예산 줄을 아예 안 그린다 — 0 으로 안 떨어뜨린다.
  const [budgetKrw, setBudgetKrw] = useState<number | null>(null);
  useEffect(() => {
    const budgetTripId = itinerary?.tripId;
    if (!budgetTripId) { setBudgetKrw(null); return; }
    let alive = true;
    void loadTripBudget(budgetTripId, accessToken).then((next) => {
      if (alive) setBudgetKrw(next.state === 'success' ? next.budgetKrw : null);
    });
    return () => { alive = false; };
  }, [itinerary?.tripId, accessToken]);

  // 장소 갈래 — 일정 항목에는 갈래 칸이 없어 장소 상세에서 받는다. 정차 사진이 이미
  // 부르고 있는 그 호출이고 세션 동안 기억하므로, 같은 장소를 두 번 묻지 않는다.
  const [placeCategories, setPlaceCategories] = useState<Record<string, string | null>>({});
  const allPlaceIds = useMemo(
    () => (itinerary ? itinerary.days.flatMap((entry) => entry.items.map((entryItem) => entryItem.placeId)) : []),
    [itinerary],
  );
  useEffect(() => {
    if (!allPlaceIds.length) { setPlaceCategories({}); return; }
    let alive = true;
    void loadPlacePhotos(allPlaceIds).then((photos) => {
      if (!alive) return;
      const next: Record<string, string | null> = {};
      Object.entries(photos).forEach(([placeId, photo]) => { next[placeId] = photo.category; });
      setPlaceCategories(next);
    });
    return () => { alive = false; };
  }, [allPlaceIds]);

  const budget = useMemo(
    () => (itinerary ? summarizeItineraryBudget(itinerary, placeCategories, budgetKrw) : null),
    [itinerary, placeCategories, budgetKrw],
  );

  // 방문지 수 — 모든 날의 정차를 합친다.
  const stopCount = itinerary ? itinerary.days.reduce((sum, day) => sum + day.items.length, 0) : 0;

  // 헤더 요약 — 값이 있는 것만 잇는다. 「미확인」이라고 적힌 칸은 정보가 아니라 잡음이다.
  const heroSummary = itinerary ? [
    itinerary.days.length > 0 ? tx(`${itinerary.days.length}일`, enCount(itinerary.days.length, 'day', 'days')) : null,
    stopCount > 0 ? tx(`${stopCount}곳`, `${stopCount} stops`) : null,
    // 🔴 여기도 「없음」과 「0」을 가른다. 대중교통 여행은 서버가 이 값을 안 채우므로
    //    예전에는 이 칸이 조용히 빠졌다 (S15P21E201-1466).
    typeof itinerary.totalWalkingMeters === 'number' && itinerary.totalWalkingMeters > 0
      ? txf(tx, '도보 %skm', '%skm on foot', (itinerary.totalWalkingMeters / 1000).toFixed(1))
      : null,
    typeof itinerary.totalEstimatedCostKrw === 'number' && itinerary.totalEstimatedCostKrw > 0
      ? txf(tx, '약 %s', 'about %s', formatManwon(itinerary.totalEstimatedCostKrw, tx)) : null,
  ].filter(Boolean).join(' · ') : '';

  // ── 네이비 헤더 (시안 design_handoff_itinerary 2·3절) ──────────────────────
  return <View style={styles.shell}><Screen scroll wide withTabBar style={styles.canvas}>
    <View style={styles.hero}>
      <View style={styles.heroTop}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={styles.heroBack}><Text variant="title" color={color.brand.navy}>‹</Text></Pressable>
        {/* 「초안 v1 · 기본 추천」 배지를 뺐다. 우리가 아는 것을 그대로
            내보인 말이지 사용자가 알아야 할 것이 아니었다 — 「초안」은 이미 저장된 여행에
            대고 아직 안 끝났다고 말하고, 「v1」은 편집할 때마다 올라가 불안만 주고
            「기본 추천」(fallbackMode=BASELINE)은 좋은 건지 나쁜 건지 알 수 없다.
            값을 버린 것이 아니다. version 은 되돌리기가 그대로 쓰고 fallbackMode 도
            응답에 남아 있다. 헤더에 안 그릴 뿐이다.
        */}
        {/* ⋯ — 시안 3.1. 늘 놓을 자리가 없는 것(통계·전체 일정·다시 계산·되돌리기)을 여기 담는다.
            화면에 다 늘어놓으면 정작 하루의 동선이 아래로 밀려 한 칸도 안 보인다.
        */}
        {itinerary ? <Pressable accessibilityRole="button" accessibilityLabel={tx('더 보기', 'More')} accessibilityState={{ expanded: menuOpen }} onPress={() => setMenuOpen((open) => !open)} style={styles.heroBack}><Text variant="title" color={color.brand.navy}>⋯</Text></Pressable> : <View style={styles.heroBackSpacer} />}
      </View>
      <View style={styles.heroTitleRow}>
        <Text variant="display" weight="bold" color={color.text.heading} style={styles.heroTitle}>{tripNameOrDates({ title: itinerary?.title, startDate: itinerary?.days[0]?.date, endDate: itinerary?.days[itinerary.days.length - 1]?.date }, tx, locale)}</Text>
        {/* 「이름 바꾸기」 — 시안 ④. 페이지로 가지 않고 그 자리에서 겹쳐 연다. */}
        {itinerary?.tripId ? (
          <Pressable accessibilityRole="button" onPress={() => setNaming(true)} style={styles.renameButton}>
            <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('이름 바꾸기', 'Rename')}</Text>
          </Pressable>
        ) : null}
      </View>
      {heroSummary ? <Text color={color.text.muted}>{heroSummary}</Text> : null}

      {/* 🔴 「지금」 카드 — 시안 ⑤. 일정표는 「오늘 무엇을 하나」를 말하지만 이 카드는
          「지금 무엇을 하고 있나」를 말한다. 길 안내를 보는 사람이 실제로 읽는 것은 뒤쪽이다.
          오늘 날짜의 일차에서만 그린다 — 지난 날짜나 다음 날짜에 「출발」이 있으면 거짓이다. */}
      {itinerary && dayStops.length && isToday(day?.date, localDateKey(new Date())) ? (
        <View style={styles.nowWrap}>
          <NowCard
            status={progress.status}
            gpsUsable={gpsUsable}
            title={nowTitle}
            detail={nowDetail}
            clock={nowClock}
            driftText={nowDrift}
            progress={nowProgressRatio}
            showManualArrival={needsManualArrival(progress.status, gpsUsable)}
            onStart={() => sendProgress(startRun(progress), () => startProgress(itineraryId, accessToken))}
            onPause={() => sendProgress(pauseRun(progress), () => pauseProgress(itineraryId, accessToken))}
            onArrive={() => currentStopId && sendProgress(
              arriveAt(progress, dayStopIds, currentStopId, new Date().toISOString(), 'manual'),
              () => arriveProgress(itineraryId, currentStopId, 'manual', accessToken))}
            onSkip={() => currentStopId && sendProgress(
              skipStop(progress, dayStopIds, currentStopId, new Date().toISOString()),
              () => skipProgress(itineraryId, currentStopId, accessToken))}
            tx={tx}
          />
          {/* 🔴 기기에만 남는 판에서는 그 사실을 적는다. 안 적으면 사용자는 어디서나
              이어지는 줄 안다. 서버에 남는 판에서는 적을 것이 없으므로 안 그린다. */}
          {deviceOnly ? (
            <Text variant="caption" color={color.text.muted}>
              {tx('진행 상태는 이 기기에만 저장돼요. 다른 기기에서는 아직 안 보여요.',
                'Progress is saved on this device only — it does not show on your other devices yet.')}
            </Text>
          ) : null}
          {progressError ? (
            <Text accessibilityRole="alert" variant="caption" color={color.state.danger}>{progressError}</Text>
          ) : null}
        </View>
      ) : null}
      {/* — 지도로 가는 문. 이 화면에는 지도로 가는 길이 아예 없었다.
          그래서 카카오 지도·경로선·3D 부산·그늘/휠체어 실측이 다 들어 있는 화면에 아무도
          못 들어갔다(주소를 직접 쳐야만 보였다). 동행 초대·날씨는 서버가 여행 번호를
          실어 주는 판에서만 그린다 — 위 tripId 주석 참고.

          🔴 2026-09-21 (S15P21E201-1432) — 「추천 다시 보기」를 여기서 뺐다(시안 1절).
          없앤 것이 아니라 **자리를 옮긴 것도 아니다** — 이 줄에서만 지웠다. 추천 화면으로
          가는 길은 여행 만들기 흐름에 따로 있고, 여기 있던 것은 이미 짜인 일정을 보다가
          「다시 고를까」로 새는 문이었다. 칩이 넷이면 폰에서 줄이 넘어가기도 했다.
      */}
      {itinerary ? <View style={styles.heroActions}>
        <Pressable accessibilityRole="button" onPress={() => router.push({ pathname: '/[id]/map', params: { id } })} style={styles.heroAction}>
          <Text variant="caption" weight="bold" numberOfLines={1} color={color.brand.navy}>{tx('지도 보기', 'View map')}</Text>
        </Pressable>
        {/* 🔴 동행 초대·참여자·준비물 화면은 있었는데 «들어가는 문»이 없었다(2026-09-21 실서버 실기, S15P21E201-1376) —
            (trip)/[id]/share·collaborate·prepare 로 가는 길이 앱 어디에도 없어 주소를 쳐야만 열렸다. 여행 번호는 서버가 준다. */}
        {itinerary.tripId ? <Pressable accessibilityRole="button" onPress={() => router.push(`/${itinerary.tripId}/share`)} style={styles.heroAction}>
          <Text variant="caption" weight="bold" numberOfLines={1} color={color.brand.navy}>{tx('동행 초대', 'Invite')}</Text>
        </Pressable> : null}
        {itinerary.tripId ? <Pressable accessibilityRole="button" onPress={() => router.push(`/${itinerary.tripId}/prepare`)} style={styles.heroAction}>
          <Text variant="caption" weight="bold" numberOfLines={1} color={color.brand.navy}>{tx('날씨', 'Weather')}</Text>
        </Pressable> : null}
      </View> : null}
      {/* 일차 탭은 헤더에 붙어 있다 (시안 2.3 · 3.1) — 탭이 헤더에서 떨어져 있으면
          어느 날을 보고 있는지가 제목과 따로 놀아서, 스크롤을 내리면 둘 다 안 보인다.
          하루짜리 여행에는 고를 것이 없으므로 안 그린다.
      */}
      {itinerary && viewMode === 'day' && itinerary.days.length > 1 ? <View style={styles.heroTabsWrap}>
        <View accessibilityRole="tablist" style={styles.heroTabTrack} onLayout={(event) => setDayTrackWidth(event.nativeEvent.layout.width)}>
          {/* 🔴 고른 칸을 **옮겨 그리지 않고 하나를 미끄러뜨린다.** 칸마다 배경을 켜고 끄면
              날짜가 순간이동해서, 어느 쪽에서 어느 쪽으로 갔는지가 안 남는다.
              폭을 재고 나서야 자리를 알 수 있으므로 onLayout 전에는 안 그린다. */}
          {daySlotWidth > 0 ? <Animated.View style={[styles.heroTabPill, { width: daySlotWidth, transform: [{ translateX: dayPillX }] }]} /> : null}
          {itinerary.days.map((entry, index) => <Pressable key={`hero-${entry.date}-${index}`} testID={`itinerary-day-${index + 1}`} accessibilityRole="tab" accessibilityLabel={tx(`${index + 1}일차`, `Day ${index + 1}`)} accessibilityState={{ selected: selectedDay === index }} onPress={() => selectDay(index)} style={styles.heroTab}>
            <Text variant="caption" weight="bold" numberOfLines={1} color={selectedDay === index ? color.text.onAction : color.text.body}>{tx(`${index + 1}일차`, `Day ${index + 1}`)}</Text>
          </Pressable>)}
        </View>
      </View> : null}
    </View>
    {loading ? <View accessibilityLabel={tx('일정을 불러오고 있어요', 'Loading itinerary')} style={styles.route}>{[0, 1, 2].map((key) => (
      <View key={key} style={styles.stopRow}>
        <View style={styles.rail}><Skeleton width={32} height={32} /></View>
        <View style={styles.stopBody}><Skeleton width="60%" height={16} /><View style={styles.metaRow}><Skeleton width="30%" height={12} /><Skeleton width="30%" height={12} /></View></View>
      </View>
    ))}</View> : null}
    {!loading && result.state !== 'success' ? <View style={styles.stateCard}><Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : tx('일정을 불러오지 못했어요', 'Could not load the itinerary')}</Text><Text color={color.text.body}>{localizeMessage(tx, result.message)}</Text><Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void reload()} /></View> : null}
    {!loading && itinerary ? <>
      {hint && canEdit ? <ItineraryHint onDone={() => setHint(false)} /> : null}
      {menuOpen ? <View style={styles.menuPanel}>
        <View style={styles.stats}>{stats.map((stat) => <View key={stat.key} style={styles.stat}><Text variant="title" weight="bold">{stat.value}</Text><Text variant="caption" color={color.text.muted}>{stat.label}</Text></View>)}</View>
        {rhythm ? <Text variant="caption" color={color.text.body}>{tx(`하루 평균 ${rhythm.averageItemsPerDay}곳`, `${rhythm.averageItemsPerDay} places/day avg.`)}{rhythm.travelShare != null ? txf(tx, ' · 이동 비중 %s%', ' · %s% travel time', Math.round(rhythm.travelShare * 100)) : ''}{rhythm.plannedVsActual != null ? txf(tx, ' · 계획 대비 실제 %s배', ' · %sx planned pace', rhythm.plannedVsActual) : ''}</Text> : null}
        <View style={styles.menuActions}>
          {itinerary?.tripId ? <Pressable accessibilityRole="button" onPress={() => { setMenuOpen(false); router.push(`/${itinerary.tripId}/collaborate`); }} style={styles.recalcButton}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('참여자·역할', 'Participants')}</Text></Pressable> : null}
          <Pressable accessibilityRole="button" onPress={() => { selectView(viewMode === 'all' ? 'day' : 'all'); setMenuOpen(false); }} style={styles.recalcButton}><Text variant="caption" weight="bold" color={color.brand.navy}>{viewMode === 'all' ? tx('날짜별 보기', 'By day') : tx('전체 일정 보기', 'All days')}</Text></Pressable>
          {canReorder && !reorderMode ? <Pressable testID="itinerary-reorder" accessibilityRole="button" accessibilityLabel={tx('일정 순서 변경', 'Reorder itinerary')} accessibilityState={{ disabled: excludingItemId !== null }} disabled={excludingItemId !== null} onPress={() => { startReorder(); setMenuOpen(false); }} style={[styles.recalcButton, excludingItemId !== null && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('순서 변경', 'Reorder')}</Text></Pressable> : null}
          {canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={tx('이 날짜 다시 계산', 'Recalculate this day')} accessibilityState={{ busy: dayActionBusy }} disabled={dayActionBusy || !day?.items.length || excludingItemId !== null} onPress={() => void recalculateDay()} style={[styles.recalcButton, (dayActionBusy || !day?.items.length || excludingItemId !== null) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{dayActionBusy ? tx('계산 중', 'Calculating') : tx('다시 계산', 'Recalculate')}</Text></Pressable> : null}
          {canEdit ? <Pressable accessibilityRole="button" accessibilityLabel={tx('남은 하루 다시 계획', 'Replan the rest of the day')} accessibilityState={{ busy: replanBusy }} disabled={replanBusy || !day?.items.length || excludingItemId !== null} onPress={() => { setReplanConfirming(true); setMenuOpen(false); }} style={[styles.recalcButton, (replanBusy || !day?.items.length || excludingItemId !== null) && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('다시 계획', 'Replan')}</Text></Pressable> : null}
          {canEdit && versions.length > 1 ? <Pressable testID="itinerary-undo" accessibilityRole="button" accessibilityLabel={tx('최근 변경 취소', 'Undo last change')} accessibilityState={{ busy: revertBusy, disabled: revertBusy }} disabled={revertBusy} onPress={() => void revert()} style={[styles.recalcButton, revertBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{revertBusy ? tx('처리 중', 'Processing') : tx('되돌리기', 'Undo')}</Text></Pressable> : null}
        </View>
      </View> : null}
      {!canEdit ? <View style={styles.viewerNotice}><Text variant="caption" weight="bold" color={color.text.muted}>{tx('보기 전용 — 이 일정을 편집할 권한이 없어요.', "View only — you don't have permission to edit this itinerary.")}</Text></View> : null}
      {syncDisconnected ? <View accessibilityRole="alert" style={styles.conflict}><Text variant="body" weight="bold">{tx('같이 보는 사람의 변경을 지금은 못 받아요', 'Changes from your companions aren’t coming through right now')}</Text><Text variant="caption" color={color.text.body}>{tx('네트워크 연결을 확인해 주세요. 보고 있는 화면은 최신이 아닐 수 있어요.', 'Please check your network connection. What you see may not be up to date.')}</Text><Button label={tx('새로고침', 'Refresh')} variant="tertiary" onPress={() => void manualSyncRefresh()} /></View> : null}
      {conflict ? <View accessibilityRole="alert" style={styles.conflict}><Text variant="body" weight="bold">{tx('최신 일정과 충돌했어요', 'Conflicted with the latest itinerary')}</Text><Text variant="caption" color={color.text.body}>{conflict}</Text><Button label={tx('최신 일정 불러오기', 'Load latest itinerary')} variant="tertiary" onPress={() => void reload()} /></View> : null}
      {actionMessage ? <View accessibilityRole="alert" style={styles.actionNotice}><Text variant="caption" color={color.text.body}>{actionMessage}</Text></View> : null}
      {latestWarnings.length ? <View style={styles.warningNotice}>{latestWarnings.map((message, index) => <Text key={index} variant="caption" color={color.text.body}>{message}</Text>)}</View> : null}
      {openingHoursNotice.length ? <View accessibilityRole="alert" style={styles.warningNotice}>{openingHoursNotice.map((message, index) => <Text key={index} variant="caption" color={color.text.body}>{message}</Text>)}</View> : null}
      {dayOutOfRange ? <View style={styles.actionNotice}><Text variant="caption" color={color.text.body}>{tx(`요청한 날짜가 없어서 1일차를 보여드려요. (전체 ${itinerary.days.length}일)`, `That day doesn't exist, so day 1 is shown instead. (${itinerary.days.length} days total)`)}</Text></View> : null}

      {viewMode === 'all' ? (
        <View style={styles.route}>
          {itinerary.days.map((entry, dayIndex) => (
            <View key={`${entry.date}-${dayIndex}`}>
              <Pressable accessibilityRole="button" accessibilityLabel={tx(`${dayIndex + 1}일차만 보기`, `View only day ${dayIndex + 1}`)} onPress={() => { selectView('day'); selectDay(dayIndex); }} style={styles.dayLine}>
                <Text variant="body" weight="bold">{formatDayHeading(entry.date, dayIndex, tx, locale)}</Text>
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
            <Text variant="body" weight="bold">{day ? formatDayHeading(day.date, selectedDay, tx, locale) : tx(`${selectedDay + 1}일차`, `Day ${selectedDay + 1}`)}</Text>
            {dayFacts ? <Text variant="caption" color={color.text.muted}>{dayFacts}</Text> : null}
          </View>
          {pace?.atRiskItemIds.length ? <View accessibilityRole="alert" style={styles.warningNotice}><Text variant="caption" weight="bold" color={color.state.danger}>{txf(tx, '%s곳이 하루를 넘길 위험이 있어요.%s', '%s place(s) risk running past the day.%s', pace.atRiskItemIds.length, paceEstimated ? tx(' (기록이 적어 추정값이에요)', ' (estimated — few records yet)') : '')}</Text></View> : null}
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
            <Pressable testID="itinerary-cancel-order" accessibilityRole="button" accessibilityLabel={tx('순서 변경 취소', 'Cancel reordering')} accessibilityState={{ disabled: reorderBusy }} disabled={reorderBusy} onPress={cancelReorder} style={[styles.recalcButton, reorderBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.brand.navy}>{tx('취소', 'Cancel')}</Text></Pressable>
            <Pressable testID="itinerary-save-order" accessibilityRole="button" accessibilityLabel={tx('순서 저장', 'Save order')} accessibilityState={{ busy: reorderBusy }} disabled={reorderBusy} onPress={() => void saveReorder()} style={[styles.recalcButtonPrimary, reorderBusy && styles.actionDisabled]}><Text variant="caption" weight="bold" color={color.text.onAction}>{reorderBusy ? tx('저장 중', 'Saving') : tx('저장', 'Save')}</Text></Pressable>
          </View> : null}
          {/* 가로 노선도는 넓은 화면에만 — 폰에서는 아래 세로 노선이 같은 일을 더 잘한다.
              순서를 바꾸는 중에는 숨긴다: 아직 저장 안 된 순서를 확정된 동선처럼 그리면
              무엇이 진짜인지 헷갈린다.
          */}
          {!reorderMode && wide ? <RouteStrip items={displayedItems} times={slotTimes} tx={tx} locale={locale} /> : null}
          {displayedItems.length ? <View style={wide ? styles.wideGrid : undefined}>
            <View style={wide ? styles.timelineColumn : undefined}>
              <View style={styles.route}>{displayedItems.map((item, index) => <ImpressionView key={item.id} tracker={impressions} placeId={item.placeId} requestId={item.requestId}><StopRow key={item.id} item={item} index={index} isLast={index === displayedItems.length - 1} displayTime={slotTimes[index] ?? item.startsAt} wide={wide} expanded={expandedItemId === item.id} onToggleExpand={() => setExpandedItemId((current) => current === item.id ? null : item.id)} canEdit={canEdit} lockBusy={busyItemId === item.id} excludeBusy={excludingItemId === item.id} dayBusy={dayActionBusy || excludingItemId !== null} onLock={() => void toggleLock(item)} onExclude={() => setExcludeConfirming(item)} reorderMode={reorderMode} canMoveUp={index > 0 && !item.locked && !displayedItems[index - 1].locked} canMoveDown={index < displayedItems.length - 1 && !item.locked && !displayedItems[index + 1].locked} moveBusy={reorderBusy} onMoveUp={() => moveDraftItem(index, -1)} onMoveDown={() => moveDraftItem(index, 1)} pace={paceByItemId.get(item.id)} estimated={paceEstimated} actualBusy={actualBusyItemId === item.id} onRecordArrival={recordToday && !arrivalOf(item.id) ? () => void recordArrival(item) : undefined} onRecordDeparture={recordToday && arrivalOf(item.id) ? () => void recordDeparture(item) : undefined} accessToken={accessToken} stepState={dayStepStates[index]} /></ImpressionView>)}</View>
            </View>
            {/* 이동 요약 — 시안 p6 의 3칸(장소 · 이동 합계 · 수단). 폰에도 둔다
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
                    ? <Text variant="title" weight="bold">{tx(`${dayTravelMinutes}분`, `${dayTravelMinutes} min`)}</Text>
                    : <Text variant="caption" color={color.text.muted}>{tx('아직 없어요', 'Not yet')}</Text>}
                </View>
                <View style={styles.summaryCell}>
                  <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx('수단', 'Modes')}</Text>
                  <Text variant="caption" color={color.text.muted}>{tx('아직 없어요', 'Not yet')}</Text>
                </View>
              </View>
              {/* 예산 명세 — 시안 7절. 데스크톱은 오른쪽 360 기둥, 폰은 노선 아래. */}
              {budget ? <BudgetCard summary={budget} /> : null}
              <View style={styles.asideCard}>
                <Text variant="caption" weight="bold" color={color.text.eyebrow}>{tx(`${selectedDay + 1}일차 이동 요약`, `Day ${selectedDay + 1} travel summary`)}</Text>
                {dayWalkingMeters > 0 ? <View accessibilityLabel={tx(`정차별 도보 비중`, 'Walking share per stop')} style={styles.shareBar}>
                  {displayedItems.map((item) => item.walkingMeters ? <View key={item.id} style={[styles.shareSlice, { flex: item.walkingMeters }]} /> : null)}
                </View> : null}
                {dayWalking.known > 0
                  ? <Text variant="caption" color={color.text.body}>{txf(tx, '도보 %s', '%s on foot', formatWalk(dayWalkingMeters))}</Text>
                  : <Text variant="caption" color={color.text.muted}>{tx('도보 거리 — 대중교통 구간은 재지 않아요', 'Walking distance — not measured on transit legs')}</Text>}
                {dayTravelMinutes > 0
                  ? <Text variant="caption" color={color.text.body}>{tx(`이동 합계 ${dayTravelMinutes}분`, `${dayTravelMinutes} min travel in total`)}</Text>
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

  {/* 🔴 여행 이름은 **페이지가 아니다**(시안 ④). 이름을 묻자고 화면을 통째로 갈아 끼우면
      방금 만든 일정이 사라지고, 사용자는 「내 일정 어디 갔지」를 먼저 겪는다. 겹쳐 뜨면
      뒤에 일정이 그대로 있어서 무엇에 이름을 붙이는지 보인다. */}
  {naming && itinerary?.tripId ? (
    <TripNameSheet
      tripId={itinerary.tripId}
      currentTitle={humanTripTitle(itinerary.title)}
      dateLabel={heroSummary || null}
      accessToken={accessToken}
      onClose={() => setNaming(false)}
      onSaved={(title) => {
        setNaming(false);
        // 서버를 다시 부르지 않고 이 화면의 제목만 바꾼다. 다시 부르면 제목이 잠깐 옛
        // 이름으로 있다가 바뀌는데, 방금 바꾼 사람에게는 그게 「안 바뀌었다」로 보인다.
        setResult((prev) => (prev.state === 'success' && title
          ? { ...prev, itinerary: { ...prev.itinerary, title } }
          : prev));
      }}
    />
  ) : null}
  {/* 하단 고정 줄 — 시안 3.5.
      시안의 「저장」(초안을 내 여행으로 확정)은 안 만들었다. 부를 API 가 없다
      src/plan/itinerary.ts 에 확정 함수가 없고, 이 화면은 이미 「내 여행」에서 열리는
      저장된 일정이다. 누르면 아무 일도 안 나는 버튼은 없는 버튼보다 나쁘다.
  */}
  {!wide && itinerary && canReorder && !reorderMode ? <View style={[styles.bottomBar, { paddingBottom: bottomBarClearance(insets.bottom) }]}>
    <Button testID="itinerary-reorder-button" label={tx('순서 수정', 'Reorder')} variant="tertiary" onPress={startReorder} />
  </View> : null}
  {/* 🔴 순서 수정 중에는 위 단추 줄이 사라져 스크롤 창이 떠 있는 탭바 밑까지 늘어났다 — 두 번째 장소의 위/아래
      화살표가 탭바에 깔려 못 눌렀다(S15P21E201-1872 실기). 같은 높이를 비워 창이 탭바 위에서 끝나게 한다. */}
  {!wide && itinerary && reorderMode ? <View testID="itinerary-reorder-tabbar-spacer" pointerEvents="none" style={{ height: bottomBarClearance(insets.bottom) }} /> : null}
  <ExcludeConfirmModal
    visible={excludeConfirming !== null}
    placeTitle={excludeConfirming?.title ?? ''}
    busy={excludingItemId !== null}
    onCancel={() => setExcludeConfirming(null)}
    onConfirm={(reason) => {
      const target = excludeConfirming;
      setExcludeConfirming(null);
      if (target) void excludeItem(target, reason);
    }}
  />
  <TabBar active="map" /></View>;
}


const styles = StyleSheet.create({ shell: { flex: 1, backgroundColor: color.canvas },
  // ── 네이비 헤더 (시안 design_handoff_itinerary 2·3절) ──────────────────────
  // Screen 이 좌우 gutter(24)와 위 spacing[6] 을 이미 넣으므로, 그만큼 음수 여백으로
  // 되밀어야 색이 띠처럼 화면 끝까지 간다. 안 그러면 네이비가 카드처럼 떠 보인다.
  // 🔴 2026-09-21 (S15P21E201-1432) — 어두운 남색 띠를 걷어냈다. 이 화면에서 어두운
  //    면은 「지금」 카드 **하나**다. 머리까지 어두우면 그 카드가 안 도드라지고, 「지금
  //    무엇을 하고 있나」가 제목과 같은 무게로 읽힌다.
  //    바탕이 canvas 와 같은 색이라 띠가 안 보이지만, 음수 여백은 그대로 둔다 —
  //    「지금」 카드가 화면 끝까지 닿는 폭을 이 여백이 만든다.
  hero: { backgroundColor: color.canvas, marginHorizontal: -gutter, marginTop: -spacing[6], paddingHorizontal: gutter, paddingTop: spacing[3], gap: spacing[1] },
  heroTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2] },
  heroBack: { width: 44, height: 44, marginLeft: -spacing[3], alignItems: 'center', justifyContent: 'center' },
  heroBackSpacer: { width: 44, height: 44 },
  heroGhost: { minHeight: 36, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: 'rgba(255, 255, 255, 0.4)', alignItems: 'center', justifyContent: 'center' },
  heroTitle: { flex: 1, minWidth: 0, marginTop: spacing[2] },
  heroTitleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  nowWrap: { gap: spacing[2], marginTop: spacing[3] },
  renameButton: { minHeight: 32, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  // 탭은 헤더 바닥에 붙는다 — 위쪽만 둥글고 아래는 각져서 헤더와 한 덩이로 보인다.
  // 지도·추천으로 가는 문. 일차 탭과 같은 반투명 흰색이라 헤더와 한 덩이로
  // 보이고, 탭보다 위에 놓아 「어느 날을 보나」와 「어디로 가나」가 안 섞인다.
  heroActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[3], flexWrap: 'wrap' },
  // 칩 — 밝은 바탕 위라 반투명 흰색이 안 보인다. 흰 알약에 얇은 선으로 세운다.
  heroAction: { minHeight: 44, paddingHorizontal: spacing[3], justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  // 일차 탭 — 세그먼트 안에서 알약이 미끄러진다. 넓은 화면에서는 가운데 560 으로 묶는다.
  heroTabsWrap: { marginTop: spacing[4], alignItems: 'center' },
  heroTabTrack: { flexDirection: 'row', alignSelf: 'stretch', maxWidth: 560, width: '100%', marginHorizontal: 'auto', padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  heroTab: { flex: 1, minHeight: 44, paddingHorizontal: spacing[2], alignItems: 'center', justifyContent: 'center' },
  // 🔴 **이 빨강은 배색 규칙의 예외다. 회색으로 바꾸지 마라.**
  //
  // tokens.ts 맨 위의 규칙 둘과 정면으로 부딪힌다:
  //   1 「동백 채움은 화면당 하나 — 주 버튼」
  //   2 「선택 상태에 빨강을 쓰지 않는다 — 짙은 회색(action.secondary)」
  // 일차 탭은 정확히 **선택 상태**이고, 폰에서는 실제로 빨강이 둘이다 — 이 알약과 하단
  // 탭바 가운데 동그라미. 오늘 날짜 일정을 열면 「지금」 카드의 빨간 ▶ 출발까지 셋이 된다.
  //
  // 🔴 **배색 검사는 이것을 못 잡는다.** 검사기가 스스로 적어 둔 맹점이다 — 파일 단위로
  //    세는데 탭바와 「지금」 카드는 다른 파일이라, 실제 화면에는 여럿인데 통과한다.
  //    즉 「검사가 초록이니 괜찮다」는 여기서 근거가 못 된다.
  //
  // 그런데도 빨강인 이유: **시안이 그렇게 그렸고, 2026-09-21 에 사람이 그대로 가기로
  // 정했다**(S15P21E201-1432). 화면을 직접 보고 내린 결정이다 — 규칙이 맞는 말이라는
  // 것도 확인했지만, 시안이 팀 확정안이고 발표가 가깝다는 쪽을 골랐다.
  // 근거: frontend/docs/design_handoff_itinerary/README-예산실시간재설계.md 1절
  //       (「동백색(#D83A48) 알약이 좌우로 미끄러짐」).
  //
  // 🔴 순서 수정 조각(시안 5절)에서 하단 탭바 가운데가 동백 원이 될 때 **같은 문제가 한 번
  //    더 나온다.** 그때도 혼자 회색으로 바꾸지 말고 사람에게 물어라. 조용히 바꾸면 시안과
  //    어긋나고, 왜 어긋났는지는 아무 데도 안 남는다.
  heroTabPill: { position: 'absolute', top: spacing[1], bottom: spacing[1], left: spacing[1], borderRadius: radius.full, backgroundColor: color.action.primary },
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
  riskChip: { backgroundColor: color.state.dangerBg },
  stopHead: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2] },
  stopPhoto: { width: 56, height: 56, borderRadius: radius.md, backgroundColor: color.surface.soft },
  stopPhotoWide: { width: 72, height: 72 },
  stopPhotoEmpty: { alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.tint },
  stopRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[1] },
  stopRightWide: { alignItems: 'flex-start' },
  stopRightStack: { alignItems: 'flex-end' },
  // 제목 덩이 — 사진과 시간·자물쇠 사이의 남는 자리만 쓴다. 공용 `grow` 의 최소 폭 180 을 쓰면
  // 폰(390)에서 시간·자물쇠가 오른쪽 밖으로 50 쯤 밀려 잘렸다(S15P21E201-1701). 길면 줄을 바꾼다.
  stopTitle: { flex: 1, minWidth: 0, gap: spacing[1] },
  stopDetail: { gap: spacing[2], paddingTop: spacing[2], borderTopWidth: 1, borderTopColor: color.surface.border },

  nodeWide: { width: 40, height: 40 },
  // 날짜 줄 (시안 3.2) — 왼쪽 날짜, 오른쪽 그날 합계
  dayLine: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing[2], flexWrap: 'wrap', marginTop: spacing[4] },
  // ⋯ 패널 — 늘 놓을 자리가 없는 것들
  menuPanel: { gap: spacing[3], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  menuActions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  reorderBar: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  // 하단 고정 줄 — 탭바 위에 형제로 놓는다. absolute 로 띄우면 목록 끝이 그만큼 가린다.
  // 🔴 아래 여백은 여기서 정하지 않는다 — bottomBarClearance 가 탭바와 안전영역을
  //    함께 센다. 8px 만 두었더니 단추가 탭바와 탐색줄 뒤로 완전히 들어갔다(빌드 29).
  bottomBar: { paddingHorizontal: gutter },
  // 정차별 도보 비중 (시안 2.5 · 3.4)
  shareBar: { flexDirection: 'row', gap: 2, height: 6, borderRadius: radius.full, overflow: 'hidden' },
  // 도보 비중 막대 — 누를 것이 아니라 읽을 값이다. 동백은 누를 것에만 쓴다.
  shareSlice: { backgroundColor: color.action.secondary, borderRadius: radius.full },

  titleText: { flexShrink: 1 },
  statusChip: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full },
  statusVerified: { backgroundColor: color.state.successBg },
  statusEstimated: { backgroundColor: color.state.warningBg },
  statusUnknown: { backgroundColor: color.surface.soft },
 canvas: { backgroundColor: color.canvas }, header: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], marginBottom: spacing[6] }, back: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, headerTitle: { flex: 1, gap: spacing[1] }, headerSpacer: { width: 44 }, headerActions: { alignItems: 'flex-end', gap: spacing[2] }, version: { minWidth: 44, minHeight: 32, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, revertButton: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, stateCard: { gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center', borderWidth: 1, borderColor: color.surface.field }, stats: { flexDirection: 'row', gap: spacing[2] }, stat: { flex: 1, minHeight: 88, padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card, justifyContent: 'center', gap: spacing[1] }, viewerNotice: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, conflict: { gap: spacing[2], marginTop: spacing[4], padding: spacing[4], borderRadius: radius.md, backgroundColor: color.state.dangerBg }, actionNotice: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, warningNotice: { gap: spacing[1], marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint }, dayTabs: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[6] }, dayTab: { minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, alignItems: 'center', justifyContent: 'center' }, dayTabActive: { backgroundColor: color.brand.navy, borderColor: color.brand.navy }, allDayHeading: { marginTop: spacing[6], marginBottom: spacing[1] }, sectionHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: spacing[6], flexWrap: 'wrap', gap: spacing[2] }, sectionHeadingRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], flexWrap: 'wrap' }, recalcButton: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, recalcButtonPrimary: { minHeight: 32, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, reorderHint: { marginTop: spacing[3], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft }, moveButtons: { flexDirection: 'row', gap: spacing[2] }, moveButton: { width: 44, height: 44, borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, timeline: { gap: spacing[3], marginTop: spacing[3] },
  // 넓은 화면 2단 — 본문이 남는 폭을 가져가고 요약 칸은 고정이다. 비율로 나누면 넓어질수록
  // 요약 칸까지 늘어나 여백만 커진다(Split.tsx 가 같은 이유로 고정폭을 쓴다).
  wideGrid: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6] },
  timelineColumn: { flex: 1, minWidth: 0 },
  aside: { width: 360 },
  summaryRow: { flexDirection: 'row', gap: spacing[2], marginBottom: spacing[3] },
  summaryCell: { flex: 1, gap: 2, padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  asideCard: { gap: spacing[2], marginTop: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  // ── 예산 명세 카드 (시안 7절) ───────────────────────────────────────────
  budgetCard: { gap: spacing[3], marginTop: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  budgetHead: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3], flexWrap: 'wrap' },
  budgetHeadLeft: { flex: 1, minWidth: 120, gap: spacing[1] },
  budgetHeadRight: { alignItems: 'flex-end', gap: spacing[1] },
  // 총액 대 예산 — 시안의 10px 막대
  budgetTotalTrack: { flexDirection: 'row', height: 10, borderRadius: radius.full, overflow: 'hidden', backgroundColor: color.surface.soft },
  budgetTotalFill: { backgroundColor: color.action.secondary },
  // 🔴 넘쳤을 때의 빨강. state.danger 는 **글자 색**이고 채움으로 쓰면 배색 검사가 막는다
  //    (위험은 채우지 않는다). 이 막대는 글자가 없는 표시라 state.dot 자리다.
  budgetTotalFillOver: { backgroundColor: color.state.dot },
  // 갈래 분해 — 시안의 6px 막대
  budgetSplit: { flexDirection: 'row', gap: 2, height: 6, borderRadius: radius.full, overflow: 'hidden' },
  budgetSliceFood: { backgroundColor: color.action.secondary },
  budgetSliceCafe: { backgroundColor: color.text.muted },
  // 시안의 #A0A0A6 — 막대 전용 토큰(surface.chartMid). 비활성 탭 글자색을 빌려 쓰다가 그 색이 대비 때문에 짙어져 카페 조각과 구분이 안 됐다.
  budgetSliceAdmission: { backgroundColor: color.surface.chartMid },
  budgetSliceTransit: { backgroundColor: color.surface.field },
  budgetRows: { gap: spacing[2] },
  budgetRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  budgetDot: { width: 8, height: 8, borderRadius: radius.full },
  budgetRowLabel: { flex: 1, minWidth: 0 },
  budgetRowValue: { minWidth: 72, textAlign: 'right' },
  budgetDays: { gap: spacing[2] },
  budgetDayRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  budgetDayLabel: { width: 44 },
  budgetDayTrack: { flex: 1, flexDirection: 'row', height: 6, borderRadius: radius.full, overflow: 'hidden', backgroundColor: color.surface.soft },
  budgetDayFill: { backgroundColor: color.action.secondary },
  budgetDayValue: { minWidth: 72, textAlign: 'right' },
  // 노선도 — 정차 노드와 구간. 정차가 많으면 가로로 스크롤한다.
  strip: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2], paddingVertical: spacing[3] },
  stripEntry: { flexDirection: 'row', alignItems: 'center' },
  segment: { alignItems: 'center', gap: spacing[1], paddingHorizontal: spacing[2] },
  segmentLine: { width: 72, height: 3, borderRadius: radius.full, backgroundColor: color.brand.navy },
  stop: { width: 112, alignItems: 'center', gap: spacing[1] },
  stopName: { textAlign: 'center' },
  node: { width: 40, height: 40, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  // 출발점 — 시안의 노드 색은 다녀옴 초록 · 현재 F25454 · 다음 2B2B2E · 이후 흰색 넷뿐이고 빨강은 「현재」 하나다.
  nodeFirst: { backgroundColor: color.action.secondary },
  nodeDone: { backgroundColor: color.state.success },
  nodeCurrent: { backgroundColor: color.state.dot, borderWidth: 4, borderColor: color.surface.tint },
  nodeLater: { backgroundColor: color.surface.card, borderWidth: 2, borderColor: color.surface.field }, itemRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[3] }, time: { width: 48, paddingTop: spacing[4] }, itemCard: { flex: 1, gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card }, itemTitleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2], flexWrap: 'wrap' }, grow: { flex: 1, gap: spacing[1], minWidth: 180 }, itemActions: { flexDirection: 'row', gap: spacing[2] }, lockButton: { minWidth: 64, minHeight: 44, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' }, lockButtonActive: { backgroundColor: color.brand.navy }, lockBadge: { minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, excludeButton: { minWidth: 56, minHeight: 44, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.state.dangerBg, alignItems: 'center', justifyContent: 'center' }, actionDisabled: { opacity: 0.5 }, metaRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing[2] }, empty: { marginTop: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' }, rhythmCard: { gap: spacing[1], marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint }, itemCardAtRisk: { borderWidth: 1, borderColor: color.state.danger }, paceRow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], marginTop: spacing[1] }, actualButtons: { flexDirection: 'row', gap: spacing[2] }, actualButton: { minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, replanActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] }, reviewButton: { alignSelf: 'flex-start', minHeight: 44, paddingHorizontal: spacing[3], marginTop: spacing[1], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' }, reviewedBadge: { alignSelf: 'flex-start', minHeight: 28, paddingHorizontal: spacing[3], marginTop: spacing[1], borderRadius: radius.full, backgroundColor: color.state.successBg, alignItems: 'center', justifyContent: 'center' } });
