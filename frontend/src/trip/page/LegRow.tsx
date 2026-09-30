// 장소 사이의 이동 한 칸 — UI 캔버스 ㉓-2b(여정 한 줄), S15P21E201-1884.
//
// 전에는 카드 사이 32px 줄에 「이동 25분 · 길 보기 ›」 작은 글자뿐이라, 처음 쓰는 사람은 그것이 누르는 곳인 줄 몰랐다
// (여행 중 가장 자주 눌러야 할 곳이다). 이제 두 모양이다.
//
//   접힌 칸  「동백섬횟집까지 가는 길」 + 걸리는 시간 · 요금, 오른쪽에 짙은 「길 안내 ›」. 칸 전체가 누르는 곳이다
//   펼친 칸  지금 향하는 곳으로 가는 구간 하나만(여행 중 「다음 갈 곳」). 경로를 한 번 물어 걸음마다 한 문장으로
//           — 정류장까지 걷고, 몇 번을 타고, 어디서 내린다. 외국어 화면은 표지판의 한국어를 옆에 붙인다
//
// 🔴 펼친 칸은 경로를 «그 한 구간만» 묻는다. 모든 구간을 물으면 일정 하나에 길찾기가 열 번 넘게 나간다.
// 🔴 받지 못하면(오프라인·서버 없음) 걸음 문장만 빼고 접힌 칸과 같은 글로 남는다 — 지어낸 걸음을 그리지 않는다.
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Button } from '@/components/Button';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { txf } from '@/i18n/format';
import type { LanguageCode } from '@/i18n/languages';
import { getRouteDirections } from '@/map/routeDirections';
import type { TransitStepKind } from '@/field/routeLegs';

import { journeyOf, type Journey } from './journeySteps';
import type { LegRouteParams } from './legRoute';

type Tx = (ko: string, en: string) => string;

export function LegRow({ destName, label, transit, route, now, onOpen, accessToken, language, tx }: {
  /** 이 구간이 가는 곳 — 화면에 적는 이름 */
  destName: string;
  /** 「이동 25분 · 1,550원」 — formatTravelLabel. 모르면 null */
  label: string | null;
  /** 대중교통 요금이 붙은 구간인가 — 수단을 모르는 칸에 「대중교통」을 지어 붙이지 않으려고 요금으로만 가른다 */
  transit: boolean;
  /** 경로를 물을 좌표. 모르면 null — 누를 수 없는 글자로 남는다 */
  route: LegRouteParams | null;
  /** 지금 향하는 곳으로 가는 구간 — 펼친다 */
  now: boolean;
  onOpen: (() => void) | null;
  accessToken: string | null;
  language: LanguageCode;
  tx: Tx;
}) {
  const journey = useNowJourney(now ? route : null, destName, accessToken, language, tx);

  if (!onOpen) {
    return label ? <Text variant="micro" weight="bold" color={color.text.muted} style={styles.plain}>{label}</Text> : null;
  }

  if (now) {
    return (
      <View testID="leg-now" style={styles.nowCard}>
        <View style={styles.nowHead}>
          <Text variant="micro" weight="bold" color={color.state.danger}>{tx('다음 이동', 'Next move')}</Text>
          <Text variant="title" weight="bold">{txf(tx, '%s까지 가는 법', 'Getting to %s', destName)}</Text>
          {label ? <Text variant="caption" color={color.text.body}>{label}</Text> : null}
        </View>
        {journey ? (
          <>
            <JourneyBar parts={journey.bar} />
            <View style={styles.steps}>
              {journey.steps.map((step, index) => (
                <View key={`${step.chip}-${index}`} style={styles.step}>
                  <View style={[styles.chip, chipStyle(step.kind)]}>
                    <Text variant="micro" weight="bold" color={step.kind === 'walk' ? color.text.heading : color.text.onAction} numberOfLines={1}>{step.chip}</Text>
                  </View>
                  <View style={styles.stepCopy}>
                    <Text variant="caption" color={color.text.heading}>{`${index + 1}. ${step.text}`}</Text>
                    {/* 표지판 딱지 — 외국어 화면에서만. 길에서 보는 글자와 그대로 맞춰 본다 */}
                    {step.sign ? (
                      <View style={styles.sign} accessibilityLabel={txf(tx, '표지판: %s', 'Sign: %s', step.sign)}>
                        <Text variant="micro" weight="bold" color={color.text.onDarkMuted}>{tx('표지판', 'SIGN')}</Text>
                        <Text variant="caption" weight="bold" color={color.text.onAction}>{step.sign}</Text>
                      </View>
                    ) : null}
                  </View>
                </View>
              ))}
            </View>
          </>
        ) : null}
        <Button
          variant="secondary"
          label={tx('길 안내 시작', 'Start directions')}
          accessibilityLabel={txf(tx, '%s까지 길 안내 시작', 'Start directions to %s', destName)}
          onPress={onOpen}
        />
      </View>
    );
  }

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={txf(tx, '%s까지 가는 길 보기', 'Directions to %s', destName)}
      onPress={onOpen}
      style={({ pressed }) => [styles.row, pressed && styles.pressed]}
    >
      {/* 제목은 가는 곳 이름만 — 「가는 길」은 오른쪽 단추가 말한다. 폰 폭에서 한 줄로 자르면 이름이 「…」로 잘렸다. */}
      <View style={styles.rowCopy}>
        <Text variant="caption" weight="bold" numberOfLines={2}>{txf(tx, '%s까지', 'To %s', destName)}</Text>
        {label ? <Text variant="micro" color={color.text.muted} numberOfLines={1}>{transit ? `${tx('대중교통', 'Transit')} · ${label}` : label}</Text> : null}
      </View>
      <View style={styles.go}><Text variant="micro" weight="bold" color={color.text.onAction}>{tx('길 안내 ›', 'Directions ›')}</Text></View>
    </Pressable>
  );
}

