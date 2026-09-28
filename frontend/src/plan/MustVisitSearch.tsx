// 꼭 가고 싶은 장소 — 검색 겸 입력창 + 자동완성 (시안 ①, 인계 §2·§7①).
//
// 🔴 자유 입력이 아니다. 고른 것은 **우리 DB 의 장소**여야 추천 엔진이 그 자리를 비워 둘 수
//    있다. 사람이 친 글자를 그대로 저장하면 「광안리 근처 어디」가 되어 아무 데도 못 넣는다.
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { searchPlacesByName, type PlaceSearchItem } from '@/discovery/places';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import type { MustVisitPlace } from '@/plan/PlanProvider';
import { txf } from '@/i18n/format';
import { KOREAN_OR_ENGLISH_HINT, needsKoreanOrEnglishName } from '@/discovery/nameSearchHint';

/** 최대 몇 곳까지 담나. 넘으면 추천이 「이 여행」이 아니라 「이 목록」이 된다. */
export const MUST_VISIT_MAX = 5;
/** 글자를 멈춘 뒤 이만큼 기다렸다 물어본다. 타이핑마다 부르면 한 낱말에 열 번 나간다. */
const DEBOUNCE_MS = 250;
/** 이 글자 수 미만은 서버에 안 보낸다 — 한 글자로는 온 부산이 다 나온다. */
const MIN_QUERY = 2;

