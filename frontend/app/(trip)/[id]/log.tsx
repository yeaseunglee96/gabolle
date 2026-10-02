// 19 여행 기록·회고 — Figma 19_여행 기록·회고 실측 그대로.
import { StyleSheet, View } from 'react-native';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Eyebrow } from '@/components/Eyebrow';
import { Button } from '@/components/Button';
import { SampleNotice } from '@/components/SampleNotice';
import { useI18n } from '@/i18n';
import { shareLink } from '@/utils/shareLink';

const STATS = [
  { key: 'places', labelKo: '방문 장소', labelEn: 'Places visited', value: '8' },
  { key: 'distance', labelKo: '이동 거리', labelEn: 'Distance traveled', value: '42.6km' },
  { key: 'photos', labelKo: '사진', labelEn: 'Photos', value: '23' },
];

const TIMELINE = [
  { time: '10:20', nameKo: '송도 케이블카', nameEn: 'Songdo Cable Car', descKo: '바다 위 첫 풍경', descEn: 'The first view over the sea' },
  { time: '15:14', nameKo: '흰여울 골목', nameEn: 'Huinnyeoul Alley', descKo: '가장 많이 찍은 곳', descEn: 'Where you took the most photos' },
  { time: '19:08', nameKo: '광안리', nameEn: 'Gwangalli', descKo: '오늘의 베스트', descEn: "Today's best moment" },
];

export default function Log() {
  const { tx } = useI18n();

  function share() {
    // 네트워크 호출 없이 OS 공유 시트만 띄운다(react-native 내장 Share, 새 의존성 아님).
    void shareLink({ message: tx('부산에서 보낸 2일 — 바다와 골목을 따라, 부산', '2 days in Busan — along the sea and the alleys') }).catch(() => undefined);
  }

  return (
    <Screen scroll>
      <Eyebrow>
        {tx('여행 후 · 자동 회고', 'After the trip · Auto recap')}
      </Eyebrow>
      {/* — 이 화면의 숫자와 시간표는 전부 지어낸 예시다. 표시가 없으면
          사용자는 자기 여행이 이렇게 기록된 줄 안다.
      */}
      <SampleNotice
        badge={tx('샘플', 'Sample')}
        description={tx('아래 숫자와 시간표는 화면을 보여주기 위한 예시예요. 실제 여행 기록이 아니에요.', 'The numbers and timeline below are placeholders — not your actual trip.')}
      />
      <Text variant="display" weight="bold" style={styles.title}>
        {tx('부산에서 보낸 2일', '2 days in Busan')}
      </Text>

      <View style={styles.hero}>
        <Text variant="title" weight="bold" color={color.text.onAction}>
          {tx('바다와 골목을 따라, 부산', 'Along the sea and the alleys, Busan')}
        </Text>
        <Text variant="caption" weight="medium" color={color.text.onAction} style={styles.heroDate}>
          2026.08.24 – 08.25
        </Text>
      </View>

      <View style={styles.statsRow}>
        {STATS.map((stat) => (
          <View key={stat.key} style={styles.statCard}>
            <Text variant="title" weight="bold" color={color.text.accent}>
              {stat.value}
            </Text>
            <Text variant="caption" style={styles.statLabel}>
              {tx(stat.labelKo, stat.labelEn)}
            </Text>
          </View>
        ))}
      </View>

      <Text variant="title" weight="bold" style={styles.sectionTitle}>
        {tx('기억에 남은 순간', 'Moments to remember')}
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
                {tx(item.nameKo, item.nameEn)}
              </Text>
              <Text variant="caption" style={styles.timelineDesc}>
                {tx(item.descKo, item.descEn)}
              </Text>
            </View>
          </View>
        ))}
      </View>

      <View style={styles.shareCard}>
        <Text variant="caption" weight="bold">
          {tx('공개 범위', 'Visibility')}
        </Text>
        <Text variant="caption" weight="medium" color={color.text.accent} style={styles.shareValue}>
          {tx('친구만 · 위치는 지역 단위로 표시', 'Friends only · Location shown by area')}
        </Text>
      </View>

      <Button label={tx('여행 카드 공유하기', 'Share trip card')} variant="field" containerStyle={styles.cta} onPress={share} />
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
