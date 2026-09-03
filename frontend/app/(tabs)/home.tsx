// 02 메인 홈 — 로그인 뒤 제일 먼저 보는 화면(시연 경로가 반드시 지나간다). Figma 02_메인 홈 실측.
//
// 🔴 이 셸은 아직 진짜 탭 내비게이션이 아니라 Stack 하나뿐이다(app/_layout.tsx).
// 하단 탭은 화면마다 TabBar 로 직접 그린다 — 홈·내 정보만 화면이 있어 그 둘만 눌린다.
//
// 아래 세 구역(실시간 혼잡도 · 추천 일정 · 오늘의 부산 카드)은 전부 하드코딩 목업이다.
// 실제 API 가 없어 그렇다 — "오늘의 부산" 카드가 Editor's Pick 성격이라 그 자리에만
// 엔드포인트 TODO 를 남긴다.
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useState } from 'react';

import { color, gutter, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { TabBar } from '@/components/TabBar';

type CategoryKey = 'hotplace' | 'sea' | 'food' | 'nature' | 'experience';

type Category = {
  key: CategoryKey;
  icon: string;
  label: string;
};

const CATEGORIES: Category[] = [
  { key: 'hotplace', icon: '🔥', label: '핫플' },
  { key: 'sea', icon: '🌊', label: '바다' },
  { key: 'food', icon: '🍜', label: '미식' },
  { key: 'nature', icon: '🌿', label: '자연' },
  { key: 'experience', icon: '✨', label: '체험' },
];

type CongestionLevel = 'relaxed' | 'normal' | 'crowded';

type CongestionSpot = {
  name: string;
  level: CongestionLevel;
  label: string;
};

const CONGESTION: CongestionSpot[] = [
  { name: '해운대 해수욕장', level: 'relaxed', label: '여유' },
  { name: '광안리 해수욕장', level: 'normal', label: '보통' },
  { name: '감천문화마을', level: 'crowded', label: '혼잡' },
];

// success/warning/danger 는 이미 "상태" 색으로 정의돼 있어 그대로 재사용한다 —
// Figma 의 혼잡도 점 색(#43b28e·#ffbd45·#f47069)과 완전히 같은 값은 아니지만
// 같은 3단계 의미(여유·보통·혼잡)라 새 토큰을 더하지 않는다.
const CONGESTION_COLOR: Record<CongestionLevel, string> = {
  relaxed: color.state.success,
  normal: color.state.warning,
  crowded: color.state.danger,
};

export default function Home() {
  const router = useRouter();
  const [category, setCategory] = useState<CategoryKey>('sea');
  const [liked, setLiked] = useState(false);

  return (
    <Screen scroll>
      <View style={styles.headerRow}>
        <View style={styles.headerCopy}>
          <Text variant="caption" weight="medium" color={color.text.body}>
            좋은 아침이에요, 미리님 ☀
          </Text>
          <Text variant="display" weight="bold" color={color.text.heading} style={styles.headerTitle}>
            오늘, 부산{'\n'}어디로 떠나볼까요?
          </Text>
        </View>
        {/* 알림 진입점. 알림 화면이 아직 없어 지금은 장식만 한다. */}
        <View style={styles.bellButton}>
          <Text variant="title">🔔</Text>
        </View>
      </View>

      {/* TODO: 실제 검색 화면 미정 — 지금은 자리만 두고 누를 수 없게 한다. */}
      <View style={styles.searchBar}>
        <Text variant="body" color={color.text.muted}>
          어디로 갈지 검색해보세요
        </Text>
        <Text variant="body" color={color.text.muted}>
          🔍
        </Text>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.categoryScroll}>
        {CATEGORIES.map((item) => {
          const selected = item.key === category;
          return (
            <Pressable
              key={item.key}
              onPress={() => setCategory(item.key)}
              style={[styles.categoryChip, selected && styles.categoryChipSelected]}
            >
              <Text variant="body">{item.icon}</Text>
              <Text variant="caption" weight="bold" color={selected ? color.text.accent : color.text.heading}>
                {item.label}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>

      {/* 🔴 Figma 02 메인 홈에는 계획으로 들어가는 입구가 없다. 그런데 명세 v1.1 의 S-04 는
          홈의 기능을 "Editor's Pick, **계획 만들기**, 지금 갈 곳, 최근 여행" 으로 못박는다.
          Figma 가 빠뜨린 쪽으로 보고 명세를 따라 넣는다 — 이게 없으면 06~12 화면이
          앱 안에서 도달 불가능한 화면이 된다. */}
      <Pressable accessibilityRole="button" accessibilityLabel="여행 계획 만들기" style={styles.planCta} onPress={() => router.push('/plan/basic')}>
        <View>
          <Text variant="body" weight="bold" color={color.text.onAction}>
            여행 계획 만들기
          </Text>
          <Text variant="caption" color={color.text.onAction}>
            조건을 알려주면 갈 수 있는 곳만 골라 드려요
          </Text>
        </View>
        <Text variant="title" weight="bold" color={color.text.onAction}>
          →
        </Text>
      </Pressable>

      {/* Editor's Pick 성격의 카드. 실제 데이터가 오기 전까지 송도 케이블카로 고정한다. */}
      {/* TODO: GET /api/v1/feed/editorial-picks */}
      <Pressable style={styles.featured} onPress={() => router.push('/place/songdo-cablecar')}>
        <View style={styles.featuredTopRow}>
          <View style={styles.featuredPill}>
            <Text variant="caption" weight="bold" color={color.text.accent}>
              오늘의 부산
            </Text>
          </View>
          <Pressable
            style={styles.featuredHeart}
            onPress={(event) => {
              event.stopPropagation();
              setLiked((prev) => !prev);
            }}
          >
            <Text variant="body" color={liked ? color.state.danger : color.text.muted}>
              {liked ? '♥' : '♡'}
            </Text>
          </Pressable>
        </View>
        <View style={styles.featuredCopy}>
          <Text variant="title" weight="bold" color={color.text.onAction}>
            송도 해상 케이블카
          </Text>
          <Text variant="caption" color={color.text.onAction} style={styles.featuredSubtitle}>
            바다 위를 가로지르는 감동, 부산의 대표 뷰
          </Text>
        </View>
      </Pressable>

      <View style={styles.sectionHeaderRow}>
        <Text variant="title" weight="bold" color={color.text.heading}>
          실시간 혼잡도
        </Text>
        <Text variant="caption" weight="medium" color={color.text.body}>
          10:30 기준 〉
        </Text>
      </View>

      <View style={styles.congestionRow}>
        {CONGESTION.map((spot) => (
          <View key={spot.name} style={styles.congestionCard}>
            <Text variant="caption" color={color.text.body} style={styles.congestionName}>
              {spot.name}
            </Text>
            <View style={styles.congestionValueRow}>
              <View style={[styles.congestionDot, { backgroundColor: CONGESTION_COLOR[spot.level] }]} />
              <Text variant="body" weight="bold" color={color.text.heading}>
                {spot.label}
              </Text>
            </View>
          </View>
        ))}
      </View>

      <View style={styles.sectionHeaderRow}>
        <Text variant="title" weight="bold" color={color.text.heading}>
          미리님을 위한 일정
        </Text>
        <Text variant="caption" weight="medium" color={color.text.body}>
          전체 보기 〉
        </Text>
      </View>

      <Pressable style={styles.courseCard} onPress={() => router.push('/demo-trip/result')}>
        <View style={styles.courseThumb} />
        <View style={styles.courseCopy}>
          <Text variant="body" weight="bold" color={color.text.heading}>
            바다와 골목을 담은 1일 코스
          </Text>
          <Text variant="caption" color={color.text.body}>
            송도 → 흰여울 → 광안리
          </Text>
          <Text variant="caption" weight="bold" color={color.text.accent}>
            현지인 추천 92% · 약 6시간
          </Text>
        </View>
      </Pressable>

      <Pressable style={styles.chatPill} onPress={() => router.push('/chat')}>
        <Text variant="caption" weight="bold" color={color.text.accent}>
          동백이에게 물어보기 ✦
        </Text>
      </Pressable>

      <View style={styles.tabBar}>
        <TabBar active="home" />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  headerCopy: {
    flex: 1,
    gap: spacing[2],
  },
  headerTitle: {
    marginTop: spacing[1],
  },
  bellButton: {
    width: 43,
    height: 43,
    borderRadius: radius.md,
    backgroundColor: color.surface.card,
    alignItems: 'center',
    justifyContent: 'center',
  },
  searchBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
    marginTop: spacing[6],
  },
  categoryScroll: {
    marginTop: spacing[4],
  },
  planCta: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing[3],
    marginTop: spacing[6],
    padding: spacing[4],
    borderRadius: radius.md,
    backgroundColor: color.action.primary,
  },
  categoryChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
    marginRight: spacing[2],
  },
  categoryChipSelected: {
    backgroundColor: color.surface.soft,
  },
  featured: {
    height: 205,
    borderRadius: radius.lg,
    backgroundColor: color.action.primary,
    marginTop: spacing[6],
    padding: spacing[3],
    justifyContent: 'space-between',
  },
  featuredTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  featuredPill: {
    backgroundColor: color.surface.card,
    borderRadius: radius.full,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
  },
  featuredHeart: {
    width: 34,
    height: 34,
    borderRadius: radius.md,
    backgroundColor: color.surface.card,
    alignItems: 'center',
    justifyContent: 'center',
  },
  featuredCopy: {
    gap: spacing[1],
  },
  featuredSubtitle: {
    opacity: 0.92,
  },
  sectionHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: spacing[8],
  },
  congestionRow: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
  },
  congestionCard: {
    flex: 1,
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[3],
    gap: spacing[2],
  },
  congestionName: {
    height: 28,
  },
  congestionValueRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
  },
  congestionDot: {
    width: 7,
    height: 7,
    borderRadius: radius.full,
  },
  courseCard: {
    flexDirection: 'row',
    gap: spacing[3],
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[3],
    marginTop: spacing[3],
  },
  courseThumb: {
    width: 76,
    height: 53,
    borderRadius: radius.sm,
    backgroundColor: color.surface.soft,
  },
  courseCopy: {
    flex: 1,
    gap: spacing[1],
    justifyContent: 'center',
  },
  chatPill: {
    alignSelf: 'center',
    backgroundColor: color.surface.card,
    borderRadius: radius.full,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
    marginTop: spacing[8],
  },
  tabBar: {
    marginTop: spacing[4],
    marginHorizontal: -gutter,
  },
});
