// 로컬 탐색 화면 (S15P21E201-472, 상세설계서 v2 P-17). 8개 갈래(축제·야시장·전통시장·액티비티·
// 산책·자연·야경·기념품샵)를 아코디언으로 접었다 펴며 보여준다. 한 번에 하나만 열리고, 열 때
// 그 갈래의 장소만 부른다 — 화면 진입 시 8개를 한꺼번에 부르지 않는다.
//
// 갈래 이름·순서는 GET /api/v1/places/facets 응답을 그대로 쓴다(jaehyeon 님 2026-09-08:
// "목록을 화면 코드에 박지 마세요" — 서버가 갈래를 추가하거나 이름을 바꿔도 앱을 다시 배포하지
// 않아도 되게 하려는 것). 그래서 여기엔 8개 이름의 하드코딩 배열이 없다.
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import * as Location from 'expo-location';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { color, radius, spacing } from '@/design/tokens';
import { getFacets, getNearbyPlaces, type FacetKeyEntry, type FacetsLoadResult, type NearbyPlacesLoadResult } from '@/discovery/localExplore';
import { useI18n } from '@/i18n';

// 여덟 갈래의 실제 값(jaehyeon 님 확인) — 서버가 이 여덟을 항상 함께 돌려주므로, 응답에서
// 이 값과 일치하는 항목만 골라 순서는 서버가 준 그대로 둔다. 화면 쪽에서 새로 만들지 않는다.
const KNOWN_FACET_KEYS = new Set(['FESTIVAL', 'NIGHT_MARKET', 'TRADITIONAL_MARKET', 'ACTIVITY', 'WALK', 'NATURE', 'NIGHT_VIEW', 'SOUVENIR_SHOP']);

// S15P21E201-898: 장소가 0곳인 갈래는 목록에서 아예 뺀다(지우는 게 아니라 거르는 것 —
// 적재가 돌아 placeCount 가 늘면 다음 조회에서 코드 변경 없이 다시 나타난다).
function flattenLocalFacets(result: FacetsLoadResult): FacetKeyEntry[] | null {
  if (result.state !== 'success') return null;
  const flat = result.facets.flatMap((group) => group.keys);
  const local = flat.filter((entry) => KNOWN_FACET_KEYS.has(entry.featureKey));
  const withPlaces = (local.length ? local : flat).filter((entry) => entry.placeCount > 0);
  return withPlaces;
}

type LocationState = 'detecting' | 'granted' | 'denied';

export default function LocalExplore() {
  const router = useRouter();
  const { tx } = useI18n();
  const [result, setResult] = useState<FacetsLoadResult>({ state: 'success', facets: [] });
  const [loading, setLoading] = useState(true);
  const [openKey, setOpenKey] = useState<string | null>(null);
  const [locationState, setLocationState] = useState<LocationState>('detecting');
  const [coords, setCoords] = useState<{ latitude: number; longitude: number } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setResult(await getFacets());
    setLoading(false);
  }, []);

  const detectLocation = useCallback(async () => {
    setLocationState('detecting');
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (!permission.granted) { setLocationState('denied'); return; }
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      setLocationState('granted');
    } catch {
      setLocationState('denied');
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { void detectLocation(); }, [detectLocation]);

  const facets = flattenLocalFacets(result);

  return (
    <Screen scroll style={styles.screen}>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
        <View style={styles.spacer} />
      </View>

      <View style={styles.heading}>
        <Eyebrow>{tx('로컬 탐색', 'Local explore')}</Eyebrow>
        <Text variant="display" weight="bold">{tx('부산 로컬 탐색', 'Explore Busan like a local')}</Text>
        <Text color={color.text.body}>{tx('갈래를 눌러 열면 그 자리에서 장소를 찾아요.', 'Tap a category to load places for it.')}</Text>
      </View>

      {loading ? (
        <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.brand.orange} /><Text color={color.text.body}>{tx('갈래를 불러오고 있어요', 'Loading categories')}</Text></View>
      ) : null}

      {!loading && result.state !== 'success' ? (
        <View accessibilityRole="alert" style={styles.stateCard}>
          <Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : result.state === 'unavailable' ? tx('로컬 탐색 API를 기다리고 있어요', 'Waiting for the local explore API') : tx('갈래를 불러오지 못했어요', 'Could not load categories')}</Text>
          <Text color={color.text.body}>{result.message}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void load()} />
        </View>
      ) : null}

      {!loading && facets && facets.length === 0 ? (
        <View style={styles.stateCard}><Text color={color.text.body}>{tx('지금은 둘러볼 수 있는 갈래가 없어요. 자료가 들어오면 다시 열어 드릴게요.', 'No categories to explore right now — check back once new places are added.')}</Text></View>
      ) : null}

      {!loading && facets && facets.length > 0 ? (
        <View style={styles.accordion}>
          {facets.map((facet) => {
            const open = openKey === facet.featureKey;
            return (
              <View key={facet.featureKey} style={styles.branch}>
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ expanded: open }}
                  onPress={() => setOpenKey(open ? null : facet.featureKey)}
                  style={styles.branchHeader}
                >
                  <Text variant="body" weight="bold" color={color.text.heading}>{facet.labelKo}</Text>
                  <View style={styles.branchRight}>
                    <View style={styles.countBadge}><Text variant="caption" weight="bold" color={color.text.body}>{facet.placeCount}</Text></View>
                    <Text variant="title" color={color.text.heading}>{open ? '︿' : '﹀'}</Text>
                  </View>
                </Pressable>
                {open ? <LocalBranchList facetKey={facet.featureKey} coords={coords} locationState={locationState} onRetryLocation={() => void detectLocation()} /> : null}
              </View>
            );
          })}
        </View>
      ) : null}
    </Screen>
  );
}

