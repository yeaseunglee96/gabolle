import { Pressable, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { TabBar } from '@/components/TabBar';
import { Text } from '@/components/Text';
import { color, gutter, radius, spacing } from '@/design/tokens';

export default function Feed() {
  const router = useRouter();

  return <View style={styles.shell}><Screen scroll>
    <View style={styles.headerRow}><View><Text variant="eyebrow" weight="bold">TRAVEL STORIES</Text><Text variant="display" weight="bold" style={styles.headerTitle}>여행 이야기</Text></View><View accessibilityRole="text" style={styles.pendingBadge}><Text variant="caption" weight="bold" color={color.brand.orange}>준비 중</Text></View></View>
    <View style={styles.stateCard}><View style={styles.icon}><Text variant="display" color={color.brand.orange}>✦</Text></View><Text variant="title" weight="bold">부산 여행 기록을 모으고 있어요</Text><Text color={color.text.body} style={styles.description}>게시물 API가 연결되기 전에는 다른 여행자의 이름이나 사진, 좋아요 수를 임의로 보여주지 않아요. 먼저 내 일정을 만들고 여행을 준비해 보세요.</Text><View style={styles.actions}><Button label="내 여행 보기" onPress={() => router.push('/trips')} containerStyle={styles.primaryAction} /><Button label="새 여행 만들기" variant="ghost" onPress={() => router.push('/plan/basic')} /></View></View>
    <View style={styles.promiseCard}><Text variant="body" weight="bold">연결되면 제공할 기능</Text><View style={styles.promiseRow}><Text variant="caption" color={color.brand.orange}>01</Text><Text variant="caption" color={color.text.body} style={styles.grow}>방문한 장소와 여행 기록 확인</Text></View><View style={styles.promiseRow}><Text variant="caption" color={color.brand.orange}>02</Text><Text variant="caption" color={color.text.body} style={styles.grow}>공개 범위를 확인한 뒤 게시</Text></View><View style={styles.promiseRow}><Text variant="caption" color={color.brand.orange}>03</Text><Text variant="caption" color={color.text.body} style={styles.grow}>좋아요·팔로우는 실제 서버 결과로만 표시</Text></View></View>
    <Pressable accessibilityRole="button" onPress={() => router.replace('/home')} style={({ pressed }) => [styles.homeLink, pressed && styles.pressed]}><Text variant="caption" weight="bold" color={color.brand.orange}>홈으로 돌아가기</Text></Pressable>
    <View style={styles.tabBar}><TabBar active="home" /></View>
  </Screen></View>;
}

const styles = StyleSheet.create({
  shell: { flex: 1, backgroundColor: color.brand.ivory }, headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }, headerTitle: { marginTop: spacing[1] }, pendingBadge: { borderRadius: radius.full, paddingHorizontal: spacing[4], paddingVertical: spacing[2], backgroundColor: color.surface.tint },
  stateCard: { minHeight: 330, marginTop: spacing[6], padding: spacing[6], borderWidth: 1, borderColor: '#ece6dc', borderRadius: radius.lg, backgroundColor: color.surface.card, alignItems: 'center', justifyContent: 'center', gap: spacing[3] }, icon: { width: 68, height: 68, borderRadius: radius.full, backgroundColor: color.surface.tint, alignItems: 'center', justifyContent: 'center' }, description: { maxWidth: 360, textAlign: 'center' }, actions: { width: '100%', maxWidth: 320, gap: spacing[2], marginTop: spacing[2] },
  primaryAction: { backgroundColor: color.brand.navy },
  promiseCard: { marginTop: spacing[4], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.soft, gap: spacing[3] }, promiseRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] }, grow: { flex: 1 }, homeLink: { minHeight: 44, marginTop: spacing[3], alignItems: 'center', justifyContent: 'center' }, pressed: { opacity: 0.72 }, tabBar: { marginTop: spacing[6], marginHorizontal: -gutter },
});
