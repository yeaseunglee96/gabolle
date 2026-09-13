// 기념품샵 지도 — S15P21E201-121/-470(상세설계서 v2 P-19). 마지막 방문지를 기준 위치로
// 삼아 가까운 순서대로 기념품샵을 지도+목록으로 보여준다. "지금 여기서 가까운가"가
// 중요하다는 게 이 화면의 이유라, 기기 GPS 가 아니라 여행 안에서 실제로 도착을 찍은
// 마지막 장소를 기준으로 삼는다(getLastVisitedPlace, -470 코멘트 참고).
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { Button } from '@/components/Button';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { getLastVisitedPlace, type LastVisitedPlaceResult } from '@/discovery/lastVisitedPlace';
import { getNearbyPlaces, type NearbyPlacesLoadResult } from '@/discovery/localExplore';
import { RouteMap } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { loadTripItineraries } from '@/trip/trips';

const SOUVENIR_FACET_KEY = 'SOUVENIR_SHOP';

export default function Souvenirs() {
  const { tx } = useI18n();
  const router = useRouter();
  const { accessToken } = useAuth();
  const { id } = useLocalSearchParams<{ id: string }>();
  const tripId = id ?? 'demo-trip';

  const [origin, setOrigin] = useState<LastVisitedPlaceResult | null>(null);
  const [result, setResult] = useState<NearbyPlacesLoadResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    // S15P21E201-912: getLastVisitedPlace는 일정 식별자를 받는다 — 여기 tripId(여행
    // 식별자)를 그대로 넘기면 서버에 없는 자원을 찾아 항상 실패한다. 먼저 일정 목록을
    // 받아 그 첫 항목의 itineraryId로 바꿔 넘긴다(prepare.tsx와 같은 방식).
    void loadTripItineraries(tripId, accessToken).then(async (refsResult) => {
      if (!active) return;
      const itineraryId = refsResult.state === 'success' ? refsResult.itineraries[0]?.itineraryId : undefined;
      if (!itineraryId) { setOrigin({ state: 'unavailable', message: '일정을 아직 못 불러왔어요.' }); setLoading(false); return; }
      const originResult = await getLastVisitedPlace(itineraryId, accessToken);
      if (!active) return;
      setOrigin(originResult);
      if (originResult.state !== 'success') { setLoading(false); return; }
      const nearby = await getNearbyPlaces({ lat: originResult.place.lat, lng: originResult.place.lng, facetKey: SOUVENIR_FACET_KEY });
      if (!active) return;
      setResult(nearby);
      setLoading(false);
    });
    return () => { active = false; };
  }, [tripId, accessToken]);

  const stops: MapStop[] = useMemo(() => {
    if (!result || result.state !== 'success') return [];
    return result.items.map((item, index) => ({ id: item.placeId, number: index + 1, name: item.nameKo, latitude: item.lat, longitude: item.lng }));
  }, [result]);

  useEffect(() => {
    if (stops.length && !stops.some((stop) => stop.id === selectedId)) setSelectedId(stops[0].id);
  }, [stops, selectedId]);

  return (
    <Screen scroll style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace(`/${tripId}/prepare`)} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
      </View>

      <View style={styles.heading}>
        <Eyebrow>{tx('기념품샵', 'Souvenir shops')}</Eyebrow>
        <Text variant="display" weight="bold">{tx('마지막 방문지 근처 기념품샵', 'Souvenir shops near your last stop')}</Text>
        <Text color={color.text.body}>{tx('가까운 순서대로 보여드려요.', 'Sorted by how close they are.')}</Text>
      </View>

      {loading ? (
        <View accessibilityLiveRegion="polite" style={styles.stateCard}><Text color={color.text.body}>{tx('기준 위치를 찾고 있어요…', 'Finding your reference location…')}</Text></View>
      ) : null}

      {!loading && origin?.state === 'none' ? (
        <View style={styles.stateCard}>
          <Text variant="title" weight="bold">{tx('아직 방문 기록이 없어요', 'No visits recorded yet')}</Text>
          <Text color={color.text.body}>{tx('여행에서 첫 방문지의 도착을 찍으면 그 자리를 기준으로 근처 기념품샵을 보여드려요.', "Once you mark your arrival at your first stop, we'll show souvenir shops near it.")}</Text>
        </View>
      ) : null}

      {!loading && origin && (origin.state === 'unavailable' || origin.state === 'offline' || origin.state === 'error') ? (
        <View accessibilityRole="alert" style={styles.stateCard}>
          <Text variant="title" weight="bold">{tx('마지막 방문지를 확인하지 못했어요', "Couldn't check your last stop")}</Text>
          <Text color={color.text.body}>{origin.message}</Text>
        </View>
      ) : null}

      {!loading && origin?.state === 'success' && result && result.state !== 'success' ? (
        <View accessibilityRole="alert" style={styles.stateCard}>
          <Text variant="title" weight="bold">{tx('기념품샵을 불러오지 못했어요', 'Could not load souvenir shops')}</Text>
          <Text color={color.text.body}>{result.message}</Text>
        </View>
      ) : null}

      {!loading && result?.state === 'success' && result.items.length === 0 ? (
        <View style={styles.stateCard}><Text color={color.text.body}>{tx('근처에 기념품샵이 없어요.', 'No souvenir shops nearby.')}</Text></View>
      ) : null}

      {!loading && result?.state === 'success' && result.items.length > 0 ? (
        <>
          <RouteMap stops={stops} selectedId={selectedId} onSelect={setSelectedId} routes={[]} height={280} />

          {result.radiusExpanded ? (
            <View style={styles.expandedNotice}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx(`반경을 ${result.effectiveRadiusM.toLocaleString()}m로 넓혔습니다`, `Widened the search radius to ${result.effectiveRadiusM.toLocaleString()}m`)}</Text></View>
          ) : null}

          <View style={styles.list}>
            {result.items.map((item) => (
              <Pressable
                key={item.placeId}
                accessibilityRole="button"
                accessibilityState={{ selected: selectedId === item.placeId }}
                onPress={() => setSelectedId(item.placeId)}
                style={[styles.placeRow, selectedId === item.placeId && styles.placeRowSelected]}
              >
                <View style={styles.grow}>
                  <Text weight="bold">{item.nameKo}</Text>
                  {item.address ? <Text variant="caption" color={color.text.muted}>{item.address}</Text> : null}
                </View>
                <Text variant="caption" weight="bold" color={color.text.accent}>{tx(`${item.distanceM.toLocaleString()}m`, `${item.distanceM.toLocaleString()}m`)}</Text>
              </Pressable>
            ))}
          </View>
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  heading: { gap: spacing[2], marginBottom: spacing[6] },
  stateCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  expandedNotice: { marginTop: spacing[3], padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.tint },
  list: { marginTop: spacing[4], gap: spacing[2] },
  placeRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field },
  placeRowSelected: { borderColor: color.brand.orange },
  grow: { flex: 1 },
});
