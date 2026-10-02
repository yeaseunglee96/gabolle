// 가까운 도움 — 병원·약국·경찰을 골라 지도와 목록으로(UI 캔버스 ㉒-4, S15P21E201-1889). 긴급 도움 화면에서 들어온다.
//
// 번호는 긴급 도움 화면에서 바로 걸린다. 여기는 「가까운 곳이 어디냐」 — 다치거나 아플 때 여행자가 먼저 묻는 것.
// 자료는 서버가 먼저다(S15P21E201-1894) — 건강보험심사평가원 병의원·약국(부산 약국 1,733곳)에 오늘 진료시간까지.
// 🔴 서버를 못 받으면(오프라인·늦음) 앱에 실은 OSM 자료로 물러선다(src/field/nearbyHelp.ts, 약국 70곳뿐) —
//    급할 때 빈 화면을 보이지 않으려고. 그때는 「일부만 있어요」라고 먼저 말한다.
// 위치는 누른 뒤에만 묻는다(버스 화면 transit.tsx 와 같은 규칙).
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Linking, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Location from 'expo-location';

import { useAuth } from '@/auth/AuthProvider';
import { BrandLogoLink } from '@/components/BrandLogoLink';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { romanizeKorean } from '@/discovery/romanize';
import { getNearbyHelp } from '@/field/helpPlacesApi';
import { HELP_KINDS, kakaoSearchUrl, nearestHelp, telOf, type HelpKind } from '@/field/nearbyHelp';
import { distanceText } from '@/field/subwayStations';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { readCurrentPosition } from '@/location/currentPosition';
import { RouteMap, type MapRouteLayer } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { syncLocationConsent } from '@/personalization/locationConsent';
import { useLocationGate } from '@/personalization/useLocationGate';

/** 이 지도는 경로가 아니다 — 번호 점을 선으로 잇지 않는다(transit.tsx 의 NO_ROUTES 와 같은 이유). */
const NO_ROUTES: MapRouteLayer[] = [];
/** preview=ui 의 기준점 — 광안리해수욕장 앞(UI 캔버스 ㉒-4 와 같은 자리). */
const PREVIEW_ORIGIN = { latitude: 35.1532, longitude: 129.1186 };
/** 이만큼 가까우면 걸어서 가는 길로 연다. */
const WALK_M = 1200;

type LocationState = 'detecting' | 'granted' | 'denied';
type Tx = (ko: string, en: string) => string;

/** 한 곳 — 서버 자료와 앱 자료를 같은 모양으로 */
export type HelpItem = {
  name: string;
  nameEn: string | null;
  /** 종별 — 「종합병원」「의원」. 앱 자료에는 없다 */
  type: string | null;
  address: string | null;
  phone: string | null;
  latitude: number;
  longitude: number;
  distanceM: number;
  emergency: boolean;
  todayOpen: string | null;
  todayClose: string | null;
  openNow: boolean | null;
};

type Found = { source: 'server' | 'local'; items: HelpItem[] } | null;

const KIND_LABEL: Record<HelpKind, [string, string]> = {
  hospital: ['병원·의원', 'Hospitals'],
  pharmacy: ['약국', 'Pharmacies'],
  police: ['경찰', 'Police'],
};

/** 종별 이름 — 외국어 화면에서만 옮긴다. 모르는 종별은 그대로 */
const TYPE_LABEL: Record<string, [string, string]> = {
  상급종합: ['상급종합병원', 'Tertiary hospital'],
  종합병원: ['종합병원', 'General hospital'],
  병원: ['병원', 'Hospital'],
  의원: ['의원', 'Clinic'],
  보건소: ['보건소', 'Public health center'],
  보건지소: ['보건지소', 'Public health branch'],
  보건진료소: ['보건진료소', 'Public health post'],
};