/** 지금 가는 구간 하나만 경로를 묻는다. 좌표가 바뀌면 다시. */
function useNowJourney(route: LegRouteParams | null, destName: string, accessToken: string | null, language: LanguageCode, tx: Tx): Journey | null {
  const [directions, setDirections] = useState<Parameters<typeof journeyOf>[0] | null>(null);
  const key = route ? `${route.originLat},${route.originLng}>${route.destLat},${route.destLng}` : null;
  useEffect(() => {
    setDirections(null);
    if (!route) return;
    let alive = true;
    void getRouteDirections({ originLat: Number(route.originLat), originLng: Number(route.originLng), destLat: Number(route.destLat), destLng: Number(route.destLng), mode: 'TRANSIT' }, accessToken)
      .then((result) => { if (alive && result.state === 'success') setDirections(result.directions); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, accessToken]);
  return directions ? journeyOf(directions, destName, language, tx) : null;
}

/** 시간 비율 막대 — 걷기·버스·지하철이 막대 길이로. 글을 안 읽어도 얼마나 걷는지 보인다. */
function JourneyBar({ parts }: { parts: Journey['bar'] }) {
  return (
    <View style={styles.bar} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {parts.map((part, index) => <View key={index} style={[styles.barPart, { flex: part.minutes }, barStyle(part.kind)]} />)}
    </View>
  );
}

function chipStyle(kind: TransitStepKind) {
  return kind === 'walk' ? styles.chipWalk : kind === 'subway' ? styles.chipSubway : styles.chipBus;
}
function barStyle(kind: TransitStepKind) {
  return kind === 'walk' ? styles.barWalk : kind === 'subway' ? styles.chipSubway : styles.chipBus;
}

const styles = StyleSheet.create({
  plain: { alignSelf: 'center' },
  pressed: { opacity: 0.72 },
  // 접힌 칸 — 테두리 있는 누르는 칸. 장소 카드(흰 채움·테두리 없음)와 모양부터 다르다.
  row: { flex: 1, minWidth: 0, minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingVertical: spacing[1] + 2, paddingLeft: spacing[3], paddingRight: spacing[1] + 2, borderRadius: radius.md, borderWidth: 1.5, borderColor: color.surface.field, backgroundColor: color.surface.card },
  rowCopy: { flex: 1, minWidth: 0, gap: 1 },
  go: { minHeight: 34, paddingHorizontal: spacing[3], borderRadius: radius.sm, backgroundColor: color.action.secondary, alignItems: 'center', justifyContent: 'center' },
  // 칩 색은 경로 화면(route-detail)과 같다 — 버스 파랑 · 지하철 노랑 · 걷기 연회색. 폭을 맞춰 걸음 글이 한 줄로 선다.
  chip: { minWidth: 52, minHeight: 24, paddingHorizontal: spacing[2], borderRadius: radius.sm, alignItems: 'center', justifyContent: 'center' },
  chipBus: { backgroundColor: color.state.info },
  chipSubway: { backgroundColor: color.state.warning },
  chipWalk: { backgroundColor: color.surface.tint },
  // 펼친 칸
  nowCard: { flex: 1, minWidth: 0, gap: spacing[3], padding: spacing[3] + 2, borderRadius: radius.lg, borderWidth: 2, borderColor: color.text.heading, backgroundColor: color.surface.card },
  nowHead: { gap: 2 },
  bar: { flexDirection: 'row', gap: 3, height: 8 },
  barPart: { borderRadius: radius.full },
  barWalk: { backgroundColor: color.surface.field },
  steps: { gap: spacing[2] + 2 },
  step: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing[2] },
  stepCopy: { flex: 1, minWidth: 0, gap: spacing[1], paddingTop: 2 },
  sign: { alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: spacing[2], paddingHorizontal: spacing[2], paddingVertical: 2, borderRadius: radius.sm, backgroundColor: color.action.secondary },
});
