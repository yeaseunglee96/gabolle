// 가까운 도움 — 병원·약국·경찰을 골라 지도와 목록으로(UI 캔버스 ㉒-4, S15P21E201-1889). 긴급 도움 화면에서 들어온다.
//
// 번호는 긴급 도움 화면에서 바로 걸린다. 여기는 「가까운 곳이 어디냐」 — 다치거나 아플 때 여행자가 먼저 묻는 것.
// 🔴 자료는 앱에 실은 OSM 추출본이다(src/field/nearbyHelp.ts). 모든 곳이 있지 않아서 «가장 가까운 곳»이라고 말하지 않고,
//    카카오맵 검색으로 더 찾게 한다. 위치는 누른 뒤에만 묻는다(버스 화면 transit.tsx 와 같은 규칙).
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
import { HELP_KINDS, kakaoSearchUrl, nearestHelp, telOf, type HelpKind, type NearbyHelp } from '@/field/nearbyHelp';
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

const KIND_LABEL: Record<HelpKind, [string, string]> = {
  hospital: ['병원·의원', 'Hospitals'],
  pharmacy: ['약국', 'Pharmacies'],
  police: ['경찰', 'Police'],
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

  const places = useMemo(() => (coords ? nearestHelp(kind, coords) : []), [coords, kind]);
  const idOf = (place: NearbyHelp, index: number) => `${kind}-${index}`;
  const stops: MapStop[] = useMemo(() => places.map((place, index) => ({ id: idOf(place, index), number: index + 1, name: place.name, latitude: place.latitude, longitude: place.longitude })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [places]);
  useEffect(() => { setSelectedId(stops[0]?.id ?? ''); }, [stops]);

  const directions = (place: NearbyHelp) => {
    if (!coords) return;
    router.push({ pathname: '/route-detail', params: {
      originLat: String(coords.latitude), originLng: String(coords.longitude), originName: tx('내 위치', 'My location'),
      destLat: String(place.latitude), destLng: String(place.longitude), destName: place.name,
      ...(place.distanceM <= WALK_M ? { mode: 'WALK' } : {}),
    } });
  };

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

    {/* 🔴 약국은 지도 자료에 실제보다 훨씬 적다(부산 OSM 70곳) — 2km 넘는 곳만 보이면 약국이 없는 동네로 읽힌다. 맨 위에서 먼저 말한다. */}
    {kind === 'pharmacy' ? (
      <Pressable testID="nearby-pharmacy-note" accessibilityRole="link" onPress={() => void Linking.openURL(kakaoSearchUrl('pharmacy'))} style={({ pressed }) => [styles.note, pressed && styles.pressed]}>
        <Text variant="caption" color={color.text.body} style={styles.grow}>{tx('약국은 이 지도에 일부만 있어요. 실제로는 훨씬 가까이 있을 수 있어요.', 'Only some pharmacies are on this map. There may be one much closer.')}</Text>
        <Text variant="caption" weight="bold">{tx('카카오맵 ›', 'Kakao Map ›')}</Text>
      </Pressable>
    ) : null}

    {locationState !== 'granted' ? (
      <View style={styles.card}>
        <Text variant="body" weight="bold">{locationState === 'detecting' ? tx('위치를 확인하고 있어요…', 'Finding your location…') : tx('위치를 켜면 가까운 곳을 보여 드려요', 'Turn on location to see places near you')}</Text>
        {locationState === 'denied' ? (
          <>
            <Text variant="caption" color={color.text.body}>{tx('거리는 이 기기 안에서 세요. 길 안내를 누를 때만 길찾기에 보내요.', 'Distances are measured on this device. We send your location only when you tap Directions.')}</Text>
            <Button label={canAskAgain ? tx('내 위치 켜기', 'Turn on location') : tx('설정에서 위치 허용하기', 'Allow location in Settings')} onPress={() => void ask()} />
          </>
        ) : null}
      </View>
    ) : (
      <>
        <View style={styles.mapWrap}>
          <RouteMap stops={stops} routes={NO_ROUTES} selectedId={selectedId} onSelect={setSelectedId} focusSelected currentLocation={coords} height={220} />
        </View>
        {places.length === 0 ? (
          <View style={styles.card}><Text variant="body" weight="bold">{txf(tx, '5km 안에서 지도 자료에 있는 %s을 못 찾았어요', 'No %s within 5 km in our map data', tx(...KIND_LABEL[kind]))}</Text><Text variant="caption" color={color.text.body}>{tx('아래 카카오맵에서 더 찾아보세요.', 'Try Kakao Map below.')}</Text></View>
        ) : (
          <View style={styles.list}>
            {places.map((place, index) => (
              <HelpRow key={idOf(place, index)} place={place} number={index + 1} selected={selectedId === idOf(place, index)} onSelect={() => setSelectedId(idOf(place, index))} onDirections={() => directions(place)} language={language} tx={tx} />
            ))}
          </View>
        )}
      </>
    )}

    <Pressable testID="nearby-kakao" accessibilityRole="link" onPress={() => void Linking.openURL(kakaoSearchUrl(kind))} style={({ pressed }) => [styles.more, pressed && styles.pressed]}>
      <View style={styles.grow}>
        <Text variant="body" weight="bold">{txf(tx, '카카오맵에서 %s 더 찾기', 'Find more %s on Kakao Map', tx(...KIND_LABEL[kind]))}</Text>
        <Text variant="caption" color={color.text.body}>{tx('여기 지도에 없는 곳도 있어요. 카카오맵은 더 많은 곳을 보여 줘요.', 'Some places are missing from this map. Kakao Map shows more.')}</Text>
      </View>
      <Text variant="title" color={color.text.muted}>›</Text>
    </Pressable>

    <Text variant="caption" color={color.text.muted} style={styles.foot}>{tx('지도 자료: © OpenStreetMap 기여자 · 거리는 직선이에요 · 문을 열었는지는 가기 전에 전화로 확인하세요', 'Map data: © OpenStreetMap contributors · distances are straight-line · call ahead to check they are open')}</Text>
  </Screen>;
}

function HelpRow({ place, number, selected, onSelect, onDirections, language, tx }: {
  place: NearbyHelp; number: number; selected: boolean; onSelect: () => void; onDirections: () => void; language: string; tx: Tx;
}) {
  // 외국어 화면: 읽는 이름을 크게, 간판과 같은 한국어를 아래에 — 길에서 간판과 맞춰 본다.
  const foreign = language !== 'ko';
  const title = foreign ? place.nameEn ?? romanizeKorean(place.name) ?? place.name : place.name;
  return (
    <View style={[styles.row, selected && styles.rowOn]}>
      <Pressable accessibilityRole="button" accessibilityState={{ selected }} accessibilityLabel={txf(tx, '%s 지도에서 보기', 'Show %s on the map', title)} onPress={onSelect} style={styles.rowHead}>
        <View style={styles.pin}><Text variant="caption" weight="bold" color={color.text.onAction}>{number}</Text></View>
        <View style={styles.grow}>
          <Text variant="body" weight="bold">{title}</Text>
          {foreign && title !== place.name ? <Text variant="caption" color={color.text.body}>{place.name}</Text> : null}
          <Text variant="caption" color={color.text.muted}>{[txf(tx, '직선 %s', '%s straight-line', distanceText(place.distanceM)), place.emergency ? tx('응급실', 'Emergency room') : null, place.hours ? tx('운영시간 정보 있음', 'Hours listed') : null].filter(Boolean).join(' · ')}</Text>
        </View>
      </Pressable>
      <View style={styles.rowActions}>
        {place.phone ? <Button label={tx('전화', 'Call')} variant="tertiary" compact onPress={() => void Linking.openURL(telOf(place.phone!))} /> : null}
        <Button label={tx('길 안내', 'Directions')} variant="secondary" compact onPress={onDirections} />
      </View>
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
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], padding: spacing[3], borderRadius: radius.lg, backgroundColor: color.surface.card, borderWidth: 1.5, borderColor: color.surface.card },
  rowOn: { borderColor: color.text.heading },
  // 한 줄 — 번호 · 이름/거리 · 버튼. 버튼을 아래 줄로 내리면 칸마다 빈 자리가 커서 다섯 곳이 한 화면에 안 들어왔다.
  rowHead: { flex: 1, minWidth: 0, flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  pin: { width: 26, height: 26, borderRadius: radius.full, backgroundColor: color.text.heading, alignItems: 'center', justifyContent: 'center' },
  rowActions: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  note: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[3], marginBottom: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },
  more: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  foot: { marginTop: spacing[4], marginBottom: spacing[8] },
});
