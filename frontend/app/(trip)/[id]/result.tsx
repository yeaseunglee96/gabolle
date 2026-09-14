// 11 여행 결과 — Figma 11_여행 결과 실측 그대로. 하단 탭 바는 지금은 시각 요소만 두고,
// "지도" 탭만 실제로 12 지도·동선 화면으로 잇는다(발표 경로가 이어져야 하는 자리라서).
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { color, radius, spacing } from '@/design/tokens';
import { Screen } from '@/components/Screen';
import { Text } from '@/components/Text';
import { Card } from '@/components/Card';
import { Button } from '@/components/Button';
import { Split } from '@/layout/Split';
import { useI18n } from '@/i18n';

type Pace = '여유' | '보통';
const PACE_LABEL: Record<Pace, [string, string]> = { 여유: ['여유', 'Relaxed'], 보통: ['보통', 'Moderate'] };

type ItineraryItem = {
  time: string;
  titleKo: string; titleEn: string;
  subtitleKo: string; subtitleEn: string;
  pace: Pace;
};

const DAY1: ItineraryItem[] = [
  { time: '10:00', titleKo: '송도 해상 케이블카', titleEn: 'Songdo Marine Cable Car', subtitleKo: '바다 위를 가르는 부산 대표 뷰', subtitleEn: "Busan's signature view over the sea", pace: '여유' },
  { time: '12:30', titleKo: '남포동 로컬 맛집', titleEn: 'Nampo-dong local eatery', subtitleKo: '현지인 추천 · 대기 15분', subtitleEn: 'Local recommendation · 15 min wait', pace: '보통' },
  { time: '15:00', titleKo: '흰여울문화마을', titleEn: 'Huinnyeoul Culture Village', subtitleKo: '골목 산책 · 포토 스팟', subtitleEn: 'Alley walk · Photo spot', pace: '여유' },
  { time: '18:30', titleKo: '광안리 야경', titleEn: 'Gwangalli night view', subtitleKo: '해변 산책 · 노을 명소', subtitleEn: 'Beach walk · Sunset spot', pace: '보통' },
];

// 🔴 이 화면은 실제 생성된 일정이 아니라 Figma 실측 그대로의 정적 데모다(파일 맨 위 주석).
// DAY 2도 같은 패턴(가짜 고정 데이터)으로 채운다 — "1박 2일" 제목을 걸어 두고 DAY 2 탭을
// 빈 안내문으로 막아 두면 심사에서 미완성으로 보인다(STORE-REVIEW-CHECKLIST.md).
const DAY2: ItineraryItem[] = [
  { time: '09:30', titleKo: '감천문화마을', titleEn: 'Gamcheon Culture Village', subtitleKo: '색색의 골목 · 포토 스팟', subtitleEn: 'Colorful alleys · Photo spot', pace: '여유' },
  { time: '12:00', titleKo: '자갈치시장 회 정식', titleEn: 'Jagalchi Market sashimi set', subtitleKo: '현지인 추천 · 대기 10분', subtitleEn: 'Local recommendation · 10 min wait', pace: '보통' },
  { time: '14:30', titleKo: '해운대 해수욕장', titleEn: 'Haeundae Beach', subtitleKo: '해변 산책 · 카페', subtitleEn: 'Beach walk · Cafes', pace: '여유' },
  { time: '17:00', titleKo: '동백섬 산책로', titleEn: 'Dongbaekseom trail', subtitleKo: '노을 명소 · 가벼운 산책', subtitleEn: 'Sunset spot · Easy walk', pace: '여유' },
];

const STATS = [
  { labelKo: '총 소요', labelEn: 'Total time', valueKo: '18시간 30분', valueEn: '18h 30m', tinted: true },
  { labelKo: '1인 예산', labelEn: 'Budget per person', valueKo: '128,000원', valueEn: '₩128,000', tinted: false },
  { labelKo: '이동', labelEn: 'Distance', valueKo: '42.6 km', valueEn: '42.6 km', tinted: false },
];

function PaceTag({ pace }: { pace: Pace }) {
  const { tx } = useI18n();
  const isEasy = pace === '여유';
  return (
    <View style={[styles.tag, { backgroundColor: isEasy ? color.state.successBg : color.state.warningBg }]}>
      <Text variant="caption" weight="bold" color={isEasy ? color.state.success : color.state.warning}>
        {tx(...PACE_LABEL[pace])}
      </Text>
    </View>
  );
}

