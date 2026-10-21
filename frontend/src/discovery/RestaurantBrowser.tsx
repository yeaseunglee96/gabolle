// 로컬 탐색 「맛집」·「카페」 갈래의 본문 — 구 칩 한 줄 → 구를 고르면 동 칩 한 줄 → 동을 고르면 기존 장소 카드 격자
// (S15P21E201-2051 맛집 · S15P21E201-2059 카페·내 위치로 기본 구).
//
// 맛집·카페에서는 「내 근처 / 부산 전체」가 아니라 **구·동이 범위를 대신한다.** 그래서 이 갈래를 고르면 explore 화면이 범위 토글을 감춘다.
// 카드 격자는 explore 의 PlaceRows 를 그대로 쓴다(사진·출처 표시·이름 읽는 법까지 한 벌) — 부르는 쪽이 renderRows 로 넘긴다.
// 맛집과 카페는 같은 부품이다 — `kind` 만 다르다. 부르는 쪽이 `key={kind}` 로 갈래가 바뀔 때 상태째 새로 띄운다.
//
// 🔴 내 위치로 기본 구 (S15P21E201-2059) — 위치를 읽었으면(`coords`) 서버가 알려 주는 «내 위치에서 가장 가까운 곳의 구»를 미리 고르고
//    그 구의 「전체」 목록을 바로 보여 준다. 위치를 거부했거나 못 읽었거나 부산 밖이면 아무것도 안 바뀐다 — 예전처럼 «먼저 구를 골라 주세요».
//    사용자가 구·동을 한 번이라도 직접 누르면 그 뒤로는 위치가 늦게 와도 덮어쓰지 않는다.
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, gutter, radius, spacing } from '@/design/tokens';
import {
  areaLabel,
  getLocalAreas,
  getLocalList,
  sourceBadgeLabels,
  type LocalCoords,
  type LocalKind,
  type RestaurantGu,
  type RestaurantItem,
} from '@/discovery/localRestaurants';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';
import { coarseCoordinate } from '@/personalization/locationConsent';

type Chip = { key: string; label: string; count: number };

/** 구 안의 모든 곳을 고르는 칸의 열쇠 — 동 이름과 겹치지 않게 서버가 안 쓰는 글자를 쓴다. */
const ALL_DONGS = '*';

/** 칩 한 줄 — 폰에서는 옆으로 밀리는 레일, 넓은 화면에서는 줄바꿈. 갈래 칩(FacetPicker)과 같은 모양이다. */
function ChipRow({ title, chips, selectedKey, onSelect, rail }: {
  title: string;
  chips: Chip[];
  selectedKey: string | null;
  onSelect: (key: string) => void;
  rail: boolean;
}) {
  // 🔴 고른 칩이 줄 밖에 숨어 있으면 무엇을 고른 것인지 모른다 — 내 위치로 미리 고른 구가 («해운대구» 카페는 9곳이라 오른쪽 끝에 있다) 화면 밖이거나
  //    칩이 화면 가장자리에 반쯤 걸려 «고장»처럼 보였다. 고른 칩이 보이게 줄을 옮긴다(갈래 칩 FacetPicker 와 같다).
  //    칩이 놓인 자리(layout)는 그린 뒤에 비동기로 오므로, 고른 것이 먼저 바뀌어도(칩의 자리를 아직 모름) 자리가 오는 순간 다시 옮긴다 —
  //    그때의 «고른 칩»은 그 칩을 그린 때의 값이 아니라 지금 값(selectedRef)이어야 한다.
  const railRef = useRef<ScrollView>(null);
  const chipX = useRef<Record<string, number>>({});
  const selectedRef = useRef<string | null>(selectedKey);
  selectedRef.current = selectedKey;
  const scrollToSelected = () => {
    const key = selectedRef.current;
    const x = key ? chipX.current[key] : undefined;
    if (x !== undefined) railRef.current?.scrollTo({ x: Math.max(0, x - gutter), animated: false });
  };
  useEffect(scrollToSelected, [selectedKey]);
  const nodes = chips.map((chip) => {
    const selected = chip.key === selectedKey;
    return (
      <Pressable
        key={chip.key}
        onLayout={(event) => { chipX.current[chip.key] = event.nativeEvent.layout.x; if (chip.key === selectedRef.current) scrollToSelected(); }}
        accessibilityRole="button"
        accessibilityState={{ selected }}
        onPress={() => onSelect(chip.key)}
        style={[styles.chip, selected && styles.chipSelected]}
      >
        <Text weight="bold" numberOfLines={1} color={selected ? color.text.onAction : color.text.heading}>{selected ? '✓ ' : ''}{chip.label}</Text>
        <Text variant="caption" weight="bold" numberOfLines={1} color={selected ? color.text.onAction : color.text.muted} style={styles.chipCount}>{chip.count}</Text>
      </Pressable>
    );
  });
  return (
    <View style={styles.row} accessibilityLabel={title}>
      <Text variant="caption" weight="bold" color={color.text.muted}>{title}</Text>
      {rail
        ? <ScrollView ref={railRef} horizontal showsHorizontalScrollIndicator={false} style={styles.railBleed} contentContainerStyle={styles.rail}>{nodes}</ScrollView>
        : <View style={styles.wrap}>{nodes}</View>}
    </View>
  );
}

