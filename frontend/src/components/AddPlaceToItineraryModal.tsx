// 장소를 내 여행 일정에 더한다. 축제 화면이 처음 연다 — 서버 쪽 주석대로
// "축제가 그 날 열리는가" 검사는 그 장소가 실제로 기간이 있는 행사일 때만 걸리고, 보통 장소는
// 그냥 더해진다.
import { useEffect, useRef, useState } from 'react';
import { localDateKey } from '@/plan/tripProgress';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { addItineraryItem, recalculateItineraryDay } from '@/plan/itinerary';
import { effectiveTripStatus } from '@/trip/tripStatus';
import { loadTripItineraries, loadTrips, tripDisplayTitle, type TripItineraryRefDto, type TripSummaryDto } from '@/trip/trips';
import { useAuth } from '@/auth/AuthProvider';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { formatDayHeading } from '@/i18n/datetime';
import { Text } from './Text';
import { txf } from '@/i18n/format';

type Step = 'loadingTrips' | 'pickTrip' | 'loadingItineraries' | 'pickItinerary' | 'pickDay' | 'submitting' | 'done' | 'error';

type AddPlaceToItineraryModalProps = {
  visible: boolean;
  placeId: string;
  onClose: () => void;
};

// 여행 시작일 + n일 — 「9월 19일 (금)」. 시작일을 못 읽으면 지어내지 않고 「n일차」만 적는다.
function dayLabel(startDate: string | null, dayIndex: number, tx: (ko: string, en: string) => string, locale: string) {
  const fallback = tx(`${dayIndex + 1}일차`, `Day ${dayIndex + 1}`);
  if (!startDate) return fallback;
  const date = new Date(`${startDate}T00:00:00`);
  if (Number.isNaN(date.getTime())) return fallback;
  date.setDate(date.getDate() + dayIndex);
  // 날짜 표기는 고른 언어에 맡긴다(9월 20일 (토) · September 20 (Sat) · 9月20日(土)) — S15P21E201-1355 와 같은 방식.
  const heading = formatDayHeading(localDateKey(date), locale) ?? `${date.getMonth() + 1}. ${date.getDate()}.`;
  return txf(tx, '%s일차 · %s', 'Day %s · %s', dayIndex + 1, heading);
}