export function MustVisitSearch({
  picked,
  onChange,
  tx,
  ko,
}: {
  picked: MustVisitPlace[];
  onChange: (next: MustVisitPlace[]) => void;
  tx: (koText: string, enText: string) => string;
  ko: boolean;
}) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PlaceSearchItem[]>([]);
  const [searching, setSearching] = useState(false);
  const [failed, setFailed] = useState(false);
  const full = picked.length >= MUST_VISIT_MAX;

  // 🔴 앞 요청을 끊는다. 안 끊으면 느린 응답이 나중에 도착해 **방금 친 글자의 결과를 덮는다**.
  //    「광안」을 치다 「광」의 결과가 뒤늦게 오는 식이라, 화면만 보면 검색이 고장 난 것처럼 보인다.
  const inflight = useRef<AbortController | null>(null);
  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < MIN_QUERY) {
      inflight.current?.abort();
      setResults([]);
      setSearching(false);
      setFailed(false);
      return;
    }
    setSearching(true);
    setFailed(false);
    const timer = setTimeout(() => {
      inflight.current?.abort();
      const controller = new AbortController();
      inflight.current = controller;
      searchPlacesByName(trimmed, controller.signal)
        .then((items) => { if (!controller.signal.aborted) { setResults(items.slice(0, MUST_VISIT_MAX)); setSearching(false); } })
        .catch(() => {
          // 끊은 것은 실패가 아니다 — 다음 글자가 이미 물어보고 있다.
          if (controller.signal.aborted) return;
          setResults([]); setSearching(false); setFailed(true);
        });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => () => inflight.current?.abort(), []);

  const add = (item: PlaceSearchItem) => {
    if (full || picked.some((place) => place.placeId === item.placeId)) return;
    onChange([...picked, { placeId: item.placeId, nameKo: item.nameKo, nameEn: item.nameEn, lat: item.lat, lng: item.lng }]);
    setQuery('');
    setResults([]);
  };
  const remove = (placeId: string) => onChange(picked.filter((place) => place.placeId !== placeId));

  return (
    <View style={styles.wrap}>
      <View style={styles.field}>
        <Text variant="body" color={color.text.muted}>⌕</Text>
        <TextInput
          accessibilityLabel={tx('장소 검색', 'Search places')}
          value={query}
          onChangeText={setQuery}
          editable={!full}
          placeholder={tx('장소 이름을 입력해 보세요 — 예: 광안리, 감천문화마을', 'Type a place — e.g. Gwangalli, Gamcheon')}
          placeholderTextColor={color.text.muted}
          style={styles.input}
        />
        {query ? (
          <Pressable accessibilityRole="button" accessibilityLabel={tx('검색어 지우기', 'Clear search')} onPress={() => setQuery('')} style={styles.clear}>
            <Text variant="caption" weight="bold" color={color.text.muted}>✕</Text>
          </Pressable>
        ) : null}
      </View>

      {/* 🔴 「찾는 중」과 「없음」을 가른다. 둘 다 빈 목록이지만 사용자가 할 일이 다르다 —
          앞은 기다리면 되고 뒤는 다르게 쳐야 한다. */}
      {query.trim().length >= MIN_QUERY ? (
        <View style={styles.panel}>
          {searching ? (
            <View style={styles.note}><ActivityIndicator color={color.action.primary} /><Text variant="caption" color={color.text.muted}>{tx('찾는 중이에요…', 'Searching…')}</Text></View>
          ) : failed ? (
            <View style={styles.note}><Text variant="caption" color={color.text.muted}>{tx('장소를 찾지 못했어요. 잠시 후 다시 시도해 주세요.', 'Could not search right now. Please try again shortly.')}</Text></View>
          ) : results.length === 0 ? (
            // 🔴 한자·가나로만 치면 서버가 원래 못 찾는다(S15P21E201-1519). 그때 「없어요」라고 하면
            //    있는 장소를 없다고 단정하게 된다 — 찾아지는 글자를 말해 준다.
            <View style={styles.note}><Text variant="caption" color={color.text.muted}>{needsKoreanOrEnglishName(query)
              ? tx(KOREAN_OR_ENGLISH_HINT.ko, KOREAN_OR_ENGLISH_HINT.en)
              : tx('그런 이름의 장소가 없어요.', 'No place with that name.')}</Text></View>
          ) : results.map((item) => {
            const already = picked.some((place) => place.placeId === item.placeId);
            return (
              <Pressable
                key={item.placeId}
                accessibilityRole="button"
                accessibilityState={{ disabled: already || full }}
                disabled={already || full}
                onPress={() => add(item)}
                style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
              >
                <View style={styles.badge}><Text variant="caption" weight="bold" color={color.text.muted} numberOfLines={1}>{(item.category ?? '').slice(0, 2)}</Text></View>
                <View style={styles.rowCopy}>
                  <Text weight="bold" numberOfLines={1}>{ko ? item.nameKo : item.nameEn ?? item.nameKo}</Text>
                  <Text variant="caption" color={color.text.muted} numberOfLines={1}>{item.address}</Text>
                </View>
                <Text variant="caption" weight="bold" color={already ? color.state.success : color.brand.navy}>
                  {already ? tx('✓ 담김', '✓ Added') : tx('담기', 'Add')}
                </Text>
              </Pressable>
            );
          })}
        </View>
      ) : null}

      {picked.length ? (
        <View style={styles.picked}>
          {picked.map((place) => (
            <View key={place.placeId} style={styles.pickedChip}>
              <Text variant="caption" weight="bold" color={color.text.onAction} numberOfLines={1}>{ko ? place.nameKo : place.nameEn ?? place.nameKo}</Text>
              <Pressable accessibilityRole="button" accessibilityLabel={txf(tx, '%s 빼기', 'Remove %s', place.nameKo)} onPress={() => remove(place.placeId)} style={styles.pickedRemove}>
                <Text variant="caption" weight="bold" color={color.text.onAction}>✕</Text>
              </Pressable>
            </View>
          ))}
        </View>
      ) : null}

      <Text variant="caption" color={color.text.muted}>
        {picked.length
          ? txf(tx, '%s곳 담았어요 · 최대 %s곳. 일정에 꼭 넣고 나머지를 주변으로 채워요.', '%s added · up to %s. We always include these and fill around them.', picked.length, MUST_VISIT_MAX)
          : txf(tx, '최대 %s곳까지 담을 수 있어요. 담은 곳은 일정에 꼭 들어가요.', 'Up to %s places. Whatever you add always makes the itinerary.', MUST_VISIT_MAX)}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing[3] },
  field: {
    flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 52, paddingHorizontal: spacing[4],
    borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card,
  },
  input: { flex: 1, minWidth: 0, minHeight: 52, color: color.text.heading },
  clear: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full },

  panel: {
    borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card,
    overflow: 'hidden',
  },
  note: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], padding: spacing[3] },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 48, paddingHorizontal: spacing[3], paddingVertical: spacing[2] },
  rowPressed: { backgroundColor: color.surface.tint },
  badge: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm, backgroundColor: color.surface.soft },
  rowCopy: { flex: 1, minWidth: 0 },

  picked: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  pickedChip: {
    flexDirection: 'row', alignItems: 'center', gap: spacing[2], minHeight: 32, paddingLeft: spacing[3], paddingRight: spacing[1],
    borderRadius: radius.full, backgroundColor: color.brand.navy,
  },
  pickedRemove: { width: 22, height: 22, alignItems: 'center', justifyContent: 'center', borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.18)' },
});
