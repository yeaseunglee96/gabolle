// 주변 버스 도착 — S15P21E201-1138.
// 지도·가까운 지하철역·거리 — S15P21E201-1830. 목록만으로는 「그 정류장이 어디 있는지」를 몰랐다.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Linking, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Location from 'expo-location';
import { readCurrentPosition } from '@/location/currentPosition';

import { useAuth } from '@/auth/AuthProvider';
import { syncLocationConsent } from '@/personalization/locationConsent';
import { useLocationGate } from '@/personalization/useLocationGate';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import {
  arrivalLabel,
  loadNearbyBusArrivals,
  sortStops,
  type BusBlockedReason,
  type BusStop,
} from '@/field/busArrivals';
import { vendorNotReadyMessage } from '@/api/vendorReady';
import { useI18n } from '@/i18n';
import { isAtLeast } from '@/layout/breakpoints';
import { useLayout } from '@/layout/useLayout';
import { txf } from '@/i18n/format';
import { RouteMap, type MapPointLayer } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { distanceText, nearbyStations, straightDistanceM, walkMinutes } from '@/field/subwayStations';

/** 위치를 모를 때 기준으로 삼는 부산 중심 — explore.tsx 와 같은 자리. */
const BUSAN_CENTER = { latitude: 35.1796, longitude: 129.0756 };
/** preview=ui 의 기준점 — 해운대해수욕장 앞. */
const PREVIEW_ORIGIN = { latitude: 35.15870, longitude: 129.16040 };

type LocationState = 'detecting' | 'granted' | 'denied';

