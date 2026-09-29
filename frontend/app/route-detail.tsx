// 경로 상세·내비 화면 —/-208(상세설계서 v2 P-15). 좁은 폭(360px)부터 쌓는다
// 제목 → 수단 탭 → 지도 → 요약 → 단계별 안내 → 액션 버튼. 1024px 이상에서는 왼쪽 안내 + 오른쪽 지도
// 2열로 바뀐다(작업 내용 4번, breakpoint.md = 1023).
//
// 🔴 수단을 나란히 비교한다 — S15P21E201-1831. 전에는 한 수단만 받고 지도에 직선을 그었고, 대중교통이면
//    「단계별 안내는 이 앱에서 못 드려요」라고 적었다. 서버는 이미 버스·지하철 노선 단계와 택시비와 걷는 길을 준다
//    (src/field/routeLegs.ts 머리). 셋을 한꺼번에 받아 탭에 걸리는 시간·택시비를 적고, 고른 수단의 실제 길을 그린다.
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { askRideNotifications, RidePanel } from '@/field/RidePanel';
import { useLocationGate } from '@/personalization/useLocationGate';
import { usableFix } from '@/trip/page/autoArrival';
import { useLiveLocation } from '@/trip/page/useLiveLocation';
import { Screen } from '@/components/Screen';
import { Card } from '@/components/Card';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { Button } from '@/components/Button';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import { RouteMap, type MapRouteLayer } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { getRouteDirections, type RouteDirections, type RouteDirectionsResult, type TravelMode } from '@/map/routeDirections';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';
import { distanceText } from '@/field/subwayStations';
import { estimateReasonText, formatDuration, modeFareLine, parseTransitGuidance, parseTravelMode, ROUTE_MODES, toMapPath, transitStepKind } from '@/field/routeLegs';

const MODE_LABEL: Record<TravelMode, readonly [string, string]> = {
  CAR: ['택시·자동차', 'Taxi · car'],
  TRANSIT: ['대중교통', 'Transit'],
  WALK: ['도보', 'Walk'],
};

function parseNumber(value: string | string[] | undefined): number | null {
  const raw = Array.isArray(value) ? value[0] : value;
  const parsed = raw != null ? Number(raw) : NaN;
  return Number.isFinite(parsed) ? parsed : null;
}

function parseText(value: string | string[] | undefined): string | undefined {
  const raw = Array.isArray(value) ? value[0] : value;
  return raw && raw.length ? raw : undefined;
}

