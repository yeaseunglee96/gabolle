// 탑승 중 카드 — 경로 상세의 대중교통 탭. S15P21E201-1837.
//
// 타고 가는 동안 「남은 정류장·다음 정류장·내릴 곳까지 거리」를 보여주고, 내릴 곳이 다가오면 진동·알림·배너를 한 번 준다.
// 위치는 부모(app/route-detail.tsx)가 useLiveLocation 으로 받아 넘긴다 — 지도도 같은 위치로 내 점을 그린다.
//
// 🔴 화면이 켜져 있는 동안만 돈다. 화면을 꺼도 오는 알림은 iOS 「위치 항상 허용」(백그라운드 위치)이 필요해 이번 범위
//    밖이다. 그래서 사용자에게 「화면을 켜 두세요」라고 먼저 말한다 — 조용히 안 울리면 믿고 있던 사람이 지나친다.
import { useEffect, useMemo, useRef, useState } from 'react';
import { Platform, StyleSheet, Vibration, View } from 'react-native';
import * as Notifications from 'expo-notifications';

import { Button } from '@/components/Button';
import { GabolleMascot } from '@/components/DongbaekMascot';
import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { rideLegs, rideProgress, type RideLeg } from '@/field/ride';
import { distanceText } from '@/field/subwayStations';
import { txf } from '@/i18n/format';
import type { RouteDirections } from '@/map/routeDirections';
import type { LiveLocation } from '@/trip/page/useLiveLocation';
import { usableFix } from '@/trip/page/autoArrival';

type Tx = (ko: string, en: string) => string;

/** 진동 — 짧게 세 번. 주머니 속에서도 알 만큼, 놀랄 만큼은 아니게. */
const ALERT_VIBRATION = [0, 400, 200, 400, 200, 400];

async function notify(title: string, body: string) {
  if (Platform.OS === 'web') return;
  try {
    const permission = await Notifications.getPermissionsAsync();
    if (!permission.granted) return;
    await Notifications.scheduleNotificationAsync({ content: { title, body }, trigger: null });
  } catch {
    // 알림을 못 띄워도 진동과 화면 배너는 남는다 — 여기서 멈추지 않는다.
  }
}

/** 탑승을 시작할 때 한 번 — 알림 권한이 아직 안 정해졌으면 묻는다. 거절해도 탑승은 이어진다(진동·배너). */
export async function askRideNotifications() {
  if (Platform.OS === 'web') return;
  try {
    const current = await Notifications.getPermissionsAsync();
    if (!current.granted && current.canAskAgain) await Notifications.requestPermissionsAsync();
  } catch {
    // 무시 — 위와 같다.
  }
}