export default function Bus() {
  const router = useRouter();
  const { tx } = useI18n();
  const { preview } = useLocalSearchParams<{ preview?: string }>();
  const { accessToken, ready } = useAuth();
  const locationGate = useLocationGate(accessToken);
  const { width } = useLayout();
  const wide = isAtLeast(width, 'md');

  const [coords, setCoords] = useState(BUSAN_CENTER);
  const [locationState, setLocationState] = useState<LocationState>('detecting');
  const [canAskAgain, setCanAskAgain] = useState(true);
  const [state, setState] = useState<'loading' | 'ready' | 'blocked'>('loading');
  const [reason, setReason] = useState<BusBlockedReason | null>(null);
  const [stops, setStops] = useState<BusStop[]>([]);
  const [checkedAt, setCheckedAt] = useState<Date | null>(null);
  const [selectedId, setSelectedId] = useState('');

  // 화면 상태를 눈으로 확인하기 위한 자리 — exchange.tsx 의 preview=ui 와 같은 방식이다.
  // 버스 도착은 로그인해야 받을 수 있어서, 로그인 없이 "시간이 찍힌 화면" 을 볼 길이 달리
  // 없다. __DEV__ 에서만 산다 — 배포본에는 이 가지가 아예 안 들어간다.
  const previewStops: BusStop[] = [
    { nodeId: 'p1', nodeName: '해운대해수욕장', lat: 35.15918, lng: 129.15906, arrivals: [
      { routeNo: '139', arrivalSeconds: 95, remainingStops: 1, vehicleType: null },
      { routeNo: '1001', arrivalSeconds: 420, remainingStops: 4, vehicleType: null },
      { routeNo: '307', arrivalSeconds: null, remainingStops: null, vehicleType: null },
    ] },
    { nodeId: 'p2', nodeName: '해운대시장', lat: 35.16195, lng: 129.16042, arrivals: [
      { routeNo: '40', arrivalSeconds: 30, remainingStops: 0, vehicleType: null },
      { routeNo: '100', arrivalSeconds: 1260, remainingStops: 11, vehicleType: null },
    ] },
    { nodeId: 'p3', nodeName: '중동역', lat: 35.16614, lng: 129.16816, arrivals: [] },
  ];

  const load = useCallback(async (at: { latitude: number; longitude: number }) => {
    if (__DEV__ && preview === 'ui') {
      // 지도와 거리를 보려면 기준점도 있어야 한다 — 해운대해수욕장 앞.
      setCoords(PREVIEW_ORIGIN); setLocationState('granted');
      setStops(sortStops(previewStops)); setCheckedAt(new Date()); setState('ready');
      return;
    }
    if (__DEV__ && preview === 'empty') {
      setStops([]); setCheckedAt(new Date()); setState('ready');
      return;
    }
    setState('loading');
    const out = await loadNearbyBusArrivals(at, accessToken);
    if (out.state === 'ready') {
      setStops(out.stops);
      setCheckedAt(new Date());
      setState('ready');
      return;
    }
    setReason(out.reason);
    setState('blocked');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, preview]);

  // 이미 허용한 사람만 조용히 읽는다. 처음이거나 거부한 사람에게는 팝업을 띄우지 않는다.
  const restoreGrantedLocation = useCallback(async () => {
    // 🔴 동의가 없으면 조용히 읽지도 않는다(S15P21E201-1691). 묻는 것은 「내 위치 허용」을 누를 때다.
    if ((await syncLocationConsent(accessToken)) !== true) { setLocationState('denied'); return null; }
    try {
      const permission = await Location.getForegroundPermissionsAsync();
      if (!permission.granted) { setCanAskAgain(permission.canAskAgain); setLocationState('denied'); return null; }
      const position = await readCurrentPosition();
      const next = { latitude: position.coords.latitude, longitude: position.coords.longitude };
      setCoords(next);
      setLocationState('granted');
      return next;
    } catch {
      setLocationState('denied');
      return null;
    }
  }, [accessToken]);

  /**
   * 거부한 뒤에도 버튼이 살아 있어야 한다 이 같은 실수를 고쳤다).
   * 다시 물을 수 없는 상태면 설정으로 보낸다 — 눌러도 아무 일도 안 일어나는 버튼이 제일 나쁘다.
   */
  const askForLocation = useCallback(async () => {
    // 🔴 위치 동의가 먼저다(S15P21E201-1691) — 누른 것은 쓰고 싶다는 뜻이라, 거절했어도 다시 묻는다.
    if (!(await locationGate.request())) { setLocationState('denied'); return; }
    const current = await Location.getForegroundPermissionsAsync();
    if (!current.granted && !current.canAskAgain) {
      setCanAskAgain(false);
      void Linking.openSettings();
      return;
    }
    setLocationState('detecting');
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (!permission.granted) { setCanAskAgain(permission.canAskAgain); setLocationState('denied'); return; }
      const position = await readCurrentPosition();
      const next = { latitude: position.coords.latitude, longitude: position.coords.longitude };
      setCoords(next);
      setLocationState('granted');
      void load(next);
    } catch {
      setLocationState('denied');
    }
  }, [load, locationGate.request]);

  useEffect(() => {
    if (!ready) return;
    void (async () => {
      if (__DEV__ && preview === 'ui') { void load(PREVIEW_ORIGIN); return; }
      const granted = await restoreGrantedLocation();
      void load(granted ?? BUSAN_CENTER);
    })();
  }, [ready, restoreGrantedLocation, load, preview]);

  function blockedText(r: BusBlockedReason): { title: string; body: string } {
    if (r === 'signed-out') return {
      title: tx('로그인하면 도착 시간을 보여드려요', 'Sign in to see arrival times'),
      body: tx('버스 도착 정보는 한도가 있는 외부 자료라 로그인한 분에게만 보여드려요.', 'Arrival data is metered, so it is for signed-in travelers.'),
    };
    if (r === 'not-built') return {
      title: tx('버스 정보가 아직 서버에 없어요', 'Bus data is not on the server yet'),
      body: tx('곧 열려요. 조금 뒤에 다시 들러 주세요.', 'It is coming. Please check back a little later.'),
    };
    // — 열쇠가 안 꽂힌 것은 「잠시 뒤」가 아니다. 그렇게 말하면 거짓말이다.
    if (r === 'not-ready') return {
      title: tx('버스 도착 정보는 아직 준비 중이에요', 'Bus arrivals are not set up yet'),
      body: vendorNotReadyMessage(tx),
    };
    if (r === 'vendor') return {
      title: tx('지금은 도착 정보를 못 받았어요', 'Could not get arrivals right now'),
      body: tx('버스 정보 제공처가 잠시 응답하지 않아요. 잠시 후 다시 시도해 주세요.', 'The transit provider is not responding. Please try again shortly.'),
    };
    return {
      title: tx('도착 정보를 불러오지 못했어요', 'Could not load arrivals'),
      body: tx('잠시 후 다시 시도해 주세요.', 'Please try again shortly.'),
    };
  }

  function arrivalText(seconds: number | null) {
    const label = arrivalLabel(seconds);
    if (label.kind === 'unknown') return tx('정보 없음', 'No data');
    if (label.kind === 'imminent') return tx('곧 도착', 'Arriving');
    return tx(`${label.minutes}분`, `${label.minutes} min`);
  }

  // 🔴 거리는 내 위치를 알 때만 적는다. 부산 중심에서 잰 「350m」는 걸어갈 사람에게 거짓말이다.
  const located = locationState === 'granted';
  const stations = useMemo(() => nearbyStations(coords), [coords]);
  const busStops = state === 'ready' ? stops : [];
  // 버스 정류장은 목록과 같은 번호로 찍는다 — 지도에서 본 「2」를 목록에서 바로 찾게.
  // 🔴 지하철역은 「지하철」 표시로 따로 찍는데, 버스 정류장이 하나도 없을 때(로그인 전·정보 없음)는 역을 번호로 찍는다.
  //    폰 지도(RouteMap.native)는 번호 장소가 없으면 빈 칸만 그려서, 역만 있는 지도가 아예 안 떴다.
  const stationsNumbered = busStops.length === 0;
  const mapStops: MapStop[] = useMemo(() => (stationsNumbered
    ? stations.map((station, index) => ({ id: `subway-${station.name}`, number: index + 1, name: `${station.name}역`, latitude: station.latitude, longitude: station.longitude }))
    : busStops.map((stop, index) => ({ id: stop.nodeId, number: index + 1, name: stop.nodeName, latitude: stop.lat, longitude: stop.lng }))),
  [stationsNumbered, stations, busStops]);
  const mapPoints: MapPointLayer[] = useMemo(() => (stationsNumbered || stations.length === 0 ? [] : [{
    id: 'subway',
    label: tx('지하철', 'Metro'),
    color: color.state.warning,
    stops: stations.map((station, index) => ({ id: `subway-${station.name}`, number: index + 1, name: `${station.name}역`, latitude: station.latitude, longitude: station.longitude })),
  }]), [stationsNumbered, stations, tx]);
  const showMap = mapStops.length > 0;

  function howFar(lat: number, lng: number) {
    const meters = straightDistanceM(coords, { latitude: lat, longitude: lng });
    return txf(tx, '%s · 걸어서 약 %s분', '%s · about %s min walk', distanceText(meters), walkMinutes(meters));
  }

  // 넓은 화면은 왼쪽 목록·오른쪽 지도 — 지도를 가로로 길게 깔면 목록이 한참 아래로 밀린다(S15P21E201-1830).
  const mapView = showMap ? (
    <View style={wide ? styles.mapColumn : styles.mapWrap}>
      <RouteMap
        stops={mapStops}
        points={mapPoints}
        selectedId={selectedId}
        onSelect={setSelectedId}
        currentLocation={located ? coords : null}
        height={wide ? 520 : 260}
      />
    </View>
  ) : null;

  const timeOnly = checkedAt
    ? `${String(checkedAt.getHours()).padStart(2, '0')}:${String(checkedAt.getMinutes()).padStart(2, '0')}`
    : '';

  return (
    <Screen scroll wide>
      <Text variant="display" weight="bold">{tx('주변 버스', 'Buses nearby')}</Text>
      <Text variant="caption" color={color.text.body} style={styles.subtitle}>
        {tx('기다릴지 택시를 탈지, 남은 시간을 보고 정하세요.', 'See how long the wait is, then decide: bus or taxi.')}
      </Text>

      {/* 어느 좌표를 기준으로 찾았는지 숨기지 않는다 — 내 위치가 아니면 그렇게 말한다. */}
      <View style={styles.originRow}>
        {/* 여기에 확인 시각까지 붙였더니 390 폭에서 두 줄로 깨졌다. 시각은 목록 아래
            「다시 불러오기」 옆으로 옮겼다 — 거기가 그 값을 실제로 쓰는 자리다.
        */}
        <Text variant="caption" color={color.text.muted} style={styles.originText}>
          {locationState === 'granted'
            ? tx('내 위치 기준', 'From your location')
            : tx('부산 중심 기준 · 내 위치를 켜면 더 정확해요', 'From central Busan · turn on location for accuracy')}
        </Text>
        {locationState !== 'granted' ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={canAskAgain ? tx('내 위치로 찾기', 'Use my location') : tx('설정에서 위치 권한 열기', 'Open location settings')}
            onPress={() => void askForLocation()}
            style={({ pressed }) => [styles.smallButton, pressed && styles.pressed]}
          >
            <Text variant="caption" weight="bold" color={color.brand.navy}>
              {canAskAgain ? tx('내 위치로 찾기', 'Use my location') : tx('설정 열기', 'Open settings')}
            </Text>
          </Pressable>
        ) : null}
      </View>

      {!wide ? mapView : null}
      <View style={wide && showMap ? styles.columns : undefined}>
        <View style={wide && showMap ? styles.listColumn : undefined}>
          {stations.length > 0 ? (
            <View style={[styles.card, styles.stationCard]}>
              <Text variant="body" weight="bold">{tx('가까운 지하철역', 'Nearby metro stations')}</Text>
              {stations.map((station, index) => {
                const id = `subway-${station.name}`;
                return (
                  <Pressable
                    key={id}
                    accessibilityRole="button"
                    accessibilityLabel={txf(tx, '%s 지도에서 보기', 'Show %s on the map', txf(tx, '%s역', '%s Station', station.name))}
                    onPress={() => setSelectedId(id)}
                    style={[styles.stationRow, selectedId === id && styles.selectedRow]}
                  >
                    {stationsNumbered ? <View style={styles.numberBadge}><Text variant="micro" weight="bold" color={color.text.onAction}>{index + 1}</Text></View> : null}
                    <View style={styles.grow}>
                      <Text weight="bold">{txf(tx, '%s역', '%s Station', station.name)}</Text>
                      {located ? <Text variant="caption" color={color.text.muted}>{howFar(station.latitude, station.longitude)}</Text> : null}
                    </View>
                    <View style={styles.lineChips}>
                      {station.lines.map((line) => (
                        <View key={line} style={styles.lineChip}><Text variant="micro" weight="bold" color={color.state.warning}>{txf(tx, '%s호선', 'Line %s', line)}</Text></View>
                      ))}
                    </View>
                  </Pressable>
                );
              })}
            </View>
          ) : null}

          {state === 'loading' ? (
            <View style={styles.card}><Text color={color.text.muted}>{tx('도착 정보를 확인하고 있어요…', 'Checking arrivals…')}</Text></View>
          ) : null}

          {state === 'blocked' && reason ? (
            <View accessibilityLiveRegion="polite" style={styles.card}>
              <Text variant="title" weight="bold">{blockedText(reason).title}</Text>
              <Text color={color.text.body} style={styles.blockedBody}>{blockedText(reason).body}</Text>
              {/* — 준비되지 않은 기능에는 「다시 시도」를 안 보여준다.
                  눌러도 달라지지 않는 단추는 없는 것보다 나쁘다 — 사람을 거기 묶어 둔다.
              */}
              {reason === 'signed-out'
                ? <Button label={tx('로그인하기', 'Sign in')} containerStyle={styles.cta} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/field/transit' } })} />
                : reason === 'not-ready'
                  ? null
                  : <Button label={tx('다시 시도', 'Try again')} variant="tertiary" containerStyle={styles.cta} onPress={() => void load(coords)} />}
            </View>
          ) : null}

          {state === 'ready' && stops.length === 0 ? (
            <View style={styles.card}>
              <Text variant="title" weight="bold">{tx('근처에 정류소가 없어요', 'No stops nearby')}</Text>
              <Text color={color.text.body} style={styles.blockedBody}>
                {tx('조금 움직인 뒤 다시 찾아보세요.', 'Move a little and search again.')}
              </Text>
              <Button label={tx('다시 찾기', 'Search again')} variant="tertiary" containerStyle={styles.cta} onPress={() => void load(coords)} />
            </View>
          ) : null}

          {state === 'ready' && stops.length > 0 ? (
            <>
              <View style={[styles.list, wide && !showMap && styles.listWide]}>
                {stops.map((stop, stopIndex) => (
                  <Pressable
                    key={stop.nodeId}
                    accessibilityRole="button"
                    accessibilityLabel={txf(tx, '%s 지도에서 보기', 'Show %s on the map', stop.nodeName)}
                    onPress={() => setSelectedId(stop.nodeId)}
                    style={[styles.card, wide && !showMap && styles.cardWide, selectedId === stop.nodeId && styles.selectedCard]}
                  >
                    <View style={styles.stopHead}>
                      <View style={styles.numberBadge}><Text variant="micro" weight="bold" color={color.text.onAction}>{stopIndex + 1}</Text></View>
                      <View style={styles.grow}>
                        <Text variant="body" weight="bold">{stop.nodeName}</Text>
                        {located ? <Text variant="caption" color={color.text.muted}>{howFar(stop.lat, stop.lng)}</Text> : null}
                      </View>
                    </View>
                    {stop.arrivals.length === 0 ? (
                      <Text variant="caption" color={color.text.muted}>{tx('지금 오는 버스가 없어요', 'No buses coming right now')}</Text>
                    ) : (
                      stop.arrivals.map((arrival, index) => (
                        <View key={`${stop.nodeId}-${arrival.routeNo}-${index}`} style={styles.arrivalRow}>
                          <View style={styles.routeBadge}>
                            <Text variant="caption" weight="bold" color={color.text.onAction}>{arrival.routeNo}</Text>
                          </View>
                          <Text variant="title" weight="bold" style={styles.arrivalTime}>{arrivalText(arrival.arrivalSeconds)}</Text>
                          {/* 몇 정류장 전인지도 없을 수 있다 — 없으면 아예 안 적는다.
                              0은 안 적는다. 「0정류장 전」은 사람이 쓰는 말이 아니고
                              그 경우는 옆의 「곧 도착」이 이미 같은 것을 말하고 있다.
                          */}
                          {arrival.remainingStops != null && arrival.remainingStops > 0 ? (
                            <Text variant="caption" color={color.text.muted}>{tx(`${arrival.remainingStops}정류장 전`, `${arrival.remainingStops} stops away`)}</Text>
                          ) : null}
                        </View>
                      ))
                    )}
                  </Pressable>
                ))}
              </View>
              <Button label={tx('다시 불러오기', 'Refresh')} variant="tertiary" containerStyle={styles.refresh} onPress={() => void load(coords)} />
              {checkedAt ? (
                <Text variant="caption" color={color.text.muted} style={styles.checkedAt}>
                  {txf(tx, '%s 기준이에요', 'As of %s', timeOnly)}
                </Text>
              ) : null}
            </>
          ) : null}
        </View>
        {wide ? mapView : null}
      </View>
      {locationGate.sheet}
    </Screen>
  );
}

