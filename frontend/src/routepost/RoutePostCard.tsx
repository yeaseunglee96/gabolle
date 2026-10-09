// 루트 포스트 카드 — 피드의 「루트」 탭과 장소 화면의 「이 장소가 들어간 루트」가 같이 쓴다 (S15P21E201-2027·2028).
// 기록(story) 카드와 일부러 모양이 다르다: 위에 경로 지도, 아래에 들른 곳 줄. 한눈에 「이건 코스다」가 보여야 한다.
import { Pressable, StyleSheet, View } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { txf } from '@/i18n/format';
import { AuthorAvatar } from '@/social/AuthorAvatar';

import { RouteThumb } from './RouteThumb';
import { distanceKm, durationParts, type RoutePostDto } from './routePosts';

export function routeMetaLine(post: Pick<RoutePostDto, 'stops' | 'durationMin' | 'distanceM' | 'dayIndex' | 'dayCount'>, tx: (ko: string, en: string) => string): string {
  const parts: string[] = [];
  parts.push(post.dayIndex == null && post.dayCount > 1 ? txf(tx, '%d일 코스', '%d-day route', post.dayCount) : tx('하루 코스', 'One-day route'));
  parts.push(txf(tx, '%d곳', '%d stops', post.stops.length));
  const d = durationParts(post.durationMin);
  if (d) parts.push(d.h > 0 ? (d.m > 0 ? txf(tx, '약 %d시간 %d분', 'About %dh %dm', d.h, d.m) : txf(tx, '약 %d시간', 'About %dh', d.h)) : txf(tx, '약 %d분', 'About %d min', d.m));
  const km = distanceKm(post.distanceM);
  if (km) parts.push(`${km}km`);
  return parts.join(' · ');
}

export function stopName(stop: { name: string; nameEn?: string | null }, language: string): string {
  return language !== 'ko' && stop.nameEn ? stop.nameEn : stop.name;
}

export function RoutePostCard({ post, rank, compact = false, onOpen }: { post: RoutePostDto; rank?: number | null; compact?: boolean; onOpen: () => void }) {
  const { tx, language } = useI18n();
  const names = post.stops.map((stop) => stopName(stop, language));
  const shown = names.slice(0, 4);
  const more = names.length - shown.length;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${post.title}. ${routeMetaLine(post, tx)}`}
      onPress={onOpen}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
      testID="route-post-card"
    >
      <View style={styles.thumbWrap}>
        <RouteThumb path={post.path} stops={post.stops} height={compact ? 150 : 180} />
        <View style={styles.badge}><Text variant="micro" weight="bold" color={color.text.onAction}>{tx('루트', 'Route')}</Text></View>
        {rank ? <View style={styles.rank}><Text variant="caption" weight="bold" color={color.text.onAction}>{String(rank)}</Text></View> : null}
      </View>
      <View style={styles.body}>
        <Text variant="title" weight="bold" numberOfLines={2} color={color.text.heading}>{post.title}</Text>
        <Text variant="caption" color={color.text.muted}>{routeMetaLine(post, tx)}</Text>
        <Text variant="caption" color={color.text.body} numberOfLines={2}>
          {shown.map((name, index) => `${index + 1} ${name}`).join('  →  ')}{more > 0 ? txf(tx, '  외 %d곳', '  +%d more', more) : ''}
        </Text>
        <View style={styles.footer}>
          <View style={styles.author}>
            <AuthorAvatar name={post.author.displayName} uri={post.author.avatarUrl ?? undefined} style={styles.avatar} />
            <Text variant="caption" color={color.text.body} numberOfLines={1} style={styles.authorName}>{post.author.displayName}</Text>
          </View>
          <Text variant="caption" color={color.text.muted}>
            {`♥ ${post.likeCount}   ★ ${post.saveCount}   ${txf(tx, '따라 간 사람 %d', '%d followed', post.copyCount)}`}
          </Text>
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: color.surface.card, borderRadius: radius.lg, overflow: 'hidden', borderWidth: 1, borderColor: color.surface.border },
  pressed: { opacity: 0.85 },
  thumbWrap: { position: 'relative' },
  badge: { position: 'absolute', left: spacing[3], top: spacing[3], backgroundColor: color.brand.navy, borderRadius: radius.full, paddingHorizontal: spacing[2], paddingVertical: 2 },
  rank: { position: 'absolute', right: spacing[3], top: spacing[3], width: 28, height: 28, borderRadius: 14, backgroundColor: color.action.primary, alignItems: 'center', justifyContent: 'center' },
  body: { padding: spacing[4], gap: spacing[2] },
  footer: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[2], marginTop: spacing[1] },
  author: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], flexShrink: 1 },
  authorName: { flexShrink: 1 },
  avatar: { width: 24, height: 24, borderRadius: 12 },
});
