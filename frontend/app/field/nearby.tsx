// 근처 편의시설 — 주차장 · 약국 · 화장실 · 짐보관 네 탭 (S15P21E201-2052). AI 챗봇의 도구 칸에서 들어온다.
//
// 여행자가 내 위치(또는 위치를 못 쓸 때 부산 중심 서면)에서 가까운 곳을 찾는다. 자료는 서버(GET /api/v1/facilities/nearby)가 주고,
// 정렬도 서버가 한다 — 주차장은 예상요금이 싼 순(요금을 모르는 곳은 맨 뒤), 약국은 지금 연 곳이 먼저, 나머지는 가까운 순.
//
// 🔴 위치는 explore.tsx · nearby-help.tsx 와 같은 길이다(S15P21E201-1691). 들어올 때 이미 허락한 사람만 조용히 읽고, 처음이거나 거절한
//    사람에게는 창을 띄우지 않는다. 대신 서면 기준 결과를 먼저 보여 주고 «내 위치로 다시 찾기»로 묻는다.
//    위치를 못 쓴 채 찾았다는 사실을 화면이 밝힌다 — 거리 숫자가 왜 이런지 설명이 안 되면 안 된다.
// 🔴 서면에서 찾은 곳으로 «길 안내»를 열지 않는다. 내 위치가 아닌 출발지에서 찾은 길은 틀린다(DestinationPicker 와 같은 이유).
//    그때는 지도 앱으로 그 자리만 연다.
// 🔴 «장소 기준»으로도 열린다 — 장소 상세의 「근처 주차장」 줄이 `?lat=&lng=&name=&kind=PARKING&stay=1` 로 연다(S15P21E201-2052).
//    이때는 내 위치를 읽지도 묻지도 않는다(그 장소 근처가 궁금한 것이지 내 근처가 아니다). 화면이 «「이름」 근처에서 찾았다 · 거리도 그
//    장소에서 잰 것»이라고 밝히고, «내 위치로 찾기»를 눌러야 비로소 위치를 묻는다. 길 안내도 안 연다 — 출발지가 내가 아니라 그 장소다.
// 🔴 지도 — 목록 바로 위에 가까운 도움(nearby-help.tsx)과 같은 지도 상자(RouteMap, 높이 220)를 둔다(S15P21E201-2052). 목록의 곳마다 번호 점이 있고,
//    카드를 누르면 지도에서 그 점이, 지도의 점을 누르면 그 카드가 골라진다. 가는 법은 카드 아래의 단추가 맡는다.
//    기준점: 내 위치로 찾았으면 지도의 «현재 위치» 점이 곧 기준이고, 장소 기준이거나 위치를 못 써 서면에서 찾았으면 그 자리를 «기준점»으로 찍는다.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Linking, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Location from 'expo-location';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { ChipLabel } from '@/components/ChipLabel';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { telOf } from '@/field/nearbyHelp';
import {
  DEFAULT_RADIUS_M,
  DEFAULT_STAY_HOURS,
  FACILITY_KINDS,
  getNearbyFacilities,
  SEOMYEON,
  STAY_HOURS_CHOICES,
  WIDE_RADIUS_M,
  type FacilityItem,
  type FacilityKind,
  type LatLng,
  type NearbyFacilities,
} from '@/field/nearbyFacilities';
import { ExchangeCard, LockerCard, ParkingCard, PharmacyCard, ToiletCard, type FacilityActions, type ListLink } from '@/field/NearbyFacilityCards';
import { distanceText } from '@/field/subwayStations';
import { useI18n } from '@/i18n';
import { enPlural, txf } from '@/i18n/format';
import { readCurrentPosition } from '@/location/currentPosition';
import { RouteMap, type MapPointLayer, type MapRouteLayer } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { syncLocationConsent } from '@/personalization/locationConsent';
import { useLocationGate } from '@/personalization/useLocationGate';
import { NearAreaPicker, type NearArea } from '@/field/NearAreaPicker';
import { listAvailableMapApps } from '@/utils/externalMaps';

/** 이만큼 가까우면 걸어서 가는 길로 연다(nearby-help 와 같다). */
const WALK_ROUTE_M = 1200;
/**
 * 지도에 줄 «빈 값»들 — 렌더마다 새 배열을 만들면 지도 effect 가 렌더마다 다시 돈다(nearby-help.tsx 와 같은 까닭).
 * 이 지도는 경로가 아니다 — 번호 점을 선으로 잇지 않으려고 routes 에 빈 배열을 준다(안 주면 점을 순서대로 잇는다).
 */
