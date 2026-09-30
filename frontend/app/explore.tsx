// 로컬 탐색 화면 (상세설계서 v2 P-17). 8개 갈래(축제·야시장·전통시장·액티비티
// 산책·자연·야경·기념품샵) 중 하나를 고르고, 내 근처와 부산 전체를 전환해 본다.
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Image, Linking, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Location from 'expo-location';
import { readCurrentPosition } from '@/location/currentPosition';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { PhotoCreditBar } from '@/components/PhotoCreditBar';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { ScopeSwitch } from '@/discovery/ScopeSwitch';
import { EXPLORE_GRID_GAP, exploreCardWidth } from '@/discovery/exploreGrid';
import { PhotoSubjectBadge } from '@/components/PhotoSubjectBadge';
import { color, gutter, radius, spacing } from '@/design/tokens';
import { flattenLocalFacets, getFacets, getNearbyPlaces, localFacetLabel, localPlaceName, type FacetsLoadResult, type LocalFacetEntry, type NearbyPlacesLoadResult } from '@/discovery/localExplore';
import { getPlacesByFacet, photoLabels, type PhotoSubject, type PlaceSearchItem } from '@/discovery/places';
import { useI18n } from '@/i18n';
import { romanizeKorean } from '@/discovery/romanize';
import { addressForLanguage } from '@/discovery/localAddress';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';
import { syncLocationConsent } from '@/personalization/locationConsent';
import { useLocationGate } from '@/personalization/useLocationGate';

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
  const { accessToken } = useAuth();
  const locationGate = useLocationGate(accessToken);
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
    // 🔴 위치 동의가 먼저다(S15P21E201-1691) — 「내 위치로」를 누른 것은 쓰고 싶다는 뜻이라, 거절했어도 다시 묻는다.
    if (!(await locationGate.request())) { setLocationState('denied'); return; }
    setLocationState('detecting');
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (!permission.granted) { setCanAskAgain(permission.canAskAgain); setLocationState('denied'); return; }
      const position = await readCurrentPosition();
      setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      setLocationState('granted');
    } catch {
      setLocationState('denied');
    }
  }, [locationGate.request]);

  const restoreGrantedLocation = useCallback(async () => {
    // 🔴 동의가 없으면 조용히 읽지도 않는다(S15P21E201-1691). 묻는 것은 「내 위치로」를 누를 때다.
    if ((await syncLocationConsent(accessToken)) !== true) { setLocationState('denied'); return; }
    setLocationState('detecting');
    try {
      // 화면을 둘러보기만 해도 권한 팝업부터 띄우지 않는다. 이미 허용한 사람에게만 위치를
      // 읽고, 처음이거나 거부한 사람은 부산 중심 결과를 먼저 보여 준 뒤 버튼으로 선택하게 한다.
      const permission = await Location.getForegroundPermissionsAsync();
      if (!permission.granted) { setCanAskAgain(permission.canAskAgain); setLocationState('denied'); return; }
      const position = await readCurrentPosition();
      setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      setLocationState('granted');
    } catch {
      setLocationState('denied');
    }
  }, [accessToken]);

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
  // 폭 분기. 경계값은 layout/breakpoints.ts 가 정한 셋을 그대로 쓴다
  // 이 화면에서 새 숫자를 만들지 않는다.
  // wide 600~ : 갈래 칩이 줄바꿈되고 결과가 카드 격자가 된다
  // split 1024~ : 내용 최대 폭을 넓게 연다. 배치가 둘로 갈리던 자리였는데, 왼쪽 기둥을
  //               없애고(시안 05) 지금은 폭만 정한다
  const { width, desktop } = useLayout();
  const wide = isAtLeast(width, 'md');
  // 데스크톱 판인가 — 폭만이 아니라 폴드 펼침 가로까지, 판정은 useLayout 한 곳(S15P21E201-1563).
  const split = desktop;
  const selectedFacet = visibleFacets?.find((entry) => entry.featureKey === selectedKey) ?? visibleFacets?.[0] ?? null;
  // 폰 2열 · 600~1023 3열 · 1024~ 4열. 계산은 exploreGrid 가 하고 여기서는 값만 받는다.
  const cardWidth = exploreCardWidth(width);

  return (
    <Screen scroll wide={split} style={styles.screen}>
      {/* 🔴 시안의 **탐색 데스크톱**에는 이 줄이 없다. 넓은 화면에는 사이트 머리띠가 이미
          있어서 로고가 두 번 나온다 — 알고 남긴 것이다 (2026-09-19, S15P21E201-1318).

          같은 줄을 쓰는 화면이 **열둘**이고 시안은 탐색 한 장만 그렸다. 나머지 열한 장을
          어떻게 할지는 어디에도 안 적혀 있다. **한 장을 맞추려고 열한 장을 어긋나게 하지
          않는다** — 바꾸려면 열둘을 한 번에 바꾼다. */}
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
        <BrandLogoLink href="/home" imageStyle={styles.logo} />
        <View style={styles.spacer} />
      </View>

      <View style={styles.heading}>
        <Eyebrow>{tx('로컬 탐색', 'Local explore')}</Eyebrow>
        {/* 🔴 큰 제목(hero)은 기본 글자색이 흰색이다 — 어두운 바탕 위에 쓰라고 만든 것이라서.
            색을 안 주면 아이보리 바탕에 흰 글자가 되어 아무것도 안 보인다. 타입도 시험도
            안 잡는 종류라 여기서 반드시 준다. */}
        <Text variant={wide ? 'hero' : 'display'} weight="bold" color={color.text.heading}>
          {tx('부산 로컬 둘러보기', 'Explore local Busan')}
        </Text>
        <Text color={color.text.body}>{tx('갈래를 고르고, 내 근처 또는 부산 전체에서 찾아보세요.', 'Choose a category, then search nearby or across Busan.')}</Text>
      </View>

      {loading ? (
        <View accessibilityLiveRegion="polite" style={styles.stateCard}><ActivityIndicator color={color.action.primary} /><Text color={color.text.body}>{tx('갈래를 불러오고 있어요', 'Loading categories')}</Text></View>
      ) : null}

      {!loading && result.state !== 'success' ? (
        <View accessibilityRole="alert" style={styles.stateCard}>
          <Text variant="title" weight="bold">{result.state === 'offline' ? tx('인터넷 연결을 확인해 주세요', 'Please check your internet connection') : tx('갈래를 불러오지 못했어요', 'Could not load categories')}</Text>
          <Text color={color.text.body}>{localizeMessage(tx, result.message)}</Text>
          <Button label={tx('다시 시도', 'Try again')} variant="tertiary" compact onPress={() => void load()} />
        </View>
      ) : null}

      {!loading && visibleFacets && visibleFacets.length === 0 ? (
        <View style={styles.stateCard}><Text color={color.text.body}>{tx('지금은 둘러볼 수 있는 갈래가 없어요. 자료가 들어오면 다시 열어 드릴게요.', 'No categories to explore right now — check back once new places are added.')}</Text></View>
      ) : null}

      {!loading && visibleFacets && visibleFacets.length > 0 ? (
        <View style={styles.results}>
          {/* 고르는 줄 — 갈래 칩과 범위 토글이 한 행에 있고, 스크롤해도 위에 붙어 따라온다.
              전에는 1024 부터 왼쪽 기둥(320)이 됐는데, 그 폭만큼 정작 보러 온 결과가 좁아졌다.
              갈래는 여덟 개뿐이라 한 줄에 들어간다 (시안 05).
          */}
          <View style={[styles.filterBar, wide ? styles.filterBarWide : styles.filterBarPhone]}>
            <FacetPicker
              facets={visibleFacets}
              selectedKey={selectedFacet?.featureKey ?? null}
              onSelect={setSelectedKey}
              mode={wide ? 'wrap' : 'rail'}
              language={language}
            />
            <ScopeSwitch
              options={[
                { value: 'nearby', label: tx('내 근처', 'Nearby') },
                { value: 'all', label: tx('부산 전체', 'All Busan') },
              ]}
              value={scope}
              onChange={setScope}
              style={wide ? styles.scopeWide : undefined}
            />
          </View>

          {/* 결과 머리 — 안내 한 줄이 여기 붙는다. 고르는 자리에 있으면 「무엇을 고를까」를
              보는 동안 읽히는데, 정작 필요한 때는 결과를 보며 「왜 이만큼만 나오지」를
              물을 때다 (시안 05). */}
          {selectedFacet ? (
            <View style={styles.resultHead}>
              <Text variant="display" weight="bold">
                {localFacetLabel(selectedFacet, language, tx)}{' '}
                <Text variant="display" weight="bold" color={color.text.muted}>{tx(`${selectedFacet.placeCount}곳`, `${selectedFacet.placeCount} places`)}</Text>
              </Text>
              <Text variant="caption" style={styles.resultNote}>
                {scope === 'all'
                  ? tx('부산 전체는 거리 제한 없이 찾아요.', 'All Busan searches without a distance limit.')
                  : tx('내 근처는 반경 안에서 찾아요. 결과의 검색 범위를 확인하거나 부산 전체로 바꿔 보세요.', 'Nearby searches within a radius. Check the range shown with results, or switch to All Busan.')}
              </Text>
            </View>
          ) : null}

          {selectedFacet ? <LocalBranchList facet={selectedFacet} scope={scope} coords={coords} canAskAgain={canAskAgain} onRetryLocation={() => void detectLocation()} cardWidth={cardWidth} /> : null}
        </View>
      ) : null}
      {locationGate.sheet}
    </Screen>
  );
}