export default function RouteDetail() {
  const { tx } = useI18n();
  const router = useRouter();
  const { accessToken } = useAuth();
  const params = useLocalSearchParams<{ originLat?: string; originLng?: string; originName?: string; destLat?: string; destLng?: string; destName?: string; destPlaceId?: string; mode?: string }>();

  const originLat = parseNumber(params.originLat);
  const originLng = parseNumber(params.originLng);
  const destLat = parseNumber(params.destLat);
  const destLng = parseNumber(params.destLng);
  const originName = parseText(params.originName) ?? tx('출발지', 'Origin');
  const destName = parseText(params.destName) ?? tx('도착지', 'Destination');
  const destPlaceId = parseText(params.destPlaceId);
  // 부르는 쪽이 여행의 이동수단을 넘긴다. 안 넘기면 대중교통 — 이제 서버가 노선망으로 찾는다(S15P21E201-1831).
  const [mode, setMode] = useState<TravelMode>(parseTravelMode(parseText(params.mode)) ?? 'TRANSIT');
  // 탑승 중 — S15P21E201-1837. 위치는 「탑승 시작」을 누른 뒤에만 켠다(일정 화면의 출발과 같은 규칙: 왜 필요한지 분명할 때 묻는다).
  const [riding, setRiding] = useState(false);
  const locationGate = useLocationGate(accessToken);
  const live = useLiveLocation(riding);
  const startRide = async () => {
    if (!(await locationGate.request())) return;
    void askRideNotifications();
    setRiding(true);
  };
  // 대중교통이 아닌 탭으로 옮기면 탑승을 끝낸다 — 보이지 않는 곳에서 위치를 계속 쓰지 않는다.
  useEffect(() => { if (mode !== 'TRANSIT') setRiding(false); }, [mode]);

  const [results, setResults] = useState<Partial<Record<TravelMode, RouteDirectionsResult>>>({});

  const hasCoords = originLat != null && originLng != null && destLat != null && destLng != null;

  useEffect(() => {
    if (!hasCoords) return;
    let active = true;
    setResults({});
    // 셋을 한꺼번에 묻는다 — 탭마다 걸리는 시간이 적혀 있어야 누르기 전에 비교가 된다.
    for (const each of ROUTE_MODES) {
      void getRouteDirections({ originLat: originLat!, originLng: originLng!, destLat: destLat!, destLng: destLng!, mode: each }, accessToken).then((next) => {
        if (active) setResults((prev) => ({ ...prev, [each]: next }));
      });
    }
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [originLat, originLng, destLat, destLng, accessToken]);

  const stops: MapStop[] = useMemo(() => {
    if (!hasCoords) return [];
    return [
      { id: 'origin', number: 1, name: originName, latitude: originLat!, longitude: originLng! },
      { id: 'dest', number: 2, name: destName, latitude: destLat!, longitude: destLng! },
    ];
  }, [hasCoords, originLat, originLng, originName, destLat, destLng, destName]);

  // 데스크톱 판인가 — 폭만이 아니라 폴드 펼침 가로까지, 판정은 useLayout 한 곳(S15P21E201-1563).
  const twoColumn = useLayout().desktop;
  const result = results[mode];
  const loading = hasCoords && !result;
  const directions = result?.state === 'success' ? result.directions : null;

  // 받은 길이 있으면 그 길을, 없으면(받는 중·실패) 두 점을 옅은 직선으로 — 직선을 실제 길인 척 그리지 않는다.
  const routes: MapRouteLayer[] = useMemo(() => {
    const path = directions ? toMapPath(directions.path) : [];
    return [{
      id: `route-${mode}`,
      color: color.text.heading,
      stops,
      ...(path.length >= 2 ? { path, estimated: directions?.estimated !== false } : { estimated: true }),
      ...(directions?.pieces?.length ? { pieces: directions.pieces } : {}),
    }];
  }, [directions, mode, stops]);

  function tabLine(each: TravelMode): string {
    const got = results[each];
    if (!got) return tx('찾는 중…', 'Searching…');
    if (got.state !== 'success') return tx('못 찾았어요', 'Not found');
    return formatDuration(got.directions.durationMin, tx);
  }

  function fareLine(each: TravelMode): string | null {
    const got = results[each];
    return got?.state === 'success' ? modeFareLine(got.directions, tx) : null;
  }

  const liveFix = riding && live.fix && usableFix(live.fix) ? { latitude: live.fix.latitude, longitude: live.fix.longitude } : null;
  const map = (height: number) => <RouteMap stops={stops} selectedId="dest" onSelect={() => {}} routes={routes} height={height} refitKey={mode} currentLocation={liveFix} />;

  return (
    <Screen scroll wide>
      <View style={styles.topBar}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('이전 화면으로 이동', 'Go back')} onPress={() => router.canGoBack() ? router.back() : router.replace('/home')} style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
          <Text variant="title" weight="bold">‹</Text>
        </Pressable>
      </View>

      <View style={styles.heading}>
        <Eyebrow>{tx('이동 경로', 'Route')}</Eyebrow>
        <Text variant="display" weight="bold">{txf(tx, '%s → %s', '%s → %s', originName, destName)}</Text>
      </View>

      {!hasCoords ? (
        <Card style={styles.stateCard}><Text variant="title" weight="bold">{tx('경로 정보가 없어요', 'No route information')}</Text><Text color={color.text.body}>{tx('출발지와 도착지 좌표를 확인할 수 없어요.', "We couldn't find the origin and destination coordinates.")}</Text></Card>
      ) : (
        <>
          <View accessibilityRole="tablist" style={styles.tabs}>
            {ROUTE_MODES.map((each) => {
              const selected = each === mode;
              return (
                <Pressable
                  key={each}
                  accessibilityRole="tab"
                  accessibilityState={{ selected }}
                  onPress={() => setMode(each)}
                  style={({ pressed }) => [styles.tab, selected && styles.tabSelected, pressed && styles.pressed]}
                >
                  <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading} numberOfLines={1}>{tx(...MODE_LABEL[each])}</Text>
                  <Text variant="micro" color={selected ? color.text.onDarkMuted : color.text.muted} numberOfLines={1}>{tabLine(each)}</Text>
                  {fareLine(each) ? <Text variant="micro" color={selected ? color.text.onDarkMuted : color.text.muted} numberOfLines={1}>{fareLine(each)}</Text> : null}
                </Pressable>
              );
            })}
          </View>

          <View style={twoColumn ? styles.columns : undefined}>
            {!twoColumn ? map(260) : null}

            <View style={twoColumn ? styles.infoColumn : styles.infoStack}>
              {loading ? (
                <Card style={styles.stateCard}><Text color={color.text.body}>{tx('경로를 불러오고 있어요…', 'Loading the route…')}</Text></Card>
              ) : null}

              {result && result.state !== 'success' ? (
                <Card style={styles.stateCard} accessibilityRole="alert"><Text variant="title" weight="bold">{tx('경로를 불러오지 못했어요', 'Could not load the route')}</Text><Text color={color.text.body}>{localizeMessage(tx, result.message)}</Text></Card>
              ) : null}

              {mode === 'TRANSIT' && directions?.mode === 'TRANSIT' ? (
                <RidePanel directions={directions} riding={riding} live={live} onStart={() => void startRide()} onStop={() => setRiding(false)} tx={tx} />
              ) : null}
              {directions ? <Summary directions={directions} tx={tx} /> : null}
              {directions ? <Steps directions={directions} originName={originName} destName={destName} tx={tx} /> : null}

              {/* 🔴 외부 지도 앱(카카오맵·구글맵)으로 보내지 않는다 — S15P21E201-1831. 이 앱이 푸는 문제가 「구글맵은 한국
                  대중교통·도보 길찾기를 못 하고, 카카오맵은 한국어뿐이다」라서(기획서 v7), 그 앱들로 보내면 풀던 문제로 되돌려 보낸다.
                  길·타는 법·택시비는 이 화면이 보여준다.
                  🔴 택시 카드는 택시·자동차 탭에서만 — 대중교통·도보를 고른 사람에게 「택시 기사에게 보여주기」는 맥락이 안 맞는다. */}
              {mode === 'CAR' && destPlaceId ? (
                <View style={styles.actions}>
                  <Button label={tx('택시 기사에게 보여주기', 'Show to a taxi driver')} onPress={() => router.push(`/taxi-card/${destPlaceId}`)} containerStyle={styles.actionButton} />
                </View>
              ) : null}
            </View>

            {twoColumn ? <View style={styles.mapColumn}>{map(480)}</View> : null}
          </View>
        </>
      )}
      {locationGate.sheet}
    </Screen>
  );
}

