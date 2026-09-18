// 로컬 탐색 화면 상세설계서 v2 P-17). 8개 갈래(축제·야시장·전통시장·액티비티
// 산책·자연·야경·기념품샵) 중 하나를 고르고, 내 근처와 부산 전체를 전환해 본다.
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Image, Linking, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Location from 'expo-location';

import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { PhotoSubjectBadge } from '@/components/PhotoSubjectBadge';
import { color, radius, spacing } from '@/design/tokens';
import { flattenLocalFacets, getFacets, getNearbyPlaces, localFacetLabel, localPlaceName, type FacetsLoadResult, type LocalFacetEntry, type NearbyPlacesLoadResult } from '@/discovery/localExplore';
import { getPlacesByFacet, type PhotoSubject, type PlaceSearchItem } from '@/discovery/places';
import { useI18n } from '@/i18n';

// 여덟 갈래의 실제 값(jaehyeon 님 확인) — 서버가 이 여덟을 항상 함께 돌려주므로, 응답에서
// 이 값과 일치하는 항목만 골라 순서는 서버가 준 그대로 둔다. 화면 쪽에서 새로 만들지 않는다.
const KNOWN_FACET_KEYS = new Set(['FESTIVAL', 'NIGHT_MARKET', 'TRADITIONAL_MARKET', 'ACTIVITY', 'WALK', 'NATURE', 'NIGHT_VIEW', 'SOUVENIR_SHOP']);

// : 장소가 0곳인 갈래는 목록에서 아예 뺀다(지우는 게 아니라 거르는 것
// 적재가 돌아 placeCount 가 늘면 다음 조회에서 코드 변경 없이 다시 나타난다).
type LocationState = 'detecting' | 'granted' | 'denied';

// — 거부 뒤에 「다시 물을 수 있는가」를 따로 들고 있어야 한다.
type ExploreScope = 'nearby' | 'all';