// 열린 갈래 하나의 장소 목록 — GET /api/v1/places/nearby(S15P21E201-469)를 그 갈래를 열 때만
// 부른다(화면 진입 시 8개를 한꺼번에 안 부르는 완료 기준). 한 갈래의 실패가 나머지 일곱 갈래를
// 막지 않도록, 이 컴포넌트 안에서만 상태를 갖는다.
function LocalBranchList({ facetKey, coords, locationState, onRetryLocation }: {
  facetKey: string;
  coords: { latitude: number; longitude: number } | null;
  locationState: 'detecting' | 'granted' | 'denied';
  onRetryLocation: () => void;
}) {
  const { tx } = useI18n();
  const [result, setResult] = useState<NearbyPlacesLoadResult | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!coords) return;
    let active = true;
    setLoading(true);
    (async () => {
      const next = await getNearbyPlaces({ lat: coords.latitude, lng: coords.longitude, facetKey });
      if (active) { setResult(next); setLoading(false); }
    })();
    return () => { active = false; };
  }, [coords, facetKey]);

  if (locationState === 'detecting') {
    return <View style={styles.branchBody}><Text color={color.text.body}>{tx('현재 위치를 확인하고 있어요…', 'Checking your current location…')}</Text></View>;
  }
  if (locationState === 'denied') {
    return <View style={styles.branchBody}>
      <Text color={color.text.body}>{tx('위치 권한이 꺼져 있어요. 근처 장소를 찾으려면 위치가 필요해요.', 'Location permission is off. We need it to find nearby places.')}</Text>
      <Button label={tx('위치 권한 다시 요청', 'Ask for location again')} variant="ghost" onPress={onRetryLocation} containerStyle={styles.branchRetry} />
    </View>;
  }
  if (loading || !result) {
    return <View accessibilityLiveRegion="polite" style={styles.branchBody}><ActivityIndicator color={color.brand.orange} /></View>;
  }
  if (result.state !== 'success') {
    return <View style={styles.branchBody}>
      <Text color={color.text.body}>{result.message}</Text>
      <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => coords && void getNearbyPlaces({ lat: coords.latitude, lng: coords.longitude, facetKey }).then(setResult)} containerStyle={styles.branchRetry} />
    </View>;
  }
  if (result.items.length === 0) {
    // S15P21E201-919: 갈래 배지 개수(부산 전체 집계)와 이 목록(반경 안 검색)은 서로 다른
    // 걸 잰다 — 배지에 숫자가 있어도 반경 안에는 없을 수 있다. "아예 없다"처럼 읽히지
    // 않도록 실제로 넓혀 본 반경을 밝힌다.
    const radiusKm = (result.effectiveRadiusM / 1000).toLocaleString(undefined, { maximumFractionDigits: 1 });
    return <View style={styles.branchBody}><Text color={color.text.body}>{tx(`${radiusKm}km 이내에는 이 갈래의 장소가 없어요. 부산 전체에는 있을 수 있어요.`, `No places in this category within ${radiusKm}km. There may be some elsewhere in Busan.`)}</Text></View>;
  }
  return (
    <View style={styles.branchBody}>
      {result.radiusExpanded && (
        <View style={styles.expandedNotice}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx(`반경을 ${result.effectiveRadiusM.toLocaleString()}m로 넓혔습니다`, `Widened the search radius to ${result.effectiveRadiusM.toLocaleString()}m`)}</Text></View>
      )}
      {result.items.map((item) => (
        <View key={item.placeId} style={styles.placeRow}>
          <View style={styles.grow}>
            <Text weight="bold">{item.nameKo}</Text>
            {item.address ? <Text variant="caption" color={color.text.muted}>{item.address}</Text> : null}
          </View>
          <Text variant="caption" weight="bold" color={color.text.accent}>{tx(`${item.distanceM.toLocaleString()}m`, `${item.distanceM.toLocaleString()}m`)}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.brand.ivory },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  logo: { width: 96, height: 28 },
  spacer: { width: 44 },
  heading: { gap: spacing[2], marginBottom: spacing[6] },
  stateCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  accordion: { gap: spacing[2] },
  branch: { borderRadius: radius.lg, backgroundColor: color.surface.card, overflow: 'hidden' },
  branchHeader: { minHeight: 56, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: spacing[4] },
  branchRight: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  countBadge: { minWidth: 28, minHeight: 24, paddingHorizontal: spacing[2], borderRadius: radius.full, backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  branchBody: { padding: spacing[4], paddingTop: 0, gap: spacing[2] },
  branchRetry: { alignSelf: 'flex-start' },
  expandedNotice: { padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.tint },
  placeRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingVertical: spacing[2], borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border },
  grow: { flex: 1 },
});