const NO_ITEMS: FacilityItem[] = [];
const NO_POINTS: MapPointLayer[] = [];
const NO_ROUTES: MapRouteLayer[] = [];

/** idle — 아직 위치를 읽으려 하지 않았다(장소 기준으로 열렸을 때). */
type LocationState = 'idle' | 'detecting' | 'granted' | 'denied';
// pick — 내 위치를 못 쓰고 고른 자리도 없다. 🔴 서면으로 넘어가지 않고 «어디 근처를 볼까요?»를 묻는다(S15P21E201-2069).
type Load = { status: 'loading' } | { status: 'pick' } | { status: 'error' } | { status: 'ready'; data: NearbyFacilities };
/** 장소 상세에서 «이 장소 근처»로 열렸을 때의 기준점. */
type Anchor = LatLng & { name: string | null };

const TAB_EMOJI: Record<FacilityKind, string> = { PARKING: '🅿️', PHARMACY: '💊', TOILET: '🚻', LOCKER: '🧳', EXCHANGE: '💱' };

function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/** `?kind=pharmacy` 로 특정 탭에서 열 수 있게 — 대소문자를 가리지 않는다. 모르는 값은 첫 탭. */
function kindFromParam(value: string | string[] | undefined): FacilityKind | null {
  const raw = firstParam(value)?.toUpperCase();
  return FACILITY_KINDS.find((kind) => kind === raw) ?? null;
}

/**
 * `?lat=&lng=&name=` — 둘 다 올바른 좌표일 때만 «장소 기준»이다. 하나라도 비었거나 숫자가 아니거나 지구 밖이면 없는 것으로 친다(내 위치 길).
 * 🔴 빈 문자열은 `Number('') === 0` 이라 따로 거른다 — 안 거르면 «적도·본초자오선»에서 찾는다.
 */
function anchorFromParams(latValue: string | string[] | undefined, lngValue: string | string[] | undefined, nameValue: string | string[] | undefined): Anchor | null {
  const rawLat = firstParam(latValue)?.trim();
  const rawLng = firstParam(lngValue)?.trim();
  if (!rawLat || !rawLng) return null;
  const latitude = Number(rawLat);
  const longitude = Number(rawLng);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
  if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180 || (latitude === 0 && longitude === 0)) return null;
  // 이름은 화면에 그대로 찍히니 길이를 막는다 — 주소창으로 들어온 값일 수 있다.
  const name = firstParam(nameValue)?.trim().slice(0, 60);
  return { latitude, longitude, name: name ? name : null };
}

/** `?stay=1` — 머무는 시간 칩의 시작값. 칩에 없는 값은 기본값. */
function stayFromParam(value: string | string[] | undefined): number | null {
  const hours = Number(firstParam(value));
  return STAY_HOURS_CHOICES.includes(hours) ? hours : null;
}