// 열린 갈래 하나의 장소 목록 — GET /api/v1/places/nearby를 그 갈래를 열 때만
// 부른다(화면 진입 시 8개를 한꺼번에 안 부르는 완료 기준). 한 갈래의 실패가 나머지 일곱 갈래를
// 막지 않도록, 이 컴포넌트 안에서만 상태를 갖는다.
/** 내 위치를 못 쓸 때의 기준점 — S15P21E201-982. 부산 시청이다. */
const BUSAN_CENTER = { lat: 35.1796, lng: 129.0756 };

/** 갈래를 고르는 자리 — 폭에 따라 세 모양, 한 자료** */
function FacetPicker({ facets, selectedKey, onSelect, mode, language }: {
  facets: LocalFacetEntry[];
  selectedKey: string | null;
  onSelect: (key: string) => void;
  mode: 'rail' | 'wrap';
  /**
   * useI18n 의 language 를 그대로 받는다. 문자열로 느슨하게 두면 localFacetLabel 이
   * 받는 다섯 언어 말고 아무 값이나 들어올 수 있어, 그 자리에서 타입으로 묶는다.
   */
  language: Parameters<typeof localFacetLabel>[1];
}) {
  const { tx } = useI18n();
  // 🔴 고른 갈래가 칩 줄 밖에 숨어 있으면 무엇을 보고 있는지 모른다 — 홈의 「자연」 카드로 들어오면 첫 화면에
  //    「전통시장 · 축제 · 액티비티」만 보이고 고른 「자연」은 오른쪽 밖이었다. 고른 칩이 보이게 줄을 옮긴다.
  const railRef = useRef<ScrollView>(null);
  const chipX = useRef<Record<string, number>>({});
  const scrollToSelected = () => {
    const x = selectedKey ? chipX.current[selectedKey] : undefined;
    if (x !== undefined) railRef.current?.scrollTo({ x: Math.max(0, x - gutter), animated: false });
  };
  useEffect(scrollToSelected, [selectedKey]);
  const chips = facets.map((entry) => {
    const selected = entry.featureKey === selectedKey;
    const label = localFacetLabel(entry, language, tx);
    return (
      <Pressable key={entry.featureKey} onLayout={(event) => { chipX.current[entry.featureKey] = event.nativeEvent.layout.x; if (selected) scrollToSelected(); }} accessibilityRole="button" accessibilityState={{ selected }} onPress={() => onSelect(entry.featureKey)} style={[styles.categoryChip, selected && styles.categoryChipSelected]}>
        {/* 개수는 이름과 다른 굵기·흐린 색으로 둔다 — 「축제 12」가 한 덩어리로 읽히면
            12가 이름의 일부처럼 보인다 (시안 05). */}
        <Text weight="bold" numberOfLines={1} color={selected ? color.text.onAction : color.text.heading}>{selected ? '✓ ' : ''}{label}</Text>
        <Text variant="caption" weight="bold" numberOfLines={1} color={selected ? color.text.onAction : color.text.muted} style={styles.chipCount}>{entry.placeCount}</Text>
      </Pressable>
    );
  });
  if (mode === 'rail') {
    return <ScrollView ref={railRef} horizontal showsHorizontalScrollIndicator={false} style={styles.categoryRailBleed} contentContainerStyle={styles.categoryRail}>{chips}</ScrollView>;
  }
  return <View style={styles.facetWrap}>{chips}</View>;
}

