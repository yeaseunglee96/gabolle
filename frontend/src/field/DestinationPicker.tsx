// 「어디로 가세요?」 — 주변 버스 화면 맨 위의 목적지 칸. S15P21E201-1834.
//
// 🔴 버스는 목적지가 있어야 탄다. 목적지를 고르면 내 위치에서 대중교통·택시 길을 받아 「무엇을, 어디서, 언제」 탈지를
//    한 줄로 답한다. 타는 정류장이 주변 정류장 목록에 있으면 그 버스의 실시간 도착을 붙인다(goFromHere.rideSummary).
import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { arrivalLabel, type BusStop } from '@/field/busArrivals';
import { busLinesOf, loadTodayTargets, rideSummary, searchDestinations, type Destination, type TodayTargets } from '@/field/goFromHere';
import { formatDuration } from '@/field/routeLegs';
import { taxiCardHref } from '@/field/taxiDestination';
import { txf } from '@/i18n/format';
import { useI18n } from '@/i18n';
import { addressForLanguage } from '@/discovery/localAddress';
import { getRouteDirections, type RouteDirections, type RouteDirectionsResult } from '@/map/routeDirections';

type Tx = (ko: string, en: string) => string;
type LatLng = { latitude: number; longitude: number };

/** 검색을 쏘기 전 기다리는 시간 — 글자마다 서버를 부르지 않게. */
const SEARCH_DELAY_MS = 350;

