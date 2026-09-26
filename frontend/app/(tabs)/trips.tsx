import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, Image, Modal, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Eyebrow } from '@/components/Eyebrow';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { effectiveTripStatus, tripStatusLabel, tripTimingLabel } from '@/trip/tripStatus';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import { deleteTrip, loadTrips, resolveTripItinerary, type TripsLoadResult, type TripSummaryDto } from '@/trip/trips';
import { humanTripTitle, tripDatesLabel, tripNameOrDates } from '@/trip/tripNaming';
import { leaveTrip } from '@/trip/collaboration';
import { TripNameSheet } from '@/trip/TripNameSheet';
import { enCount, enPlural, txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';

/** 보관소에서 이 목록을 찾는 열쇠. 사람이 바뀌면 남의 목록을 보면 안 되므로 사용자 id 를 넣는다. */
const TRIPS_KEY = (userId: string | undefined) => ['trips', userId ?? 'anonymous'] as const;

/** 「9월 26일 (토) – 9월 28일 (월)」 — 고른 언어의 표기로. 날짜 원문(2026-09-26)은 사람이 읽는 말이 아니다. */
function dateLabel(trip: TripSummaryDto, tx: (ko: string, en: string) => string, locale: string) {
  return tripDatesLabel(trip.startDate, trip.endDate, locale) ?? tx('날짜 미확인', 'Date unknown');
}

/** 카드 제목 — 사용자가 붙인 이름이 있으면 그것, 없으면 지금까지처럼 날짜 */
function cardTitle(trip: TripSummaryDto, tx: (ko: string, en: string) => string, locale: string) {
  return tripNameOrDates(trip, tx, locale);
}

export default function Trips() {
  const router = useRouter();
  const { open } = useLocalSearchParams<{ open?: string }>();
  const { tx, locale } = useI18n();
  const { accessToken, user } = useAuth();
  // 넓은 화면은 최대 1200 폭의 3열 카드 격자다(시안 docs/design_handoff_my_trips, S15P21E201-1587).
  // 전에는 카드 한 장이 1440 폭 전체로 늘어진 한 줄 목록이었다.
  const { desktop } = useLayout();
  const queryClient = useQueryClient();
  const [openingTripId, setOpeningTripId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState('');
  const [confirmTarget, setConfirmTarget] = useState<TripSummaryDto | null>(null);
  const [removingTripId, setRemovingTripId] = useState<string | null>(null);
  // 🔴 「여행 삭제」는 ⋯ 안에 있다 — 카드마다 붉은 글자로 서 있으면 실수로 누르기 쉬운 자리다(2026-09-21, S15P21E201-1393).
  const [menuTripId, setMenuTripId] = useState<string | null>(null);
  // 이름을 바꾸거나 붙이려고 연 여행. null 이면 안 열려 있다.
  const [naming, setNaming] = useState<TripSummaryDto | null>(null);

  // 화면 밖 보관소에서 읽는다. 탭을 오가며 이 화면이 사라졌다
  // 다시 만들어져도, 보관소는 그대로라 서버를 다시 안 부른다. 낡았을 때만(기본 30초)
  // 조용히 다시 불러오면서 이전 값을 계속 보여준다.
  const tripsQuery = useQuery({
    queryKey: TRIPS_KEY(user?.userId),
    queryFn: () => loadTrips(accessToken as string),
    enabled: Boolean(accessToken),
  });
  const result: TripsLoadResult = tripsQuery.data ?? { state: 'success', trips: [] };
  const loading = tripsQuery.isPending;
  const reload = tripsQuery.refetch;

  const openItinerary = (itineraryId: string) => router.push(`/trips/${itineraryId}/itinerary`);

  const openTrip = async (trip: TripSummaryDto) => {
    if (openingTripId) return;
    if (open === 'prepare') {
      router.push(`/${trip.tripId}/prepare`);
      return;
    }
    setFeedback('');
    setOpeningTripId(trip.tripId);
    // 🔴 일정이 여럿이어도 묻지 않는다 — 확정한 일정을 바로 연다(S15P21E201-1605). 규칙은 resolveTripItinerary 가 갖는다.
    const outcome = await resolveTripItinerary(trip, accessToken);
    setOpeningTripId(null);
    // : 일정 생성이 실패하면 여행만 남고 일정은 영원히 안 생긴다(재시도 기능은
    // 아직 없다) — "아직 없어요"라고만 하면 곧 생기는 것처럼 들려 계속 눌러보게 만든다.
    // 실제로 할 수 있는 행동(지우고 새로 만들기)을 바로 알려준다.
    if (outcome.state === 'none') { setFeedback(tx('이 여행은 일정이 만들어지지 않았어요. 아래에서 삭제하고 새로 만들어 주세요.', "This trip's itinerary was never created. Delete it below and start a new one.")); return; }
    if (outcome.state !== 'open') { setFeedback(outcome.message); return; }
    openItinerary(outcome.itineraryId);
  };

  const confirmRemove = async () => {
    if (!confirmTarget || !accessToken) return;
    const trip = confirmTarget;
    setConfirmTarget(null);
    setRemovingTripId(trip.tripId);
    const outcome = trip.role === 'OWNER'
      ? await deleteTrip(trip.tripId, accessToken)
      : user?.userId
        ? await leaveTrip(trip.tripId, user.userId, accessToken).then(() => ({ state: 'success' as const })).catch((error) => ({ state: 'error' as const, message: error instanceof Error ? error.message : tx('여행에서 나가지 못했어요.', "Couldn't leave the trip.") }))
        : { state: 'error' as const, message: tx('로그인이 필요해요.', 'Please sign in.') };
    setRemovingTripId(null);
    if (outcome.state === 'success') {
      // 지운 여행을 보관소에서도 바로 뺀다 — 서버에 다시 묻지 않고 화면이 즉시 맞는다.
      queryClient.setQueryData<TripsLoadResult>(TRIPS_KEY(user?.userId), (current) =>
        current && current.state === 'success'
          ? { state: 'success', trips: current.trips.filter((item) => item.tripId !== trip.tripId) }
          : current);
      setFeedback(trip.role === 'OWNER' ? tx('여행을 삭제했어요.', 'Trip deleted.') : tx('여행에서 나갔어요.', 'You left the trip.'));
    } else {
      setFeedback('message' in outcome ? outcome.message : tx('처리하지 못했어요.', 'Could not process the request.'));
    }
  };

  const trips = result.state === 'success' ? result.trips : [];

  return <View style={styles.shell}><Screen scroll wide withTabBar style={[styles.canvas, desktop && styles.canvasDesktop]}>
    {/* 🔴 폰은 헤더 위 여백을 따로 안 준다 — Screen 이 이미 24 를 주고, 헤더의 24 가 겹쳐 48 이 비어 있었다(시안 변경 3).
        헤더의 행동은 「새 여행」 하나다(시안 변경 2). */}
    <View style={[styles.header, desktop && styles.headerDesktop]}><View style={styles.headerCopy}><Eyebrow>{open === 'prepare' ? tx('날씨·준비물', 'Weather & packing') : tx('여행 목록', 'My trips')}</Eyebrow><Text variant="display" weight="bold" style={styles.title}>{open === 'prepare' ? tx('확인할 여행을 골라주세요', 'Choose a trip to check') : tx('내 여행', 'My trips')}</Text><Text color={color.text.body}>{open === 'prepare' ? tx('여행 카드를 누르면 출발일 예보와 준비물을 보여드려요.', 'Tap a trip to see its departure forecast and packing tips.') : tx('내가 만들었거나 초대받은 여행이에요.', "Trips you've created or been invited to.")}</Text></View><View style={styles.headerActions}><Button label={tx('새 여행', 'New trip')} variant="outline" onPress={() => router.push('/plan')} containerStyle={styles.newTrip} /></View></View>

    {!accessToken ? <View style={styles.state}><Text weight="bold">{tx('비회원으로 여행 만들기 화면을 둘러볼 수 있어요.', 'You can browse the trip planner as a guest.')}</Text><Text color={color.text.body}>{tx('내 여행을 저장하고 다시 보려면 로그인해 주세요.', 'Sign in to save and revisit your trips.')}</Text><Button label={tx('여행 만들기 둘러보기', 'Browse trip planner')} onPress={() => router.push('/plan')} containerStyle={styles.emptyCta} /><Button label={tx('로그인', 'Sign in')} variant="tertiary" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/trips' } })} containerStyle={styles.emptyCta} /></View> : null}

    {feedback ? <Pressable accessibilityRole="button" accessibilityLabel={tx('안내 닫기', 'Dismiss notice')} accessibilityLiveRegion="polite" onPress={() => setFeedback('')} style={styles.feedback}><Text variant="caption" weight="bold" color={color.text.onAction}>{feedback}</Text><Text variant="caption" color={color.text.onAction}>{tx('닫기', 'Close')}</Text></Pressable> : null}

    {accessToken && loading ? <View accessibilityLiveRegion="polite" style={styles.state}><ActivityIndicator color={color.action.primary} /><Text weight="bold">{tx('내 여행을 불러오고 있어요', 'Loading your trips')}</Text></View> : null}

    {accessToken && !loading && result.state !== 'success' ? <View style={styles.state}><GabolleMascot state="sad" style={styles.sadMascot} /><Text weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : tx('내 여행을 불러오지 못했어요', 'Could not load your trips')}</Text><Text color={color.text.body}>{localizeMessage(tx, result.message)}</Text><Button compact label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void reload()} /></View> : null}

    {accessToken && !loading && result.state === 'success' && trips.length === 0 ? <View style={styles.empty}><GabolleMascot state="open" style={styles.emptyMascot} /><Text variant="title" weight="bold">{tx('아직 만든 여행이 없어요', 'No trips yet')}</Text><Text color={color.text.body} style={styles.center}>{tx('여행을 만들면 이곳에 보여드려요.', "Once you create a trip, it'll show up here.")}</Text><Button label={tx('첫 여행 만들기', 'Create your first trip')} onPress={() => router.push('/plan')} containerStyle={styles.emptyCta} /></View> : null}

    {accessToken && !loading && result.state === 'success' && trips.length > 0 ? <View style={[styles.list, desktop && styles.grid]}>{trips.map((trip) => <View key={trip.tripId} style={desktop ? styles.gridSlot : undefined}><View style={[styles.card, desktop && styles.cardInGrid]}><Pressable accessibilityRole="button" accessibilityState={{ busy: openingTripId === trip.tripId }} accessibilityLabel={open === 'prepare' ? txf(tx, '%s 날씨와 준비물 보기', 'View weather and packing for %s', cardTitle(trip, tx, locale)) : txf(tx, '%s 여행 열기', 'Open trip %s', cardTitle(trip, tx, locale))} disabled={openingTripId === trip.tripId || removingTripId === trip.tripId} onPress={() => void openTrip(trip)} style={({ pressed }) => [styles.cardBody, pressed && styles.cardPressed]}>
      <TripCover uri={trip.coverImageUrl} />
      <View style={styles.cardTop}><View style={styles.cardCopy}><Text variant="title" weight="bold">{cardTitle(trip, tx, locale)}</Text>{humanTripTitle(trip.title) ? <Text variant="caption" color={color.text.muted}>{dateLabel(trip, tx, locale)}</Text> : null}{trip.role !== 'OWNER' ? <Text variant="caption" color={color.text.muted}>{tx('초대받은 여행', 'Invited trip')}</Text> : null}</View>{openingTripId === trip.tripId ? <ActivityIndicator color={color.action.primary} /> : <Text variant="title" color={color.text.muted}>›</Text>}</View>
      <View style={styles.meta}>
        {/* : 서버가 이미 주는 status를 화면이 안 읽어서, 일정 생성이 실패해도
            정상 여행과 카드가 똑같이 보였다 — PLANNING(아직 일정 없음)만 눈에 띄게 표시한다.
        */}
        {/* 상태 배지 — 계획 중은 붉은 선(사용자가 손볼 것), 진행 중은 초록 점, 나머지는 회색. 언제인지(「3일 뒤 출발」)도 한 칸. */}
        {/* 배지 글자는 한 줄 — 격자로 좁아진 카드에서 「일정 준비 중」이 두 줄로 깨졌다(시안 변경 1). */}
        {trip.status === 'PLANNING' ? <View style={styles.statusPillPending}><Text variant="caption" weight="bold" color={color.state.danger} numberOfLines={1}>{tx('일정 준비 중', 'Itinerary pending')}</Text></View>
          : <View style={[styles.metaPill, effectiveTripStatus(trip) === 'IN_PROGRESS' && styles.statusPillLive]}>{effectiveTripStatus(trip) === 'IN_PROGRESS' ? <View style={styles.liveDot} /> : null}<Text variant="caption" weight="bold" color={effectiveTripStatus(trip) === 'IN_PROGRESS' ? color.state.success : color.text.heading} numberOfLines={1}>{tripStatusLabel(effectiveTripStatus(trip), tx)}</Text></View>}
        {tripTimingLabel(trip, tx) ? <Text variant="caption" color={color.text.muted}>{tripTimingLabel(trip, tx)}</Text> : null}
        <View style={styles.metaPill}><Text variant="caption" weight="bold" numberOfLines={1}>{tx(`${trip.dayCount}일`, enCount(trip.dayCount, 'day', 'days'))}</Text></View><View style={styles.metaPill}><Text variant="caption" weight="bold" numberOfLines={1}>{txf(tx, '%s명', `%s ${enPlural(trip.partySize, 'traveler', 'travelers')}`, trip.partySize)}</Text></View>
      </View>
      </Pressable>
      {/* 🔴 행동 단추는 카드 Pressable 의 «형제»다. 안에 넣으면 웹에서 <button> 속 <button> 이 되어(RN-web 은 button 역할을 진짜 button 으로 그린다) 안쪽 단추가 안 눌리거나 둘 다 눌린다. */}
      <View style={styles.cardActions}>
      {/* VIEWER 만 이름을 못 바꾼다. 서버가 그렇게 정했다(TripTitleService) — OWNER 뿐
          아니라 EDITOR 도 바꿀 수 있다. 여기서 더 좁히면 있는 권한을 화면이 숨기게 된다.
      */}
      {trip.role !== 'VIEWER' ? <Pressable
        accessibilityRole="button"
        accessibilityLabel={trip.title?.trim() ? tx('여행 이름 바꾸기', 'Rename trip') : tx('여행 이름 붙이기', 'Name trip')}
        onPress={(event) => { event.stopPropagation(); setNaming(trip); }}
        style={({ pressed }) => [styles.removeButton, pressed && styles.removeButtonPressed]}
      >
        <Text variant="caption" weight="bold" color={color.brand.navy} numberOfLines={1}>{trip.title?.trim() ? tx('이름 바꾸기', 'Rename') : tx('이름 붙이기', 'Name it')}</Text>
      </Pressable> : <View />}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={tx('더 보기', 'More')}
        accessibilityState={{ expanded: menuTripId === trip.tripId }}
        onPress={(event) => { event.stopPropagation(); setMenuTripId((open) => (open === trip.tripId ? null : trip.tripId)); }}
        style={({ pressed }) => [styles.moreButton, pressed && styles.removeButtonPressed]}
      >
        <Text variant="title" color={color.text.muted}>⋯</Text>
      </Pressable>
      </View>
      {menuTripId === trip.tripId ? (
        <View style={styles.cardMenu}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={trip.role === 'OWNER' ? tx('여행 삭제', 'Delete trip') : tx('여행에서 나가기', 'Leave trip')}
            accessibilityState={{ busy: removingTripId === trip.tripId, disabled: removingTripId === trip.tripId }}
            disabled={removingTripId === trip.tripId}
            onPress={(event) => { event.stopPropagation(); setMenuTripId(null); setConfirmTarget(trip); }}
            style={({ pressed }) => [styles.cardMenuItem, pressed && styles.removeButtonPressed]}
          >
            <Text variant="caption" weight="bold" color={color.state.danger}>{removingTripId === trip.tripId ? tx('처리 중…', 'Working…') : trip.role === 'OWNER' ? tx('여행 삭제', 'Delete trip') : tx('여행에서 나가기', 'Leave trip')}</Text>
          </Pressable>
        </View>
      ) : null}
    </View></View>)}</View> : null}
  </Screen><TabBar active="map" />

  {naming ? <TripNameSheet
    tripId={naming.tripId}
    currentTitle={naming.title}
    dateLabel={naming.startDate ? dateLabel(naming, tx, locale) : null}
    accessToken={accessToken}
    onClose={() => setNaming(null)}
    onSaved={(title) => {
      const tripId = naming.tripId;
      setNaming(null);
      // 서버를 다시 부르지 않고 보관소의 그 한 줄만 바꾼다. 다시 부르면 카드가 잠깐
      // 옛 이름으로 있다가 바뀌는데, 방금 바꾼 사람에게는 그게 "안 바뀌었다" 로 보인다.
      queryClient.setQueryData<TripsLoadResult>(TRIPS_KEY(user?.userId), (current) =>
        current && current.state === 'success'
          ? { state: 'success', trips: current.trips.map((item) => item.tripId === tripId ? { ...item, title } : item) }
          : current);
      setFeedback(title ? tx('이름을 저장했어요.', 'Name saved.') : tx('이름을 지웠어요. 카드에 날짜가 보여요.', 'Name cleared — the card shows the dates.'));
    }}
  /> : null}

  <Modal visible={!!confirmTarget} transparent animationType="fade" onRequestClose={() => setConfirmTarget(null)}>
    <View style={styles.modalBackdrop}><View accessibilityViewIsModal style={styles.modalCard}>
      <Text variant="title" weight="bold">{confirmTarget?.role === 'OWNER' ? tx('이 여행을 삭제할까요?', 'Delete this trip?') : tx('이 여행에서 나갈까요?', 'Leave this trip?')}</Text>
      <Text color={color.text.body}>{confirmTarget?.role === 'OWNER' ? tx('일정·기록·초대 링크가 모두 사라지고 되돌릴 수 없어요.', "The itinerary, records, and invite links will all be gone — this can't be undone.") : tx('이 여행 목록에서 빠지고, 다시 초대받아야 볼 수 있어요.', "You'll be removed from this trip and need a new invite to see it again.")}</Text>
      <View style={styles.confirmActions}>
        <Button label={tx('취소', 'Cancel')} variant="tertiary" onPress={() => setConfirmTarget(null)} containerStyle={styles.confirmButton} />
        <Button label={confirmTarget?.role === 'OWNER' ? tx('삭제', 'Delete') : tx('나가기', 'Leave')} onPress={() => void confirmRemove()} variant="danger" containerStyle={[styles.confirmButton]} />
      </View>
    </View></View>
  </Modal>
  </View>;
}

/**
 * 여행 카드 커버 — 폰은 없으면 자리를 만들지 않는다. 빈 회색 판은 「못 불러왔다」로 읽힌다.
 *
 * 🔴 넓은 화면 3열 격자에서만 사진이 없어도 연한 빈 판(높이 160)을 둔다 — 사용자 결정(2026-09-24, S15P21E201-1587).
 *    안 두면 같은 줄 카드끼리 제목 높이가 어긋난다. 폰은 한 줄에 한 장이라 맞출 옆 카드가 없다.
 *
 * 🔴 사진 주소는 목록 응답이 그대로 준다(trip.coverImageUrl). 여기서 따로 부르지 않는다 —
 * 전에는 카드마다 일정·장소를 세 번씩 불러서, 「내 여행」을 열 때 1초에 51건이 나가고 서버 앞단이
 * 그중 59건을 503 으로 거절했다. 거절된 카드는 앱을 끌 때까지 사진이 없었다(S15P21E201-1435).
 */
function TripCover({ uri }: { uri: string | null }) {
  const { desktop } = useLayout();
  if (!uri) return desktop ? <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.cover, styles.coverDesktop, styles.coverBlank]} /> : null;
  return <Image source={{ uri }} resizeMode="cover" accessibilityLabel="" style={[styles.cover, desktop && styles.coverDesktop]} />;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.canvas }, canvas: { backgroundColor: color.canvas },
  // 넓은 화면은 1200 까지 — Screen 의 넓은 판(1440) 위에 덮는다. 가운데 정렬은 Screen 이 이미 한다.
  canvasDesktop: { maxWidth: 1200 },
  header: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing[3] },
  // 넓은 화면은 윗줄 메뉴 아래라 24 를 그대로 두고, 「새 여행」을 제목 덩어리 바닥에 맞춘다(시안).
  headerDesktop: { marginTop: spacing[6], alignItems: 'flex-end' },
  headerCopy: { flex: 1, minWidth: 0 }, title: { marginTop: spacing[1], marginBottom: spacing[2] }, headerActions: { gap: spacing[2] }, newTrip: { width: 96, minHeight: 48, flexShrink: 0 },
  feedback: { minHeight: 48, marginTop: spacing[4], paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.brand.navy, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] }, state: { minHeight: 220, alignItems: 'center', justifyContent: 'center', gap: spacing[3] }, sadMascot: { width: 96, height: 96 }, emptyMascot: { width: 110, height: 110 },
  empty: { minHeight: 320, marginTop: spacing[6], padding: spacing[6], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center', gap: spacing[3] }, emptyMark: { width: 68, height: 68, borderRadius: radius.full, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' }, emptyIcon: { width: 32, height: 32, tintColor: color.text.muted }, center: { maxWidth: 300, textAlign: 'center' }, emptyCta: { minWidth: 180, marginTop: spacing[2] },
  cover: { width: '100%', height: 132, borderRadius: radius.md, backgroundColor: color.surface.soft },
  coverDesktop: { height: 160 }, coverBlank: { backgroundColor: color.surface.tint },
  list: { marginTop: spacing[6], gap: spacing[3] },
  // 3열 격자 — 칸 사이 16 은 칸마다 사방 8 로 내고, 바깥 8 은 음수 여백으로 거둔다(퍼센트 폭에서 gap 을 빼는 계산이 RN 에 없다).
  // 한 줄의 칸은 줄 높이만큼 늘어나고(stretch), 카드가 칸을 채워 같은 줄 카드 높이가 같다.
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 0, marginTop: spacing[6] - spacing[2], marginHorizontal: -spacing[2], marginBottom: -spacing[2] },
  gridSlot: { width: '33.3333%', padding: spacing[2] },
  // 행동 줄(이름 바꾸기 / ⋯)이 늘 카드 바닥에 붙는다.
  cardInGrid: { flex: 1, justifyContent: 'space-between' }, cardBody: { gap: spacing[3] }, card: { padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card, gap: spacing[3], shadowColor: color.brand.navy, shadowOpacity: 0.06, shadowRadius: 10, shadowOffset: { width: 0, height: 4 }, elevation: 2 }, cardPressed: { opacity: 0.72, transform: [{ scale: 0.99 }] }, cardTop: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, cardCopy: { flex: 1, gap: spacing[1] }, meta: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing[2] }, metaPill: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  statusPillLive: { flexDirection: 'row', alignItems: 'center', gap: spacing[1] }, liveDot: { width: 6, height: 6, borderRadius: radius.full, backgroundColor: color.state.success },
  statusPillPending: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.state.dangerBg, borderWidth: 1, borderColor: color.state.danger },
  cardActions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: spacing[2] },
  removeButton: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[3] }, moreButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full }, cardMenu: { alignSelf: 'flex-end', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card }, cardMenuItem: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4] }, removeButtonPressed: { opacity: 0.6 },
  modalBackdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  modalCard: { width: '100%', maxWidth: 480, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  confirmActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] }, confirmButton: { flex: 1 },
});