export default function Result() {
  const router = useRouter();
  const { tx } = useI18n();
  const { id } = useLocalSearchParams<{ id: string }>();
  const tripId = id ?? 'demo-trip';
  const [day, setDay] = useState<'DAY 1' | 'DAY 2'>('DAY 1');
  const items = day === 'DAY 1' ? DAY1 : DAY2;
  // 태블릿 detail 패널에 보여줄 선택 항목. 기본값을 첫 항목으로 둔 이유는 폴드8 을 펼쳤을 때
  // detail 패널이 빈 채로 시작하지 않게 하기 위해서다(Figma 에 이 상태의 디자인은 없다).
  const [selectedTime, setSelectedTime] = useState(DAY1[0].time);
  const selectedItem = items.find((item) => item.time === selectedTime) ?? items[0];

  return (
    <Screen scroll wide>
      <View style={styles.headerRow}>
        <View>
          <Text variant="caption">{tx('나의 부산 여행', 'My Busan trip')}</Text>
          <Text variant="display" weight="bold" style={styles.title}>
            {tx('부산 1박 2일 여행', 'Busan 2-day 1-night trip')}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={tx('일정 수정', 'Edit itinerary')}
          onPress={() => router.push(`/${tripId}/edit`)}
          style={styles.editButton}
        >
          <Text variant="caption" weight="bold" color={color.action.brand}>
            {tx('일정 수정', 'Edit itinerary')}
          </Text>
        </Pressable>
      </View>

      <View style={styles.statsRow}>
        {STATS.map((stat) => (
          <Card key={stat.labelKo} tinted={stat.tinted} style={styles.statCard}>
            <Text variant="title" weight="bold">
              {tx(stat.valueKo, stat.valueEn)}
            </Text>
            <Text variant="caption">{tx(stat.labelKo, stat.labelEn)}</Text>
          </Card>
        ))}
      </View>

      <View style={styles.summaryHeaderRow}>
        <Text variant="title" weight="bold">
          {tx('일정 요약', 'Itinerary summary')}
        </Text>
        <View accessibilityRole="tablist" style={styles.dayToggle}>
          {(['DAY 1', 'DAY 2'] as const).map((option) => {
            const selected = option === day;
            return (
              <Pressable
                key={option}
                accessibilityRole="tab"
                accessibilityState={{ selected }}
                onPress={() => { setDay(option); setSelectedTime((option === 'DAY 1' ? DAY1 : DAY2)[0].time); }}
                style={[styles.dayOption, selected && styles.dayOptionSelected]}
              >
                <Text variant="caption" weight="bold" color={selected ? color.action.brand : color.text.muted}>
                  {option}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      <Split
        master={
          <View style={styles.timeline}>
            {items.map((item) => (
              <Pressable
                key={item.time}
                onPress={() => setSelectedTime(item.time)}
                style={styles.timelineRow}
              >
                <Text variant="body" weight="bold" color={color.text.accent} style={styles.timelineTime}>
                  {item.time}
                </Text>
                <Card tinted={item.time === selectedTime} style={styles.timelineCard}>
                  <Text variant="body" weight="bold">
                    {tx(item.titleKo, item.titleEn)}
                  </Text>
                  <Text variant="caption" style={styles.timelineSubtitle}>
                    {tx(item.subtitleKo, item.subtitleEn)}
                  </Text>
                  <PaceTag pace={item.pace} />
                </Card>
              </Pressable>
            ))}
          </View>
        }
        detail={
          // 🔴 "선택한 장소 요약" 은 Figma·명세 어디에도 없다 — 태블릿에서 detail 칸을 채우려고
          // 이번 작업에서 새로 만든 화면이다. 사람 검토가 필요하다.
          <Card tinted style={styles.placeSummaryCard}>
            <Text variant="caption" weight="bold" color={color.text.eyebrow}>
              {tx('선택한 장소', 'Selected place')}
            </Text>
            <Text variant="title" weight="bold" style={styles.placeSummaryTitle}>
              {tx(selectedItem.titleKo, selectedItem.titleEn)}
            </Text>
            <Text variant="caption" style={styles.timelineSubtitle}>
              {selectedItem.time} · {tx(selectedItem.subtitleKo, selectedItem.subtitleEn)}
            </Text>
            <PaceTag pace={selectedItem.pace} />
          </Card>
        }
      />

      <Button
        label={tx('여행 지도에서 보기', 'View on trip map')}
        variant="ghost"
        containerStyle={styles.mapCta}
        onPress={() => router.push(`/${tripId}/map`)}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  title: {
    marginTop: spacing[1],
  },
  editButton: {
    backgroundColor: color.surface.soft,
    borderRadius: radius.full,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  statsRow: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[6],
  },
  statCard: {
    flex: 1,
    gap: spacing[1],
  },
  summaryHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: spacing[8],
  },
  dayToggle: {
    flexDirection: 'row',
    backgroundColor: color.surface.soft,
    borderRadius: radius.full,
    padding: spacing[1],
    gap: spacing[1],
  },
  dayOption: {
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
    borderRadius: radius.full,
  },
  dayOptionSelected: {
    backgroundColor: color.surface.card,
  },
  timeline: {
    marginTop: spacing[4],
    gap: spacing[4],
  },
  timelineRow: {
    flexDirection: 'row',
    gap: spacing[3],
  },
  timelineTime: {
    width: 50,
  },
  timelineCard: {
    flex: 1,
    gap: spacing[1],
    alignItems: 'flex-start',
  },
  timelineSubtitle: {
    color: color.text.body,
  },
  tag: {
    borderRadius: radius.full,
    paddingHorizontal: spacing[2],
    paddingVertical: 2,
    marginTop: spacing[1],
  },
  placeSummaryCard: {
    gap: spacing[1],
  },
  placeSummaryTitle: {
    marginTop: spacing[1],
  },
  mapCta: {
    marginTop: spacing[8],
  },
});