const styles = StyleSheet.create({
  subtitle: { marginTop: spacing[2], marginBottom: spacing[4] },
  originRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], marginBottom: spacing[4] },
  originText: { flex: 1 },
  checkedAt: { marginTop: spacing[2], textAlign: 'center' },
  smallButton: { minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.tint },
  list: { gap: spacing[3] },
  mapWrap: { marginBottom: spacing[4] },
  columns: { flexDirection: 'row', gap: spacing[6], alignItems: 'flex-start' },
  listColumn: { flex: 1, minWidth: 0 },
  mapColumn: { flex: 1 },
  stationCard: { marginBottom: spacing[4], gap: spacing[2] },
  stationRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 44, paddingVertical: spacing[1], paddingHorizontal: spacing[2], marginHorizontal: -spacing[2], borderRadius: radius.md },
  selectedRow: { backgroundColor: color.surface.tint },
  selectedCard: { backgroundColor: color.surface.tint },
  stopHead: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  numberBadge: { width: 24, height: 24, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.brand.navy },
  lineChips: { flexDirection: 'row', gap: spacing[1] },
  lineChip: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, borderWidth: 1, borderColor: color.state.warning },
  grow: { flex: 1 },
  listWide: { flexDirection: 'row', flexWrap: 'wrap' },
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  cardWide: { flexGrow: 1, flexBasis: 320, maxWidth: 480 },
  arrivalRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  routeBadge: { minWidth: 56, minHeight: 32, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[2], borderRadius: radius.sm, backgroundColor: color.brand.navy },
  arrivalTime: { flex: 1 },
  blockedBody: { lineHeight: 22 },
  cta: { marginTop: spacing[2] },
  refresh: { marginTop: spacing[4] },
  pressed: { opacity: 0.78 },
});
