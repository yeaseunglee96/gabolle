// 「이 장소가 들어간 루트」 — 장소 상세와 장소를 단 기록 상세에 붙는다 (S15P21E201-2028).
// 피드백: 「장소 포스트도 이거 포함한 루트들 추천해주면 좋겠다」. 인기순 5개까지.
//
// 🔴 루트가 없으면 칸째 안 그린다. 「아직 없어요」 빈 칸을 장소마다 붙이면 장소 화면 대부분이 빈 칸을 하나 더 갖는다.
//    못 불러왔을 때도 조용히 안 그린다 — 장소 화면의 본론이 아니다.
import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import { useAuth } from '@/auth/AuthProvider';
import { Text } from '@/components/Text';
import { color, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';

import { RoutePostCard } from './RoutePostCard';
import { loadPlaceRoutes, type RoutePostDto } from './routePosts';

export function PlaceRoutesSection({ placeId, wide = false }: { placeId: string; wide?: boolean }) {
  const { tx } = useI18n();
  const router = useRouter();
  const { accessToken } = useAuth();
  // 보관소(react-query)를 쓰지 않는다 — 장소 화면은 보관소 없이도 그려지는 화면이라(시험·공유 링크) 여기서 그것을 요구하면 장소 화면째 깨진다.
  const [items, setItems] = useState<RoutePostDto[]>([]);
  useEffect(() => {
    if (!placeId) return undefined;
    let alive = true;
    void loadPlaceRoutes(placeId, accessToken).then((result) => { if (alive) setItems(result.state === 'success' ? result.items : []); });
    return () => { alive = false; };
  }, [placeId, accessToken]);
  if (!items.length) return null;
  return (
    <View style={styles.wrap} testID="place-routes">
      <Text variant="title" weight="bold" color={color.text.heading}>{tx('이 장소가 들어간 루트', 'Routes that include this place')}</Text>
      <Text variant="caption" color={color.text.muted}>{tx('다른 여행자가 이곳을 넣어 다녀온 코스예요. 인기 많은 순.', 'Courses other travelers took through here, most popular first.')}</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
        {items.map((post) => (
          <View key={post.routePostId} style={[styles.cell, wide && styles.cellWide]}>
            <RoutePostCard post={post} compact onOpen={() => router.push(`/route-post/${post.routePostId}` as never)} />
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing[2], marginTop: spacing[4] },
  row: { gap: spacing[3], paddingVertical: spacing[2] },
  cell: { width: 280 },
  cellWide: { width: 320 },
});
