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
import { useCallback, useEffect, useMemo, useState } from 'react';
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
import { LockerCard, ParkingCard, PharmacyCard, ToiletCard, type FacilityActions } from '@/field/NearbyFacilityCards';
import { distanceText } from '@/field/subwayStations';
import { useI18n } from '@/i18n';
import { enPlural, txf } from '@/i18n/format';
import { readCurrentPosition } from '@/location/currentPosition';
import { syncLocationConsent } from '@/personalization/locationConsent';
import { useLocationGate } from '@/personalization/useLocationGate';
import { listAvailableMapApps } from '@/utils/externalMaps';

/** 이만큼 가까우면 걸어서 가는 길로 연다(nearby-help 와 같다). */
const WALK_ROUTE_M = 1200;

type LocationState = 'detecting' | 'granted' | 'denied';
type Load = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: NearbyFacilities };

const TAB_EMOJI: Record<FacilityKind, string> = { PARKING: '🅿️', PHARMACY: '💊', TOILET: '🚻', LOCKER: '🧳' };

/** `?kind=pharmacy` 로 특정 탭에서 열 수 있게 — 대소문자를 가리지 않는다. 모르는 값은 첫 탭. */
function kindFromParam(value: string | string[] | undefined): FacilityKind | null {
  const raw = (Array.isArray(value) ? value[0] : value)?.toUpperCase();
  return FACILITY_KINDS.find((kind) => kind === raw) ?? null;
}

export default function NearbyFacilitiesScreen() {
  const router = useRouter();
  const { tx, language } = useI18n();
  const { accessToken, ready } = useAuth();
  const locationGate = useLocationGate(accessToken);
  const params = useLocalSearchParams<{ kind?: string }>();
  const [kind, setKind] = useState<FacilityKind>(kindFromParam(params.kind) ?? 'PARKING');
  const [stayHours, setStayHours] = useState(DEFAULT_STAY_HOURS);
  const [accessibleOnly, setAccessibleOnly] = useState(false);
  const [diaperOnly, setDiaperOnly] = useState(false);
  const [radiusM, setRadiusM] = useState(DEFAULT_RADIUS_M);
  const [coords, setCoords] = useState<LatLng | null>(null);
  const [locationState, setLocationState] = useState<LocationState>('detecting');
  const [canAskAgain, setCanAskAgain] = useState(true);
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [reloadKey, setReloadKey] = useState(0);

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
      setRadiusM(DEFAULT_RADIUS_M);
    } catch {
      setLocationState('denied');
    }
  }, [locationGate.request]);

  useEffect(() => { if (ready) void restore(); }, [ready, restore]);

  // 내 위치를 모르면 서면에서 찾는다.
  const origin: LatLng = useMemo(() => coords ?? SEOMYEON, [coords]);
  const usingFallback = locationState === 'denied';

  // ── 서버 ────────────────────────────────────────────────────────────────

  useEffect(() => {
    // 위치를 읽는 동안에는 묻지 않는다 — 서면으로 한 번 찾고 내 위치로 또 찾는 두 번 호출을 피한다.
    if (!ready || locationState === 'detecting') return;
    const controller = new AbortController();
    let alive = true;
    setLoad({ status: 'loading' });
    getNearbyFacilities(kind, origin, accessToken, {
      radiusM,
      stayMinutes: kind === 'PARKING' ? stayHours * 60 : undefined,
      accessible: kind === 'TOILET' && accessibleOnly,
      diaper: kind === 'TOILET' && diaperOnly,
      signal: controller.signal,
    }).then((data) => {
      if (alive) setLoad({ status: 'ready', data });
    }).catch(() => {
      if (alive) setLoad({ status: 'error' });
    });
    return () => { alive = false; controller.abort(); };
  }, [ready, locationState, kind, origin, accessToken, radiusM, stayHours, accessibleOnly, diaperOnly, reloadKey]);

  const selectKind = (next: FacilityKind) => {
    if (next === kind) return;
    setKind(next);
    setRadiusM(DEFAULT_RADIUS_M);
  };

  // ── 가는 법 ─────────────────────────────────────────────────────────────

  const actionsFor = (item: FacilityItem): FacilityActions => ({
    onRoute: coords
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

  const renderCard = (item: FacilityItem) => {
    const actions = actionsFor(item);
    switch (item.kind) {
      case 'PARKING': return <ParkingCard key={item.id} item={item} actions={actions} stayHours={stayHours} />;
      case 'PHARMACY': return <PharmacyCard key={item.id} item={item} actions={actions} onCall={call} />;
      case 'TOILET': return <ToiletCard key={item.id} item={item} actions={actions} />;
      case 'LOCKER': return <LockerCard key={item.id} item={item} actions={actions} />;
    }
  };

  // ── 글 ──────────────────────────────────────────────────────────────────

  const tabLabel = (each: FacilityKind) => {
    switch (each) {
      case 'PARKING': return tx('주차장', 'Parking');
      case 'PHARMACY': return tx('약국', 'Pharmacy');
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
      <Text color={color.text.body} style={styles.lead}>{tx('가까운 주차장 · 약국 · 화장실 · 짐보관함을 찾아 드려요.', 'Find nearby parking, pharmacies, restrooms and luggage lockers.')}</Text>

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

      {usingFallback ? (
        <View testID="facility-fallback-notice" style={styles.notice}>
          <Text variant="caption" weight="bold" color={color.state.info}>{tx('내 위치를 몰라 부산 중심(서면)에서 찾았어요. 거리도 그 기준이에요.', 'We could not get your location, so we searched from central Busan (Seomyeon). Distances use that point.')}</Text>
          {canAskAgain
            ? <Button testID="facility-ask-location" label={tx('내 위치로 다시 찾기', 'Search from my location')} variant="tertiary" compact onPress={() => void ask()} />
            : <Button testID="facility-open-settings" label={tx('설정에서 위치 허용하기', 'Allow location in Settings')} variant="tertiary" compact onPress={() => void Linking.openSettings()} />}
        </View>
      ) : null}
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

      {load.status === 'loading' ? (
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
            : null}
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
  list: { gap: spacing[2], marginTop: spacing[2], marginBottom: spacing[3] },
  foot: { marginTop: spacing[4], marginBottom: spacing[8] },
});
