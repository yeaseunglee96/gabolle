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
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { deleteTrip, loadTripItineraries, loadTrips, tripDisplayTitle, type TripItineraryRefDto, type TripsLoadResult, type TripSummaryDto } from '@/trip/trips';
import { leaveTrip } from '@/trip/collaboration';
import { TripNameSheet } from '@/trip/TripNameSheet';

/** 보관소에서 이 목록을 찾는 열쇠. 사람이 바뀌면 남의 목록을 보면 안 되므로 사용자 id 를 넣는다. */
const TRIPS_KEY = (userId: string | undefined) => ['trips', userId ?? 'anonymous'] as const;

function dateLabel(trip: TripSummaryDto, tx: (ko: string, en: string) => string) {
  if (!trip.startDate) return tx('날짜 미확인', 'Date unknown');
  return trip.endDate && trip.endDate !== trip.startDate ? `${trip.startDate} – ${trip.endDate}` : trip.startDate;
}

/** 카드 제목 — 사용자가 붙인 이름이 있으면 그것, 없으면 지금까지처럼 날짜 */
function cardTitle(trip: TripSummaryDto, tx: (ko: string, en: string) => string) {
  return tripDisplayTitle(trip, dateLabel(trip, tx));
}

export default function Trips() {
  const router = useRouter();
  const { open } = useLocalSearchParams<{ open?: string }>();
  const { tx } = useI18n();
  const { accessToken, user } = useAuth();
  const queryClient = useQueryClient();
  const [openingTripId, setOpeningTripId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState('');
  const [picker, setPicker] = useState<{ tripId: string; itineraries: TripItineraryRefDto[] } | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<TripSummaryDto | null>(null);
  const [removingTripId, setRemovingTripId] = useState<string | null>(null);
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
    const outcome = await loadTripItineraries(trip.tripId, accessToken);
    setOpeningTripId(null);
    if (outcome.state !== 'success') { setFeedback(outcome.message); return; }
    // : 일정 생성이 실패하면 여행만 남고 일정은 영원히 안 생긴다(재시도 기능은
    // 아직 없다) — "아직 없어요"라고만 하면 곧 생기는 것처럼 들려 계속 눌러보게 만든다.
    // 실제로 할 수 있는 행동(지우고 새로 만들기)을 바로 알려준다.
    if (outcome.itineraries.length === 0) { setFeedback(tx('이 여행은 일정이 만들어지지 않았어요. 아래에서 삭제하고 새로 만들어 주세요.', "This trip's itinerary was never created. Delete it below and start a new one.")); return; }
    if (outcome.itineraries.length === 1) { openItinerary(outcome.itineraries[0].itineraryId); return; }
    // 배열 순서가 계약이 아니라 어느 것이 최신인지 서버가 정해 주지 않는다 — 사용자가 고른다.
    setPicker({ tripId: trip.tripId, itineraries: outcome.itineraries });
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

  return <View style={styles.shell}><Screen scroll wide withTabBar style={styles.canvas}>
    <View style={styles.header}><View style={styles.headerCopy}><Eyebrow>{open === 'prepare' ? tx('날씨·준비물', 'Weather & packing') : tx('여행 목록', 'My trips')}</Eyebrow><Text variant="display" weight="bold" style={styles.title}>{open === 'prepare' ? tx('확인할 여행을 골라주세요', 'Choose a trip to check') : tx('내 여행', 'My trips')}</Text><Text color={color.text.body}>{open === 'prepare' ? tx('여행 카드를 누르면 출발일 예보와 준비물을 보여드려요.', 'Tap a trip to see its departure forecast and packing tips.') : tx('내가 만들었거나 초대받은 여행이에요.', "Trips you've created or been invited to.")}</Text></View><View style={styles.headerActions}><Button label={tx('부슐랭', 'My places')} variant="tertiary" onPress={() => router.push('/collection')} containerStyle={styles.newTrip} /><Button label={tx('새 여행', 'New trip')} variant="outline" onPress={() => router.push('/plan')} containerStyle={styles.newTrip} /></View></View>

    {!accessToken ? <View style={styles.state}><Text weight="bold">{tx('비회원으로 여행 만들기 화면을 둘러볼 수 있어요.', 'You can browse the trip planner as a guest.')}</Text><Text color={color.text.body}>{tx('내 여행을 저장하고 다시 보려면 로그인해 주세요.', 'Sign in to save and revisit your trips.')}</Text><Button label={tx('여행 만들기 둘러보기', 'Browse trip planner')} onPress={() => router.push('/plan')} containerStyle={styles.emptyCta} /><Button label={tx('로그인', 'Sign in')} variant="tertiary" onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/trips' } })} containerStyle={styles.emptyCta} /></View> : null}

    {feedback ? <Pressable accessibilityRole="button" accessibilityLabel={tx('안내 닫기', 'Dismiss notice')} accessibilityLiveRegion="polite" onPress={() => setFeedback('')} style={styles.feedback}><Text variant="caption" weight="bold" color={color.text.onAction}>{feedback}</Text><Text variant="caption" color={color.text.onAction}>{tx('닫기', 'Close')}</Text></Pressable> : null}

    {accessToken && loading ? <View accessibilityLiveRegion="polite" style={styles.state}><ActivityIndicator color={color.action.primary} /><Text weight="bold">{tx('내 여행을 불러오고 있어요', 'Loading your trips')}</Text></View> : null}

    {accessToken && !loading && result.state !== 'success' ? <View style={styles.state}><Text weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : tx('내 여행을 불러오지 못했어요', 'Could not load your trips')}</Text><Text color={color.text.body}>{result.message}</Text><Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void reload()} /></View> : null}

    {accessToken && !loading && result.state === 'success' && trips.length === 0 ? <View style={styles.empty}><View style={styles.emptyMark}><Image source={require('../../assets/icons/home/route.png')} accessibilityLabel={tx('여행 경로', 'Trip route')} style={styles.emptyIcon} /></View><Text variant="title" weight="bold">{tx('아직 만든 여행이 없어요', 'No trips yet')}</Text><Text color={color.text.body} style={styles.center}>{tx('여행을 만들면 이곳에 보여드려요.', "Once you create a trip, it'll show up here.")}</Text><Button label={tx('첫 여행 만들기', 'Create your first trip')} onPress={() => router.push('/plan')} containerStyle={styles.emptyCta} /></View> : null}

    {accessToken && !loading && result.state === 'success' && trips.length > 0 ? <View style={styles.list}>{trips.map((trip) => <Pressable key={trip.tripId} accessibilityRole="button" accessibilityState={{ busy: openingTripId === trip.tripId }} accessibilityLabel={open === 'prepare' ? tx(`${cardTitle(trip, tx)} 날씨와 준비물 보기`, `View weather and packing for ${cardTitle(trip, tx)}`) : tx(`${cardTitle(trip, tx)} 여행 열기`, `Open trip ${cardTitle(trip, tx)}`)} disabled={openingTripId === trip.tripId || removingTripId === trip.tripId} onPress={() => void openTrip(trip)} style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
      <View style={styles.cardTop}><View style={styles.cardCopy}><Text variant="title" weight="bold">{cardTitle(trip, tx)}</Text>{trip.title?.trim() ? <Text variant="caption" color={color.text.muted}>{dateLabel(trip, tx)}</Text> : null}{trip.role !== 'OWNER' ? <Text variant="caption" color={color.text.muted}>{tx('초대받은 여행', 'Invited trip')}</Text> : null}</View>{openingTripId === trip.tripId ? <ActivityIndicator color={color.action.primary} /> : <Text variant="title" color={color.text.muted}>›</Text>}</View>
      <View style={styles.meta}>
        {/* : 서버가 이미 주는 status를 화면이 안 읽어서, 일정 생성이 실패해도
            정상 여행과 카드가 똑같이 보였다 — PLANNING(아직 일정 없음)만 눈에 띄게 표시한다.
        */}
        {trip.status === 'PLANNING' ? <View style={styles.statusPillPending}><Text variant="caption" weight="bold" color={color.state.danger}>{tx('일정 준비 중', 'Itinerary pending')}</Text></View> : null}
        <View style={styles.metaPill}><Text variant="caption" weight="bold">{tx(`${trip.dayCount}일`, `${trip.dayCount} days`)}</Text></View><View style={styles.metaPill}><Text variant="caption" weight="bold">{tx(`${trip.partySize}명`, `${trip.partySize} travelers`)}</Text></View>
      </View>
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
        accessibilityLabel={trip.role === 'OWNER' ? tx('여행 삭제', 'Delete trip') : tx('여행에서 나가기', 'Leave trip')}
        accessibilityState={{ busy: removingTripId === trip.tripId, disabled: removingTripId === trip.tripId }}
        disabled={removingTripId === trip.tripId}
        onPress={(event) => { event.stopPropagation(); setConfirmTarget(trip); }}
        style={({ pressed }) => [styles.removeButton, pressed && styles.removeButtonPressed]}
      >
        <Text variant="caption" weight="bold" color={color.state.danger}>{removingTripId === trip.tripId ? tx('처리 중…', 'Working…') : trip.role === 'OWNER' ? tx('여행 삭제', 'Delete trip') : tx('여행에서 나가기', 'Leave trip')}</Text>
      </Pressable>
      </View>
    </Pressable>)}</View> : null}
  </Screen><TabBar active="map" />

  {naming ? <TripNameSheet
    tripId={naming.tripId}
    currentTitle={naming.title}
    dateLabel={naming.startDate ? dateLabel(naming, tx) : null}
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

  <Modal visible={!!picker} transparent animationType="fade" onRequestClose={() => setPicker(null)}>
    <View style={styles.modalBackdrop}><View accessibilityViewIsModal style={styles.modalCard}>
      <Text variant="title" weight="bold">{tx('열 일정을 골라주세요', 'Choose which itinerary to open')}</Text>
      <Text color={color.text.body}>{tx('이 여행에는 일정이 여러 개 있어요.', 'This trip has more than one itinerary.')}</Text>
      <View style={styles.pickerList}>{picker?.itineraries.map((itinerary) => <Pressable key={itinerary.itineraryId} accessibilityRole="button" onPress={() => { setPicker(null); openItinerary(itinerary.itineraryId); }} style={styles.pickerItem}><Text weight="bold">{tx(`버전 ${itinerary.latestVersion}`, `Version ${itinerary.latestVersion}`)}</Text><Text variant="title" color={color.text.muted}>›</Text></Pressable>)}</View>
      <Button label={tx('취소', 'Cancel')} variant="tertiary" onPress={() => setPicker(null)} />
    </View></View>
  </Modal>

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

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.canvas }, canvas: { backgroundColor: color.canvas },
  header: { marginTop: spacing[6], flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing[3] }, headerCopy: { flex: 1, minWidth: 0 }, title: { marginTop: spacing[1], marginBottom: spacing[2] }, headerActions: { gap: spacing[2] }, newTrip: { width: 96, minHeight: 44, flexShrink: 0 },
  feedback: { minHeight: 48, marginTop: spacing[4], paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.brand.navy, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] }, state: { minHeight: 220, alignItems: 'center', justifyContent: 'center', gap: spacing[3] },
  empty: { minHeight: 320, marginTop: spacing[6], padding: spacing[6], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center', gap: spacing[3] }, emptyMark: { width: 68, height: 68, borderRadius: radius.full, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' }, emptyIcon: { width: 32, height: 32, tintColor: color.text.muted }, center: { maxWidth: 300, textAlign: 'center' }, emptyCta: { minWidth: 180, marginTop: spacing[2] },
  list: { marginTop: spacing[6], gap: spacing[3] }, card: { padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card, gap: spacing[3], shadowColor: color.brand.navy, shadowOpacity: 0.06, shadowRadius: 10, shadowOffset: { width: 0, height: 4 }, elevation: 2 }, cardPressed: { opacity: 0.72, transform: [{ scale: 0.99 }] }, cardTop: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, cardCopy: { flex: 1, gap: spacing[1] }, meta: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, metaPill: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  statusPillPending: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.state.dangerBg, borderWidth: 1, borderColor: color.state.danger },
  cardActions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: spacing[2] },
  removeButton: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[3] }, removeButtonPressed: { opacity: 0.6 },
  modalBackdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  modalCard: { width: '100%', maxWidth: 480, gap: spacing[3], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  pickerList: { gap: spacing[2] }, pickerItem: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.soft },
  confirmActions: { flexDirection: 'row', gap: spacing[2], marginTop: spacing[2] }, confirmButton: { flex: 1 },
});