type Tx = (ko: string, en: string) => string;

function Summary({ directions, tx }: { directions: RouteDirections; tx: Tx }) {
  const reason = estimateReasonText(directions.estimateReason, tx);
  return (
    <Card style={styles.summaryCard}>
      <View style={styles.summaryHeader}>
        {/* 대중교통을 물었는데 걷기로 답이 오면(걷는 편이 빠를 때) 받은 수단 이름을 적는다. */}
        <Text variant="title" weight="bold">{tx(...MODE_LABEL[directions.mode])}</Text>
        {directions.estimated ? (
          <View style={styles.estimatedBadge}><Text variant="caption" weight="bold" color={color.text.heading}>{tx('예상', 'Estimated')}</Text></View>
        ) : null}
      </View>
      <Text color={color.text.body}>{`${formatDuration(directions.durationMin, tx)} · ${(directions.distanceM / 1000).toFixed(1)}km`}</Text>
      {/* 요금은 위 탭의 둘째 줄이 말한다 — 여기 또 적으면 같은 숫자가 두 번 나왔다(UI 캔버스 ⑭). */}
      {directions.tollFareKrw != null ? <Text color={color.text.body}>{tx(`통행료 약 ${directions.tollFareKrw.toLocaleString()}원`, `Estimated toll ₩${directions.tollFareKrw.toLocaleString()}`)}</Text> : null}
      {directions.mode === 'TRANSIT' && directions.transferCount != null ? (
        <Text color={color.text.body}>{directions.transferCount === 0 ? tx('갈아타지 않아요', 'No transfers') : tx(`환승 ${directions.transferCount}회`, `${directions.transferCount} transfer(s)`)}</Text>
      ) : null}
      {reason ? <Text variant="caption" color={color.text.muted}>{reason}</Text> : null}
    </Card>
  );
}