export default function LocalExplore() {
  const router = useRouter();
  const { facet } = useLocalSearchParams<{ facet?: string }>();
  const { tx, language } = useI18n();
  const [result, setResult] = useState<FacetsLoadResult>({ state: 'success', facets: [] });
  const [loading, setLoading] = useState(true);
  const requestedFacet = facet && KNOWN_FACET_KEYS.has(facet) ? facet : null;
  const [selectedKey, setSelectedKey] = useState<string | null>(requestedFacet);
  const [scope, setScope] = useState<ExploreScope>('all');
  const [locationState, setLocationState] = useState<LocationState>('detecting');
  const [canAskAgain, setCanAskAgain] = useState(true);
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
      if (!permission.granted) { setCanAskAgain(permission.canAskAgain); setLocationState('denied'); return; }
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      setLocationState('granted');
    } catch {
      setLocationState('denied');
    }
  }, []);

  const restoreGrantedLocation = useCallback(async () => {
    setLocationState('detecting');
    try {
      // 화면을 둘러보기만 해도 권한 팝업부터 띄우지 않는다. 이미 허용한 사람에게만 위치를
      // 읽고, 처음이거나 거부한 사람은 부산 중심 결과를 먼저 보여 준 뒤 버튼으로 선택하게 한다.
      const permission = await Location.getForegroundPermissionsAsync();
      if (!permission.granted) { setCanAskAgain(permission.canAskAgain); setLocationState('denied'); return; }
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      setLocationState('granted');
    } catch {
      setLocationState('denied');
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { void restoreGrantedLocation(); }, [restoreGrantedLocation]);
  useEffect(() => { if (requestedFacet) setSelectedKey(requestedFacet); }, [requestedFacet]);

  const facets = flattenLocalFacets(result, KNOWN_FACET_KEYS);
  // 홈에서 특정 갈래를 눌러 들어온 경우 그 갈래를 맨 위에서 바로 펼친다. 선택값을 버린 채
  // 같은 첫 화면만 보여 주면 사용자는 버튼이 동작하지 않았다고 느낀다.
  const visibleFacets = facets && requestedFacet
    ? [...facets].sort((a, b) => Number(b.featureKey === requestedFacet) - Number(a.featureKey === requestedFacet))
    : facets;
  useEffect(() => {
    if (!selectedKey && visibleFacets?.length) setSelectedKey(visibleFacets[0].featureKey);
  }, [selectedKey, visibleFacets]);
  // 폭 분기 경계값은 layout/breakpoints.ts 가 정한 셋을 그대로 쓴다
  // 이 화면에서 새 숫자를 만들지 않는다.
  // wide 600~ 갈래 칩이 줄바꿈되고 결과가 카드 격자가 된다
  // split 1024~ 왼쪽 기둥(갈래·범위)과 오른쪽 결과로 나뉜다
  const { width } = useLayout();
  const wide = isAtLeast(width, 'md');
  const split = isAtLeast(width, 'lg');
  const selectedFacet = visibleFacets?.find((entry) => entry.featureKey === selectedKey) ?? visibleFacets?.[0] ?? null;

  return (
    <Screen scroll wide={split} style={styles.screen}>
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
        <Text color={color.text.body}>{tx('관심 갈래를 고르고 내 근처 또는 부산 전체에서 찾아보세요.', 'Choose a category, then search nearby or across Busan.')}</Text>
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

      {!loading && visibleFacets && visibleFacets.length === 0 ? (
        <View style={styles.stateCard}><Text color={color.text.body}>{tx('지금은 둘러볼 수 있는 갈래가 없어요. 자료가 들어오면 다시 열어 드릴게요.', 'No categories to explore right now — check back once new places are added.')}</Text></View>
      ) : null}

      {!loading && visibleFacets && visibleFacets.length > 0 ? (
        <View style={split ? styles.split : styles.results}>
          {/* 고르는 자리 — 좁은 화면에서는 내용 위에 가로로 눕고, 1024 부터는 왼쪽 기둥이 된다.
              들어 있는 것은 어느 폭에서나 같다: 갈래 범위 토글 안내 한 줄.
          */}
          <View style={split ? styles.sidebar : styles.pickers}>
            <FacetPicker
              facets={visibleFacets}
              selectedKey={selectedFacet?.featureKey ?? null}
              onSelect={setSelectedKey}
              mode={split ? 'list' : wide ? 'wrap' : 'rail'}
              language={language}
            />
            <View accessibilityRole="tablist" style={styles.scopeSwitch}>
              {(['nearby', 'all'] as const).map((value) => {
                const selected = scope === value;
                return <Pressable key={value} accessibilityRole="tab" accessibilityState={{ selected }} onPress={() => setScope(value)} style={[styles.scopeOption, selected && styles.scopeOptionSelected]}>
                  <Text weight="bold" color={selected ? color.text.onAction : color.text.body}>{selected ? '✓ ' : ''}{value === 'nearby' ? tx('내 근처', 'Nearby') : tx('부산 전체', 'All Busan')}</Text>
                </Pressable>;
              })}
            </View>
            <Text variant="caption">{scope === 'all' ? tx('부산 전체는 거리 제한 없이 찾아요.', 'All Busan searches without a distance limit.') : tx('내 근처는 반경 안에서 찾아요. 결과의 검색 범위를 확인하거나 부산 전체로 바꿔 보세요.', 'Nearby searches within a radius. Check the range shown with results, or switch to All Busan.')}</Text>
          </View>
          <View style={split ? styles.detail : undefined}>
            {selectedFacet ? <LocalBranchList facet={selectedFacet} scope={scope} coords={coords} canAskAgain={canAskAgain} onRetryLocation={() => void detectLocation()} cards={wide} /> : null}
          </View>
        </View>
      ) : null}
    </Screen>
  );
}

// 열린 갈래 하나의 장소 목록 — GET /api/v1/places/nearby를 그 갈래를 열 때만
// 부른다(화면 진입 시 8개를 한꺼번에 안 부르는 완료 기준). 한 갈래의 실패가 나머지 일곱 갈래를
// 막지 않도록, 이 컴포넌트 안에서만 상태를 갖는다.
/** 내 위치를 못 쓸 때의 기준점 —. 부산 시청이다. */
const BUSAN_CENTER = { lat: 35.1796, lng: 129.0756 };

/** 갈래를 고르는 자리 — 폭에 따라 세 모양, 한 자료 */
function FacetPicker({ facets, selectedKey, onSelect, mode, language }: {
  facets: LocalFacetEntry[];
  selectedKey: string | null;
  onSelect: (key: string) => void;
  mode: 'rail' | 'wrap' | 'list';
  /**
   * useI18n 의 language 를 그대로 받는다. 문자열로 느슨하게 두면 localFacetLabel 이
   * 받는 다섯 언어 말고 아무 값이나 들어올 수 있어, 그 자리에서 타입으로 묶는다.
   */
  language: Parameters<typeof localFacetLabel>[1];
}) {
  const chips = facets.map((entry) => {
    const selected = entry.featureKey === selectedKey;
    const label = localFacetLabel(entry, language);
    if (mode === 'list') {
      return (
        <Pressable key={entry.featureKey} accessibilityRole="button" accessibilityState={{ selected }} onPress={() => onSelect(entry.featureKey)} style={[styles.facetRow, selected && styles.categoryChipSelected]}>
          <Text weight="bold" color={selected ? color.text.onAction : color.text.heading} style={styles.grow}>{selected ? '✓ ' : ''}{label}</Text>
          <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.muted}>{entry.placeCount}</Text>
        </Pressable>
      );
    }
    return (
      <Pressable key={entry.featureKey} accessibilityRole="button" accessibilityState={{ selected }} onPress={() => onSelect(entry.featureKey)} style={[styles.categoryChip, selected && styles.categoryChipSelected]}>
        <Text weight="bold" color={selected ? color.text.onAction : color.text.heading}>{selected ? '✓ ' : ''}{label} · {entry.placeCount}</Text>
      </Pressable>
    );
  });
  if (mode === 'rail') {
    return <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.categoryRail}>{chips}</ScrollView>;
  }
  return <View style={mode === 'list' ? styles.facetList : styles.facetWrap}>{chips}</View>;
}