/**
 * 카드에 작게 다는 출처 배지 — 어느 목록이 소개·인증했는지(부산시 맛집 · 모범음식점 · 택슐랭 · 관광공사 · 레드테이블 · 현지인 추천).
 * 모르는 낱말뿐이면 아무것도 안 그린다. `broadcastOrAward` 는 레드테이블이 방송·수상 이력도 적어 둔 곳이다.
 */
export function RestaurantSourceBadges({ sources, broadcastOrAward = false }: { sources: readonly string[] | undefined; broadcastOrAward?: boolean }) {
  const { tx } = useI18n();
  const labels = sourceBadgeLabels(sources ?? [], tx, { broadcastOrAward });
  if (!labels.length) return null;
  return (
    <View style={styles.badges}>
      {labels.map((label) => (
        <View key={label} style={styles.badge}>
          <Text variant="caption" weight="bold" color={color.text.body} numberOfLines={1}>{label}</Text>
        </View>
      ))}
    </View>
  );
}

type Loaded<T> = { state: 'loading' } | { state: 'error'; message: string } | { state: 'ready'; value: T };
/** 목록은 «어느 요청의 것인가»를 같이 든다 — 구·동을 바꾼 첫 그림에서 옛 목록이 새 제목 밑에 한 번 비치지 않게. */
type ListState = Loaded<RestaurantItem[]> & { requestKey: string };

/** 갈래마다 다른 문구 — 문구는 글자 그대로 tx 에 넣는다(번역 검사가 보도록). */
function copyFor(kind: LocalKind, tx: (ko: string, en: string) => string) {
  return kind === 'CAFE'
    ? {
      loading: tx('카페를 불러오고 있어요', 'Loading cafes'),
      failed: tx('카페를 불러오지 못했어요.', 'Could not load cafes.'),
      pickDong: tx('동을 골라 주세요. 「전체」를 누르면 이 구의 카페가 모두 나와요.', 'Pick a neighborhood. “All” shows every cafe in this district.'),
      empty: tx('이곳에는 카페가 없어요.', 'No cafes here.'),
    }
    : {
      loading: tx('맛집을 불러오고 있어요', 'Loading restaurants'),
      failed: tx('맛집을 불러오지 못했어요.', 'Could not load restaurants.'),
      pickDong: tx('동을 골라 주세요. 「전체」를 누르면 이 구의 맛집이 모두 나와요.', 'Pick a neighborhood. “All” shows every restaurant in this district.'),
      empty: tx('이곳에는 맛집이 없어요.', 'No restaurants here.'),
    };
}

