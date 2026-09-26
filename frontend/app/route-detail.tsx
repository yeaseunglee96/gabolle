// 경로 상세·내비 화면 —/-208(상세설계서 v2 P-15). 좁은 폭(360px)부터 쌓는다
// 제목 → 지도 → 요약 → 단계별 안내 → 액션 버튼. 1024px 이상에서는 왼쪽 안내 + 오른쪽 지도
// 2열로 바뀐다(작업 내용 4번, breakpoint.md = 1023).
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { Screen } from '@/components/Screen';
import { Card } from '@/components/Card';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { Button } from '@/components/Button';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { useLayout } from '@/layout/useLayout';
import { RouteMap } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { getRouteDirections, type RouteDirectionsResult, type TravelMode } from '@/map/routeDirections';
import { listAvailableRouteMapApps, type AvailableMapProvider } from '@/utils/externalMaps';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';

const MODE_LABEL: Record<TravelMode, readonly [string, string]> = {
  CAR: ['자동차', 'Car'],
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
  // — 기본값이 대중교통이면 언제나 직선 어림값이 나온다.
  const requestedMode: TravelMode = (parseText(params.mode) as TravelMode | undefined) ?? 'CAR';

  const [result, setResult] = useState<RouteDirectionsResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [mapApps, setMapApps] = useState<AvailableMapProvider[]>([]);

  const hasCoords = originLat != null && originLng != null && destLat != null && destLng != null;

  useEffect(() => {
    if (!hasCoords) { setLoading(false); return; }
    let active = true;
    setLoading(true);
    void getRouteDirections({ originLat: originLat!, originLng: originLng!, destLat: destLat!, destLng: destLng!, mode: requestedMode }, accessToken).then((next) => {
      if (active) { setResult(next); setLoading(false); }
    });
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [originLat, originLng, destLat, destLng, requestedMode, accessToken]);

  useEffect(() => {
    if (!hasCoords) return;
    let active = true;
    void listAvailableRouteMapApps({ originLat: originLat!, originLng: originLng!, destLat: destLat!, destLng: destLng!, destName }).then((apps) => { if (active) setMapApps(apps); });
    return () => { active = false; };
  }, [originLat, originLng, destLat, destLng, destName, hasCoords]);

  const stops: MapStop[] = useMemo(() => {
    if (!hasCoords) return [];
    return [
      { id: 'origin', number: 1, name: originName, latitude: originLat!, longitude: originLng! },
      { id: 'dest', number: 2, name: destName, latitude: destLat!, longitude: destLng! },
    ];
  }, [hasCoords, originLat, originLng, originName, destLat, destLng, destName]);

  // 데스크톱 판인가 — 폭만이 아니라 폴드 펼침 가로까지, 판정은 useLayout 한 곳(S15P21E201-1563).
  const twoColumn = useLayout().desktop;
  const directions = result?.state === 'success' ? result.directions : null;

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
        <View style={twoColumn ? styles.columns : undefined}>
          {!twoColumn ? <RouteMap stops={stops} selectedId="dest" onSelect={() => {}} routes={[{ id: 'route', color: color.text.heading, stops }]} height={260} /> : null}

          <View style={twoColumn ? styles.infoColumn : styles.infoStack}>
            {loading ? (
              <Card style={styles.stateCard}><Text color={color.text.body}>{tx('경로를 불러오고 있어요…', 'Loading the route…')}</Text></Card>
            ) : null}

            {!loading && result && result.state !== 'success' ? (
              <Card style={styles.stateCard} accessibilityRole="alert"><Text variant="title" weight="bold">{tx('경로를 불러오지 못했어요', 'Could not load the route')}</Text><Text color={color.text.body}>{localizeMessage(tx, result.message)}</Text></Card>
            ) : null}

            {directions ? (
              <>
                <Card style={styles.summaryCard}>
                  <View style={styles.summaryHeader}>
                    <Text variant="title" weight="bold">{tx(...MODE_LABEL[directions.mode])}</Text>
                    {directions.estimated ? (
                      <View style={styles.estimatedBadge}><Text variant="caption" weight="bold" color={color.text.heading}>{tx('예상', 'Estimated')}</Text></View>
                    ) : null}
                  </View>
                  <View style={styles.summaryRow}>
                    <Text color={color.text.body}>{txf(tx, '%s분 · %skm', '%s min · %skm', directions.durationMin, (directions.distanceM / 1000).toFixed(1))}</Text>
                  </View>
                  {directions.taxiFareKrw != null ? <Text color={color.text.body}>{tx(`택시 요금 약 ${directions.taxiFareKrw.toLocaleString()}원`, `Estimated taxi fare ₩${directions.taxiFareKrw.toLocaleString()}`)}</Text> : null}
                  {directions.tollFareKrw != null ? <Text color={color.text.body}>{tx(`통행료 약 ${directions.tollFareKrw.toLocaleString()}원`, `Estimated toll ₩${directions.tollFareKrw.toLocaleString()}`)}</Text> : null}
                  {directions.transferCount != null ? <Text color={color.text.body}>{tx(`환승 ${directions.transferCount}회`, `${directions.transferCount} transfer(s)`)}</Text> : null}
                  {directions.estimateReason ? <Text variant="caption" color={color.text.muted}>{directions.estimateReason}</Text> : null}
                </Card>

                <Card style={styles.stepsCard}>
                  <Text variant="title" weight="bold" style={styles.stepsTitle}>{tx('단계별 안내', 'Step-by-step')}</Text>
                  {directions.steps.length === 0 ? (
                    <Text color={color.text.body}>{tx('단계별 안내는 이 앱에서 못 드려요. 아래 지도 앱 버튼을 누르면 대중교통 경로를 볼 수 있어요.', "We can not give step-by-step guidance here. Use the map app buttons below to see transit routes.")}</Text>
                  ) : (
                    <View style={styles.stepList}>
                      {directions.steps.map((step, index) => (
                        <View key={`${step.name}-${index}`} style={styles.stepRow}>
                          <View style={styles.stepMarker}><Text variant="caption" weight="bold" color={color.text.onAction}>{index + 1}</Text></View>
                          <View style={styles.grow}>
                            <Text weight="bold">{step.name}</Text>
                            <Text variant="caption" color={color.text.muted}>{step.guidance}</Text>
                          </View>
                          <Text variant="caption" color={color.text.muted}>{tx(`${step.distanceM}m`, `${step.distanceM}m`)}</Text>
                        </View>
                      ))}
                    </View>
                  )}
                </Card>
              </>
            ) : null}

            <View style={styles.actions}>
              {destPlaceId ? <Button label={tx('택시 기사에게 보여주기', 'Show to a taxi driver')} onPress={() => router.push(`/taxi-card/${destPlaceId}`)} containerStyle={styles.actionButton} /> : null}
              {mapApps.map((app) => (
                <Button key={app.key} variant="tertiary" label={txf(tx, '%s에서 경로 열기', 'Open route in %s', tx(app.labelKo, app.labelEn))} onPress={() => void app.open()} containerStyle={styles.actionButton} />
              ))}
            </View>
          </View>

          {twoColumn ? (
            <View style={styles.mapColumn}>
              <RouteMap stops={stops} selectedId="dest" onSelect={() => {}} routes={[{ id: 'route', color: color.text.heading, stops }]} height={480} />
            </View>
          ) : null}
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  topBar: { minHeight: 52, marginTop: spacing[6], flexDirection: 'row', alignItems: 'center', marginBottom: spacing[3] },
  back: { width: 44, height: 44, borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.surface.card },
  pressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  heading: { gap: spacing[2], marginBottom: spacing[6] },
  stateCard: { gap: spacing[3], alignItems: 'center' },
  columns: { flexDirection: 'row', gap: spacing[6], alignItems: 'flex-start' },
  mapColumn: { flex: 1 },
  infoColumn: { flex: 1, gap: spacing[4] },
  infoStack: { gap: spacing[4], marginTop: spacing[4] },
  summaryCard: { gap: spacing[2] },
  summaryHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  summaryRow: { flexDirection: 'row' },
  estimatedBadge: { paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.full, backgroundColor: color.surface.tint },
  stepsCard: { gap: spacing[2] },
  stepsTitle: { marginBottom: spacing[1] },
  stepList: { gap: spacing[3] }, mapAppsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2], marginTop: spacing[1] }, mapAppButton: { minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, borderWidth: 1, borderColor: color.brand.navy, alignItems: 'center', justifyContent: 'center' },
  stepRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  stepMarker: { width: 26, height: 26, borderRadius: radius.full, backgroundColor: color.action.secondary, alignItems: 'center', justifyContent: 'center' },
  grow: { flex: 1 },
  actions: { gap: spacing[2] },
  actionButton: { alignSelf: 'stretch' },
});