function LocalBranchList({ facet, scope, coords, canAskAgain, onRetryLocation, cards = false }: {
  facet: LocalFacetEntry;
  scope: ExploreScope;
  coords: { latitude: number; longitude: number } | null;
  canAskAgain: boolean;
  onRetryLocation: () => void;
  /** 넓은 화면이면 목록 대신 카드 격자로 그린다. */
  cards?: boolean;
}) {
  const { tx } = useI18n();
  const [result, setResult] = useState<NearbyPlacesLoadResult | null>(null);
  const [allItems, setAllItems] = useState<PlaceSearchItem[] | null>(null);
  const [allError, setAllError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // 위치를 모르면 부산 중심으로 찾는다 —.
  const center = scope === 'nearby' && coords ? { lat: coords.latitude, lng: coords.longitude } : BUSAN_CENTER;
  const usingFallback = !coords;

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setLoading(true);
    (async () => {
      if (scope === 'all') {
        try {
          const items = await getPlacesByFacet(facet.placeFeatureType, facet.featureKey, 20, controller.signal);
          if (active) { setAllItems(items); setAllError(null); setLoading(false); }
        } catch (error) {
          if (active) { setAllItems([]); setAllError(error instanceof Error ? error.message : tx('장소를 불러오지 못했어요.', 'Could not load places.')); setLoading(false); }
        }
        return;
      }
      const next = await getNearbyPlaces({ lat: center.lat, lng: center.lng, facetKey: facet.featureKey }, controller.signal);
      if (active) { setResult(next); setLoading(false); }
    })();
    return () => { active = false; controller.abort(); };
  }, [center.lat, center.lng, facet.featureKey, facet.placeFeatureType, scope, tx]);

  if (loading || (scope === 'nearby' && !result) || (scope === 'all' && !allItems)) {
    return <View accessibilityLiveRegion="polite" style={styles.branchBody}><ActivityIndicator color={color.brand.orange} /></View>;
  }
  if (scope === 'all') {
    if (allError) return <View style={styles.branchBody}><Text color={color.text.body}>{allError}</Text></View>;
    if (!allItems?.length) return <View style={styles.branchBody}><Text color={color.text.body}>{tx('부산 전체에서도 이 갈래의 장소를 찾지 못했어요.', 'No places in this category were found across Busan.')}</Text></View>;
    return <PlaceRows items={allItems} cards={cards} />;
  }
  if (!result) return null;
  if (result.state !== 'success') {
    return <View style={styles.branchBody}>
      <Text color={color.text.body}>{result.message}</Text>
      <Button label={tx('다시 시도', 'Try again')} variant="ghost" onPress={() => void getNearbyPlaces({ lat: center.lat, lng: center.lng, facetKey: facet.featureKey }).then(setResult)} containerStyle={styles.branchRetry} />
    </View>;
  }
  if (result.items.length === 0) {
    // : 갈래 배지 개수(부산 전체 집계)와 이 목록(반경 안 검색)은 서로 다른
    // 걸 잰다 — 배지에 숫자가 있어도 반경 안에는 없을 수 있다. "아예 없다"처럼 읽히지
    // 않도록 실제로 넓혀 본 반경을 밝힌다.
    const radiusKm = (result.effectiveRadiusM / 1000).toLocaleString(undefined, { maximumFractionDigits: 1 });
    return <View style={styles.branchBody}><Text color={color.text.body}>{tx(`${radiusKm}km 이내에는 이 갈래의 장소가 없어요. 부산 전체에는 있을 수 있어요.`, `No places in this category within ${radiusKm}km. There may be some elsewhere in Busan.`)}</Text></View>;
  }
  return (
    <View style={styles.branchBody}>
      <Text variant="caption">{tx(`검색 범위: ${(result.effectiveRadiusM / 1000).toLocaleString()}km 이내`, `Search range: within ${(result.effectiveRadiusM / 1000).toLocaleString()} km`)}</Text>
      {/* 내 위치를 못 쓴 채 부산 중심으로 찾았다는 사실을 밝힌다 —.
          조용히 대신 보여 주면 거리 숫자가 왜 이런지 설명이 안 된다. 권한을 다시 물을
          길도 여기 같이 둔다.
      */}
      {usingFallback && (
        <View style={styles.expandedNotice}>
          <Text variant="caption" weight="bold" color={color.brand.orange}>{tx('내 위치를 몰라 부산 중심에서 찾았어요. 거리도 그 기준이에요.', 'We searched from the center of Busan because your location is unavailable. Distances use that point.')}</Text>
          {canAskAgain
            ? <Button label={tx('내 위치로 다시 찾기', 'Search from my location')} variant="ghost" onPress={onRetryLocation} containerStyle={styles.branchRetry} />
            : <Button label={tx('설정에서 위치 허용하기', 'Allow location in Settings')} variant="ghost" onPress={() => void Linking.openSettings()} containerStyle={styles.branchRetry} />}
        </View>
      )}
      {result.radiusExpanded && (
        <View style={styles.expandedNotice}><Text variant="caption" weight="bold" color={color.brand.orange}>{tx(`반경을 ${result.effectiveRadiusM.toLocaleString()}m로 넓혔습니다`, `Widened the search radius to ${result.effectiveRadiusM.toLocaleString()}m`)}</Text></View>
      )}
      <PlaceRows items={result.items} showDistance cards={cards} />
    </View>
  );
}

