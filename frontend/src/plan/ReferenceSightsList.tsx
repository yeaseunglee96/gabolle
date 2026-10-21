// 참고 명소 — 갈래별 부산 대표 관광지, 사진 + 한 줄 소개 + 「넣기」 (S15P21E201-2019).
//
// 직접 짜기 화면과 「장소 더하기」 창이 함께 쓴다. 넣는 일(어느 날·어느 자리)은 부르는 쪽이 정한다 — 여기는 고른 장소 번호만 알린다.
// 이름·사진은 장소 상세에서 받는다(loadPlacePhoto, 세션 동안 기억). 사진이 없으면 갈래 글자를 그린다 — 다른 곳의 사진을 빌리지 않는다.
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { AppImage } from '@/components/AppImage';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { festivalDisplayTitle, getFestivals } from '@/discovery/festivals';
import { otherNameFor } from '@/discovery/localNames';
import { photoSourceShortText } from '@/discovery/places';
import { placeNameForLanguage } from '@/discovery/romanize';
import { useI18n } from '@/i18n';
import { categoryGlyph, loadPlacePhoto, type PlacePhoto } from '@/plan/placePhotos';
import { REFERENCE_THEMES, referenceSightsOf, type ReferenceThemeKey } from '@/plan/referenceSights';

type Card = { placeId: string; nameKo: string; line: string; photo: PlacePhoto | null };

const shortDate = (date: string) => date.slice(5).replace('-', '/');

