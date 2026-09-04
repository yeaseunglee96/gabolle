import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { forgetSavedTrip, loadSavedTrips, type SavedTripSummary } from '@/trip/tripLibrary';

function dateLabel(trip: SavedTripSummary) {
  if (!trip.startDate) return '날짜 미확인';
  return trip.endDate && trip.endDate !== trip.startDate ? `${trip.startDate} – ${trip.endDate}` : trip.startDate;
}

export default function Trips() {
  const router = useRouter();
  const [trips, setTrips] = useState<SavedTripSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [feedback, setFeedback] = useState('');
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setTrips(await loadSavedTrips());
    setLoading(false);
  }, []);

  useFocusEffect(useCallback(() => { void reload(); }, [reload]));

  const remove = async (trip: SavedTripSummary) => {
    await forgetSavedTrip(trip.id);
    setTrips((current) => current.filter((item) => item.id !== trip.id));
    setPendingDelete(null);
    setFeedback('내 여행에서 삭제했어요. 서버의 일정은 삭제되지 않아요.');
  };

  return <View style={styles.shell}><Screen scroll wide style={styles.canvas}>
    <View style={styles.header}><View style={styles.headerCopy}><Text variant="eyebrow" weight="bold">MY TRIPS</Text><Text variant="display" weight="bold" style={styles.title}>내 여행</Text><Text color={color.text.body}>실제로 확인한 일정만 이 기기에 모아 보여드려요.</Text></View><Button label="새 여행" variant="ghost" onPress={() => router.push('/plan/basic')} containerStyle={styles.newTrip} /></View>
    {feedback ? <Pressable accessibilityRole="button" accessibilityLabel="안내 닫기" accessibilityLiveRegion="polite" onPress={() => setFeedback('')} style={styles.feedback}><Text variant="caption" weight="bold" color={color.text.onAction}>{feedback}</Text><Text variant="caption" color={color.text.onAction}>닫기</Text></Pressable> : null}
    {loading ? <View accessibilityLiveRegion="polite" style={styles.state}><ActivityIndicator color={color.brand.orange} /><Text weight="bold">내 여행을 불러오고 있어요</Text></View> : null}
    {!loading && trips.length === 0 ? <View style={styles.empty}><View style={styles.emptyMark}><Text variant="display" color={color.brand.orange}>⌁</Text></View><Text variant="title" weight="bold">아직 저장된 여행이 없어요</Text><Text color={color.text.body} style={styles.center}>일정을 만든 뒤 상세 화면을 확인하면 이곳에 자동으로 보관돼요.</Text><Button label="첫 여행 만들기" onPress={() => router.push('/plan/basic')} containerStyle={styles.emptyCta} /></View> : null}
    {!loading && trips.length > 0 ? <View style={styles.list}>{trips.map((trip) => <Pressable key={trip.id} accessibilityRole="button" accessibilityLabel={`${trip.title} 일정 열기`} onPress={() => router.push(`/trips/${trip.id}/itinerary`)} style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}><View style={styles.cardTop}><View style={styles.cardCopy}><Text variant="title" weight="bold">{trip.title}</Text><Text variant="caption" color={color.text.muted}>{dateLabel(trip)}</Text></View><Text variant="title" color={color.brand.orange}>›</Text></View><View style={styles.meta}><View style={styles.metaPill}><Text variant="caption" weight="bold">{trip.dayCount}일</Text></View><View style={styles.metaPill}><Text variant="caption" weight="bold">장소 {trip.placeCount}곳</Text></View></View>{pendingDelete === trip.id ? <View style={styles.confirmDelete}><Text variant="caption" weight="bold">이 기기의 목록에서 삭제할까요?</Text><View style={styles.confirmActions}><Pressable accessibilityRole="button" onPress={(event) => { event.stopPropagation(); setPendingDelete(null); }} style={styles.confirmButton}><Text variant="caption" weight="bold">취소</Text></Pressable><Pressable accessibilityRole="button" onPress={(event) => { event.stopPropagation(); void remove(trip); }} style={[styles.confirmButton, styles.confirmDanger]}><Text variant="caption" weight="bold" color={color.text.onAction}>삭제</Text></Pressable></View></View> : <Pressable accessibilityRole="button" accessibilityLabel={`${trip.title} 내 여행에서 삭제`} onPress={(event) => { event.stopPropagation(); setPendingDelete(trip.id); }} style={({ pressed }) => [styles.deleteButton, pressed && styles.cardPressed]}><Text variant="caption" weight="bold" color={color.state.danger}>내 여행에서 삭제</Text></Pressable>}</Pressable>)}</View> : null}
  </Screen><TabBar active="map" /></View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory }, canvas: { backgroundColor: color.brand.ivory },
  header: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: spacing[3] }, headerCopy: { flex: 1, minWidth: 0 }, title: { marginTop: spacing[1], marginBottom: spacing[2] }, newTrip: { width: 84, minHeight: 44, flexShrink: 0 },
  feedback: { minHeight: 48, marginTop: spacing[4], paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.brand.navy, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] }, state: { minHeight: 220, alignItems: 'center', justifyContent: 'center', gap: spacing[3] },
  empty: { minHeight: 320, marginTop: spacing[6], padding: spacing[6], borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center', gap: spacing[3] }, emptyMark: { width: 68, height: 68, borderRadius: radius.full, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' }, center: { maxWidth: 300, textAlign: 'center' }, emptyCta: { minWidth: 180, marginTop: spacing[2], backgroundColor: color.brand.navy },
  list: { marginTop: spacing[6], gap: spacing[3] }, card: { padding: spacing[4], borderRadius: radius.lg, borderWidth: 1, borderColor: '#ece6dc', backgroundColor: color.surface.card, gap: spacing[3], shadowColor: color.brand.navy, shadowOpacity: 0.06, shadowRadius: 10, shadowOffset: { width: 0, height: 4 }, elevation: 2 }, cardPressed: { opacity: 0.72, transform: [{ scale: 0.99 }] }, cardTop: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, cardCopy: { flex: 1, gap: spacing[1] }, meta: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }, metaPill: { paddingHorizontal: spacing[3], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft }, deleteButton: { minHeight: 44, alignSelf: 'flex-start', justifyContent: 'center' }, confirmDelete: { gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.dangerBg }, confirmActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: spacing[2] }, confirmButton: { minWidth: 68, minHeight: 40, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center' }, confirmDanger: { backgroundColor: color.state.danger },
});
