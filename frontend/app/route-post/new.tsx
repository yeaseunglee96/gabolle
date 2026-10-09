// 루트로 올리기 — 내 여행의 하루 또는 전체를 피드 루트로 (S15P21E201-2027).
// 들어오는 길: 여행 화면 ⋯ 메뉴의 「루트로 올리기」(?itineraryId=…).
//
// 🔴 숙소는 서버가 빼고 올린다(숙소 정류장·숙소로 드나드는 선·출발점). 미리보기는 숙소도 그대로 보여 주므로
//    「숙소 위치는 올라가지 않아요」를 적어 둔다 — 미리보기에서 빼면 거르는 곳이 둘이 된다.
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { localizeMessage } from '@/i18n/messages';
import { useLayout } from '@/layout/useLayout';
import { loadItinerary } from '@/plan/itinerary';
import { publishRoutePost, ROUTE_POSTS_KEY } from '@/routepost/routePosts';

const TITLE_MAX = 80;
const BODY_MAX = 500;

export default function NewRoutePostScreen() {
  const { itineraryId } = useLocalSearchParams<{ itineraryId: string }>();
  const router = useRouter();
  const { tx } = useI18n();
  const { accessToken } = useAuth();
  const { desktop } = useLayout();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['route-post-source', itineraryId], queryFn: () => loadItinerary(itineraryId ?? '', accessToken), enabled: Boolean(itineraryId && accessToken) });
  const [day, setDay] = useState<number | null>(null);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const itinerary = query.data?.state === 'success' ? query.data.itinerary : null;
  useEffect(() => { if (itinerary && !title) setTitle(itinerary.title.slice(0, TITLE_MAX)); }, [itinerary, title]);

  const goBack = () => (router.canGoBack() ? router.back() : router.replace('/trips' as never));

  if (!accessToken) {
    return <Screen scroll><View style={styles.card}><Text weight="bold">{tx('로그인하면 내 여행을 루트로 올릴 수 있어요.', 'Sign in to post your trip as a route.')}</Text>
      <Button label={tx('로그인', 'Sign in')} compact onPress={() => router.push({ pathname: '/sign-in', params: { returnTo: `/route-post/new?itineraryId=${itineraryId}` } } as never)} /></View></Screen>;
  }
  if (query.isLoading) return <Screen scroll><View style={styles.center}><ActivityIndicator color={color.action.primary} /></View></Screen>;
  if (!itinerary) {
    return <Screen scroll><View style={styles.card}><Text weight="bold">{tx('여행을 불러오지 못했어요', 'Could not load the trip')}</Text>
      <Button label={tx('다시 시도', 'Try again')} variant="tertiary" compact onPress={() => void query.refetch()} /></View></Screen>;
  }

  const days = itinerary.days;
  const chosen = day == null ? days.flatMap((d) => d.items) : days[day]?.items ?? [];
  const canPublish = title.trim().length > 0 && chosen.length >= 2 && !busy;

  const publish = async () => {
    if (!canPublish) return;
    setBusy(true); setError(null);
    const result = await publishRoutePost({ itineraryId: itinerary.id, dayIndex: day, title: title.trim(), body: body.trim(), accessToken });
    setBusy(false);
    if (result.state !== 'success') { setError(result.message === 'TOO_FEW_STOPS' ? tx('숙소를 빼면 들른 곳이 두 곳보다 적어서 올릴 수 없어요.', 'Without your lodging there are fewer than two stops, so this cannot be posted.') : localizeMessage(tx, result.message)); return; }
    void queryClient.invalidateQueries({ queryKey: ROUTE_POSTS_KEY });
    router.replace(`/route-post/${result.post.routePostId}` as never);
  };

  const chip = (value: number | null, label: string) => (
    <Pressable key={String(value)} accessibilityRole="radio" accessibilityState={{ checked: day === value }} onPress={() => setDay(value)} style={[styles.chip, day === value && styles.chipOn]}>
      <Text variant="caption" weight="bold" color={day === value ? color.text.onAction : color.text.body}>{label}</Text>
    </Pressable>
  );

  return (
    <Screen scroll>
      <View style={[styles.column, desktop && styles.columnWide]}>
        <Pressable accessibilityRole="button" accessibilityLabel={tx('뒤로 가기', 'Go back')} onPress={goBack} style={styles.back}><Text variant="title">‹</Text></Pressable>
        <Text variant="display" weight="bold" color={color.text.heading}>{tx('루트로 올리기', 'Post as a route')}</Text>
        <Text color={color.text.body}>{tx('내 코스를 피드에 올려 다른 여행자가 따라 갈 수 있게 해요. 모두에게 공개돼요.', 'Share your course on the feed so other travelers can follow it. It will be public.')}</Text>

        {days.length > 1 ? (
          <View style={styles.field}>
            <Text weight="bold">{tx('어느 날을 올릴까요?', 'Which day?')}</Text>
            <View accessibilityRole="radiogroup" style={styles.chips}>
              {chip(null, tx('전체 일정', 'Whole trip'))}
              {days.map((_, index) => chip(index, txf(tx, '%d일차', 'Day %d', index + 1)))}
            </View>
          </View>
        ) : null}

        <View style={styles.field}>
          <Text weight="bold">{tx('제목', 'Title')}</Text>
          <TextInput testID="route-title" accessibilityLabel={tx('제목', 'Title')} style={styles.input} value={title} maxLength={TITLE_MAX} onChangeText={(v) => setTitle(v.slice(0, TITLE_MAX))} placeholder={tx('예: 광안리 노을 산책 코스', 'e.g. Gwangalli sunset walk')} placeholderTextColor={color.text.muted} />
        </View>
        <View style={styles.field}>
          <Text weight="bold">{tx('한 줄 소개 (선택)', 'Short note (optional)')}</Text>
          <TextInput testID="route-body" accessibilityLabel={tx('한 줄 소개', 'Short note')} style={[styles.input, styles.multiline]} multiline value={body} maxLength={BODY_MAX} onChangeText={(v) => setBody(v.slice(0, BODY_MAX))} placeholder={tx('이 코스의 좋았던 점을 적어 주세요', 'What did you like about this route?')} placeholderTextColor={color.text.muted} />
        </View>

        <View style={styles.card}>
          <Text weight="bold">{txf(tx, '올라갈 곳 %d곳', '%d stops to post', chosen.length)}</Text>
          <Text variant="caption" color={color.text.body} numberOfLines={4}>{chosen.map((item, index) => `${index + 1} ${item.title}`).join('  →  ')}</Text>
          <Text variant="caption" color={color.text.muted}>{tx('🔒 숙소와 숙소를 오가는 길은 올라가지 않아요.', '🔒 Your lodging and the way to and from it are not posted.')}</Text>
        </View>

        {chosen.length < 2 ? <Text variant="caption" color={color.state.danger}>{tx('두 곳 이상 있어야 루트로 올릴 수 있어요.', 'A route needs at least two stops.')}</Text> : null}
        {error ? <Text accessibilityLiveRegion="polite" variant="caption" color={color.state.danger}>{error}</Text> : null}
        <Button testID="route-publish" label={busy ? tx('올리는 중…', 'Posting…') : tx('피드에 올리기', 'Post to the feed')} disabled={!canPublish} onPress={() => void publish()} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { padding: spacing[8], alignItems: 'center' },
  back: { width: 44, height: 44, justifyContent: 'center' },
  column: { gap: spacing[4] },
  columnWide: { maxWidth: 640, width: '100%', alignSelf: 'center' },
  field: { gap: spacing[2] },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] },
  chip: { minHeight: 36, paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card, borderWidth: 1, borderColor: color.surface.field, justifyContent: 'center' },
  chipOn: { backgroundColor: color.brand.navy, borderColor: color.brand.navy },
  input: { minHeight: 48, borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.field, backgroundColor: color.surface.card, paddingHorizontal: spacing[3], paddingVertical: spacing[2], color: color.text.heading, fontSize: 16 },
  multiline: { minHeight: 96, textAlignVertical: 'top' },
  card: { gap: spacing[2], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
});