export function ReferenceSightsList({ startDate, endDate, addedPlaceIds, busyPlaceId, onAdd, compact = false }: {
  /** 여행 첫날·마지막 날(YYYY-MM-DD). 없으면 축제 갈래는 「날짜를 모른다」고 말한다. */
  startDate?: string | null;
  endDate?: string | null;
  /** 지금 고른 날에 이미 있는 곳 — 「넣음」으로 그리고 다시 못 누른다. */
  addedPlaceIds: ReadonlySet<string>;
  /** 넣는 중인 곳. 그동안 모든 「넣기」가 잠긴다(두 번 눌러 둘이 들어가지 않게). */
  busyPlaceId: string | null;
  onAdd: (placeId: string) => void;
  /** 창 안에 넣을 때 — 카드를 작게 줄인다. */
  compact?: boolean;
}) {
  const { tx, language } = useI18n();
  const [theme, setTheme] = useState<ReferenceThemeKey>('sea');
  const [photos, setPhotos] = useState<Record<string, PlacePhoto>>({});
  const [festivals, setFestivals] = useState<Card[] | null>(null);
  const [festivalError, setFestivalError] = useState(false);
  const datesKnown = Boolean(startDate && endDate);

  useEffect(() => {
    let alive = true;
    const ids = referenceSightsOf(theme).map((sight) => sight.placeId).filter((id) => !(id in photos));
    if (ids.length === 0) return;
    void Promise.all(ids.map(async (id) => [id, await loadPlacePhoto(id)] as const)).then((entries) => {
      if (alive) setPhotos((current) => ({ ...current, ...Object.fromEntries(entries) }));
    });
    return () => { alive = false; };
    // photos 는 넣지 않는다 — 받은 것으로 다시 받기를 되풀이한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [theme]);

  useEffect(() => {
    if (theme !== 'festival' || festivals !== null || !startDate || !endDate) return;
    let alive = true;
    getFestivals(startDate, endDate)
      .then((items) => {
        if (!alive) return;
        setFestivals(items.slice(0, 8).map((item) => ({
          placeId: item.placeId,
          nameKo: festivalDisplayTitle(item),
          line: `${shortDate(item.startDate)} – ${shortDate(item.endDate)}`,
          photo: { photoUrl: item.photoUrl ?? null, photoSource: item.photoSource ?? null, category: 'FESTIVAL', nameEn: item.nameEn ?? null, localNames: item.localNames },
        })));
      })
      .catch(() => { if (alive) { setFestivals([]); setFestivalError(true); } });
    return () => { alive = false; };
  }, [theme, festivals, startDate, endDate]);

  const cards: Card[] | null = theme === 'festival'
    ? (datesKnown ? festivals : [])
    : referenceSightsOf(theme).map((sight) => ({ placeId: sight.placeId, nameKo: sight.nameKo, line: tx(sight.lineKo, sight.lineEn), photo: photos[sight.placeId] ?? null }));

  const festivalEmpty = !datesKnown
    ? tx('여행 날짜를 알아야 축제를 찾을 수 있어요.', 'We need the trip dates to find festivals.')
    : festivalError ? tx('축제를 불러오지 못했어요.', "Couldn't load festivals.")
      : tx('여행 기간에 열리는 축제가 없어요.', 'No festivals during your trip.');

  return (
    <View style={styles.wrap} testID="reference-sights">
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.themes}>
        {REFERENCE_THEMES.map((item) => {
          const selected = item.key === theme;
          return (
            <Pressable key={item.key} testID={`reference-theme-${item.key}`} accessibilityRole="tab" accessibilityState={{ selected }} onPress={() => setTheme(item.key)} style={[styles.theme, selected && styles.themeSelected]}>
              <Text variant="caption" weight="bold" color={selected ? color.text.onAction : color.text.heading}>{`${item.glyph} ${tx(item.ko, item.en)}`}</Text>
            </Pressable>
          );
        })}
      </ScrollView>

      {cards === null ? <ActivityIndicator color={color.brand.navy} /> : null}
      {theme === 'festival' && cards !== null && cards.length === 0 ? <Text variant="caption" color={color.text.muted}>{festivalEmpty}</Text> : null}

      <View style={styles.grid}>
        {(cards ?? []).map((card) => {
          const added = addedPlaceIds.has(card.placeId);
          const disabled = added || busyPlaceId !== null;
          const name = placeNameForLanguage(card.nameKo, otherNameFor(card.photo?.nameEn, card.photo?.localNames, language), language);
          const credit = card.photo?.photoUrl && card.photo.photoSource ? photoSourceShortText(card.photo.photoSource, tx) : null;
          return (
            <View key={card.placeId} style={[styles.card, compact && styles.cardCompact]}>
              <View style={[styles.photo, compact && styles.photoCompact]}>
                {card.photo?.photoUrl
                  ? <AppImage source={{ uri: card.photo.photoUrl }} resizeMode="cover" accessibilityLabel={name} style={StyleSheet.absoluteFill} />
                  : <Text variant="title">{categoryGlyph(card.photo?.category ?? (theme === 'festival' ? 'FESTIVAL' : null))}</Text>}
              </View>
              <View style={styles.copy}>
                <Text weight="bold" numberOfLines={1}>{name}</Text>
                <Text variant="caption" color={color.text.body} numberOfLines={compact ? 1 : 2}>{card.line}</Text>
                {credit && !compact ? <Text variant="caption" color={color.text.muted} numberOfLines={1} style={styles.credit}>{credit}</Text> : null}
              </View>
              <Pressable
                testID={`reference-add-${card.placeId}`}
                accessibilityRole="button"
                accessibilityLabel={added ? tx('이미 넣었어요', 'Already added') : `${tx('넣기', 'Add')} · ${name}`}
                accessibilityState={{ disabled }}
                disabled={disabled}
                onPress={() => onAdd(card.placeId)}
                style={({ pressed }) => [styles.add, added && styles.added, (pressed || (busyPlaceId !== null && !added)) && styles.dim]}
              >
                <Text variant="caption" weight="bold" color={added ? color.text.muted : color.text.onAction}>
                  {added ? tx('넣음', 'Added') : busyPlaceId === card.placeId ? '…' : tx('넣기', 'Add')}
                </Text>
              </Pressable>
            </View>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing[3] },
  themes: { gap: spacing[2], paddingVertical: spacing[1] },
  theme: { minHeight: 36, paddingHorizontal: spacing[3], borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, justifyContent: 'center' },
  themeSelected: { backgroundColor: color.action.secondary, borderColor: color.action.secondary },
  grid: { gap: spacing[2] },
  card: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], padding: spacing[2], borderRadius: radius.md, backgroundColor: color.surface.card },
  cardCompact: { padding: spacing[1], backgroundColor: color.surface.tint },
  photo: { width: 72, height: 72, borderRadius: radius.md, overflow: 'hidden', backgroundColor: color.surface.soft, alignItems: 'center', justifyContent: 'center' },
  photoCompact: { width: 48, height: 48 },
  copy: { flex: 1, minWidth: 0, gap: 2 },
  credit: { fontSize: 11 },
  add: { minHeight: 40, minWidth: 60, paddingHorizontal: spacing[3], borderRadius: radius.full, alignItems: 'center', justifyContent: 'center', backgroundColor: color.action.secondary },
  added: { backgroundColor: color.surface.soft },
  dim: { opacity: 0.6 },
});