export default function NearbyHelpScreen() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken, ready } = useAuth();
  const locationGate = useLocationGate(accessToken);
  const { preview, kind: kindParam } = useLocalSearchParams<{ preview?: string; kind?: string }>();
  const [kind, setKind] = useState<HelpKind>(HELP_KINDS.includes(kindParam as HelpKind) ? (kindParam as HelpKind) : 'hospital');
  const [coords, setCoords] = useState<{ latitude: number; longitude: number } | null>(null);
  const [locationState, setLocationState] = useState<LocationState>('detecting');
  const [canAskAgain, setCanAskAgain] = useState(true);
  const [selectedId, setSelectedId] = useState('');
  const [found, setFound] = useState<Found>(null);

  // 이미 허용한 사람만 조용히 읽는다. 처음이거나 거부한 사람에게는 창을 띄우지 않는다 — 묻는 것은 「내 위치 켜기」를 누를 때.
  const restore = useCallback(async () => {
    if (__DEV__ && preview === 'ui') { setCoords(PREVIEW_ORIGIN); setLocationState('granted'); return; }
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
  }, [accessToken, preview]);

  const ask = useCallback(async () => {
    if (!(await locationGate.request())) { setLocationState('denied'); return; }
    const current = await Location.getForegroundPermissionsAsync();
    // 다시 물을 수 없으면 설정으로 — 눌러도 아무 일도 안 일어나는 버튼이 제일 나쁘다.
    if (!current.granted && !current.canAskAgain) { setCanAskAgain(false); void Linking.openSettings(); return; }
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

  useEffect(() => { if (ready) void restore(); }, [ready, restore]);

  // 서버 먼저, 못 받으면 앱 자료
  useEffect(() => {
    if (!coords) return;
    let alive = true;
    setFound(null);
    void getNearbyHelp(kind, coords, accessToken).then((server) => {
      if (!alive) return;
      if (server) {
        setFound({ source: 'server', items: server.places.map((place) => ({ ...place, latitude: place.lat, longitude: place.lng, distanceM: place.distanceMeters })) });
        return;
      }
      setFound({ source: 'local', items: nearestHelp(kind, coords).map((place) => ({ name: place.name, nameEn: place.nameEn, type: null, address: null, phone: place.phone, latitude: place.latitude, longitude: place.longitude, distanceM: place.distanceM, emergency: place.emergency, todayOpen: null, todayClose: null, openNow: null })) });
    });
    return () => { alive = false; };
  }, [coords, kind, accessToken]);

  const items = found?.items ?? [];
  const idOf = (index: number) => `${kind}-${index}`;
  const stops: MapStop[] = useMemo(() => items.map((item, index) => ({ id: idOf(index), number: index + 1, name: item.name, latitude: item.latitude, longitude: item.longitude })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [items]);
  useEffect(() => { setSelectedId(stops[0]?.id ?? ''); }, [stops]);

  const directions = (item: HelpItem) => {
    if (!coords) return;
    router.push({ pathname: '/route-detail', params: {
      originLat: String(coords.latitude), originLng: String(coords.longitude), originName: tx('내 위치', 'My location'),
      destLat: String(item.latitude), destLng: String(item.longitude), destName: item.name,
      ...(item.distanceM <= WALK_M ? { mode: 'WALK' } : {}),
    } });
  };
  // 택시 카드 — 우리 장소 표에 없는 곳이라 받은 이름·주소로 그린다(taxi-card 의 external)
  const showToDriver = (item: HelpItem) => router.push({ pathname: '/taxi-card/[id]', params: { id: 'external', name: item.name, address: item.address ?? '' } });

  return <Screen scroll>
    <View style={styles.top}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={() => (router.canGoBack() ? router.back() : router.replace('/emergency'))} style={styles.back}><Text variant="title">‹</Text></Pressable>
      <BrandLogoLink imageStyle={styles.logo} />
      <View style={styles.spacer} />
    </View>
    <Text variant="display" weight="bold">{tx('가까운 도움', 'Help nearby')}</Text>
    <Text color={color.text.body} style={styles.lead}>{tx('고르면 가까운 곳을 지도와 목록으로 보여 드려요.', 'Pick one and we show the places near you on a map and in a list.')}</Text>

    {/* 위급하면 찾지 말고 바로 — 이 화면에 온 사람이 가장 먼저 봐야 할 한 줄 */}
    <Pressable testID="nearby-call-119" accessibilityRole="button" accessibilityLabel={tx('119에 전화하기', 'Call 119')} onPress={() => void Linking.openURL('tel:119')} style={({ pressed }) => [styles.urgent, pressed && styles.pressed]}>
      <Text variant="body" weight="bold" color={color.state.danger} style={styles.grow}>{tx('위급하면 찾지 말고 119', 'In an emergency, call 119 now')}</Text>
      <Text variant="caption" weight="bold" color={color.state.danger}>{tx('전화 ›', 'Call ›')}</Text>
    </Pressable>

    <View accessibilityRole="tablist" style={styles.kinds}>
      {HELP_KINDS.map((each) => {
        const selected = each === kind;
        return (
          <Pressable key={each} testID={`nearby-kind-${each}`} accessibilityRole="tab" accessibilityState={{ selected }} onPress={() => setKind(each)} style={[styles.kind, selected && styles.kindOn]}>
            <Text variant="util" weight="bold" color={selected ? color.text.onAction : color.text.heading} numberOfLines={1}>{tx(...KIND_LABEL[each])}</Text>
          </Pressable>
        );
      })}
    </View>

    {/* 🔴 앱 자료로 물러섰을 때만 — 그 자료는 일부만 있다(약국은 부산 70곳). 2km 넘는 곳만 보이면 없는 동네로 읽힌다. */}
    {found?.source === 'local' ? (
      <Pressable testID="nearby-offline-note" accessibilityRole="link" onPress={() => void Linking.openURL(kakaoSearchUrl(kind))} style={({ pressed }) => [styles.note, pressed && styles.pressed]}>
        <Text variant="caption" color={color.text.body} style={styles.grow}>{tx('지금은 서버에 닿지 않아 앱에 든 자료로 보여 드려요. 일부만 있어서 더 가까운 곳이 있을 수 있어요.', 'We can’t reach our server, so this list comes from data in the app. It is incomplete — there may be a closer place.')}</Text>
        <Text variant="caption" weight="bold">{tx('카카오맵 ›', 'Kakao Map ›')}</Text>
      </Pressable>
    ) : null}

    {locationState !== 'granted' ? (
      <View style={styles.card}>
        <Text variant="body" weight="bold">{locationState === 'detecting' ? tx('위치를 확인하고 있어요…', 'Finding your location…') : tx('위치를 켜면 가까운 곳을 보여 드려요', 'Turn on location to see places near you')}</Text>
        {locationState === 'denied' ? (
          <>
            <Text variant="caption" color={color.text.body}>{tx('위치는 가까운 곳을 찾을 때만 써요.', 'We use your location only to find places near you.')}</Text>
            <Button label={canAskAgain ? tx('내 위치 켜기', 'Turn on location') : tx('설정에서 위치 허용하기', 'Allow location in Settings')} onPress={() => void ask()} />
          </>
        ) : null}
      </View>
    ) : (
      <>
        <View style={styles.mapWrap}>
          <RouteMap stops={stops} routes={NO_ROUTES} selectedId={selectedId} onSelect={setSelectedId} focusSelected currentLocation={coords} height={220} />
        </View>
        {!found ? (
          <View style={styles.card}><Text variant="body" weight="bold">{tx('가까운 곳을 찾고 있어요…', 'Finding places near you…')}</Text></View>
        ) : items.length === 0 ? (
          <View style={styles.card}><Text variant="body" weight="bold">{txf(tx, '5km 안에서 %s을 못 찾았어요', 'No %s within 5 km', tx(...KIND_LABEL[kind]))}</Text><Text variant="caption" color={color.text.body}>{tx('아래 카카오맵에서 더 찾아보세요.', 'Try Kakao Map below.')}</Text></View>
        ) : (
          <View style={styles.list}>
            {items.map((item, index) => (
              <HelpRow key={idOf(index)} item={item} number={index + 1} selected={selectedId === idOf(index)} onSelect={() => setSelectedId(idOf(index))} onDirections={() => directions(item)} onShowDriver={item.address ? () => showToDriver(item) : null} language={language} tx={tx} />
            ))}
          </View>
        )}
      </>
    )}

    <Pressable testID="nearby-kakao" accessibilityRole="link" onPress={() => void Linking.openURL(kakaoSearchUrl(kind))} style={({ pressed }) => [styles.more, pressed && styles.pressed]}>
      <View style={styles.grow}>
        <Text variant="body" weight="bold">{txf(tx, '카카오맵에서 %s 더 찾기', 'Find more %s on Kakao Map', tx(...KIND_LABEL[kind]))}</Text>
        <Text variant="caption" color={color.text.body}>{tx('리뷰와 사진까지 보고 싶을 때', 'When you want reviews and photos too')}</Text>
      </View>
      <Text variant="title" color={color.text.muted}>›</Text>
    </Pressable>

    <Text variant="caption" color={color.text.muted} style={styles.foot}>
      {found?.source === 'local'
        ? tx('지도 자료: © OpenStreetMap 기여자 · 거리는 직선이에요 · 문을 열었는지는 가기 전에 전화로 확인하세요', 'Map data: © OpenStreetMap contributors · distances are straight-line · call ahead to check they are open')
        : tx('병원·약국: 건강보험심사평가원(2026년 6월 기준) · 경찰: © OpenStreetMap 기여자 · 거리는 직선이에요 · 공휴일 진료는 가기 전에 전화로 확인하세요', 'Hospitals and pharmacies: HIRA (as of June 2026) · police: © OpenStreetMap contributors · distances are straight-line · call ahead on public holidays')}
    </Text>
  </Screen>;
}

/** 지금 진료 중인가를 한 줄로 — 모르면 null(쓰지 않는다) */
export function openLine(item: Pick<HelpItem, 'openNow' | 'todayOpen' | 'todayClose'>, tx: Tx): { text: string; open: boolean } | null {
  if (item.openNow === true && item.todayClose) return { text: txf(tx, '지금 진료 중 · %s까지', 'Open now · until %s', item.todayClose), open: true };
  if (item.openNow === false && item.todayOpen && item.todayClose) return { text: txf(tx, '지금은 진료 시간이 아니에요 · 오늘 %s–%s', 'Closed now · today %s–%s', item.todayOpen, item.todayClose), open: false };
  if (item.openNow === false) return { text: tx('오늘 휴진', 'Closed today'), open: false };
  return null;
}

function HelpRow({ item, number, selected, onSelect, onDirections, onShowDriver, language, tx }: {
  item: HelpItem; number: number; selected: boolean; onSelect: () => void; onDirections: () => void; onShowDriver: (() => void) | null; language: string; tx: Tx;
}) {
  // 외국어 화면: 읽는 이름을 크게, 간판과 같은 한국어를 아래에 — 길에서 간판과 맞춰 본다.
  const foreign = language !== 'ko';
  // 🔴 영어 이름이 없으면 한국어를 굵게, 로마자는 아래 작게 한 줄 — 로마자는 띄어쓰기 없는 긴 한 낱말(「Gangdaesiknaegwauiwon」)이라
  //    굵게 두면 좁은 폰에서 낱말 중간에서 꺾였다(전체 점검 2026-10-02). 영어 이름은 띄어쓰기가 있어 그대로 굵게.
  const title = foreign && item.nameEn ? item.nameEn : item.name;
  const subName = foreign ? (item.nameEn ? item.name : romanizeKorean(item.name)) : null;
  const typeLabel = item.type && item.type !== '약국' && item.type !== '경찰' ? (TYPE_LABEL[item.type] ? tx(...TYPE_LABEL[item.type]) : item.type) : null;
  const status = openLine(item, tx);
  return (
    <View style={[styles.row, selected && styles.rowOn]}>
      <View style={styles.rowTop}>
        <Pressable accessibilityRole="button" accessibilityState={{ selected }} accessibilityLabel={txf(tx, '%s 지도에서 보기', 'Show %s on the map', title)} onPress={onSelect} style={styles.rowHead}>
          <View style={styles.pin}><Text variant="caption" weight="bold" color={color.text.onAction}>{number}</Text></View>
          <View style={styles.grow}>
            <Text variant="body" weight="bold">{title}</Text>
            {subName && subName !== title ? <Text variant="caption" color={color.text.body} numberOfLines={1}>{subName}</Text> : null}
            <Text variant="caption" color={color.text.muted}>{[typeLabel, txf(tx, '직선 %s', '%s straight-line', distanceText(item.distanceM))].filter(Boolean).join(' · ')}</Text>
          </View>
        </Pressable>
        <Button label={tx('길 안내', 'Directions')} variant="secondary" compact onPress={onDirections} />
      </View>
      {item.emergency || status ? (
        <View style={styles.badges}>
          {item.emergency ? <View style={styles.erBadge}><Text variant="micro" weight="bold" color={color.state.danger}>{tx('응급실 있음', 'Emergency room')}</Text></View> : null}
          {status ? <Text variant="caption" weight="bold" color={status.open ? color.state.success : color.text.muted}>{status.text}</Text> : null}
        </View>
      ) : null}
      {item.phone || onShowDriver ? (
        <View style={styles.rowActions}>
          {item.phone ? (
            <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s에 전화하기', 'Call %s', title)} onPress={() => void Linking.openURL(telOf(item.phone!))} style={({ pressed }) => [styles.action, pressed && styles.pressed]}>
              <Text variant="caption" weight="bold">{tx('전화', 'Call')}</Text>
            </Pressable>
          ) : null}
          {onShowDriver ? (
            <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 주소를 기사님께 보여주기', 'Show the address of %s to a taxi driver', title)} onPress={onShowDriver} style={({ pressed }) => [styles.action, pressed && styles.pressed]}>
              <Text variant="caption" weight="bold">{tx('기사님께 보여주기', 'Show to driver')}</Text>
            </Pressable>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  top: { minHeight: 52, marginBottom: spacing[6], flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  logo: { width: 154, height: 28 },
  spacer: { width: 44 },
  lead: { marginTop: spacing[2], marginBottom: spacing[4], lineHeight: 24 },
  pressed: { opacity: 0.7 },
  grow: { flex: 1, minWidth: 0, gap: 2 },
  urgent: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], marginBottom: spacing[4], borderRadius: radius.lg, borderWidth: 1.5, borderColor: color.state.danger, backgroundColor: color.surface.card },
  kinds: { flexDirection: 'row', gap: spacing[2], marginBottom: spacing[4] },
  kind: { flex: 1, minWidth: 0, minHeight: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing[2], backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: color.surface.field },
  kindOn: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  card: { gap: spacing[3], padding: spacing[4], marginBottom: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  mapWrap: { overflow: 'hidden', borderRadius: radius.lg, marginBottom: spacing[3] },
  list: { gap: spacing[2], marginBottom: spacing[4] },
  row: { gap: spacing[2], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: color.surface.card },
  rowOn: { borderColor: color.text.heading },
  rowTop: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  rowHead: { flex: 1, minWidth: 0, flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  pin: { width: 26, height: 26, borderRadius: radius.full, backgroundColor: color.text.heading, alignItems: 'center', justifyContent: 'center' },
  // 번호 칸만큼 들여 쓴다 — 이름 글과 같은 줄에서 시작한다
  badges: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: spacing[2], marginLeft: 26 + spacing[3] },
  erBadge: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, borderWidth: 1, borderColor: color.state.danger },
  rowActions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginLeft: 26 + spacing[3] },
  action: { minHeight: 36, paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' },
  note: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], marginBottom: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },
  more: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  foot: { marginTop: spacing[4], marginBottom: spacing[8] },
});
