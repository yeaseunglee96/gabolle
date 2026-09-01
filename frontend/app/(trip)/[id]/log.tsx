// 19 여행 기록·회고 — Figma 19_여행 기록·회고 실측 그대로.
//
// 통계·타임라인은 전부 하드코딩 목업이다(실제 여행 데이터 모델 미착수).
// 히어로 사진 자산이 없어 13 장소 상세·02 메인 홈과 같은 방식으로 브랜드 색 면 위에
// 제목·날짜를 흰 글자로 얹는다.
import { Share, StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Button } from '@/components/Button';

const STATS = [
  { label: '방문 장소', value: '8' },
  { label: '이동 거리', value: '42.6km' },
  { label: '사진', value: '23' },
];

const TIMELINE = [
  { time: '10:20', name: '송도 케이블카', desc: '바다 위 첫 풍경' },
  { time: '15:14', name: '흰여울 골목', desc: '가장 많이 찍은 곳' },
  { time: '19:08', name: '광안리', desc: '오늘의 베스트' },
];

export default function Log() {
  function share() {
    // 네트워크 호출 없이 OS 공유 시트만 띄운다(react-native 내장 Share, 새 의존성 아님).
    Share.share({ message: '부산에서 보낸 2일 — 바다와 골목을 따라, 부산' });
  }

  return (
    <Screen scroll>
      <Text variant="eyebrow" weight="bold">
        여행 후 · 자동 회고
      </Text>
      <Text variant="display" weight="bold" style={styles.title}>
        부산에서 보낸 2일
      </Text>

      <View style={styles.hero}>
        <Text variant="title" weight="bold" color={color.text.onAction}>
          바다와 골목을 따라, 부산
        </Text>
        <Text variant="caption" weight="medium" color={color.text.onAction} style={styles.heroDate}>
          2026.08.24 – 08.25
        </Text>
      </View>

      <View style={styles.statsRow}>
        {STATS.map((stat) => (
          <View key={stat.label} style={styles.statCard}>
            <Text variant="title" weight="bold" color={color.text.accent}>
              {stat.value}
            </Text>
            <Text variant="caption" style={styles.statLabel}>
              {stat.label}
            </Text>
          </View>
        ))}
      </View>

      <Text variant="title" weight="bold" style={styles.sectionTitle}>
        기억에 남은 순간
      </Text>
      <View style={styles.timeline}>
        {TIMELINE.map((item) => (
          <View key={item.time} style={styles.timelineRow}>
            <View style={styles.timelineDot} />
            <Text variant="caption" weight="bold" color={color.text.accent} style={styles.timelineTime}>
              {item.time}
            </Text>
            <View style={styles.timelineBody}>
              <Text variant="body" weight="bold">
                {item.name}
              </Text>
              <Text variant="caption" style={styles.timelineDesc}>
                {item.desc}
              </Text>
            </View>
          </View>
        ))}
      </View>

      <View style={styles.shareCard}>
        <Text variant="caption" weight="bold">
          공개 범위
        </Text>
        <Text variant="caption" weight="medium" color={color.text.accent} style={styles.shareValue}>
          친구만 · 위치는 지역 단위로 표시
        </Text>
      </View>

      <Button label="여행 카드 공유하기" variant="field" containerStyle={styles.cta} onPress={share} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: {
    marginTop: spacing[1],
    marginBottom: spacing[4],
  },
  hero: {
    height: 190,
    borderRadius: radius.lg,
    backgroundColor: color.action.field,
    justifyContent: 'flex-end',
    padding: spacing[4],
    gap: spacing[1],
  },
  heroDate: {
    opacity: 0.92,
  },
  statsRow: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
  },
  statCard: {
    flex: 1,
    backgroundColor: color.surface.card,
    borderRadius: radius.md,
    padding: spacing[3],
    gap: spacing[1],
  },
  statLabel: {
    color: color.text.body,
  },
  sectionTitle: {
    marginTop: spacing[6],
    marginBottom: spacing[3],
  },
  timeline: {
    gap: spacing[4],
  },
  timelineRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
  },
  timelineDot: {
    width: 12,
    height: 12,
    borderRadius: radius.full,
    backgroundColor: color.action.field,
  },
  timelineTime: {
    width: 44,
  },
  timelineBody: {
    flex: 1,
    gap: spacing[1],
  },
  timelineDesc: {
    color: color.text.body,
  },
  shareCard: {
    marginTop: spacing[6],
    backgroundColor: color.surface.soft,
    borderRadius: radius.md,
    padding: spacing[4],
    gap: spacing[1],
  },
  shareValue: {
    marginTop: spacing[1],
  },
  cta: {
    marginTop: spacing[4],
  },
});
