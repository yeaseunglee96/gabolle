// 「지역」을 검색해서 고른다 — S15P21E201-1145.
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { regionLabelOf, searchRegions, type RegionCandidate } from '@/social/regionSearch';

type Props = {
  region: string;
  onChangeRegion: (value: string) => void;
  /** 우리 DB 장소를 고르면 그 번호, 손으로 고쳐 쓰면 undefined 로 돌아간다. */
  onChangePlaceId: (placeId: string | undefined) => void;
  placeId: string | undefined;
  accessToken: string | null;
};

export function RegionPicker({ region, onChangeRegion, onChangePlaceId, placeId, accessToken }: Props) {
  const { tx } = useI18n();
  const [query, setQuery] = useState('');
  const [items, setItems] = useState<RegionCandidate[]>([]);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const picked = useRef<string | null>(null);

  useEffect(() => {
    const text = query.trim();
    if (text.length < 2) { setItems([]); setFailed(false); return; }
    // 글자를 칠 때마다 부르지 않는다 — 손이 멈춘 뒤에 한 번만 묻는다.
    const timer = setTimeout(() => {
      const controller = new AbortController();
      setBusy(true);
      void searchRegions(text, accessToken, controller.signal)
        .then((result) => {
          if (controller.signal.aborted) return;
          if (result.state === 'success') { setItems(result.items); setFailed(false); }
          else { setItems([]); setFailed(true); }
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
    setQuery('');
    setItems([]);
  };

  return (
    <View style={styles.frame}>
      <TextInput
        accessibilityLabel={tx('장소나 지역 검색', 'Search a place or area')}
        style={styles.search}
        placeholder={tx('장소나 지역을 검색해요 (예: 해운대)', 'Search a place or area (e.g. Haeundae)')}
        placeholderTextColor={color.text.muted}
        value={query}
        onChangeText={setQuery}
        autoCorrect={false}
      />

      {busy ? <View style={styles.row}><ActivityIndicator color={color.action.primary} /></View> : null}

      {/* 검색이 실패해도 손으로 쓰는 길은 막지 않는다. */}
      {failed ? <Text variant="caption" color={color.text.muted} style={styles.note}>
        {tx('검색이 안 돼요. 아래에 직접 적어도 돼요.', 'Search is unavailable — you can type it below.')}
      </Text> : null}

      {items.map((item) => (
        <Pressable
          key={`${item.name}-${item.address}`}
          accessibilityRole="button"
          accessibilityLabel={tx(`${item.name} 고르기`, `Choose ${item.name}`)}
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

      <TextInput
        accessibilityLabel={tx('지역', 'Region')}
        style={styles.region}
        placeholder={tx('예: 해운대구', 'e.g. Haeundae-gu')}
        placeholderTextColor={color.text.muted}
        value={region}
        onChangeText={(value) => {
          onChangeRegion(value);
          // 손으로 고치면 장소 연결을 끊는다. 「해운대해수욕장」을 고른 뒤 「광안리」로
          // 바꿔 쓰면, 글에는 여전히 해운대가 달려 있게 된다 — 화면과 저장된 것이 달라진다.
          if (picked.current !== null && value !== picked.current) { picked.current = null; onChangePlaceId(undefined); }
        }}
        maxLength={60}
      />

      {placeId ? <Text variant="caption" color={color.text.muted} style={styles.note}>
        {tx('이 글이 그 장소에 함께 보여요.', 'This record will also show on that place.')}
      </Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  frame: { gap: spacing[1] },
  search: { minHeight: 40, paddingHorizontal: spacing[3], borderRadius: radius.sm, backgroundColor: color.surface.soft, color: color.text.heading },
  region: { minHeight: 40, paddingHorizontal: spacing[3], borderRadius: radius.sm, backgroundColor: color.surface.soft, color: color.text.heading },
  row: { minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[3], paddingVertical: spacing[2], borderRadius: radius.sm },
  rowBody: { flex: 1, minWidth: 0 },
  note: { paddingHorizontal: spacing[1] },
});