export function DestinationPicker({ origin, stops, accessToken, tx, onLinesChange, onAskLocation, askLabel }: {
  /** 내 위치. 모르면 null — 🔴 부산 중심에서 찾은 길은 틀리므로 길을 찾지 않는다. */
  origin: LatLng | null;
  stops: BusStop[];
  accessToken: string | null;
  tx: Tx;
  /** 이 길에서 타는 버스 번호들 — 주변 정류장 목록이 강조한다. */
  onLinesChange: (lines: Set<string>) => void;
  onAskLocation: () => void;
  askLabel: string;
}) {
  const { language } = useI18n();
  const router = useRouter();
  const [targets, setTargets] = useState<TodayTargets | null>(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Destination[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [dest, setDest] = useState<Destination | null>(null);
  const [transit, setTransit] = useState<RouteDirectionsResult | null>(null);
  const [car, setCar] = useState<RouteDirectionsResult | null>(null);

  useEffect(() => {
    let alive = true;
    void loadTodayTargets(accessToken).then((got) => { if (alive) setTargets(got); }).catch(() => undefined);
    return () => { alive = false; };
  }, [accessToken]);

  // 검색 — 두 글자부터, 멈춘 뒤 한 번. 앞 요청은 끊는다.
  const controllerRef = useRef<AbortController | null>(null);
  useEffect(() => {
    const trimmed = query.trim();
    controllerRef.current?.abort();
    if (trimmed.length < 2) { setResults(null); setSearching(false); return undefined; }
    const controller = new AbortController();
    controllerRef.current = controller;
    setSearching(true);
    const timer = setTimeout(() => {
      void searchDestinations(trimmed, accessToken, controller.signal).then((items) => {
        if (controller.signal.aborted) return;
        setResults(items);
        setSearching(false);
      });
    }, SEARCH_DELAY_MS);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [query, accessToken]);

  // 목적지와 내 위치가 둘 다 있을 때만 길을 찾는다.
  const originKey = origin ? `${origin.latitude.toFixed(4)},${origin.longitude.toFixed(4)}` : '';
  useEffect(() => {
    setTransit(null); setCar(null);
    if (!dest || !origin) return undefined;
    let alive = true;
    const base = { originLat: origin.latitude, originLng: origin.longitude, destLat: dest.latitude, destLng: dest.longitude };
    void getRouteDirections({ ...base, mode: 'TRANSIT' }, accessToken).then((next) => { if (alive) setTransit(next); });
    void getRouteDirections({ ...base, mode: 'CAR' }, accessToken).then((next) => { if (alive) setCar(next); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dest?.key, originKey, accessToken]);

  // 🔴 서버가 길을 못 찾으면 직선 거리로 어림한 시간만 준다(provider STRAIGHT_LINE — 운영 해운대→자갈치 「59분」 실측).
  //    그것을 「총 59분」이라 적으면 진짜 걸리는 시간처럼 읽힌다. 버스 번호도 타는 곳도 없는 숫자다 — 사실대로 말한다.
  const guessed = transit?.state === 'success' && transit.directions.provider === 'STRAIGHT_LINE' ? transit.directions : null;
  const transitDirections: RouteDirections | null = transit?.state === 'success' && !guessed ? transit.directions : null;
  const carDirections: RouteDirections | null = car?.state === 'success' ? car.directions : null;
  const ride = useMemo(() => (transitDirections ? rideSummary(transitDirections, stops) : null), [transitDirections, stops]);

  const linesKey = [...busLinesOf(transitDirections)].sort().join(',');
  useEffect(() => { onLinesChange(new Set(linesKey ? linesKey.split(',') : [])); }, [linesKey, onLinesChange]);

  const nameOf = (item: Destination) => item.name ?? (item.kind === 'lodging' ? tx('숙소', 'Your stay') : tx('도착지', 'Destination'));
  const choose = (item: Destination) => { setDest(item); setQuery(''); setResults(null); };
  const clear = () => { setDest(null); onLinesChange(new Set()); };

  const openDetail = () => {
    if (!dest || !origin) return;
    router.push({
      pathname: '/route-detail',
      params: {
        originLat: String(origin.latitude), originLng: String(origin.longitude), originName: tx('내 위치', 'My location'),
        destLat: String(dest.latitude), destLng: String(dest.longitude), destName: nameOf(dest),
        ...(dest.placeId ? { destPlaceId: dest.placeId } : {}), mode: 'TRANSIT',
      },
    });
  };

  function waitText(seconds: number) {
    const label = arrivalLabel(seconds);
    if (label.kind === 'imminent') return tx('곧 도착', 'Arriving');
    if (label.kind === 'minutes') return txf(tx, '%s분 뒤 도착', 'Arrives in %s min', label.minutes);
    return null;
  }

  const chips = [targets?.next, targets?.lodging].filter((item): item is Destination => Boolean(item));

  return (
    <View style={styles.card}>
      <Text variant="title" weight="bold">{tx('어디로 가세요?', 'Where are you going?')}</Text>

      {dest ? (
        <View style={styles.destRow}>
          <Text weight="bold" style={styles.grow} numberOfLines={1}>{txf(tx, '→ %s', '→ %s', nameOf(dest))}</Text>
          <Pressable accessibilityRole="button" accessibilityLabel={tx('목적지 바꾸기', 'Change destination')} onPress={clear} hitSlop={8} style={({ pressed }) => [styles.linkButton, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('바꾸기', 'Change')}</Text>
          </Pressable>
        </View>
      ) : (
        <>
          {chips.length ? (
            <View style={styles.chips}>
              {chips.map((item) => (
                <Pressable key={item.key} accessibilityRole="button" onPress={() => choose(item)} style={({ pressed }) => [styles.chip, pressed && styles.pressed]}>
                  <Text variant="micro" weight="bold" color={color.text.muted}>{item.kind === 'next' ? tx('다음 일정', 'Next stop') : tx('숙소', 'Your stay')}</Text>
                  <Text variant="caption" weight="bold" numberOfLines={1}>
                    {item.kind === 'next' && item.startsAt ? `${nameOf(item)} · ${item.startsAt.slice(11, 16)}` : nameOf(item)}
                  </Text>
                </Pressable>
              ))}
            </View>
          ) : null}
          <TextInput
            accessibilityLabel={tx('갈 곳 검색', 'Search for a destination')}
            value={query}
            onChangeText={setQuery}
            placeholder={tx('장소 이름으로 찾기 (예: 자갈치시장)', 'Search by place name (e.g. Jagalchi Market)')}
            placeholderTextColor={color.text.muted}
            returnKeyType="search"
            style={styles.input}
          />
          {searching ? <Text variant="caption" color={color.text.muted}>{tx('찾는 중…', 'Searching…')}</Text> : null}
          {results && !searching ? (
            results.length === 0
              ? <Text variant="caption" color={color.text.muted}>{tx('그 이름의 장소를 못 찾았어요.', 'No place found with that name.')}</Text>
              : (
                <View style={styles.results}>
                  {results.map((item) => (
                    <Pressable key={item.key} accessibilityRole="button" onPress={() => choose(item)} style={({ pressed }) => [styles.resultRow, pressed && styles.pressed]}>
                      <Text weight="bold" numberOfLines={1}>{nameOf(item)}</Text>
                      {item.address ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{addressForLanguage(item, language)}</Text> : null}
                    </Pressable>
                  ))}
                </View>
              )
          ) : null}
        </>
      )}

      {dest && !origin ? (
        <View style={styles.answer}>
          <Text color={color.text.body}>{tx('내 위치를 켜야 여기서 가는 길을 찾을 수 있어요.', 'Turn on your location so we can find the way from here.')}</Text>
          <Pressable accessibilityRole="button" onPress={onAskLocation} style={({ pressed }) => [styles.smallButton, pressed && styles.pressed]}>
            <Text variant="caption" weight="bold" color={color.brand.navy}>{askLabel}</Text>
          </Pressable>
        </View>
      ) : null}

      {dest && origin ? (
        <View style={styles.answer}>
          {!transit ? <Text color={color.text.muted}>{tx('길을 찾고 있어요…', 'Finding the way…')}</Text> : null}
          {transit && (transit.state !== 'success' || guessed) ? (
            <View style={styles.notFound}>
              <Text weight="bold">{tx('버스·지하철 길을 찾지 못했어요', 'We could not find a bus or metro route')}</Text>
              {guessed ? <Text variant="caption" color={color.text.body}>{txf(tx, '직선 거리로 어림하면 약 %s이지만 실제와 다를 수 있어요', 'A straight-line guess is about %s, but the real trip may differ', formatDuration(guessed.durationMin, tx))}</Text> : null}
            </View>
          ) : null}

          {ride ? (
            <View style={styles.rideRow}>
              <View style={[styles.lineChip, ride.kind === 'subway' && styles.subwayChip]}>
                <Text variant="caption" weight="bold" color={color.text.onAction} numberOfLines={1}>{ride.kind === 'bus' ? txf(tx, '%s번', '%s', ride.line) : ride.line}</Text>
              </View>
              <View style={styles.grow}>
                {ride.board ? <Text weight="bold">{txf(tx, '%s에서 타요', 'Board at %s', ride.board)}</Text> : null}
                {ride.liveSeconds != null && waitText(ride.liveSeconds)
                  ? <Text variant="caption" weight="bold" color={color.state.info}>{waitText(ride.liveSeconds)}</Text>
                  : null}
              </View>
            </View>
          ) : null}

          {transitDirections ? (
            <Text variant="caption" color={color.text.body}>
              {transitDirections.mode === 'WALK'
                ? txf(tx, '걸어서 %s이면 가요', 'About %s on foot', formatDuration(transitDirections.durationMin, tx))
                : [
                  txf(tx, '총 %s', 'Total %s', formatDuration(transitDirections.durationMin, tx)),
                  transitDirections.transferCount === 0 ? tx('갈아타지 않아요', 'No transfers') : transitDirections.transferCount != null ? tx(`환승 ${transitDirections.transferCount}회`, `${transitDirections.transferCount} transfer(s)`) : null,
                ].filter(Boolean).join(' · ')}
            </Text>
          ) : null}

          {/* 택시는 대안이라 큰 단추가 아니라 한 줄 — 누르면 기사에게 보여줄 카드(이름·주소)가 바로 뜬다. */}
          {carDirections?.taxiFareKrw != null && dest ? (
            <Pressable accessibilityRole="link" onPress={() => router.push(taxiCardHref({ key: dest.key, placeId: dest.placeId, name: nameOf(dest), address: dest.address }) as never)} style={({ pressed }) => [styles.taxiRow, pressed && styles.pressed]}>
              <Text variant="caption" color={color.text.body} style={styles.grow}>
                {txf(tx, '택시로 %s · 약 %s원', 'By taxi %s · about ₩%s', formatDuration(carDirections.durationMin, tx), carDirections.taxiFareKrw.toLocaleString('en-US'))}
              </Text>
              <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('기사에게 보여주기 ›', 'Show the driver ›')}</Text>
            </Pressable>
          ) : null}

          {transitDirections || carDirections ? (
            <Pressable accessibilityRole="button" onPress={openDetail} hitSlop={8} style={({ pressed }) => [styles.linkButton, pressed && styles.pressed]}>
              <Text variant="caption" weight="bold" color={color.brand.navy}>{tx('경로 자세히 ›', 'Route details ›')}</Text>
            </Pressable>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing[3], padding: spacing[4], marginBottom: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { maxWidth: '100%', paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.tint, gap: 2 },
  input: { minHeight: 48, paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, fontSize: 16, color: color.text.heading, backgroundColor: color.surface.card },
  results: { gap: spacing[1] },
  resultRow: { minHeight: 44, justifyContent: 'center', paddingVertical: spacing[2], paddingHorizontal: spacing[2], borderRadius: radius.sm, backgroundColor: color.surface.subtle, gap: 2 },
  destRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  answer: { gap: spacing[2], paddingTop: spacing[3], borderTopWidth: 1, borderTopColor: color.surface.border },
  rideRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  lineChip: { minWidth: 64, minHeight: 32, paddingHorizontal: spacing[2], borderRadius: radius.sm, backgroundColor: color.state.info, alignItems: 'center', justifyContent: 'center' },
  subwayChip: { backgroundColor: color.state.warning },
  smallButton: { alignSelf: 'flex-start', minHeight: 44, justifyContent: 'center', paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.tint },
  linkButton: { alignSelf: 'flex-start', minHeight: 32, justifyContent: 'center' },
  grow: { flex: 1 },
  notFound: { gap: 2, padding: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.tint },
  taxiRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 40 },
  pressed: { opacity: 0.72 },
});