export function RidePanel({ directions, riding, live, onStart, onStop, tx }: {
  directions: RouteDirections;
  riding: boolean;
  live: LiveLocation;
  onStart: () => void;
  onStop: () => void;
  tx: Tx;
}) {
  const legs = useMemo(() => rideLegs(directions), [directions]);
  const [legIndex, setLegIndex] = useState(0);
  const alerted = useRef(new Set<number>());
  const [banner, setBanner] = useState<string | null>(null);

  // 탑승을 새로 시작하면 처음 구간부터 다시 센다.
  useEffect(() => {
    if (!riding) return;
    setLegIndex(0); setBanner(null); alerted.current = new Set();
  }, [riding]);

  const leg = legs[Math.min(legIndex, legs.length - 1)];
  const fix = live.fix && usableFix(live.fix) ? live.fix : null;
  const progress = riding && leg && fix ? rideProgress(leg, fix) : null;
  const lastLeg = legIndex >= legs.length - 1;

  useEffect(() => {
    if (!progress || !leg) return;
    if (progress.arrived && !lastLeg) {
      // 갈아타는 곳에 닿았다 — 다음 구간으로.
      const next = legs[legIndex + 1];
      setLegIndex(legIndex + 1);
      setBanner(txf(tx, '%s로 갈아타세요', 'Transfer to %s', next.kind === 'bus' ? txf(tx, '%s번', '%s', next.line) : next.line));
      return;
    }
    if (progress.alertNow && !alerted.current.has(legIndex)) {
      alerted.current.add(legIndex);
      const place = leg.alightName ?? tx('내릴 곳', 'your stop');
      setBanner(tx('다음 정류장에서 내리세요!', 'Get off at the next stop!'));
      Vibration.vibrate(ALERT_VIBRATION);
      void notify(tx('곧 내릴 곳이에요', 'Your stop is next'), txf(tx, '다음 정류장 %s에서 내리세요.', 'Get off at the next stop, %s.', place));
    }
    // progress 는 매번 새 객체라 값으로 본다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [progress?.alertNow, progress?.arrived, legIndex, leg, lastLeg]);

  if (!legs.length) return null;

  if (!riding) {
    return (
      <View style={styles.card}>
        <Button label={tx('탑승 시작', 'Start ride')} onPress={onStart} containerStyle={styles.stretch} />
        <Text variant="caption" color={color.text.muted}>
          {tx('타고 가는 동안 남은 정류장을 보여주고, 내릴 곳이 다가오면 진동과 알림으로 알려드려요. 화면을 켜 두세요.', 'While you ride, we show the stops left and buzz you when your stop is near. Keep the screen on.')}
        </Text>
      </View>
    );
  }

  const arrivedEnd = Boolean(progress?.arrived && lastLeg);

  // 🔴 여행 당일 「지금」 카드(NowCard)와 한 식구 — 검은 판 + 붉은 점 「지금 · 탑승 중」(UI 캔버스 ⑮).
  //    전에는 파란 테두리 흰 카드에 「다음: 경성대.부경대역」 한 줄이라, 한국어 이름을 못 읽는 사람은 몇 개 남았는지 몰랐다.
  //    내릴 곳까지 정류장을 사다리로 세운다 — 글을 못 읽어도 점을 세면 된다.
  const lineLabel = leg.kind === 'bus' ? txf(tx, '%s번', '%s', leg.line) : leg.line;
  return (
    <View style={styles.ridingCard} accessibilityLiveRegion="polite">
      <View style={styles.head}>
        <View style={styles.live}><View style={styles.liveDot} /><Text variant="caption" weight="bold" color={color.action.primary}>{tx('지금 · 탑승 중', 'Now · riding')}</Text></View>
        <View style={[styles.lineChip, leg.kind === 'subway' && styles.subwayChip]}>
          <Text variant="caption" weight="bold" color={color.text.onAction} numberOfLines={1}>{lineLabel}</Text>
        </View>
      </View>

      {banner && !arrivedEnd ? (
        <View style={styles.banner} accessibilityRole="alert"><Text weight="bold" color={color.text.heading}>{banner}</Text></View>
      ) : null}

      {arrivedEnd ? (
        <Text variant="title" weight="bold" color={color.text.onAction}>{tx('다 왔어요. 여기서 내리세요.', 'You are here. Get off now.')}</Text>
      ) : live.state === 'denied' ? (
        <Text color={color.text.onDarkMuted}>{tx('위치 권한이 없어 따라갈 수 없어요. 설정에서 위치를 허용해 주세요.', 'We cannot follow you without location access. Allow location in Settings.')}</Text>
      ) : live.state === 'unavailable' ? (
        <Text color={color.text.onDarkMuted}>{tx('지금은 위치를 받을 수 없어요.', 'Your location is not available right now.')}</Text>
      ) : !progress ? (
        <Text color={color.text.onDarkMuted}>{tx('위치를 확인하고 있어요…', 'Finding your location…')}</Text>
      ) : (
        <>
          {progress.remainingStops !== null ? (
            // 한 문장으로 — 숫자와 「남았어요」를 따로 옮기면 일본어·중국어의 어순(あと6駅 · 还剩6站)을 못 맞춘다.
            <Text variant="display" weight="bold" color={color.text.onAction}>{tx(`${progress.remainingStops}정거장 남았어요`, progress.remainingStops === 1 ? '1 stop to go' : `${progress.remainingStops} stops to go`)}</Text>
          ) : progress.distanceToAlightM !== null ? (
            <Text variant="title" weight="bold" color={color.text.onAction}>{txf(tx, '약 %s 남았어요', 'About %s to go', distanceText(progress.distanceToAlightM))}</Text>
          ) : null}
          {progress.currentIndex !== null ? <StopLadder leg={leg} current={progress.currentIndex} tx={tx} /> : leg.alightName ? <Text weight="bold" color={color.text.onAction}>{txf(tx, '%s에서 내려요', 'Get off at %s', leg.alightName)}</Text> : null}
        </>
      )}

      <View style={styles.footer}>
        <Button label={tx('탑승 끝내기', 'End ride')} variant="tertiary" compact onPress={onStop} />
        <Text variant="caption" color={color.text.onDarkMuted} style={styles.grow}>{tx('한 정거장 전에 진동·알림으로 알려요. 화면을 켜 두세요.', 'We buzz you one stop early. Keep the screen on.')}</Text>
      </View>
    </View>
  );
}

/** 사다리에 세울 정류장 — 너무 길면 가운데를 접는다(지금 · 다음 · … · 마지막 둘). */
const LADDER_MAX = 7;

function StopLadder({ leg, current, tx }: { leg: RideLeg; current: number; tx: Tx }) {
  const last = leg.stops.length - 1;
  const ahead = leg.stops.slice(current, last + 1).map((stop, i) => ({ stop, index: current + i }));
  const rows: ({ stop: RideLeg['stops'][number]; index: number } | { skipped: number })[] = ahead.length <= LADDER_MAX
    ? ahead
    : [...ahead.slice(0, 4), { skipped: ahead.length - 6 }, ...ahead.slice(-2)];
  return (
    <View style={styles.ladder}>
      <View pointerEvents="none" style={styles.ladderLine} />
      {rows.map((row) => {
        if ('skipped' in row) {
          return <View key="skip" style={styles.rung}><View style={styles.rungMark}><View style={styles.dotLater} /></View><Text variant="caption" color={color.text.onDarkMuted}>{txf(tx, '… %s곳 더', '… %s more', String(row.skipped))}</Text></View>;
        }
        const kind = row.index === current ? 'now' : row.index === last ? 'off' : row.index === current + 1 ? 'next' : 'later';
        return (
          <View key={`${row.index}-${row.stop.name}`} style={styles.rung}>
            <View style={styles.rungMark}>
              {kind === 'now' ? <GabolleMascot state="open" still style={styles.nowMascot} /> : kind === 'off' ? <View style={styles.dotOff} /> : <View style={kind === 'next' ? styles.dotNext : styles.dotLater} />}
            </View>
            <Text weight={kind === 'later' ? 'regular' : 'bold'} color={kind === 'later' ? color.text.onDarkMuted : color.text.onAction} numberOfLines={1} style={styles.grow}>{row.stop.name}</Text>
            {kind === 'now' ? <Text variant="caption" weight="bold" color={color.action.primary}>{tx('지금 여기', 'You are here')}</Text> : null}
            {kind === 'next' ? <Text variant="caption" weight="bold" color={color.text.onDarkMuted}>{tx('다음', 'Next')}</Text> : null}
            {kind === 'off' ? <Text variant="caption" weight="bold" color={color.action.primary}>{tx('여기서 내려요', 'Get off here')}</Text> : null}
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.surface.card },
  ridingCard: { gap: spacing[3], padding: spacing[4], borderRadius: radius.lg, backgroundColor: color.brand.navy },
  live: { flexDirection: 'row', alignItems: 'center', gap: spacing[2], flex: 1 },
  liveDot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.state.dot },
  ladder: { position: 'relative', gap: 2, paddingVertical: spacing[1] },
  ladderLine: { position: 'absolute', left: 14, top: 18, bottom: 14, width: 2, backgroundColor: 'rgba(255,255,255,0.25)' },
  rung: { flexDirection: 'row', alignItems: 'center', gap: spacing[3], minHeight: 30 },
  rungMark: { width: 30, alignItems: 'center', justifyContent: 'center' },
  nowMascot: { width: 30, height: 30 },
  dotNext: { width: 10, height: 10, borderRadius: radius.full, backgroundColor: color.text.onAction },
  dotLater: { width: 10, height: 10, borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.35)' },
  dotOff: { width: 18, height: 18, borderRadius: radius.full, borderWidth: 3.5, borderColor: color.state.dot, backgroundColor: color.brand.navy },
  footer: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  head: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3] },
  lineChip: { minWidth: 64, minHeight: 32, paddingHorizontal: spacing[2], borderRadius: radius.sm, backgroundColor: color.state.info, alignItems: 'center', justifyContent: 'center' },
  subwayChip: { backgroundColor: color.state.warning },
  banner: { padding: spacing[3], borderRadius: radius.md, backgroundColor: color.state.warningBg },
  progress: { gap: spacing[1] },
  stretch: { alignSelf: 'stretch' },
  grow: { flex: 1 },
});
