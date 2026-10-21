// 끝난 여행에 매기는 별 다섯 개(S15P21E201-1908). 누르면 저장, 같은 별을 다시 누르면 지운다.
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { loadTripRating, nextScore, ratingUnavailable, saveTripRating, type TripRating } from './tripRating';

const STARS = [1, 2, 3, 4, 5] as const;

export const TRIP_RATING_KEY = (tripId: string) => ['trip-rating', tripId] as const;

export function TripRatingStars({ tripId, accessToken }: { tripId: string; accessToken: string }) {
  const { tx } = useI18n();
  const queryClient = useQueryClient();
  const [failed, setFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const query = useQuery({
    queryKey: TRIP_RATING_KEY(tripId),
    queryFn: ({ signal }) => loadTripRating(tripId, accessToken, signal),
    retry: false,
    staleTime: 60_000,
  });
  // 🔴 서버에 경로가 없으면(404·501) 줄을 통째로 숨긴다 — 눌러도 안 되는 별을 보여 주지 않는다.
  if (query.isError && ratingUnavailable(query.error)) return null;
  if (!query.data) return null;
  const rating = query.data;
  const mine = rating.myScore;

  const press = async (star: number) => {
    if (saving) return;
    const score = nextScore(mine, star);
    const previous = rating;
    setFailed(false);
    setSaving(true);
    queryClient.setQueryData<TripRating>(TRIP_RATING_KEY(tripId), { ...rating, myScore: score });
    try {
      queryClient.setQueryData(TRIP_RATING_KEY(tripId), await saveTripRating(tripId, score, accessToken));
    } catch {
      queryClient.setQueryData(TRIP_RATING_KEY(tripId), previous);
      setFailed(true);
    } finally {
      setSaving(false);
    }
  };

  // 혼자 매긴 여행에서는 평균이 곧 내 점수라 따로 안 적는다.
  const others = rating.count > 1 && rating.average != null
    ? txf(tx, '평균 %s · %s명', 'Avg %s · %s people', rating.average.toFixed(1), String(rating.count))
    : null;

  return <View style={styles.wrap}>
    <View style={styles.row} accessibilityRole="radiogroup" accessibilityLabel={tx('여행 별점', 'Trip rating')}>
      {STARS.map((star) => {
        const on = mine != null && star <= mine;
        return <Pressable
          key={star}
          accessibilityRole="radio"
          accessibilityState={{ checked: mine === star, disabled: saving }}
          accessibilityLabel={mine === star ? txf(tx, '별 %s개 — 다시 누르면 지워요', '%s stars — tap again to clear', String(star)) : txf(tx, '별 %s개', '%s stars', String(star))}
          hitSlop={4}
          onPress={(event) => { event?.stopPropagation?.(); void press(star); }}
          style={styles.star}
        >
          <Text variant="title" color={on ? color.state.rating : color.text.muted}>{on ? '★' : '☆'}</Text>
        </Pressable>;
      })}
      {mine == null ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{tx('이 여행은 어땠나요?', 'How was this trip?')}</Text> : null}
      {others ? <Text variant="caption" color={color.text.muted} numberOfLines={1}>{others}</Text> : null}
    </View>
    {failed ? <Text variant="caption" color={color.state.danger} accessibilityLiveRegion="polite">{tx('별점을 저장하지 못했어요. 다시 시도해 주세요.', "Couldn't save your rating. Please try again.")}</Text> : null}
  </View>;
}

const styles = StyleSheet.create({
  wrap: { gap: spacing[1] },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing[1], flexWrap: 'wrap' },
  star: { paddingHorizontal: 2 },
});
