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
        <View accessibilityRole="text" style={styles.pendingBadge}>
          <Text variant="caption" weight="bold" color={color.text.muted}>
            기록 기능 준비 중
          </Text>
        </View>
      </View>

      <View style={styles.previewNotice}><Text variant="caption" color={color.text.body}>피드 API 연결 전 구성 확인을 위한 예시 화면이에요. 팔로우·공감 수는 실제 데이터가 아닙니다.</Text></View>

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
            <Text variant="caption" weight="bold" color={color.text.muted}>연동 예정</Text>
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
            <Text variant="caption" weight="medium" color={color.text.muted}>♡ 128</Text>
            <Text variant="caption" weight="medium" color={color.text.muted}>
              도움돼요 34 · 실제로 가봤어요 21
            </Text>
          </View>

          <Text variant="caption" weight="bold" color={color.text.muted} style={styles.addToTripRow}>일정 연동 준비 중</Text>
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
  pendingBadge: {
    backgroundColor: color.surface.soft,
    borderRadius: radius.full,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  previewNotice: { marginTop: spacing[4], padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },
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
  addToTripRow: {
    alignSelf: 'flex-end',
  },
  tabBar: {
    marginTop: spacing[6],
    marginHorizontal: -gutter,
  },
});