export default function NearbyFacilitiesScreen() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken, ready } = useAuth();
  const locationGate = useLocationGate(accessToken);
  const params = useLocalSearchParams<{ kind?: string; lat?: string; lng?: string; name?: string; stay?: string }>();
  const paramAnchor = useMemo(() => anchorFromParams(params.lat, params.lng, params.name), [params.lat, params.lng, params.name]);
  // 「어디 근처를 볼까요?」에서 고른 자리 — 주소로 넘어온 장소보다 앞선다(S15P21E201-2069).
  const [chosen, setChosen] = useState<Anchor | null>(null);
  const anchor = chosen ?? paramAnchor;
  // 장소 기준으로 열렸나 — «내 위치로 찾기»가 성공하면 꺼진다(그 뒤로는 내 위치 기준). 처음부터 장소 기준이면 내 위치를 읽으려 하지 않는다.
  const startedAnchored = useRef(paramAnchor !== null);
  const [anchored, setAnchored] = useState(paramAnchor !== null);
  const [kind, setKind] = useState<FacilityKind>(kindFromParam(params.kind) ?? 'PARKING');
  const [stayHours, setStayHours] = useState(stayFromParam(params.stay) ?? DEFAULT_STAY_HOURS);
  const [accessibleOnly, setAccessibleOnly] = useState(false);
  const [diaperOnly, setDiaperOnly] = useState(false);
  const [radiusM, setRadiusM] = useState(DEFAULT_RADIUS_M);
  // 2km 안에도 없을 때 «서면 기준으로 찾기» — 시외에서 열면 빈 화면에서 갈 곳이 없었다.
  const [atSeomyeon, setAtSeomyeon] = useState(false);
  const [coords, setCoords] = useState<LatLng | null>(null);
  const [locationState, setLocationState] = useState<LocationState>(paramAnchor ? 'idle' : 'detecting');
  const [canAskAgain, setCanAskAgain] = useState(true);
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [reloadKey, setReloadKey] = useState(0);
  // 사람이 누른 곳(목록 카드나 지도의 점). 지금 목록에 없으면 첫 곳이 골라진 것으로 본다 — 아래 selectedId.
  const [pickedId, setPickedId] = useState('');

  // ── 위치 ────────────────────────────────────────────────────────────────

  // 이미 허락한 사람만 조용히 읽는다. 처음이거나 거절한 사람에게는 창을 띄우지 않는다 — 묻는 것은 «내 위치로 다시 찾기»를 누를 때.
  const restore = useCallback(async () => {
    if ((await syncLocationConsent(accessToken)) !== true) { setLocationState('denied'); return; }
    try {
      const permission = await Location.getForegroundPermissionsAsync();
      if (!permission.granted) { setCanAskAgain(permission.canAskAgain); setLocationState('denied'); return; }
      const position = await readCurrentPosition();
      setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      setLocationState('granted');
    } catch {
      setLocationState('denied');
    }
  }, [accessToken]);

  /** 내 위치를 얻었으면 true. 거절·실패면 false. */
  const ask = useCallback(async (): Promise<boolean> => {
    if (!(await locationGate.request())) { setLocationState('denied'); return false; }
    const current = await Location.getForegroundPermissionsAsync();
    // 다시 물을 수 없으면 설정으로 — 눌러도 아무 일도 안 일어나는 버튼이 제일 나쁘다.
    if (!current.granted && !current.canAskAgain) { setCanAskAgain(false); void Linking.openSettings(); return false; }
    setLocationState('detecting');
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (!permission.granted) { setCanAskAgain(permission.canAskAgain); setLocationState('denied'); return false; }
      const position = await readCurrentPosition();
      setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      setLocationState('granted');
      setRadiusM(DEFAULT_RADIUS_M);
      return true;
    } catch {
      setLocationState('denied');
      return false;
    }
  }, [locationGate.request]);

  // 장소 기준에서 «내 위치로 찾기» — 위치를 얻는 데 성공해야 기준을 옮긴다. 거절하면 그 장소 기준 그대로다(서면으로 튀지 않는다).
  const switchToMyLocation = useCallback(async () => {
    if (await ask()) { setAnchored(false); setAtSeomyeon(false); setChosen(null); }
  }, [ask]);

  // 고른 자리로 기준을 옮긴다 — 반경은 처음 값으로, 서면 기준은 끈다.
  const pickArea = useCallback((area: NearArea) => {
    setChosen({ latitude: area.latitude, longitude: area.longitude, name: area.name });
    setAnchored(true);
    setAtSeomyeon(false);
    setRadiusM(DEFAULT_RADIUS_M);
    setPickedId('');
  }, []);

  // 처음부터 장소 기준이면 내 위치를 읽지 않는다 — 그 장소 근처가 궁금한 것이고, 위치 창은 «내 위치로 찾기»를 눌렀을 때만 뜬다.
  useEffect(() => { if (ready && !startedAnchored.current) void restore(); }, [ready, restore]);

  // 장소 기준이면 그 장소에서, 아니면 내 위치에서 — 내 위치를 모르면 서면에서 찾는다.
  const origin: LatLng = useMemo(
    () => (atSeomyeon ? SEOMYEON : anchored && anchor ? { latitude: anchor.latitude, longitude: anchor.longitude } : coords ?? SEOMYEON),
    [atSeomyeon, anchored, anchor, coords],
  );
  const usingFallback = !anchored && locationState === 'denied';
  // 장소 기준일 때는 위치를 읽는 중이어도 기다리지 않는다 — 읽는 것은 «내 위치로 찾기» 뒤의 일이고 지금 보이는 목록과 상관없다.
  const locating = !anchored && locationState === 'detecting';

  // ── 서버 ────────────────────────────────────────────────────────────────

  useEffect(() => {
    // 위치를 읽는 동안에는 묻지 않는다 — 서면으로 한 번 찾고 내 위치로 또 찾는 두 번 호출을 피한다.
    if (!ready || locating) return;
    if (usingFallback) { setLoad({ status: 'pick' }); return; }
    const controller = new AbortController();
    let alive = true;
    setLoad({ status: 'loading' });
    getNearbyFacilities(kind, origin, accessToken, {
      radiusM,
      stayMinutes: kind === 'PARKING' ? stayHours * 60 : undefined,
      accessible: kind === 'TOILET' && accessibleOnly,
      diaper: kind === 'TOILET' && diaperOnly,
      // 장소의 자리는 줄이지 않고 보낸다 — 100m 로 줄이는 것은 «내 위치»를 가리려는 약속이다.
      exact: anchored && !atSeomyeon,
      signal: controller.signal,
    }).then((data) => {
      if (alive) setLoad({ status: 'ready', data });
    }).catch(() => {
      if (alive) setLoad({ status: 'error' });
    });
    return () => { alive = false; controller.abort(); };
  }, [ready, locating, usingFallback, anchored, kind, origin, accessToken, radiusM, stayHours, accessibleOnly, diaperOnly, reloadKey]);

  const selectKind = (next: FacilityKind) => {
    if (next === kind) return;
    setKind(next);
    setRadiusM(DEFAULT_RADIUS_M);
    setPickedId('');
  };

  // ── 지도 ────────────────────────────────────────────────────────────────

  // 지도에 줄 번호 점 — 목록과 같은 곳, 같은 순서, 같은 번호(카드의 번호 핀과 맞는다).
  const items = load.status === 'ready' ? load.data.items : NO_ITEMS;
  const stops: MapStop[] = useMemo(
    () => items.map((item, index) => ({ id: item.id, number: index + 1, name: item.name, latitude: item.lat, longitude: item.lng })),
    [items],
  );
  // 🔴 고른 곳은 목록과 «같은 렌더에서» 정한다 — effect 로 한 렌더 늦게 고르면 그 «고른 곳 바뀜»이 지도의 「고르면 확대」 규칙을 건드려,
  //    전체를 맞춘 직후 1번으로 확대한다(가까운 도움 nearby-help.tsx 에서 운영 캡처로 겪었다, S15P21E201-1889).
  const selectedId = stops.some((stop) => stop.id === pickedId) ? pickedId : (stops[0]?.id ?? '');
  // 거리를 어디서 쟀나 — 내 위치로 찾았으면 지도의 «현재 위치» 점이 곧 기준이다. 장소 기준이거나 위치를 못 써 서면에서 찾았으면
  // 그 자리(origin)를 «기준점»으로 찍는다. 그 자리는 목록의 점이 아니라서 번호 장소가 아닌 점 표시(points)로 준다.
  const showReference = anchored || usingFallback || atSeomyeon;
  const referenceName = anchored && !atSeomyeon ? (anchor?.name ?? '') : tx('서면', 'Seomyeon');
  const referenceLabel = tx('기준점', 'Origin');
  const points: MapPointLayer[] = useMemo(
    () => (showReference
      ? [{ id: 'reference', label: referenceLabel, color: color.state.info, stops: [{ id: 'reference', number: 0, name: referenceName, latitude: origin.latitude, longitude: origin.longitude }] }]
      : NO_POINTS),
    [showReference, referenceLabel, referenceName, origin],
  );
  const currentLocation = anchored ? null : coords;

  // ── 가는 법 ─────────────────────────────────────────────────────────────

  // 길 안내는 «내 위치에서 찾은» 결과에만 — 장소 기준(출발지가 그 장소)이나 서면 기준에서 찾은 길은 내 길이 아니다.
  const actionsFor = (item: FacilityItem): FacilityActions => ({
    onRoute: coords && !anchored && !atSeomyeon
      ? () => router.push({ pathname: '/route-detail', params: {
        originLat: String(coords.latitude), originLng: String(coords.longitude), originName: tx('내 위치', 'My location'),
        destLat: String(item.lat), destLng: String(item.lng), destName: item.name,
        ...(item.distanceM <= WALK_ROUTE_M ? { mode: 'WALK' } : {}),
      } })
      : null,
    onMap: () => {
      void listAvailableMapApps({ name: item.name, latitude: item.lat, longitude: item.lng })
        .then((apps) => apps[0]?.open())
        .catch(() => undefined);
    },
  });
  const call = useCallback((tel: string) => { void Linking.openURL(telOf(tel)); }, []);

  const renderCard = (item: FacilityItem, index: number) => {
    const actions = actionsFor(item);
    // 지도의 번호 점과 짝 — 카드를 누르면 지도에서 그 점이 골라진다(가는 법은 카드 아래 단추).
    const link: ListLink = { number: index + 1, selected: item.id === selectedId, onSelect: () => setPickedId(item.id) };
    switch (item.kind) {
      case 'PARKING': return <ParkingCard key={item.id} item={item} actions={actions} link={link} stayHours={stayHours} />;
      case 'PHARMACY': return <PharmacyCard key={item.id} item={item} actions={actions} link={link} onCall={call} />;
      case 'EXCHANGE': return <ExchangeCard key={item.id} item={item} actions={actions} link={link} onCall={call} />;
      case 'TOILET': return <ToiletCard key={item.id} item={item} actions={actions} link={link} />;
      case 'LOCKER': return <LockerCard key={item.id} item={item} actions={actions} link={link} />;
    }
  };

  // ── 글 ──────────────────────────────────────────────────────────────────

  const tabLabel = (each: FacilityKind) => {
    switch (each) {
      case 'PARKING': return tx('주차장', 'Parking');
      case 'PHARMACY': return tx('약국', 'Pharmacy');
      case 'EXCHANGE': return tx('환전소', 'Currency exchange');
      case 'TOILET': return tx('화장실', 'Restrooms');
      case 'LOCKER': return tx('짐보관', 'Lockers');
    }
  };
  const sortNote = (() => {
    switch (kind) {
      case 'PARKING': return tx('요금이 싼 순이에요. 요금을 모르는 곳은 맨 뒤에 있어요.', 'Cheapest first. Lots with unknown fees come last.');
      case 'PHARMACY': return tx('지금 문 연 곳이 먼저예요.', 'Pharmacies open now come first.');
      default: return tx('가까운 순이에요.', 'Nearest first.');
    }
  })();

  const back = () => (router.canGoBack() ? router.back() : router.replace('/chat'));

  return (
    <Screen scroll>
      <View style={styles.top}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={back} style={styles.back}><Text variant="title">‹</Text></Pressable>
        <BrandLogoLink imageStyle={styles.logo} />
        <View style={styles.spacer} />
      </View>
      <Text variant="display" weight="bold">{tx('근처 편의시설', 'Nearby facilities')}</Text>
      <Text color={color.text.body} style={styles.lead}>{tx('가까운 주차장 · 환전소 · 화장실 · 짐보관함을 찾아 드려요.', 'Find nearby parking, currency exchanges, restrooms and luggage lockers.')}</Text>

      <View accessibilityRole="tablist" accessibilityLabel={tx('편의시설 종류', 'Facility type')} style={styles.tabs}>
        {FACILITY_KINDS.map((each) => {
          const selected = each === kind;
          return (
            <Pressable key={each} testID={`facility-tab-${each}`} accessibilityRole="tab" accessibilityState={{ selected }} accessibilityLabel={tabLabel(each)} onPress={() => selectKind(each)} style={[styles.tab, selected && styles.tabOn]}>
              <Text variant="title" style={styles.tabEmoji}>{TAB_EMOJI[each]}</Text>
              <ChipLabel variant="caption" color={selected ? color.text.onAction : color.text.heading}>{tabLabel(each)}</ChipLabel>
            </Pressable>
          );
        })}
      </View>

      {anchored && anchor ? (
        <View testID="facility-anchor-notice" style={styles.notice}>
          <Text variant="caption" weight="bold" color={color.state.info}>
            {anchor.name
              ? txf(tx, '「%s」 근처에서 찾았어요. 거리도 그 장소에서 잰 거예요.', 'Searched near “%s”. Distances are measured from that place.', anchor.name)
              : tx('고른 장소 근처에서 찾았어요. 거리도 그 장소에서 잰 거예요.', 'Searched near the place you chose. Distances are measured from that place.')}
          </Text>
          <Button testID="facility-search-my-location" label={tx('내 위치로 찾기', 'Search from my location')} variant="tertiary" compact onPress={() => void switchToMyLocation()} />
        </View>
      ) : null}
      {usingFallback ? (
        <View testID="facility-fallback-notice" style={styles.notice}>
          <Text variant="caption" weight="bold" color={color.state.info}>{tx('내 위치를 쓸 수 없어요. 아래에서 어디 근처를 볼지 골라 주세요.', 'We cannot use your location. Choose where to look below.')}</Text>
          {canAskAgain
            ? <Button testID="facility-ask-location" label={tx('내 위치로 다시 찾기', 'Search from my location')} variant="tertiary" compact onPress={() => void ask()} />
            : <Button testID="facility-open-settings" label={tx('설정에서 위치 허용하기', 'Allow location in Settings')} variant="tertiary" compact onPress={() => void Linking.openSettings()} />}
        </View>
      ) : null}
      {/* 위치를 쓰고 있어도 다른 곳으로 바꿀 수 있다 — 숙소 근처 짐보관을 미리 찾는 것처럼. */}
      <NearAreaPicker testID="facility-area" accessToken={accessToken} onPick={pickArea} />
      {locationState === 'detecting' ? (
        <View style={styles.notice}><Text variant="caption" weight="bold" color={color.text.body}>{tx('위치를 확인하고 있어요…', 'Finding your location…')}</Text></View>
      ) : null}

      {kind === 'PARKING' ? (
        <View style={styles.controls}>
          <Text variant="body" weight="bold">{tx('몇 시간 머물러요?', 'How long will you stay?')}</Text>
          <View style={styles.chips}>
            {STAY_HOURS_CHOICES.map((hours) => {
              const selected = hours === stayHours;
              return (
                <Pressable key={hours} testID={`facility-stay-${hours}`} accessibilityRole="button" accessibilityState={{ selected }} onPress={() => setStayHours(hours)} style={[styles.chip, selected && styles.chipOn]}>
                  <ChipLabel variant="util" color={selected ? color.text.onAction : color.text.heading}>{txf(tx, '%s시간', `%s ${enPlural(hours, 'hour', 'hours')}`, hours)}</ChipLabel>
                </Pressable>
              );
            })}
          </View>
        </View>
      ) : null}
      {kind === 'EXCHANGE' ? (
        <View testID="facility-exchange-notice" style={styles.warn}>
          <Text variant="caption" weight="bold" color={color.state.warning}>{tx('영업시간과 환율은 곳마다 달라요. 가기 전에 전화로 확인해 주세요. 은행에서도 환전할 수 있어요.', 'Hours and rates differ by shop. Please call before you go. Banks also exchange currency.')}</Text>
        </View>
      ) : null}
      {kind === 'PHARMACY' ? (
        <View testID="facility-pharmacy-notice" style={styles.warn}>
          <Text variant="caption" weight="bold" color={color.state.warning}>{tx('운영시간은 약국이 신고한 정보예요. 방문 전 전화로 확인해 주세요.', 'Hours are reported by each pharmacy. Please call before you go.')}</Text>
        </View>
      ) : null}
      {kind === 'TOILET' ? (
        <View style={styles.chips}>
          <Pressable testID="facility-filter-accessible" accessibilityRole="button" accessibilityState={{ selected: accessibleOnly }} onPress={() => setAccessibleOnly((value) => !value)} style={[styles.chip, accessibleOnly && styles.chipOn]}>
            <ChipLabel variant="util" color={accessibleOnly ? color.text.onAction : color.text.heading}>{`♿ ${tx('장애인용', 'Accessible')}`}</ChipLabel>
          </Pressable>
          <Pressable testID="facility-filter-diaper" accessibilityRole="button" accessibilityState={{ selected: diaperOnly }} onPress={() => setDiaperOnly((value) => !value)} style={[styles.chip, diaperOnly && styles.chipOn]}>
            <ChipLabel variant="util" color={diaperOnly ? color.text.onAction : color.text.heading}>{`👶 ${tx('기저귀 교환대', 'Diaper changing')}`}</ChipLabel>
          </Pressable>
        </View>
      ) : null}

      {/* 지도 — 불러오는 중에는 자리만 지키고(빈 목록이어도 RouteMap 은 던지지 않는다), 결과가 있으면 번호 점이 찍힌다.
          오류·결과 없음에는 그릴 것이 없어 두지 않는다 — 빈 회색 칸을 남기지 않으려고. 못 그리면 RouteMap 이 기존 실패 안내를 낸다. */}
      {load.status === 'loading' || (load.status === 'ready' && load.data.items.length > 0) ? (
        <View testID="facility-map" style={styles.mapWrap}>
          <RouteMap stops={stops} points={points} routes={NO_ROUTES} selectedId={selectedId} onSelect={setPickedId} focusSelected fitPoints currentLocation={currentLocation} height={220} />
        </View>
      ) : null}
      {load.status === 'pick' ? (
        <View testID="facility-pick" style={styles.state}><Text variant="body" weight="bold">{tx('위에서 장소를 검색하거나 자주 가는 곳을 눌러 주세요.', 'Search a place above, or tap one of the popular spots.')}</Text></View>
      ) : load.status === 'loading' ? (
        <View testID="facility-loading" style={styles.state}><Text variant="body" weight="bold">{tx('가까운 곳을 찾고 있어요…', 'Finding places near you…')}</Text></View>
      ) : load.status === 'error' ? (
        <View testID="facility-error" style={styles.state}>
          <Text variant="body" weight="bold">{tx('지금은 이 정보를 불러올 수 없어요. 잠시 뒤 다시 시도해 주세요.', 'We cannot load this right now. Please try again in a moment.')}</Text>
          <Button testID="facility-retry" label={tx('다시 시도', 'Try again')} variant="tertiary" compact onPress={() => setReloadKey((value) => value + 1)} />
        </View>
      ) : load.data.items.length === 0 ? (
        <View testID="facility-empty" style={styles.state}>
          <Text variant="body" weight="bold">{txf(tx, '반경 %s 안에는 아직 없어요.', 'Nothing within %s yet.', distanceText(load.data.radiusM))}</Text>
          {radiusM < WIDE_RADIUS_M
            ? <Button testID="facility-widen" label={tx('2km까지 넓혀 찾기', 'Search up to 2 km')} variant="tertiary" compact onPress={() => setRadiusM(WIDE_RADIUS_M)} />
            : <Text variant="caption" color={color.text.body}>{tx('위 「어디 근처를 볼까요?」에서 다른 곳을 골라 보세요.', 'Choose another place in “Where should we look?” above.')}</Text>}
        </View>
      ) : (
        <>
          <Text variant="caption" color={color.text.muted}>{sortNote}</Text>
          <View testID="facility-list" style={styles.list}>{load.data.items.map(renderCard)}</View>
          {load.data.total > load.data.items.length ? (
            <Text testID="facility-more" variant="caption" color={color.text.muted}>{txf(tx, '%s곳 중 앞의 %s곳만 보여 드려요', '%s found · showing the first %s', load.data.total, load.data.items.length)}</Text>
          ) : null}
        </>
      )}

      <Text variant="caption" color={color.text.muted} style={styles.foot}>{tx('거리는 직선이에요. 공공 자료를 바탕으로 해서 현장과 다를 수 있어요.', 'Distances are straight-line. Based on public data, so details may differ on site.')}</Text>
      {locationGate.sheet}
    </Screen>
  );
}

