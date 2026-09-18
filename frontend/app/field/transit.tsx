// 주변 버스 도착 — S15P21E201-1138.
//
// 🔴 이 화면이 답하는 질문은 하나다: **"기다릴까, 택시 탈까."**
//    그래서 화면에서 가장 큰 글자는 정류소 이름도 노선 번호도 아니고 **남은 시간**이다.
//
// 🔴 위치는 explore.tsx 가 이미 정해 둔 방식을 그대로 따른다 — 들어오자마자 권한 팝업을
//    띄우지 않고, 이미 허용한 사람만 조용히 읽고, 나머지에게는 부산 중심 결과를 먼저 보여준 뒤
//    버튼으로 고르게 한다. 화면을 둘러보기만 해도 팝업이 뜨면 사람은 일단 거부한다.
import { useCallback, useEffect, useState } from 'react';
import { Linking, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Location from 'expo-location';

import { useAuth } from '@/auth/AuthProvider';
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

/** 위치를 모를 때 기준으로 삼는 부산 중심 — explore.tsx 와 같은 자리. */
const BUSAN_CENTER = { latitude: 35.1796, longitude: 129.0756 };

type LocationState = 'detecting' | 'granted' | 'denied';

export default function Bus() {
  const router = useRouter();
  const { tx } = useI18n();
  const { preview } = useLocalSearchParams<{ preview?: string }>();
  const { accessToken, ready } = useAuth();
  const { width } = useLayout();
  const wide = isAtLeast(width, 'md');

  const [coords, setCoords] = useState(BUSAN_CENTER);
  const [locationState, setLocationState] = useState<LocationState>('detecting');
  const [canAskAgain, setCanAskAgain] = useState(true);
  const [state, setState] = useState<'loading' | 'ready' | 'blocked'>('loading');
  const [reason, setReason] = useState<BusBlockedReason | null>(null);
  const [stops, setStops] = useState<BusStop[]>([]);
  const [checkedAt, setCheckedAt] = useState<Date | null>(null);

  // 🔴 화면 상태를 눈으로 확인하기 위한 자리 — exchange.tsx 의 preview=ui 와 같은 방식이다.
  //    버스 도착은 로그인해야 받을 수 있어서, 로그인 없이 "시간이 찍힌 화면" 을 볼 길이 달리
  //    없다. **__DEV__ 에서만 산다** — 배포본에는 이 가지가 아예 안 들어간다.
  const previewStops: BusStop[] = [
    { nodeId: 'p1', nodeName: '해운대해수욕장', lat: 0, lng: 0, arrivals: [
      { routeNo: '139', arrivalSeconds: 95, remainingStops: 1, vehicleType: null },
      { routeNo: '1001', arrivalSeconds: 420, remainingStops: 4, vehicleType: null },
      { routeNo: '307', arrivalSeconds: null, remainingStops: null, vehicleType: null },
    ] },
    { nodeId: 'p2', nodeName: '해운대시장', lat: 0, lng: 0, arrivals: [
      { routeNo: '40', arrivalSeconds: 30, remainingStops: 0, vehicleType: null },
      { routeNo: '100', arrivalSeconds: 1260, remainingStops: 11, vehicleType: null },
    ] },
    { nodeId: 'p3', nodeName: '중동역', lat: 0, lng: 0, arrivals: [] },
  ];

  const load = useCallback(async (at: { latitude: number; longitude: number }) => {
    if (__DEV__ && preview === 'ui') {
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
    try {
      const permission = await Location.getForegroundPermissionsAsync();
      if (!permission.granted) { setCanAskAgain(permission.canAskAgain); setLocationState('denied'); return null; }
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      const next = { latitude: position.coords.latitude, longitude: position.coords.longitude };
      setCoords(next);
      setLocationState('granted');
      return next;
    } catch {
      setLocationState('denied');
      return null;
    }
  }, []);

  /**
   * 🔴 거부한 뒤에도 버튼이 살아 있어야 한다 (S15P21E201-1127 이 같은 실수를 고쳤다).
   * 다시 물을 수 없는 상태면 설정으로 보낸다 — 눌러도 아무 일도 안 일어나는 버튼이 제일 나쁘다.
   */
  const askForLocation = useCallback(async () => {
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
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      const next = { latitude: position.coords.latitude, longitude: position.coords.longitude };
      setCoords(next);
      setLocationState('granted');
      void load(next);
    } catch {
      setLocationState('denied');
    }
  }, [load]);

  useEffect(() => {
    if (!ready) return;
    void (async () => {
      const granted = await restoreGrantedLocation();
      void load(granted ?? BUSAN_CENTER);
    })();
  }, [ready, restoreGrantedLocation, load]);

  function blockedText(r: BusBlockedReason): { title: string; body: string } {
    if (r === 'signed-out') return {
      title: tx('로그인하면 도착 시간을 보여드려요', 'Sign in to see arrival times'),
      body: tx('버스 도착 정보는 한도가 있는 외부 자료라 로그인한 분에게만 보여드려요.', 'Arrival data is metered, so it is for signed-in travelers.'),
    };
    if (r === 'not-built') return {
      title: tx('버스 정보가 아직 서버에 없어요', 'Bus data is not on the server yet'),
      body: tx('곧 열려요. 조금 뒤에 다시 들러 주세요.', 'It is coming. Please check back a little later.'),
    };
    // 🔴 S15P21E201-1200 — 열쇠가 안 꽂힌 것은 「잠시 뒤」가 아니다. 그렇게 말하면 거짓말이다.
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
        {/* 🔴 여기에 확인 시각까지 붙였더니 390 폭에서 두 줄로 깨졌다. 시각은 목록 아래
            「다시 불러오기」 옆으로 옮겼다 — 거기가 그 값을 실제로 쓰는 자리다. */}
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

      {state === 'loading' ? (
        <View style={styles.card}><Text color={color.text.muted}>{tx('도착 정보를 확인하고 있어요…', 'Checking arrivals…')}</Text></View>
      ) : null}

      {state === 'blocked' && reason ? (
        <View accessibilityLiveRegion="polite" style={styles.card}>
          <Text variant="title" weight="bold">{blockedText(reason).title}</Text>
          <Text color={color.text.body} style={styles.blockedBody}>{blockedText(reason).body}</Text>
          {/* 🔴 S15P21E201-1200 — 준비되지 않은 기능에는 「다시 시도」를 안 보여준다.
              눌러도 달라지지 않는 단추는 없는 것보다 나쁘다 — 사람을 거기 묶어 둔다. */}
          {reason === 'signed-out'
            ? <Button label={tx('로그인하기', 'Sign in')} containerStyle={styles.cta} onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: '/field/transit' } })} />
            : reason === 'not-ready'
              ? null
              : <Button label={tx('다시 시도', 'Try again')} variant="ghost" containerStyle={styles.cta} onPress={() => void load(coords)} />}
        </View>
      ) : null}

      {state === 'ready' && stops.length === 0 ? (
        <View style={styles.card}>
          <Text variant="title" weight="bold">{tx('근처에 정류소가 없어요', 'No stops nearby')}</Text>
          <Text color={color.text.body} style={styles.blockedBody}>
            {tx('조금 움직인 뒤 다시 찾아보세요.', 'Move a little and search again.')}
          </Text>
          <Button label={tx('다시 찾기', 'Search again')} variant="ghost" containerStyle={styles.cta} onPress={() => void load(coords)} />
        </View>
      ) : null}

      {state === 'ready' && stops.length > 0 ? (
        <>
          <View style={[styles.list, wide && styles.listWide]}>
            {stops.map((stop) => (
              <View key={stop.nodeId} style={[styles.card, wide && styles.cardWide]}>
                <Text variant="body" weight="bold">{stop.nodeName}</Text>
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
                          🔴 0은 안 적는다. 「0정류장 전」은 사람이 쓰는 말이 아니고,
                          그 경우는 옆의 「곧 도착」이 이미 같은 것을 말하고 있다. */}
                      {arrival.remainingStops != null && arrival.remainingStops > 0 ? (
                        <Text variant="caption" color={color.text.muted}>{tx(`${arrival.remainingStops}정류장 전`, `${arrival.remainingStops} stops away`)}</Text>
                      ) : null}
                    </View>
                  ))
                )}
              </View>
            ))}
          </View>
          <Button label={tx('다시 불러오기', 'Refresh')} variant="ghost" containerStyle={styles.refresh} onPress={() => void load(coords)} />
          {checkedAt ? (
            <Text variant="caption" color={color.text.muted} style={styles.checkedAt}>
              {tx(`${timeOnly} 기준이에요`, `As of ${timeOnly}`)}
            </Text>
          ) : null}
        </>
      ) : null}
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