function LocalBranchList({ facet, scope, coords, canAskAgain, onRetryLocation, cardWidth }: {
  facet: LocalFacetEntry;
  scope: ExploreScope;
  coords: { latitude: number; longitude: number } | null;
  canAskAgain: boolean;
  onRetryLocation: () => void;
  /** 한 장의 폭. 열 수는 이 값이 정한다. */
  cardWidth: number;
}) {
  const { tx } = useI18n();
  const [result, setResult] = useState<NearbyPlacesLoadResult | null>(null);
  const [allItems, setAllItems] = useState<PlaceSearchItem[] | null>(null);
  const [allError, setAllError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // 위치를 모르면 부산 중심으로 찾는다 — S15P21E201-982.
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
    return <View accessibilityLiveRegion="polite" style={styles.branchBody}><ActivityIndicator color={color.action.primary} /></View>;
  }
  if (scope === 'all') {
    if (allError) return <View style={styles.branchBody}><Text color={color.text.body}>{allError}</Text></View>;
    if (!allItems?.length) return <View style={styles.branchBody}><Text color={color.text.body}>{tx('부산 전체에서도 이 갈래의 장소를 찾지 못했어요.', 'No places in this category were found across Busan.')}</Text></View>;
    return <PlaceRows items={allItems} cardWidth={cardWidth} />;
  }
  if (!result) return null;
  if (result.state !== 'success') {
    return <View style={styles.branchBody}>
      <Text color={color.text.body}>{localizeMessage(tx, result.message)}</Text>
      <Button label={tx('다시 시도', 'Try again')} variant="tertiary" onPress={() => void getNearbyPlaces({ lat: center.lat, lng: center.lng, facetKey: facet.featureKey }).then(setResult)} compact containerStyle={styles.branchRetry} />
    </View>;
  }
  if (result.items.length === 0) {
    // : 갈래 배지 개수(부산 전체 집계)와 이 목록(반경 안 검색)은 서로 다른
    // 걸 잰다 — 배지에 숫자가 있어도 반경 안에는 없을 수 있다. "아예 없다"처럼 읽히지
    // 않도록 실제로 넓혀 본 반경을 밝힌다.
    const radiusKm = (result.effectiveRadiusM / 1000).toLocaleString(undefined, { maximumFractionDigits: 1 });
    return <View style={styles.branchBody}><Text color={color.text.body}>{txf(tx, '%skm 이내에는 이 갈래의 장소가 없어요. 부산 전체에는 있을 수 있어요.', 'No places in this category within %skm. There may be some elsewhere in Busan.', radiusKm)}</Text></View>;
  }
  return (
    <View style={styles.branchBody}>
      <Text variant="caption">{txf(tx, '검색 범위: %skm 이내', 'Search range: within %s km', (result.effectiveRadiusM / 1000).toLocaleString())}</Text>
      {/* 내 위치를 못 쓴 채 부산 중심으로 찾았다는 사실을 밝힌다 — S15P21E201-982.
          조용히 대신 보여 주면 거리 숫자가 왜 이런지 설명이 안 된다. 권한을 다시 물을
          길도 여기 같이 둔다.
      */}
      {usingFallback && (
        <View style={styles.expandedNotice}>
          <Text variant="caption" weight="bold" color={color.state.info}>{tx('내 위치를 몰라 부산 중심에서 찾았어요. 거리도 그 기준이에요.', 'We searched from the center of Busan because your location is unavailable. Distances use that point.')}</Text>
          {canAskAgain
            ? <Button label={tx('내 위치로 다시 찾기', 'Search from my location')} variant="tertiary" onPress={onRetryLocation} compact containerStyle={styles.branchRetry} />
            : <Button label={tx('설정에서 위치 허용하기', 'Allow location in Settings')} variant="tertiary" onPress={() => void Linking.openSettings()} compact containerStyle={styles.branchRetry} />}
        </View>
      )}
      {result.radiusExpanded && (
        <View style={styles.expandedNotice}><Text variant="caption" weight="bold" color={color.state.info}>{tx(`반경을 ${result.effectiveRadiusM.toLocaleString()}m로 넓혔습니다`, `Widened the search radius to ${result.effectiveRadiusM.toLocaleString()}m`)}</Text></View>
      )}
      <PlaceRows items={result.items} showDistance cardWidth={cardWidth} />
    </View>
  );
}