const styles = StyleSheet.create({
  top: { minHeight: 52, marginBottom: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  logo: { width: 154, height: 28 },
  spacer: { width: 44 },
  lead: { marginTop: spacing[2], marginBottom: spacing[4], lineHeight: 24 },
  tabs: { flexDirection: 'row', gap: spacing[2], marginBottom: spacing[4] },
  tab: { flex: 1, minWidth: 0, minHeight: 64, alignItems: 'center', justifyContent: 'center', gap: 2, paddingHorizontal: spacing[1], paddingVertical: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: color.surface.field },
  tabOn: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  tabEmoji: { textAlign: 'center' },
  notice: { gap: spacing[2], padding: spacing[3], marginBottom: spacing[3], borderRadius: radius.md, backgroundColor: color.state.infoBg },
  warn: { padding: spacing[3], marginBottom: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },
  controls: { gap: spacing[2], marginBottom: spacing[3] },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginBottom: spacing[3] },
  chip: { minHeight: 44, minWidth: 64, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: color.surface.field },
  chipOn: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  state: { gap: spacing[3], padding: spacing[4], marginBottom: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card },
  mapWrap: { overflow: 'hidden', borderRadius: radius.lg, marginBottom: spacing[3] },
  list: { gap: spacing[2], marginTop: spacing[2], marginBottom: spacing[3] },
  foot: { marginTop: spacing[4], marginBottom: spacing[8] },
});