/** 사진 자리 — 값이 있으면 사진, 없으면 핀 하나 */
function PlacePhoto({ item, style }: { item: { photoUrl?: string | null; photoSubject?: PhotoSubject | null }; style: object }) {
  if (item.photoUrl) {
    // 사진 위에 「무엇을 찍은 것인가」를 얹으려고 감싼다 —.
    // 그 장소를 직접 찍은 사진에는 아무것도 안 나온다(판정은 photoLabels 한 곳에 있다).
    return (
      <View style={[style, styles.photoWrap]}>
        <Image source={{ uri: item.photoUrl }} resizeMode="cover" style={StyleSheet.absoluteFill} />
        <PhotoSubjectBadge photoSubject={item.photoSubject} style={styles.photoBadge} />
      </View>
    );
  }
  return <View style={[style, styles.photoEmpty]}><Text variant="title" color={color.text.muted}>📍</Text></View>;
}

function PlaceRows({ items, showDistance = false, cards = false }: { items: Array<PlaceSearchItem | import('@/discovery/localExplore').NearbyPlaceItem>; showDistance?: boolean; cards?: boolean }) {
  const router = useRouter();
  const { tx, language } = useI18n();
  const open = (placeId: string) => router.push(`/place/${placeId}`);
  const label = (item: PlaceSearchItem | import('@/discovery/localExplore').NearbyPlaceItem) =>
    tx(`${item.nameKo} 상세 보기`, `View details for ${item.nameEn ?? item.nameKo}`);

  // 넓은 화면 — 카드 격자. flexWrap 으로 두 열이 되고, 한 열이 되는 폭에서는 자연히 한 열이다.
  if (cards) {
    return <View style={styles.cardGrid}>{items.map((item) => (
      <Pressable key={item.placeId} accessibilityRole="link" accessibilityLabel={label(item)} onPress={() => open(item.placeId)} style={({ pressed }) => [styles.card, pressed && styles.pressed]}>
        <View style={styles.cardPhotoWrap}>
          <PlacePhoto item={item} style={styles.cardPhoto} />
          {item.photoSource ? <View style={styles.sourcePill}><Text variant="caption" color={color.text.muted} numberOfLines={1}>{tx(`사진: ${item.photoSource}`, `Photo: ${item.photoSource}`)}</Text></View> : null}
        </View>
        <View style={styles.cardBody}>
          <View style={styles.grow}>
            <Text weight="bold">{localPlaceName(item, language)}</Text>
            {item.address ? <Text variant="caption" color={color.text.muted}>{tx(item.address, item.addressEn ?? item.address)}</Text> : null}
            {showDistance && 'distanceM' in item ? <Text variant="caption" weight="bold" color={color.text.accent} style={styles.cardDistance}>{item.distanceM.toLocaleString()}m</Text> : null}
          </View>
          <Text variant="title" color={color.brand.orange}>›</Text>
        </View>
      </Pressable>
    ))}</View>;
  }

  // 폰 — 목록 행. 시안이 여기에 72x72 썸네일을 더했다.
  return <View style={styles.placeList}>{items.map((item) => (
        <Pressable
          key={item.placeId}
          accessibilityRole="link"
          accessibilityLabel={label(item)}
          onPress={() => open(item.placeId)}
          style={({ pressed }) => [styles.placeRow, pressed && styles.pressed]}
        >
          <PlacePhoto item={item} style={styles.rowPhoto} />
          <View style={styles.grow}>
            <Text weight="bold">{localPlaceName(item, language)}</Text>
            {item.address ? <Text variant="caption" color={color.text.muted}>{tx(item.address, item.addressEn ?? item.address)}</Text> : null}
            {item.photoSource ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{tx(`사진: ${item.photoSource}`, `Photo: ${item.photoSource}`)}</Text> : null}
          </View>
          {showDistance && 'distanceM' in item ? <Text variant="caption" weight="bold" color={color.text.accent}>{item.distanceM.toLocaleString()}m</Text> : null}
          <Text variant="title" color={color.brand.orange}>›</Text>
        </Pressable>
      ))}</View>;
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
  results: { gap: spacing[4] },
  categoryRail: { gap: spacing[2], paddingRight: spacing[4] },
  categoryChip: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  categoryChipSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  scopeSwitch: { flexDirection: 'row', padding: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.soft },
  scopeOption: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },
  scopeOptionSelected: { backgroundColor: color.brand.orange },
  placeList: { gap: spacing[2] },
  branchBody: { padding: spacing[4], paddingTop: 0, gap: spacing[2] },
  branchRetry: { alignSelf: 'flex-start' },
  expandedNotice: { padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.tint },
  placeRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], paddingVertical: spacing[2], borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.surface.border },
  grow: { flex: 1 },

  // ── 넓은 화면 ──────────────────────────────────────────────
  split: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[6] },
  sidebar: { width: 320, gap: spacing[4] },
  detail: { flex: 1, minWidth: 0 },
  pickers: { gap: spacing[4] },

  // 600~1023 — 칩이 줄바꿈된다. 숨는 것이 없어야 한다는 게 이 모양의 전부다.
  facetWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },

  // 1024~ — 세로 목록. 개수를 오른쪽 끝으로 민다.
  facetList: { gap: spacing[2] },
  facetRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },

  // 사진 — 없을 때가 더 흔하다(7~18%만 온다). 자리표시가 기본 모습이라고 보면 된다.
  photoWrap: { position: 'relative', overflow: 'hidden' },
  photoBadge: { position: 'absolute', left: spacing[2], top: spacing[2] },
  photoEmpty: { alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.soft },
  rowPhoto: { width: 72, height: 72, borderRadius: radius.md, backgroundColor: color.surface.soft },

  // 카드 격자 — minWidth 280 이 시안 값이다. flexWrap 이라 폭이 남으면 두 열, 좁으면 한 열.
  cardGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[4] },
  card: { flexGrow: 1, flexBasis: 280, minWidth: 280, borderRadius: radius.lg, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card, overflow: 'hidden' },
  cardPhotoWrap: { position: 'relative' },
  cardPhoto: { width: '100%', aspectRatio: 16 / 9, backgroundColor: color.surface.soft },
  sourcePill: { position: 'absolute', left: spacing[2], bottom: spacing[2], maxWidth: '85%', paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.85)' },
  cardBody: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], padding: spacing[4], paddingTop: spacing[3] },
  cardDistance: { marginTop: spacing[2] },
});