export function RestaurantBrowser({ kind = 'RESTAURANT', wide, renderRows, coords = null }: {
  /** 맛집 또는 카페. 바뀌면 상태가 처음부터 다시 시작해야 하므로 부르는 쪽이 `key` 도 같이 준다. */
  kind?: LocalKind;
  /** 넓은 화면이면 칩이 줄바꿈되고, 아니면 옆으로 밀린다. */
  wide: boolean;
  /** 고른 동의 곳을 그리는 카드 격자 — explore 의 PlaceRows. */
  renderRows: (items: RestaurantItem[]) => ReactNode;
  /** 내 위치. 있으면 서버에 물어 가장 가까운 곳의 구를 미리 고른다. 없으면(거부·실패) 구를 직접 고른다. */
  coords?: LocalCoords | null;
}) {
  const { tx, language } = useI18n();
  const [areas, setAreas] = useState<Loaded<RestaurantGu[]>>({ state: 'loading' });
  // 서버가 준 «가장 가까운 곳의 구»와, 그 답이 어느 좌표에 대한 것인지. 좌표가 있는데 답이 아직 안 왔으면 «찾는 중»이다.
  const [nearest, setNearest] = useState<{ gu: string | null; forKey: string } | null>(null);
  const [gu, setGu] = useState<string | null>(null);
  const [dong, setDong] = useState<string | null>(null);
  const [autoGu, setAutoGu] = useState<string | null>(null);
  const [list, setList] = useState<ListState>({ state: 'loading', requestKey: '' });
  const [reloadKey, setReloadKey] = useState(0);
  // 사용자가 구·동을 한 번이라도 눌렀는가 — 그 뒤에는 늦게 온 위치가 고른 것을 덮어쓰지 않는다.
  const userPicked = useRef(false);

  // 좌표가 같으면 같은 요청이다 — 약 100m 로 줄인 값으로 비교해, 위치가 조금 흔들릴 때마다 다시 부르지 않는다.
  const coordsKey = coords ? `${coarseCoordinate(coords.latitude)},${coarseCoordinate(coords.longitude)}` : '';

  // 구·동 목록 (+ 좌표가 있으면 가장 가까운 구). 좌표가 늦게 오면 한 번 더 부르되 이미 그린 칩은 그대로 둔다.
  useEffect(() => {
    const controller = new AbortController();
    setAreas((previous) => (previous.state === 'ready' ? previous : { state: 'loading' }));
    getLocalAreas(kind, { coords, signal: controller.signal })
      .then((value) => {
        if (controller.signal.aborted) return;
        setAreas({ state: 'ready', value: value.areas });
        setNearest({ gu: value.nearestGu, forKey: coordsKey });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        // 답을 못 받았다 — «찾는 중»은 끝낸다. 이미 칩을 그렸다면 그대로 둔다(위치 때문에 실패한 것이 칩을 지우면 안 된다).
        setNearest({ gu: null, forKey: coordsKey });
        setAreas((previous) => (previous.state === 'ready' ? previous : {
          state: 'error',
          message: error instanceof Error ? localizeMessage(tx, error.message) : tx('구·동 목록을 불러오지 못했어요.', 'Could not load the district list.'),
        }));
      });
    return () => controller.abort();
    // coords 는 coordsKey 가 대신한다(같은 값이면 다시 부르지 않는다). tx 는 언어가 바뀔 때마다 달라지지만 목록은 다시 받을 까닭이 없다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kind, coordsKey, reloadKey]);

  // 서버가 알려 준 가장 가까운 구를 미리 고른다 — 사용자가 아직 아무것도 안 눌렀을 때만, 그 답이 지금 좌표에 대한 것일 때만(옛 좌표의 답이 늦게
  // 와서 지금 자리를 덮지 않게), 그리고 그 구가 목록에 있을 때만.
  useEffect(() => {
    if (userPicked.current || areas.state !== 'ready' || !coords || !nearest?.gu || nearest.forKey !== coordsKey) return;
    const entry = areas.value.find((candidate) => candidate.gu === nearest.gu);
    if (!entry) return;
    setGu(entry.gu);
    setDong(ALL_DONGS);
    setAutoGu(entry.gu);
    // coords 는 coordsKey 가 대신한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [areas, nearest, coordsKey]);

  const selectedGu = areas.state === 'ready' ? areas.value.find((entry) => entry.gu === gu) ?? null : null;

  // 구·동을 고르면 그 곳의 목록을 부른다. 구를 바꾸면 동은 비워서 목록이 옛 구의 것으로 남지 않게 한다.
  const requestKey = selectedGu && dong ? `${kind}/${selectedGu.gu}/${dong}/${reloadKey}` : '';
  useEffect(() => {
    if (!selectedGu || !dong) return undefined;
    const controller = new AbortController();
    setList({ state: 'loading', requestKey });
    getLocalList(kind, { gu: selectedGu.gu, dong: dong === ALL_DONGS ? null : dong }, controller.signal)
      .then((page) => { if (!controller.signal.aborted) setList({ state: 'ready', value: page.items, requestKey }); })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setList({ state: 'error', message: error instanceof Error ? localizeMessage(tx, error.message) : copyFor(kind, tx).failed, requestKey });
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestKey]);
  // 다른 요청의 결과는 아직 «불러오는 중»이다.
  const current: Loaded<RestaurantItem[]> = list.requestKey === requestKey ? list : { state: 'loading' };

  const copy = copyFor(kind, tx);

  if (areas.state === 'loading') {
    return <View accessibilityLiveRegion="polite" style={styles.state}><ActivityIndicator color={color.action.primary} /><Text color={color.text.body}>{tx('구·동을 불러오고 있어요', 'Loading districts')}</Text></View>;
  }
  if (areas.state === 'error') {
    return (
      <View accessibilityRole="alert" style={styles.state}>
        <Text color={color.text.body}>{areas.message}</Text>
        <Button label={tx('다시 시도', 'Try again')} variant="tertiary" compact onPress={() => setReloadKey((key) => key + 1)} />
      </View>
    );
  }
  if (areas.value.length === 0) {
    return <View style={styles.state}><Text color={color.text.body}>{copy.empty}</Text></View>;
  }

  const guChips: Chip[] = areas.value.map((entry) => ({ key: entry.gu, label: areaLabel(entry.gu, language), count: entry.placeCount }));
  const dongChips: Chip[] = selectedGu
    ? [
      { key: ALL_DONGS, label: tx('전체', 'All'), count: selectedGu.placeCount },
      ...selectedGu.dongs.map((entry) => ({ key: entry.dong, label: areaLabel(entry.dong, language), count: entry.placeCount })),
    ]
    : [];
  // 좌표를 읽었는데 서버의 «가장 가까운 구» 답이 아직 안 왔다.
  const findingNearest = !selectedGu && coords !== null && nearest?.forKey !== coordsKey;

  return (
    <View style={styles.body}>
      <ChipRow
        title={tx('구 고르기', 'Pick a district')}
        chips={guChips}
        selectedKey={gu}
        onSelect={(next) => { userPicked.current = true; setAutoGu(null); setGu(next); setDong(null); }}
        rail={!wide}
      />
      {selectedGu ? (
        <ChipRow title={tx('동 고르기', 'Pick a neighborhood')} chips={dongChips} selectedKey={dong} onSelect={(next) => { userPicked.current = true; setDong(next); }} rail={!wide} />
      ) : null}

      {/* 내 위치로 고른 구라는 것을 밝힌다 — 사용자가 누르지 않은 선택이 왜 되어 있는지 모르면 «고장»처럼 보인다. */}
      {selectedGu && autoGu === selectedGu.gu
        ? <Text variant="caption" color={color.text.muted} accessibilityLiveRegion="polite">{txf(tx, '내 위치와 가까운 구(%s)를 골랐어요. 다른 구를 눌러도 돼요.', 'Picked the district near you: %s. Tap another one anytime.', areaLabel(selectedGu.gu, language))}</Text>
        : null}
      {findingNearest
        ? <View accessibilityLiveRegion="polite" style={styles.finding}><ActivityIndicator color={color.action.primary} /><Text color={color.text.body}>{tx('내 위치로 가까운 구를 찾고 있어요', 'Finding the district near you')}</Text></View>
        : null}
      {!selectedGu && !findingNearest ? <Text color={color.text.body}>{tx('먼저 구를 골라 주세요.', 'Pick a district first.')}</Text> : null}
      {selectedGu && !dong ? <Text color={color.text.body}>{copy.pickDong}</Text> : null}

      {selectedGu && dong ? (
        <View style={styles.listBody}>
          {current.state === 'loading' ? (
            <View accessibilityLiveRegion="polite" style={styles.listState}><ActivityIndicator color={color.action.primary} /><Text color={color.text.body}>{copy.loading}</Text></View>
          ) : null}
          {current.state === 'error' ? (
            <View accessibilityRole="alert" style={styles.listState}>
              <Text color={color.text.body}>{current.message}</Text>
              <Button label={tx('다시 시도', 'Try again')} variant="tertiary" compact onPress={() => setReloadKey((key) => key + 1)} />
            </View>
          ) : null}
          {current.state === 'ready' && current.value.length === 0 ? <Text color={color.text.body}>{copy.empty}</Text> : null}
          {current.state === 'ready' && current.value.length > 0 ? (
            <>
              {/* 어느 곳의 몇 곳인지 — 칩만으로는 목록이 어디 것인지 한 번 더 확인할 데가 없다. */}
              <Text variant="caption" color={color.text.muted}>
                {`${areaLabel(selectedGu.gu, language)} · ${dong === ALL_DONGS ? tx('전체', 'All') : areaLabel(dong, language)} · ${tx(`${current.value.length}곳`, `${current.value.length} places`)}`}
              </Text>
              {renderRows(current.value)}
            </>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing[3] },
  row: { gap: spacing[2] },
  // 폰 — 칩 줄이 화면 끝까지 흐른다(갈래 칩과 같다). 여백 안에서 잘리면 끝 칩이 반쯤 걸려 「고장」처럼 보인다.
  railBleed: { marginHorizontal: -gutter },
  rail: { gap: spacing[2], paddingHorizontal: gutter },
  // 넓으면 줄바꿈 — 숨는 칩이 없어야 한다.
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 44, paddingHorizontal: spacing[4], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.border },
  chipSelected: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  chipCount: { opacity: 0.75 },
  state: { gap: spacing[3], marginTop: spacing[2], padding: spacing[6], borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center' },
  finding: { flexDirection: 'row', alignItems: 'center', gap: spacing[2] },
  listBody: { gap: spacing[2] },
  listState: { gap: spacing[3], padding: spacing[4], alignItems: 'center' },
  badges: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[1], paddingTop: spacing[1] },
  badge: { paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.full, backgroundColor: color.surface.soft },
});
