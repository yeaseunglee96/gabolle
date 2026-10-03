// 저장한 후보 — 여행 만들기 「꼭 가고 싶은 곳」 단계에서 둘러보기·장소 상세로 저장해 둔 곳을 바로 고른다
// (S15P21E201-1970).
//
// 🔴 자동으로 전부 넣지 않는다. 저장한 곳에는 이번 지역·날짜와 안 맞는 곳도 섞여 있어서, 다 넣으면 추천이
//    「이 여행」이 아니라 「저장 목록」이 된다. 누른 것만 넣고, 고른 것은 검색으로 고른 것과 같은
//    mustVisitPlaces 에 들어가 그대로 mustVisitPlaceIds 로 서버에 간다 — 서버는 바꾸지 않는다.
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { loadSavedPlaceIds } from '@/discovery/savedPlaces';
import { resolveSavedCandidate } from '@/discovery/savedPlaceCards';
import { txf } from '@/i18n/format';
import type { LanguageCode } from '@/i18n/languages';
import type { MustVisitPlace } from '@/plan/PlanProvider';

export function SavedCandidates({
  picked,
  onChange,
  max,
  tx,
  language,
}: {
  picked: MustVisitPlace[];
  onChange: (next: MustVisitPlace[]) => void;
  max: number;
  tx: (ko: string, en: string) => string;
  language: LanguageCode;
}) {
  const { accessToken } = useAuth();
  const [candidates, setCandidates] = useState<MustVisitPlace[] | null>(null);
  const [blocked, setBlocked] = useState(false);

  // 로그인 안 했으면 loadSavedPlaceIds 가 기기에 저장한 것만 돌려준다.
  useEffect(() => {
    let alive = true;
    void (async () => {
      const ids = await loadSavedPlaceIds(accessToken);
      const resolved = await Promise.all(ids.map((id) => resolveSavedCandidate(id)));
      if (alive) setCandidates(resolved.filter((place): place is MustVisitPlace => place !== null));
    })();
    return () => { alive = false; };
  }, [accessToken]);

  // 아직 못 불러왔거나 저장한 곳이 없으면 줄 자체를 안 그린다 — 빈 머리만 남으면 고장으로 읽힌다.
  if (!candidates?.length) return null;

  const name = (place: MustVisitPlace) => (language === 'ko' ? place.nameKo : place.nameEn ?? place.nameKo);
  const toggle = (place: MustVisitPlace) => {
    if (picked.some((entry) => entry.placeId === place.placeId)) {
      setBlocked(false);
      onChange(picked.filter((entry) => entry.placeId !== place.placeId));
      return;
    }
    if (picked.length >= max) { setBlocked(true); return; }
    setBlocked(false);
    onChange([...picked, place]);
  };

  return (
    <View style={styles.wrap}>
      <Text variant="caption" weight="bold" color={color.text.heading}>{tx('저장한 후보에서 고르기', 'Pick from saved places')}</Text>
      <View style={styles.row}>
        {candidates.map((place) => {
          const on = picked.some((entry) => entry.placeId === place.placeId);
          return (
            <Pressable
              key={place.placeId}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: on }}
              onPress={() => toggle(place)}
              style={[styles.chip, on && styles.chipOn]}
            >
              <Text variant="caption" weight="bold" color={on ? color.text.onAction : color.text.heading} numberOfLines={1}>{on ? `✓ ${name(place)}` : name(place)}</Text>
            </Pressable>
          );
        })}
      </View>
      {blocked ? (
        <Text variant="caption" color={color.text.muted}>{txf(tx, '이 여행에는 %s곳까지 담을 수 있어요. 하나를 빼면 다른 후보를 넣을 수 있어요.', 'This trip can hold up to %s places. Remove one to add another.', max)}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing[2] },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: {
    maxWidth: '100%', minHeight: 36, justifyContent: 'center', paddingHorizontal: spacing[3],
    borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card,
  },
  chipOn: { borderColor: color.brand.navy, backgroundColor: color.brand.navy },
});
