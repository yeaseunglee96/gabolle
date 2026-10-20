// 근처 편의시설의 «어디 근처를 볼까요?» — 기준 자리를 직접 고른다 (S15P21E201-2069, 사용자 결정 2026-10-20).
//
// 🔴 위치를 못 쓰면 서면으로 넘어가던 것을 대신한다. 공항에 막 내린 사람에게 «서면 근처 환전소»는 쓸모가 없다.
//    검색은 출발지 검색과 같은 길(GET /api/v1/origins — 카카오 지도 장소 검색)을 쓰고, 자주 가는 곳은 한 번에 누른다.
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View, type StyleProp, type ViewStyle } from 'react-native';

import { ChipLabel } from '@/components/ChipLabel';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { searchOrigins, type OriginCandidate } from '@/plan/origins';

export type NearArea = { latitude: number; longitude: number; name: string };

/** 여행자가 많이 묻는 자리 — 좌표는 각 장소의 대표 지점(공항은 국제선 청사, 역은 역 광장). */
export const POPULAR_AREAS: ReadonlyArray<NearArea & { nameEn: string }> = [
  { name: '김해공항', nameEn: 'Gimhae Airport', latitude: 35.1732, longitude: 128.9466 },
  { name: '부산역', nameEn: 'Busan Station', latitude: 35.1152, longitude: 129.0422 },
  { name: '서면', nameEn: 'Seomyeon', latitude: 35.1578, longitude: 129.0599 },
  { name: '해운대', nameEn: 'Haeundae', latitude: 35.1587, longitude: 129.1604 },
  { name: '남포동', nameEn: 'Nampo-dong', latitude: 35.0979, longitude: 129.0303 },
  { name: '광안리', nameEn: 'Gwangalli', latitude: 35.1532, longitude: 129.1187 },
];

const MIN_QUERY = 2;
const DEBOUNCE_MS = 300;

export function NearAreaPicker({ accessToken, onPick, testID = 'near-area', title, style }: {
  accessToken: string | null;
  onPick: (area: NearArea) => void;
  testID?: string;
  /** 위에 적는 물음. 없으면 「어디 근처를 볼까요?」 */
  title?: string;
  style?: StyleProp<ViewStyle>;
}) {
  const { tx, language } = useI18n();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<OriginCandidate[] | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const text = query.trim();
    // 서버가 두 글자 미만은 거절한다 — 그 호출을 안 나가게 먼저 거른다(plan/origins 와 같은 약속).
    if (text.length < MIN_QUERY) { setResults(null); setFailed(false); return; }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      void searchOrigins(text, accessToken, controller.signal).then((result) => {
        if (controller.signal.aborted) return;
        if (result.state === 'success') { setResults(result.items); setFailed(false); }
        else { setResults([]); setFailed(true); }
      });
    }, DEBOUNCE_MS);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [query, accessToken]);

  const pick = (area: NearArea) => { setQuery(''); setResults(null); onPick(area); };

  return (
    <View testID={testID} style={[styles.box, style]}>
      <Text variant="body" weight="bold">{title ?? tx('어디 근처를 볼까요?', 'Where should we look?')}</Text>
      <TextInput
        testID={`${testID}-input`}
        value={query}
        onChangeText={setQuery}
        placeholder={tx('장소·역 이름 — 예: 김해공항, 부산역', 'Place or station — e.g. Gimhae Airport')}
        placeholderTextColor={color.text.muted}
        accessibilityLabel={tx('기준 장소 검색', 'Search a place to look around')}
        style={styles.input}
        returnKeyType="search"
      />
      {results && results.length > 0 ? (
        <View testID={`${testID}-results`} style={styles.results}>
          {results.slice(0, 6).map((item, index) => (
            <Pressable
              key={`${item.name}-${index}`}
              testID={`${testID}-result-${index}`}
              accessibilityRole="button"
              onPress={() => pick({ latitude: item.lat, longitude: item.lng, name: item.name })}
              style={({ pressed }) => [styles.result, pressed && styles.pressed]}
            >
              <Text weight="bold" numberOfLines={1}>{language !== 'ko' && item.nameEn ? item.nameEn : item.name}</Text>
              {item.address ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{item.address}</Text> : null}
            </Pressable>
          ))}
        </View>
      ) : results && query.trim().length >= MIN_QUERY ? (
        <Text variant="caption" color={color.text.muted}>
          {failed ? tx('지금은 검색할 수 없어요. 아래 자주 가는 곳을 눌러 보세요.', 'Search is unavailable right now. Try one of the places below.') : tx('찾는 곳이 없어요. 다른 이름으로 찾아보세요.', 'No matches. Try another name.')}
        </Text>
      ) : null}
      <View style={styles.chips}>
        {POPULAR_AREAS.map((area) => (
          <Pressable key={area.name} testID={`${testID}-chip-${area.name}`} accessibilityRole="button" onPress={() => pick(area)} style={({ pressed }) => [styles.chip, pressed && styles.pressed]}>
            <ChipLabel variant="util" color={color.text.heading}>{tx(area.name, area.nameEn)}</ChipLabel>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  input: { minHeight: 44, paddingHorizontal: spacing[3], borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.soft, color: color.text.heading, fontSize: 16 },
  results: { gap: spacing[1] },
  result: { paddingVertical: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card },
  pressed: { opacity: 0.72 },
});