function Steps({ directions, originName, destName, tx }: { directions: RouteDirections; originName: string; destName: string; tx: Tx }) {
  if (directions.mode === 'WALK' || directions.steps.length === 0) {
    // 🔴 걷기는 단계 안내가 없다 — 길은 지도에 그렸다. 「못 드려요」라고 말하지 않는다. 길을 못 찾았을 때만 그렇게 말한다.
    if (directions.mode !== 'WALK' || !directions.estimated) return null;
    return (
      <Card style={styles.stepsCard}>
        <Text color={color.text.body}>{tx('걷는 길을 찾지 못해 직선거리로 어림했어요.', 'We could not find a walking path, so this is estimated from the straight-line distance.')}</Text>
      </Card>
    );
  }

  if (directions.mode === 'TRANSIT') {
    return (
      <Card style={styles.stepsCard}>
        <Text variant="title" weight="bold" style={styles.stepsTitle}>{tx('타는 법', 'How to ride')}</Text>
        <View style={styles.stepList}>
          <View style={styles.stepRow}>
            <View style={styles.endDot} />
            <Text weight="bold" style={styles.grow} numberOfLines={2}>{originName}</Text>
          </View>
          {directions.steps.map((step, index) => {
            const kind = transitStepKind(step);
            return (
              <View key={`${step.name}-${index}`} style={styles.stepRow}>
                <View style={[styles.rideChip, kind === 'walk' && styles.walkChip, kind === 'subway' && styles.subwayChip]}>
                  <Text variant="micro" weight="bold" color={kind === 'walk' ? color.text.heading : color.text.onAction} numberOfLines={1}>
                    {kind === 'walk' ? tx('도보', 'Walk') : step.name}
                  </Text>
                </View>
                <StepText guidance={step.guidance} tx={tx} />
                <Text variant="caption" color={color.text.muted}>{formatDuration(step.durationMin, tx)}</Text>
              </View>
            );
          })}
          <View style={styles.stepRow}>
            <View style={[styles.endDot, styles.endDotFilled]} />
            <Text weight="bold" style={styles.grow} numberOfLines={2}>{destName}</Text>
          </View>
        </View>
      </Card>
    );
  }

  // 자동차 — 갈림길 안내. 0m 짜리 「출발지·목적지」 줄은 위아래 제목과 같은 말이라 뺀다.
  const turns = directions.steps.filter((step) => step.distanceM > 0);
  return (
    <Card style={styles.stepsCard}>
      <Text variant="title" weight="bold" style={styles.stepsTitle}>{tx('단계별 안내', 'Step-by-step')}</Text>
      <View style={styles.stepList}>
        {turns.map((step, index) => (
          <View key={`${step.name}-${index}`} style={styles.stepRow}>
            <View style={styles.stepMarker}><Text variant="caption" weight="bold" color={color.text.onAction}>{index + 1}</Text></View>
            <View style={styles.grow}>
              {/* 「목적지 / 목적지」처럼 이름과 안내가 같으면 한 번만 적는다. */}
              {step.name && step.name !== step.guidance ? <Text weight="bold">{step.name}</Text> : null}
              <Text variant="caption" color={color.text.muted}>{step.guidance}</Text>
            </View>
            <Text variant="caption" color={color.text.muted}>{distanceText(step.distanceM)}</Text>
          </View>
        ))}
      </View>
    </Card>
  );
}

function StepText({ guidance, tx }: { guidance: string; tx: Tx }) {
  const parsed = parseTransitGuidance(guidance);
  if (!parsed) return <Text variant="caption" color={color.text.body} style={styles.grow}>{guidance}</Text>;
  if (parsed.kind === 'walk') {
    return <Text variant="caption" color={color.text.body} style={styles.grow}>{txf(tx, '%s → %s 걸어서 갈아타요', 'Walk from %s to %s to transfer', parsed.from, parsed.to)}</Text>;
  }
  return (
    <View style={styles.grow}>
      <Text variant="caption" weight="bold" color={color.text.heading}>{txf(tx, '%s에서 타요', 'Board at %s', parsed.from)}</Text>
      <Text variant="caption" color={color.text.body}>{txf(tx, '%s에서 내려요', 'Get off at %s', parsed.to)}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  heading: { gap: spacing[2], marginBottom: spacing[4] },
  tabs: { flexDirection: 'row', gap: spacing[2], marginBottom: spacing[4] },
  tab: { flex: 1, minHeight: 56, paddingHorizontal: spacing[2], paddingVertical: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center', gap: 2 },
  tabSelected: { backgroundColor: color.action.secondary },
  stateCard: { gap: spacing[3], alignItems: 'center' },
  columns: { flexDirection: 'row', gap: spacing[6], alignItems: 'flex-start' },
  mapColumn: { flex: 1 },
  infoColumn: { flex: 1, gap: spacing[4] },
  infoStack: { gap: spacing[4], marginTop: spacing[4] },
  summaryCard: { gap: spacing[2] },
  summaryHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  estimatedBadge: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.tint },
  stepsCard: { gap: spacing[2] },
  stepsTitle: { marginBottom: spacing[1] },
  stepList: { gap: spacing[3] },
  stepRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  stepMarker: { width: 26, height: 26, borderRadius: radius.full, backgroundColor: color.action.secondary, alignItems: 'center', justifyContent: 'center' },
  rideChip: { minWidth: 56, minHeight: 28, paddingHorizontal: spacing[2], borderRadius: radius.sm, backgroundColor: color.state.info, alignItems: 'center', justifyContent: 'center' },
  subwayChip: { backgroundColor: color.state.warning },
  walkChip: { backgroundColor: color.surface.tint },
  endDot: { width: 14, height: 14, marginHorizontal: 21, borderRadius: radius.full, borderWidth: 3, borderColor: color.action.secondary, backgroundColor: color.surface.card },
  endDotFilled: { backgroundColor: color.action.secondary },
  grow: { flex: 1 },
  actions: { gap: spacing[2] },
  actionButton: { alignSelf: 'stretch' },
});