export function AddPlaceToItineraryModal({ visible, placeId, onClose }: AddPlaceToItineraryModalProps) {
  const { tx, locale } = useI18n();
  const { accessToken } = useAuth();
  const router = useRouter();
  const [step, setStep] = useState<Step>('loadingTrips');
  const [trips, setTrips] = useState<TripSummaryDto[]>([]);
  const [selectedTrip, setSelectedTrip] = useState<TripSummaryDto | null>(null);
  const [itineraries, setItineraries] = useState<TripItineraryRefDto[]>([]);
  const [selectedItinerary, setSelectedItinerary] = useState<TripItineraryRefDto | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  // 닫은 뒤(또는 다시 연 뒤) 도착하는 응답이 그새 초기화된 상태를 덮어쓰지 않도록 막는다
  // 여닫는 동안 요청이 몇 번 겹칠 수 있는데, 그때마다 "지금 이 요청이 아직 유효한가" 를
  // 이 번호 하나로 판단한다.
  const requestIdRef = useRef(0);

  const reset = () => {
    requestIdRef.current += 1;
    setStep('loadingTrips');
    setTrips([]);
    setSelectedTrip(null);
    setItineraries([]);
    setSelectedItinerary(null);
    setErrorMessage('');
  };

  const close = () => { reset(); onClose(); };

  useEffect(() => {
    if (!visible) return;
    let active = true;
    (async () => {
      const result = await loadTrips(accessToken);
      if (!active) return;
      if (result.state !== 'success') { setErrorMessage(result.message); setStep('error'); return; }
      // VIEWER 는 이 여행에 못 더한다 — 일정이 아직 없는(PLANNING) 여행도 못 더한다.
      // 🔴 끝난 여행도 뺀다 — 날짜로 판정한다. 서버 status 는 배치로 늦어 어제 끝난 여행이 READY 로
      //    남고, 그러면 10월 축제가 9월의 지난 날에 더해졌다(S15P21E201-1786). 진행 중 여행은 넣는다.
      const eligible = result.trips.filter((trip) => trip.role !== 'VIEWER' && trip.status !== 'PLANNING' && effectiveTripStatus(trip) !== 'COMPLETED');
      setTrips(eligible);
      setStep('pickTrip');
    })();
    return () => { active = false; };
  }, [visible, accessToken]);

  const pickTrip = async (trip: TripSummaryDto) => {
    setSelectedTrip(trip);
    setStep('loadingItineraries');
    const requestId = requestIdRef.current;
    const result = await loadTripItineraries(trip.tripId, accessToken);
    if (requestId !== requestIdRef.current) return;
    if (result.state !== 'success') { setErrorMessage(result.message); setStep('error'); return; }
    if (result.itineraries.length === 0) {
      setErrorMessage(tx('이 여행에는 일정이 없어요.', 'This trip has no itinerary.'));
      setStep('error');
      return;
    }
    setItineraries(result.itineraries);
    if (result.itineraries.length === 1) { setSelectedItinerary(result.itineraries[0]); setStep('pickDay'); return; }
    setStep('pickItinerary');
  };

  const pickItinerary = (itinerary: TripItineraryRefDto) => { setSelectedItinerary(itinerary); setStep('pickDay'); };

  const pickDay = async (dayIndex: number) => {
    if (!selectedItinerary) return;
    setStep('submitting');
    const requestId = requestIdRef.current;
    const result = await addItineraryItem({ itineraryId: selectedItinerary.itineraryId, placeId, dayIndex, baseVersion: selectedItinerary.latestVersion, accessToken });
    if (requestId !== requestIdRef.current) return;
    if (result.state === 'conflict') {
      // 그 사이 다른 편집이 있었다 — 최신 판 번호로 한 번만 다시 시도한다.
      const retry = await addItineraryItem({ itineraryId: selectedItinerary.itineraryId, placeId, dayIndex, baseVersion: result.latestVersion, accessToken });
      if (requestId !== requestIdRef.current) return;
      if (retry.state !== 'success') { setErrorMessage(retry.state === 'conflict' ? tx('다른 사람이 방금 이 일정을 바꿨어요. 다시 열어서 시도해 주세요.', 'Someone else just changed this itinerary. Please reopen and try again.') : retry.message); setStep('error'); return; }
      void recalculateItineraryDay({ itineraryId: selectedItinerary.itineraryId, baseVersion: retry.itinerary.version, dayIndex, accessToken });
      setStep('done');
      return;
    }
    if (result.state !== 'success') { setErrorMessage(result.message); setStep('error'); return; }
    // 재계산은 최선을 다해 부른다 — 실패해도 더한 장소는 이미 저장돼 있다.
    void recalculateItineraryDay({ itineraryId: selectedItinerary.itineraryId, baseVersion: result.itinerary.version, dayIndex, accessToken });
    setStep('done');
  };

  const viewItinerary = () => {
    if (!selectedItinerary) return;
    close();
    router.push(`/trips/${selectedItinerary.itineraryId}/itinerary`);
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={close}>
      <View style={styles.backdrop}>
        <View accessibilityViewIsModal style={styles.card}>
          <View style={styles.header}>
            <Text variant="title" weight="bold">{tx('내 일정에 추가', 'Add to my itinerary')}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx('닫기', 'Close')} onPress={close} style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}>
              <Text variant="title" weight="bold">✕</Text>
            </Pressable>
          </View>

          {step === 'loadingTrips' || step === 'loadingItineraries' || step === 'submitting' ? (
            <View style={styles.centerState}><ActivityIndicator color={color.action.primary} /></View>
          ) : null}

          {step === 'pickTrip' && trips.length === 0 ? (
            <View style={styles.centerState}>
              <Text color={color.text.body}>{tx('아직 일정이 만들어진 여행이 없어요. 먼저 여행을 만들어 주세요.', "You don't have a trip with an itinerary yet. Plan one first.")}</Text>
              <Pressable accessibilityRole="button" onPress={() => { close(); router.push('/plan'); }} style={styles.submitButton}>
                <Text variant="body" weight="bold" color={color.text.onAction}>{tx('여행 계획 시작하기', 'Start planning')}</Text>
              </Pressable>
            </View>
          ) : null}

          {step === 'pickTrip' && trips.length > 0 ? (
            <ScrollView style={styles.list}>
              <Text variant="caption" color={color.text.body}>{tx('어느 여행에 더할까요?', 'Which trip should this go to?')}</Text>
              {trips.map((trip) => (
                <Pressable key={trip.tripId} accessibilityRole="button" onPress={() => void pickTrip(trip)} style={styles.optionRow}>
                  <Text variant="body" weight="bold">{tripDisplayTitle(trip, `${trip.startDate ?? ''} · ${tx(`${trip.dayCount}일`, `${trip.dayCount} days`)}`)}</Text>
                </Pressable>
              ))}
            </ScrollView>
          ) : null}

          {step === 'pickItinerary' ? (
            <ScrollView style={styles.list}>
              <Text variant="caption" color={color.text.body}>{tx('이 여행에는 일정이 여러 개 있어요.', 'This trip has more than one itinerary.')}</Text>
              {itineraries.map((itinerary, index) => (
                <Pressable key={itinerary.itineraryId} accessibilityRole="button" onPress={() => pickItinerary(itinerary)} style={styles.optionRow}>
                  <Text variant="body" weight="bold">{tx(`일정 ${index + 1}`, `Itinerary ${index + 1}`)}</Text>
                </Pressable>
              ))}
            </ScrollView>
          ) : null}

          {step === 'pickDay' && selectedTrip ? (
            <ScrollView style={styles.list}>
              <Text variant="caption" color={color.text.body}>{tx('어느 날에 넣을까요?', 'Which day should this go on?')}</Text>
              {Array.from({ length: selectedTrip.dayCount }, (_, index) => index).map((dayIndex) => (
                <Pressable key={dayIndex} accessibilityRole="button" onPress={() => void pickDay(dayIndex)} style={styles.optionRow}>
                  <Text variant="body" weight="bold">{dayLabel(selectedTrip.startDate, dayIndex, tx, locale)}</Text>
                </Pressable>
              ))}
            </ScrollView>
          ) : null}

          {step === 'done' ? (
            <View style={styles.centerState}>
              <Text variant="body" weight="bold" color={color.state.success}>{tx('일정에 추가했어요.', 'Added to your itinerary.')}</Text>
              <Text variant="caption" color={color.text.body}>{tx('이동 시간과 순서는 일정 화면에서 다시 계산돼요.', 'Travel times and order are recalculated on the itinerary screen.')}</Text>
              <Pressable accessibilityRole="button" onPress={viewItinerary} style={styles.submitButton}>
                <Text variant="body" weight="bold" color={color.text.onAction}>{tx('일정 보기', 'View itinerary')}</Text>
              </Pressable>
            </View>
          ) : null}

          {step === 'error' ? (
            <View style={styles.centerState}>
              <Text accessibilityRole="alert" color={color.state.danger}>{errorMessage}</Text>
              <Pressable accessibilityRole="button" onPress={close} style={styles.submitButton}>
                <Text variant="body" weight="bold" color={color.text.onAction}>{tx('닫기', 'Close')}</Text>
              </Pressable>
            </View>
          ) : null}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(25,25,25,0.62)' },
  card: { width: '100%', maxWidth: 420, maxHeight: '80%', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  closeButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.action.tertiary },
  pressed: { opacity: 0.72 },
  centerState: { gap: spacing[3], alignItems: 'center', paddingVertical: spacing[4] },
  list: { gap: spacing[2] },
  optionRow: { minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[4], marginTop: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  submitButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[6], borderRadius: radius.full, backgroundColor: color.action.primary },
});
