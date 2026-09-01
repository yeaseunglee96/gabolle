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

type Pace = '여유' | '보통';

type ItineraryItem = {
  time: string;
  title: string;
  subtitle: string;
  pace: Pace;
};

const DAY1: ItineraryItem[] = [
  { time: '10:00', title: '송도 해상 케이블카', subtitle: '바다 위를 가르는 부산 대표 뷰', pace: '여유' },
  { time: '12:30', title: '남포동 로컬 맛집', subtitle: '현지인 추천 · 대기 15분', pace: '보통' },
  { time: '15:00', title: '흰여울문화마을', subtitle: '골목 산책 · 포토 스팟', pace: '여유' },
  { time: '18:30', title: '광안리 야경', subtitle: '해변 산책 · 노을 명소', pace: '보통' },
];

const STATS = [
  { label: '총 소요', value: '18시간 30분', tinted: true },
  { label: '1인 예산', value: '128,000원', tinted: false },
  { label: '이동', value: '42.6 km', tinted: false },
];

function PaceTag({ pace }: { pace: Pace }) {
  const isEasy = pace === '여유';
  return (
    <View style={[styles.tag, { backgroundColor: isEasy ? color.state.successBg : color.state.warningBg }]}>
      <Text variant="caption" weight="bold" color={isEasy ? color.state.success : color.state.warning}>
        {pace}
      </Text>
    </View>
  );
}

export default function Result() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const tripId = id ?? 'demo-trip';
  const [day, setDay] = useState<'DAY 1' | 'DAY 2'>('DAY 1');
  // 태블릿 detail 패널에 보여줄 선택 항목. 기본값을 첫 항목으로 둔 이유는 폴드8 을 펼쳤을 때
  // detail 패널이 빈 채로 시작하지 않게 하기 위해서다(Figma 에 이 상태의 디자인은 없다).
  const [selectedTime, setSelectedTime] = useState(DAY1[0].time);
  const selectedItem = DAY1.find((item) => item.time === selectedTime) ?? DAY1[0];

  return (
    <Screen scroll>
      <View style={styles.headerRow}>
        <View>
          <Text variant="caption">나의 부산 여행</Text>
          <Text variant="display" weight="bold" style={styles.title}>
            부산 1박 2일 여행
          </Text>
        </View>
        <Pressable style={styles.editButton}>
          <Text variant="caption" weight="bold" color={color.action.brand}>
            일정 수정
          </Text>
        </Pressable>
      </View>

      <View style={styles.statsRow}>
        {STATS.map((stat) => (
          <Card key={stat.label} tinted={stat.tinted} style={styles.statCard}>
            <Text variant="title" weight="bold">
              {stat.value}
            </Text>
            <Text variant="caption">{stat.label}</Text>
          </Card>
        ))}
      </View>

      <View style={styles.summaryHeaderRow}>
        <Text variant="title" weight="bold">
          일정 요약
        </Text>
        <View style={styles.dayToggle}>
          {(['DAY 1', 'DAY 2'] as const).map((option) => {
            const selected = option === day;
            return (
              <Pressable key={option} onPress={() => setDay(option)} style={styles.dayOption}>
                <Text variant="caption" weight="bold" color={selected ? color.action.brand : color.text.muted}>
                  {option}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      {day === 'DAY 1' ? (
        <Split
          master={
            <View style={styles.timeline}>
              {DAY1.map((item) => (
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
                      {item.title}
                    </Text>
                    <Text variant="caption" style={styles.timelineSubtitle}>
                      {item.subtitle}
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
                선택한 장소
              </Text>
              <Text variant="title" weight="bold" style={styles.placeSummaryTitle}>
                {selectedItem.title}
              </Text>
              <Text variant="caption" style={styles.timelineSubtitle}>
                {selectedItem.time} · {selectedItem.subtitle}
              </Text>
              <PaceTag pace={selectedItem.pace} />
            </Card>
          }
        />
      ) : (
        <Text variant="caption" style={styles.day2Placeholder}>
          DAY 2 일정은 아직 준비 중이에요.
        </Text>
      )}

      <Button
        label="여행 지도에서 보기"
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
  day2Placeholder: {
    marginTop: spacing[4],
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
