// 장소를 내 여행 일정에 더한다 (S15P21E201-467). 축제 화면이 처음 연다 — 서버 쪽 주석대로
// "축제가 그 날 열리는가" 검사는 그 장소가 실제로 기간이 있는 행사일 때만 걸리고, 보통 장소는
// 그냥 더해진다.
//
// 🔴 더하기 자체는 시각을 안 채운다(addItineraryItem 주석). 그래서 성공하면 이어서
// recalculateItineraryDay 를 부른다 — 실패해도 더한 장소는 남고 시각만 비어 있을 뿐이라
// 그 실패로 전체를 실패 취급하지 않는다.
import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { addItineraryItem, recalculateItineraryDay } from '@/plan/itinerary';
import { loadTripItineraries, loadTrips, tripDisplayTitle, type TripItineraryRefDto, type TripSummaryDto } from '@/trip/trips';
import { useAuth } from '@/auth/AuthProvider';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { Text } from './Text';

type Step = 'loadingTrips' | 'pickTrip' | 'loadingItineraries' | 'pickItinerary' | 'pickDay' | 'submitting' | 'done' | 'error';

type AddPlaceToItineraryModalProps = {
  visible: boolean;
  placeId: string;
  onClose: () => void;
};

// 여행 시작일 + n일 — 「9월 19일 (금)」. 시작일을 못 읽으면 지어내지 않고 「n일차」만 적는다.
function dayLabel(startDate: string | null, dayIndex: number, tx: (ko: string, en: string) => string) {
  const fallback = tx(`${dayIndex + 1}일차`, `Day ${dayIndex + 1}`);
  if (!startDate) return fallback;
  const date = new Date(`${startDate}T00:00:00`);
  if (Number.isNaN(date.getTime())) return fallback;
  date.setDate(date.getDate() + dayIndex);
  const weekday = ['일', '월', '화', '수', '목', '금', '토'][date.getDay()];
  return tx(`${dayIndex + 1}일차 · ${date.getMonth() + 1}월 ${date.getDate()}일 (${weekday})`, `Day ${dayIndex + 1} · ${date.toLocaleDateString('en-US', { month: 'long', day: 'numeric', weekday: 'short' })}`);
}

export function AddPlaceToItineraryModal({ visible, placeId, onClose }: AddPlaceToItineraryModalProps) {
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const router = useRouter();
  const [step, setStep] = useState<Step>('loadingTrips');
  const [trips, setTrips] = useState<TripSummaryDto[]>([]);
  const [selectedTrip, setSelectedTrip] = useState<TripSummaryDto | null>(null);
  const [itineraries, setItineraries] = useState<TripItineraryRefDto[]>([]);
  const [selectedItinerary, setSelectedItinerary] = useState<TripItineraryRefDto | null>(null);
  const [errorMessage, setErrorMessage] = useState('');

  const reset = () => {
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
      const eligible = result.trips.filter((trip) => trip.role !== 'VIEWER' && trip.status === 'READY');
      setTrips(eligible);
      setStep('pickTrip');
    })();
    return () => { active = false; };
  }, [visible, accessToken]);

  const pickTrip = async (trip: TripSummaryDto) => {
    setSelectedTrip(trip);
    setStep('loadingItineraries');
    const result = await loadTripItineraries(trip.tripId, accessToken);
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
    const result = await addItineraryItem({ itineraryId: selectedItinerary.itineraryId, placeId, dayIndex, baseVersion: selectedItinerary.latestVersion, accessToken });
    if (result.state === 'conflict') {
      // 그 사이 다른 편집이 있었다 — 최신 판 번호로 한 번만 다시 시도한다.
      const retry = await addItineraryItem({ itineraryId: selectedItinerary.itineraryId, placeId, dayIndex, baseVersion: result.latestVersion, accessToken });
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
            <View style={styles.centerState}><ActivityIndicator color={color.brand.orange} /></View>
          ) : null}

          {step === 'pickTrip' && trips.length === 0 ? (
            <View style={styles.centerState}>
              <Text color={color.text.body}>{tx('아직 일정이 만들어진 여행이 없어요. 먼저 여행을 만들어 주세요.', "You don't have a trip with an itinerary yet. Plan one first.")}</Text>
              <Pressable accessibilityRole="button" onPress={() => { close(); router.push('/plan/basic'); }} style={styles.submitButton}>
                <Text variant="body" weight="bold" color={color.text.onAction}>{tx('여행 계획 시작하기', 'Start planning')}</Text>
              </Pressable>
            </View>
          ) : null}

          {step === 'pickTrip' && trips.length > 0 ? (
            <ScrollView style={styles.list}>
              <Text variant="caption" color={color.text.body}>{tx('어느 여행에 더할까요?', 'Which trip should this go to?')}</Text>
              {trips.map((trip) => (
                <Pressable key={trip.tripId} accessibilityRole="button" onPress={() => void pickTrip(trip)} style={styles.optionRow}>
                  <Text variant="body" weight="bold">{tripDisplayTitle(trip, `${trip.startDate ?? ''} · ${trip.dayCount}${tx('일', ' days')}`)}</Text>
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
                  <Text variant="body" weight="bold">{dayLabel(selectedTrip.startDate, dayIndex, tx)}</Text>
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
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing[4], backgroundColor: 'rgba(11,29,58,0.62)' },
  card: { width: '100%', maxWidth: 420, maxHeight: '80%', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.ivory },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  closeButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: color.surface.card },
  pressed: { opacity: 0.72 },
  centerState: { gap: spacing[3], alignItems: 'center', paddingVertical: spacing[4] },
  list: { gap: spacing[2] },
  optionRow: { minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing[4], marginTop: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  submitButton: { minHeight: 48, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[6], borderRadius: radius.full, backgroundColor: color.brand.orange },
});