/** 사진 자리 — 값이 있으면 사진, 없으면 핀 하나 */
function PlacePhoto({ item, style }: { item: { photoUrl?: string | null; photoSubject?: PhotoSubject | null }; style: object }) {
  if (item.photoUrl) {
    // 사진 위에 「무엇을 찍은 것인가」를 얹으려고 감싼다 — S15P21E201-1206.
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

function PlaceRows({ items, showDistance = false, cardWidth }: {
  items: Array<PlaceSearchItem | import('@/discovery/localExplore').NearbyPlaceItem>;
  showDistance?: boolean;
  /** 한 장의 폭. 열 수는 이 값이 정한다 — exploreGrid 가 계산한다. */
  cardWidth: number;
}) {
  const router = useRouter();
  const { tx, language } = useI18n();
  const open = (placeId: string) => router.push(`/place/${placeId}`);
  const label = (item: PlaceSearchItem | import('@/discovery/localExplore').NearbyPlaceItem) =>
    txf(tx, '%s 상세 보기', 'View details for %s', localPlaceName(item, language));

  // 폰이든 데스크톱이든 같은 격자다. 열 수만 폭이 정한다 (시안 05·06).
  // 두 벌로 만들면 한쪽만 고쳐지고, 그 차이는 두 폭을 나란히 열어 봐야만 보인다.
  return <View style={styles.cardGrid}>{items.map((item) => (
    <Pressable
      key={item.placeId}
      accessibilityRole="link"
      accessibilityLabel={label(item)}
      onPress={() => open(item.placeId)}
      style={({ pressed }) => [styles.card, { width: cardWidth }, pressed && styles.pressed]}
    >
      <View style={styles.cardPhotoWrap}>
        <PlacePhoto item={item} style={styles.cardPhoto} />
        {/* 🔴 사진 출처는 꾸밈이 아니라 이용 조건이다. 사진을 그리면 반드시 함께 그리고,
            문구는 서버가 준 값을 쓴다 — 지어내지 않는다. */}
        {/* 위키미디어 사진은 라이선스 이름을 덧붙이고, 누르면 파일 페이지가 열린다(S15P21E201-1610). */}
        {/* 홈 카드와 같은 띠(S15P21E201-1682) — 전에는 흰 알약에 흐린 글자라 어두운 사진 위에서 안 읽혔고, 한 줄에서 잘려 이용 조건이 안 보였다. */}
        {item.photoSource ? <PhotoCreditBar source={item.photoSource} license={item.photoLicense?.name ?? null} licenseUrl={photoLabels(item, tx).licenseUrl} tx={tx} /> : null}
      </View>
      <View style={styles.cardBody}>
        <Text weight="bold" numberOfLines={1}>{localPlaceName(item, language)}</Text>
        {/* 번역 이름이 없어 한글로 남은 이름엔 읽는 법을 곁들인다(S15P21E201-1867) — 외국어 화면에서 「부산연등회」만 있으면 읽지도 묻지도 못한다. */}
        {language !== 'ko' && localPlaceName(item, language) === item.nameKo && romanizeKorean(item.nameKo) ? <Text variant="caption" color={color.text.body} numberOfLines={1}>{romanizeKorean(item.nameKo)}</Text> : null}
        {item.address ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{addressForLanguage(item, language)}</Text> : null}
        {/* 🔴 거리는 「내 근처」일 때만. 부산 전체로 찾을 때는 거리 기준이 없어서,
            그리면 없는 기준을 있는 것처럼 보여준다. */}
        {showDistance && 'distanceM' in item ? <Text variant="caption" weight="bold" color={color.text.accent}>{item.distanceM.toLocaleString()}m</Text> : null}
      </View>
    </Pressable>
  ))}</View>;
}

const styles = StyleSheet.create({
  screen: { backgroundColor: color.canvas },
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  logo: { width: 154, height: 28 },
  spacer: { width: 44 },
  heading: { gap: spacing[2], marginBottom: spacing[6] },
  stateCard: { gap: spacing[3], marginTop: spacing[4], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  results: { gap: spacing[4] },
  categoryRail: { gap: spacing[2], paddingHorizontal: gutter },
  categoryRailBleed: { marginHorizontal: -gutter },
  categoryChip: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  categoryChipSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  branchBody: { padding: spacing[4], paddingTop: 0, gap: spacing[2] },
  branchRetry: { alignSelf: 'flex-start' },
  expandedNotice: { padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.tint },

  // ── 고르는 줄 ──────────────────────────────────────────────
  //
  // 🔴 스크롤을 내려도 위에 붙어 따라온다(웹 전용). 결과를 훑다 갈래를 바꾸려고 맨 위까지
  //    되돌아가는 일이 없게. position: 'sticky' 는 웹에만 있는 값이라 RN 타입에 없다.
  filterBar: {
    gap: spacing[3],
    paddingVertical: spacing[3],
    ...(Platform.OS === 'web'
      ? ({ position: 'sticky', top: 0, zIndex: 10 } as object)
      : null),
    // 🔴 화면 바탕과 같은 색 — 흰색(brand.ivory)이면 폰에서 여백 안쪽에 흰 상자가 떠 보였고, 칩 줄이 그 상자 끝에서 잘렸다.
    backgroundColor: color.canvas,
    borderBottomWidth: 1,
    borderBottomColor: color.surface.border,
  },
  // 폰 — 칩 줄이 화면 끝까지 흐른다. 여백 안에서 잘리면 끝 칩이 반쯤 걸려 「고장」처럼 보인다.
  filterBarPhone: { marginHorizontal: -gutter, paddingHorizontal: gutter },
  // 넓으면 칩과 토글이 한 행에 선다. 좁으면 칩 레일 아래에 토글이 온다.
  filterBarWide: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[4] },
  scopeWide: { width: 232 },

  // 결과 머리 — 제목과 안내 한 줄.
  // 🔴 안내 한 줄은 제목 «아래». 오른쪽에 붙이면 폰에서 제목 밑줄과 어긋난 채 두 줄로 꺾여 떠 보였다.
  resultHead: { gap: spacing[1] },
  resultNote: { color: color.text.muted },

  // 칩이 줄바꿈된다. 숨는 것이 없어야 한다는 게 이 모양의 전부다.
  // 🔴 flex 1 · minWidth 0 — 토글을 뺀 남는 폭만 쓴다. 안 주면 줄지 않고 한 줄로 늘어나, 영어판에서
  //    범위 토글이 오른쪽 밖으로 밀렸다(S15P21E201-1703 — 1280 에서 「All Busan」, 1024 에서 「Nearby」까지).
  facetWrap: { flex: 1, minWidth: 0, flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chipCount: { opacity: 0.75 },

  // 사진 — 없을 때가 더 흔하다(7~18%만 온다). 자리표시가 기본 모습이라고 보면 된다.
  photoWrap: { position: 'relative', overflow: 'hidden' },
  photoBadge: { position: 'absolute', left: spacing[2], top: spacing[2] },
  photoEmpty: { alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.soft },

  // 카드 격자 — 열 수는 exploreGrid 가 폭에서 정한다(폰 2 · 600~ 3 · 1024~ 4).
  //
  // 🔴 테두리도 바탕도 없다. 사진과 글만 둔다(시안 05·06). 정사각 사진이 줄을 맞추면
  //    테두리가 하는 일이 없고, 네 열에서는 테두리 여덟 줄이 사진보다 먼저 눈에 든다.
  cardGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: EXPLORE_GRID_GAP },
  card: { gap: spacing[1] },
  cardPhotoWrap: { position: 'relative', overflow: 'hidden', borderRadius: radius.md },
  cardPhoto: { width: '100%', aspectRatio: 1, borderRadius: radius.md, backgroundColor: color.surface.soft },
  cardBody: { gap: 2, paddingTop: spacing[1] },
});
