// 「지역」을 검색해서 고른다 — S15P21E201-1145.
//
// 🔴 입력칸은 하나다(S15P21E201-1593). 전에는 검색칸과 「지역 직접 쓰기」칸이 따로 있어서 「지역」을 누르면
//    칸이 두 개 떴고, 어느 칸에 써야 하는지 헷갈렸다. 이제 적는 글자가 곧 지역이고, 같은 글자로 검색한다.
//    결과를 고르면 장소가 연결되고, 고른 뒤 고쳐 쓰면 연결이 끊긴다.
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { regionLabelOf, searchRegions, type RegionCandidate, type StoryPlaceSnapshot } from '@/social/regionSearch';
import { txf } from '@/i18n/format';

type Props = {
  region: string;
  onChangeRegion: (value: string) => void;
  /** 우리 DB 장소를 고르면 그 번호, 손으로 고쳐 쓰면 undefined 로 돌아간다. */
  onChangePlaceId: (placeId: string | undefined) => void;
  placeId: string | undefined;
  /**
   * 카카오·대체 목록 결과를 고르면 그 장소 스냅샷, 우리 장소를 고르거나 손으로 고쳐 쓰면 undefined.
   * 서버가 이것으로 장소를 찾거나 만들어 글에 잇는다 — S15P21E201-1527.
   */
  onChangePlace?: (place: StoryPlaceSnapshot | undefined) => void;
  accessToken: string | null;
};

export function RegionPicker({ region, onChangeRegion, onChangePlaceId, placeId, onChangePlace, accessToken }: Props) {
  const { tx } = useI18n();
  const [query, setQuery] = useState('');
  const [items, setItems] = useState<RegionCandidate[]>([]);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  /**
   * 한 번이라도 «검색을 마쳤는가» — S15P21E201-1804.
   *
   * 🔴 items.length === 0 만 보면 안 된다. 아직 아무것도 안 친 처음 상태와, 쳐서 찾아봤는데
   *    없는 상태가 둘 다 0 이다. 앞의 것에 「찾는 곳이 없어요」를 띄우면 열자마자 없다고
   *    말하는 꼴이 된다.
   */
  const [searched, setSearched] = useState(false);
  const picked = useRef<string | null>(null);

  useEffect(() => {
    const text = query.trim();
    if (text.length < 2) { setItems([]); setFailed(false); setSearched(false); return; }
    // 글자를 칠 때마다 부르지 않는다 — 손이 멈춘 뒤에 한 번만 묻는다.
    const timer = setTimeout(() => {
      const controller = new AbortController();
      setBusy(true);
      void searchRegions(text, accessToken, controller.signal)
        .then((result) => {
          if (controller.signal.aborted) return;
          if (result.state === 'success') { setItems(result.items); setFailed(false); }
          else { setItems([]); setFailed(true); }
          setSearched(true);
        })
        .finally(() => { if (!controller.signal.aborted) setBusy(false); });
      return () => controller.abort();
    }, 300);
    return () => clearTimeout(timer);
  }, [accessToken, query]);

  const choose = (candidate: RegionCandidate) => {
    const label = regionLabelOf(candidate);
    picked.current = label;
    onChangeRegion(label);
    onChangePlaceId(candidate.placeId);
    onChangePlace?.(candidate.placeId ? undefined : candidate.place);
    setQuery('');
    setItems([]);
    // 고른 뒤에는 「찾는 곳이 없어요」가 남으면 안 된다 — 방금 골랐는데 없다고 말하는 꼴이다.
    setSearched(false);
  };

  return (
    <View style={styles.frame}>
      <TextInput
        accessibilityLabel={tx('장소나 지역 검색', 'Search a place or area')}
        style={styles.search}
        placeholder={tx('장소나 지역을 검색해요 (예: 해운대)', 'Search a place or area (e.g. Haeundae)')}
        placeholderTextColor={color.text.muted}
        value={region}
        onChangeText={(value) => {
          onChangeRegion(value);
          setQuery(value);
          // 손으로 고치면 장소 연결을 끊는다. 「해운대해수욕장」을 고른 뒤 「광안리」로
          // 바꿔 쓰면, 글에는 여전히 해운대가 달려 있게 된다 — 화면과 저장된 것이 달라진다.
          if (picked.current !== null && value !== picked.current) { picked.current = null; onChangePlaceId(undefined); onChangePlace?.(undefined); }
        }}
        autoCorrect={false}
        maxLength={60}
      />

      {busy ? <View style={styles.row}><ActivityIndicator color={color.action.primary} /></View> : null}

      {/* 검색이 실패해도 손으로 쓰는 길은 막지 않는다 — 적은 글자가 그대로 지역이다. */}
      {failed ? <Text variant="caption" color={color.text.muted} style={styles.note}>
        {tx('검색이 안 돼요. 적은 그대로 지역으로 저장돼요.', 'Search is unavailable — what you typed is saved as the region.')}
      </Text> : null}

      {/* 🔴 찾은 것이 없을 때도 말해 준다 (S15P21E201-1804).
          검색이 «실패»했을 때는 위에서 안내하는데, 서버가 멀쩡히 「그런 곳 없음」이라고
          답하면 목록도 안내도 아무것도 안 떴다. 사용자 눈에는 입력칸에 글자만 남고 고를
          것이 없으니 「이 지역은 안 되는구나」로 읽힌다 — 실제로는 적은 그대로 저장된다.
          구·동 이름(「수영구」·「광안동」)을 적어도 되는 자리라는 것을 여기서 알린다. */}
      {!busy && !failed && searched && items.length === 0 ? <Text variant="caption" color={color.text.muted} style={styles.note}>
        {tx('찾는 곳이 없어요. 적은 그대로 지역으로 저장돼요. 「수영구」처럼 구·동 이름도 괜찮아요.', 'No match. What you typed is saved as the region, and a district or neighborhood name works too.')}
      </Text> : null}

      {items.map((item) => (
        <Pressable
          key={`${item.name}-${item.address}`}
          accessibilityRole="button"
          accessibilityLabel={txf(tx, '%s 고르기', 'Choose %s', item.name)}
          onPress={() => choose(item)}
          style={styles.row}
        >
          <View style={styles.rowBody}>
            <Text variant="body" weight="bold" numberOfLines={1}>{item.name}</Text>
            <Text variant="caption" color={color.text.muted} numberOfLines={1}>{item.address}</Text>
          </View>
          {/* 우리 DB 장소만 글에 이을 수 있다. 그 차이를 사용자가 알아야 고를 이유가 생긴다. */}
          {item.placeId ? <Text variant="caption" weight="bold" color={color.state.info}>{tx('장소 연결', 'Linked place')}</Text> : null}
        </Pressable>
      ))}

      {placeId ? <Text variant="caption" color={color.text.muted} style={styles.note}>
        {tx('이 글이 그 장소에 함께 보여요.', 'This record will also show on that place.')}
      </Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  frame: { gap: spacing[1] },
  search: { minHeight: 40, paddingHorizontal: spacing[3], borderRadius: radius.sm, backgroundColor: color.surface.soft, color: color.text.heading },
  row: { minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.sm },
  rowBody: { flex: 1, minWidth: 0 },
  note: { paddingHorizontal: spacing[1] },
});
