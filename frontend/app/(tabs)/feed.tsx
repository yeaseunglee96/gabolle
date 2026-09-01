// 20 여행 피드·팔로잉 — Figma 20_여행 피드·팔로잉 실측 그대로.
//
// 🔴 명세 MVP 범위표는 "팔로잉" 을 고급 팔로우 기능으로 분류해 후속 단계로 뺐는데,
// Jira Soc 에픽에는 팔로우 탭·팔로우 버튼이 그대로 들어 있다. 어느 쪽을 따를지는 사람이
// 정할 일이라 판단하지 않고 Figma 대로 만든다.
//
// 하단 탭은 Figma 자체가 "홈" 을 활성 탭으로 그렸다(피드 전용 탭이 없다) — 공용 TabBar 에
// 새 탭을 만들지 않고 그대로 따른다.
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { color, gutter, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { TabBar } from '@/components/TabBar';

type FeedTab = '전체' | '팔로잉';

export default function Feed() {
  const [tab, setTab] = useState<FeedTab>('전체');
  const [following, setFollowing] = useState(false);
  const [liked, setLiked] = useState(false);

  return (
    <Screen scroll>
      <View style={styles.headerRow}>
        <View>
          <Text variant="caption" weight="medium">
            부산 여행자들의 지금
          </Text>
          <Text variant="display" weight="bold" style={styles.headerTitle}>
            여행 피드
          </Text>
        </View>
        {/* TODO: 게시글 작성 화면 미정 — 지금은 장식만 한다. */}
        <View style={styles.writeButton}>
          <Text variant="caption" weight="bold" color={color.text.onAction}>
            ✎ 기록 쓰기
          </Text>
        </View>
      </View>

      <View style={styles.tabsRow}>
        {(['전체', '팔로잉'] as FeedTab[]).map((option) => {
          const active = option === tab;
          return (
            <Pressable
              key={option}
              onPress={() => setTab(option)}
              style={[styles.tab, active ? styles.tabActive : styles.tabInactive]}
            >
              <Text variant="caption" weight="bold" color={active ? color.text.onAction : color.text.muted}>
                {option}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {tab === '팔로잉' ? (
        <Text variant="caption" style={styles.emptyNote}>
          팔로우한 여행자가 아직 없어요.
        </Text>
      ) : (
        <View style={styles.card}>
          <View style={styles.cardHeader}>
            <View style={styles.avatar}>
              <Text variant="title">🍲</Text>
            </View>
            <View style={styles.cardHeaderCopy}>
              <Text variant="body" weight="bold">
                부산한입 · 진미리
              </Text>
              <Text variant="caption" weight="medium" color={color.action.secondary}>
                방문 인증 · 18분 전
              </Text>
            </View>
            <Pressable onPress={() => setFollowing((prev) => !prev)}>
              <Text variant="caption" weight="bold" color={color.action.secondary}>
                {following ? '팔로잉' : '팔로우'}
              </Text>
            </Pressable>
          </View>

          <View style={styles.photo}>
            {/* TODO: 실제 여행자 사진. 자산이 오기 전까지 색 면으로 대체한다. */}
            <View style={styles.placeTag}>
              <Text variant="caption" weight="bold">
                📍 흰여울문화마을
              </Text>
            </View>
          </View>

          <Text variant="body" weight="medium" style={styles.caption}>
            바다 바로 옆 골목을 천천히 걷기 좋았어요. 노을 한 시간 전부터 풍경이 정말 예뻐요.
          </Text>
          <Text variant="caption" weight="medium" color={color.action.secondary}>
            #부산골목 #노을명소 #도보여행
          </Text>

          <View style={styles.divider} />

          <View style={styles.cardFooter}>
            <Pressable style={styles.likeRow} onPress={() => setLiked((prev) => !prev)}>
              <Text variant="caption" weight="medium" color={liked ? color.state.danger : color.text.muted}>
                {liked ? '♥' : '♡'} {128 + (liked ? 1 : 0)}
              </Text>
            </Pressable>
            <Text variant="caption" weight="medium" color={color.text.muted}>
              도움돼요 34 · 실제로 가봤어요 21
            </Text>
          </View>

          {/* TODO: 일정 데이터 모델 미정 — 지금은 눌러도 담기지 않는다. */}
          <Pressable style={styles.addToTripRow}>
            <Text variant="caption" weight="bold" color={color.action.secondary}>
              내 일정에 담기 ›
            </Text>
          </Pressable>
        </View>
      )}

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
  headerTitle: {
    marginTop: spacing[1],
  },
  writeButton: {
    backgroundColor: color.action.secondary,
    borderRadius: radius.full,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  tabsRow: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[6],
  },
  tab: {
    borderRadius: radius.full,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[2],
  },
  tabActive: {
    backgroundColor: color.action.secondary,
  },
  tabInactive: {
    backgroundColor: color.surface.soft,
  },
  emptyNote: {
    marginTop: spacing[6],
    textAlign: 'center',
  },
  card: {
    marginTop: spacing[4],
    backgroundColor: color.surface.card,
    borderRadius: radius.lg,
    padding: spacing[3],
    gap: spacing[3],
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
  },
  avatar: {
    width: 42,
    height: 42,
    borderRadius: radius.full,
    backgroundColor: color.state.warningBg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardHeaderCopy: {
    flex: 1,
    gap: spacing[1],
  },
  photo: {
    height: 210,
    borderRadius: radius.md,
    backgroundColor: color.surface.soft,
    justifyContent: 'flex-end',
    padding: spacing[3],
  },
  placeTag: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    backgroundColor: color.surface.card,
    borderRadius: radius.full,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
  },
  caption: {
    color: color.text.heading,
  },
  divider: {
    height: 1,
    backgroundColor: color.surface.field,
  },
  cardFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  likeRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  addToTripRow: {
    alignSelf: 'flex-end',
  },
  tabBar: {
    marginTop: spacing[6],
    marginHorizontal: -gutter,
  },
});
