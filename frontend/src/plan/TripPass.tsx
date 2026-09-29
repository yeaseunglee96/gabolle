// 여행 티켓(TRIP PASS) — 프린터에서 영수증이 출력되는 컴포넌트.
// 시안: docs/design_handoff_plan_flow/TripPassCard.dc.html
import { useEffect, useMemo, useRef, useState } from 'react';
import { Animated, Easing, Image, Platform, Pressable, StyleSheet, View } from 'react-native';
import Svg, { Path, Rect } from 'react-native-svg';
import qrcodeGenerator from 'qrcode-generator';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import type { TripPassData, TripPassDetail } from '@/plan/tripPassData';

const RECEIPT_WIDTH = 286;
const logo = require('../../assets/brand/gabolle-logo-hd.png');
const stamp = require('../../assets/brand/busan-arrived-stamp.png');
const PRINTER_WIDTH = 318;
const PRINTER_HEIGHT = 52;
/**
 * 종이가 나오는 방식 — 감열 프린터처럼 **조금 나오고 멈추고**를 되풀이한다(S15P21E201-1577).
 * 전에는 1.6초 동안 한 번에 매끄럽게 미끄러져 나와 「레이저 프린터 같다」고 했다.
 *
 * move 는 종이 한 장 중 이번에 나오는 몫, ms 는 그 몫이 나오는 시간, pauseMs 는 그 뒤에 멈추는 시간.
 * 모터 속도는 일정하다고 보고 ms 를 move 에 비례하게 두었다. 몫과 멈춤을 일부러 제각각으로 둔다 —
 * 똑같은 간격이면 기계가 아니라 박자로 읽힌다.
 */
export const PRINT_FEED: ReadonlyArray<{ move: number; ms: number; pauseMs: number }> = [
  { move: 0.11, ms: 115, pauseMs: 90 },
  { move: 0.17, ms: 180, pauseMs: 60 },
  { move: 0.09, ms: 95, pauseMs: 140 },
  { move: 0.19, ms: 200, pauseMs: 75 },
  { move: 0.13, ms: 135, pauseMs: 120 },
  { move: 0.18, ms: 190, pauseMs: 65 },
  { move: 0.13, ms: 135, pauseMs: 0 },
];
/** 시안의 gbPrint 와 같은 길이. 프린터에서 종이가 다 나오는 데 걸리는 시간이다 — 끊겨 나와도 이 시간은 그대로다. */
export const PRINT_MS = 1600;
/** QR 은 종이가 다 나온 뒤에 인쇄된다 — 시안의 1.7s 딜레이. */
const QR_DELAY_MS = 1700;
const QR_MS = 700;
/** 뒤집는 데 걸리는 시간. 시안의 800ms 스프링. */
const FLIP_MS = 800;
// 🔴 출력 뒤 3.6초마다 카드를 -16° 젖히던 「gbTease」는 뺐다(2026-09-21 실기, S15P21E201-1400) — 사람은
//    「뒤집을 수 있다」가 아니라 「티켓이 자꾸 오른쪽으로 움직인다」로 읽었다. 그 말은 아래 「눌러서 여행표 상세 보기 ↻」 글줄이 한다.

/** 지그재그 절취선. 시안은 CSS 그라데이션인데 RN 에 없어서 삼각형을 늘어놓는다. */
function TearLine() {
  const teeth = Math.ceil(RECEIPT_WIDTH / 14);
  return (
    <Svg width={RECEIPT_WIDTH} height={14} style={styles.tear}>
      {Array.from({ length: teeth }).map((_, index) => (
        <Path
          key={index}
          d={`M${index * 14} 0 L${index * 14 + 7} 14 L${index * 14 + 14} 0 Z`}
          fill={color.surface.card}
        />
      ))}
    </Svg>
  );
}

/** 진짜로 읽히는 QR — 담는 것은 그 일정을 여는 주소다(`tripPassUrl`). */
const QUIET_ZONE = 4;

function QrCode({ value, size }: { value: string; size: number }) {
  const matrix = useMemo(() => {
    const qr = qrcodeGenerator(0, 'M');
    qr.addData(value);
    qr.make();
    const count = qr.getModuleCount();
    const dark: Array<{ x: number; y: number }> = [];
    for (let y = 0; y < count; y += 1) {
      for (let x = 0; x < count; x += 1) if (qr.isDark(y, x)) dark.push({ x, y });
    }
    return { count, dark };
  }, [value]);

  const total = matrix.count + QUIET_ZONE * 2;
  const unit = size / total;
  return (
    <Svg width={size} height={size} accessibilityLabel={value}>
      <Rect x={0} y={0} width={size} height={size} fill={color.surface.card} />
      {matrix.dark.map((cell) => (
        <Rect
          key={`${cell.x}-${cell.y}`}
          x={(cell.x + QUIET_ZONE) * unit}
          y={(cell.y + QUIET_ZONE) * unit}
          // 칸 사이에 틈이 생기면 리더가 못 읽는다. 반올림 오차를 덮으려고 조금 겹쳐 그린다.
          width={unit + 0.5}
          height={unit + 0.5}
          fill={color.text.heading}
        />
      ))}
    </Svg>
  );
}

function PlaneIcon() {
  return (
    // 🔴 그림이 위를 향하게 그려져 있어 「출발 → 부산」 사이에서 하늘로 올라가는 것처럼 보였다(2026-09-21 실기).
    //    도착 쪽으로 90° 돌린다.
    <Svg width={16} height={16} viewBox="0 0 24 24" style={{ transform: [{ rotate: '90deg' }] }}>
      <Path
        d="M21 16v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5z"
        fill={color.text.heading}
      />
    </Svg>
  );
}

export type TripPassProps = {
  data: TripPassData;
  /** 넓은 화면이면 QR 을 크게 둔다. 시안의 desktop 모드다. */
  wide?: boolean;
  /** 「다시 출력」. 주면 버튼이 생긴다. */
  onReprint?: () => void;
  /**
   * 뒷면에 적을 줄들(`buildTripPassDetails`). **주면 카드가 뒤집힌다.**
   *
   * 🔴 안 주면 뒤집기 자체가 없다. 만드는 중에는 뒤집을 내용이 없는데 뒤집히면
   *    빈 뒷면이 나오고, 사용자는 고장으로 읽는다.
   */
  details?: TripPassDetail[];
  /** 뒷면의 「일정 보기 →」. 없으면 단추를 안 그린다. */
  onOpenItinerary?: () => void;
  /** 뒷면의 「지도에서 보기」. */
  onOpenMap?: () => void;
  /** 뒷면 머리 사진 — 첫 정차지의 관광공사 사진(S15P21E201-1378). 없으면 안 그린다. */
  coverUrl?: string | null;
  /**
   * 찍을 값이 다 왔나. **false 인 동안은 종이를 내보내지 않고, true 가 되는 순간 한 번 출력한다.**
   *
   * 🔴 값이 바뀌었다고 다시 출력하지 않는다(S15P21E201-1577). 전에는 코드·날짜가 바뀌면 처음부터
   *    다시 돌았는데, 일정을 받아 오기 전(코드가 빈 값)에 한 번 나오고 받아 온 뒤에 또 나와서
   *    사람 눈에는 영수증이 두 번 나왔다. 「다시 출력」은 부르는 쪽이 key 를 바꿔 새로 그린다.
   */
  ready?: boolean;
  tx: (ko: string, en: string) => string;
};

export function TripPass({ data, wide = false, onReprint, details, onOpenItinerary, onOpenMap, coverUrl, ready = true, tx }: TripPassProps) {
  // 종이는 프린터 뒤에서 내려온다. 시안의 gbPrint 와 같은 값이다.
  const [printed, setPrinted] = useState(false);
  const slide = useRef(new Animated.Value(0)).current;
  const codeMark = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    slide.setValue(0);
    codeMark.setValue(0);
    setPrinted(false);
    if (!ready) return undefined;
    let fed = 0;
    const print = Animated.sequence(PRINT_FEED.flatMap((step, index) => {
      fed += step.move;
      const chunk = Animated.timing(slide, {
        // 마지막 조각은 1 에 딱 맞춘다 — 소수를 더한 오차로 종이가 1px 덜 나오지 않게.
        toValue: index === PRINT_FEED.length - 1 ? 1 : fed,
        duration: step.ms,
        easing: Easing.linear,
        useNativeDriver: true,
      });
      return step.pauseMs > 0 ? [chunk, Animated.delay(step.pauseMs)] : [chunk];
    }));
    const mark = Animated.timing(codeMark, {
      toValue: 1,
      duration: QR_MS,
      delay: QR_DELAY_MS - PRINT_MS,
      easing: Easing.out(Easing.quad),
      useNativeDriver: true,
    });
    const sequence = Animated.sequence([print, mark]);
    // 🔴 다 나온 뒤에야 뒤집을 수 있다. 나오는 중에 뒤집으면 종이가 프린터 안에서
    //    돌아가는 꼴이 되고, 창이 잘라 내고 있어서 반쪽만 보인다.
    sequence.start(({ finished }) => { if (finished) setPrinted(true); });
    return () => sequence.stop();
  }, [ready, slide, codeMark]);

  const translateY = slide.interpolate({ inputRange: [0, 1], outputRange: [-520, 0] });
  const markScale = codeMark.interpolate({ inputRange: [0, 1], outputRange: [0.6, 1] });

  // ── 뒤집기 ────────────────────────────────────────────────────────────────
  const canFlip = printed && Boolean(details && details.length);
  const [flipped, setFlipped] = useState(false);
  const flip = useRef(new Animated.Value(0)).current;
  // 🔴 안 보이는 면은 화면 읽기(aria-hidden)와 키보드 탭(tabIndex)에서 뺀다(S15P21E201-1674). pointerEvents 는 누르기만 막아서,
  //    앞면을 보는데 「앞면으로 돌리기」가 읽히고 뒤집은 뒤에는 안 보이는 「내 일정 보기」에 탭이 섰다.
  const frontHidden = canFlip && flipped;
  const frontTab = frontHidden ? -1 : 0;
  const backTab = flipped ? 0 : -1;

  useEffect(() => {
    if (!canFlip) return;
    Animated.spring(flip, { toValue: flipped ? 1 : 0, damping: 14, stiffness: 120, mass: 1, useNativeDriver: true }).start();
  }, [canFlip, flip, flipped]);


  const frontRotate = flip.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '180deg'] });
  const backRotate = flip.interpolate({ inputRange: [0, 1], outputRange: ['180deg', '360deg'] });
  const flipScale = flip.interpolate({ inputRange: [0, 1], outputRange: [1, wide ? 1.18 : 1.06] });

  return (
    <View style={styles.root}>
      <View style={styles.printer}>
        <View style={styles.slot} />
      </View>

      {/* 인쇄가 끝나 overflow 가 빠지면 이 창은 배치만 남아 네이티브가 없애 버린다(뷰 평탄화). 그러면 두 면이
          바깥 카드 레이어로 올라가 거기서 3D 로 돌고, iOS 는 z 가 0 뒤로 넘어간 반쪽을 가린다 — 뒤집을 때 왼쪽·오른쪽이
          번갈아 사라졌다(S15P21E201-1787). 인쇄 중과 같은 상태로 창을 남겨 두 면을 이 안에 가둔다. 웹은 이 값을 안 쓴다. */}
      <View collapsable={false} style={[styles.paperWindow, printed ? null : styles.clipWhilePrinting]}>
        <Animated.View
          style={[
            styles.paper,
            {
              transform: [
                { translateY },
                { perspective: 1200 },
                { rotateY: frontRotate },
                { scale: flipScale },
              ],
            },
            // 🔴 뒤집힌 동안 앞면을 못 누르게 한다. RN 웹은 backfaceVisibility 를 지키지만
            //    안드로이드는 판마다 다르다 — 눌림까지 막아야 확실하다.
            canFlip && flipped ? styles.faceHidden : null,
          ]}
          pointerEvents={frontHidden ? 'none' : 'auto'}
          aria-hidden={frontHidden}
        >
          {/* 🔴 앞면 어디를 눌러도 뒤집히지만 앞면은 «단추»가 아니다(S15P21E201-1666). 단추로 두면 화면 읽기 프로그램이
              앞면을 한 덩어리로 읽어 안쪽 「내 일정 보기」를 따로 못 누르고, 웹에서는 단추 안에 단추가 들어간다.
              그래서 낭독(accessible)·키보드(tabIndex)에서는 빼고, 뒤집는 단추는 맨 아래 안내 줄이 맡는다. */}
          <Pressable
            accessible={false}
            tabIndex={-1}
            disabled={!canFlip}
            onPress={() => setFlipped(true)}
          >
          <View style={styles.sheet}>
            <View style={styles.rowBetween}>
              {/* 워드마크는 글자가 아니라 로고 그림이다 — O 자리에 동백이가 있는 진짜 로고와
                  글자로 흉내 낸 「GAB O LLE」가 앱 안에서 두 벌로 보이면 안 된다. */}
              <Image source={logo} resizeMode="contain" accessibilityLabel="GABOLLE" style={styles.wordmark} />
              {!!data.code && <Text variant="caption" weight="bold" color={color.text.body}>{data.code}</Text>}
            </View>

            {data.firstStop && data.lastStop ? (
              <View style={styles.legRow}>
                <View style={styles.legEnd}>
                  <Text variant="caption" color={color.text.body}>{tx('첫 일정', 'First stop')}</Text>
                  <Text variant="display" weight="bold" numberOfLines={1}>{data.firstStop.time || '—'}</Text>
                  <Text variant="caption" weight="bold" numberOfLines={2}>{data.firstStop.name}</Text>
                </View>
                <View style={styles.legMiddle}>
                  <View style={styles.dashed} />
                  <PlaneIcon />
                  <View style={styles.dashed} />
                </View>
                <View style={[styles.legEnd, styles.legEndRight]}>
                  <Text variant="caption" color={color.text.body}>{tx('마지막 일정', 'Last stop')}</Text>
                  <Text variant="display" weight="bold" numberOfLines={1}>{data.lastStop.time || '—'}</Text>
                  <Text variant="caption" weight="bold" numberOfLines={2} style={styles.legNameRight}>{data.lastStop.name}</Text>
                </View>
              </View>
            ) : (
            <View style={styles.legRow}>
              {/* 출발지를 모를 때 「부산」으로 채우지 않는다. 그러면 티켓이
                  「부산 → 부산」이 되어 사람이 고장으로 읽는다. 모르면 칸을 접는다.
              */}
              {data.fromLabel ? (
                <View style={styles.legEnd}>
                  <Text variant="caption" color={color.text.body}>{tx('출발', 'From')}</Text>
                  <Text variant="display" weight="bold" numberOfLines={1}>{data.fromLabel}</Text>
                  {!!data.startTime && <Text variant="caption" color={color.text.body}>{data.startTime}</Text>}
                </View>
              ) : (
                <View style={styles.legEnd}>
                  <Text variant="caption" color={color.text.body}>{tx('출발 시각', 'Departs')}</Text>
                  <Text variant="display" weight="bold" numberOfLines={1}>{data.startTime || '—'}</Text>
                </View>
              )}
              <View style={styles.legMiddle}>
                <View style={styles.dashed} />
                <PlaneIcon />
                <View style={styles.dashed} />
              </View>
              <View style={[styles.legEnd, styles.legEndRight]}>
                <Text variant="caption" color={color.text.body}>{tx('도착', 'To')}</Text>
                <Text variant="display" weight="bold" numberOfLines={1}>{data.toLabel}</Text>
                {!!data.endTime && <Text variant="caption" color={color.text.body}>{data.endTime}</Text>}
              </View>
            </View>
            )}

            {(!!data.dateRange || !!data.mode) && (
              <View style={styles.rowBetween}>
                {!!data.dateRange && (
                  <View>
                    <Text variant="caption" color={color.text.body}>{tx('날짜', 'Dates')}</Text>
                    <Text variant="caption" weight="bold">{data.dateRange}</Text>
                  </View>
                )}
                {!!data.mode && (
                  <View style={styles.modeBadge}>
                    <Text variant="caption" weight="bold">{data.mode}</Text>
                  </View>
                )}
              </View>
            )}

            {!!data.owner && (
              <View>
                <Text variant="caption" color={color.text.body}>{tx('여행자', 'Traveler')}</Text>
                <Text variant="caption" weight="bold">{data.owner}</Text>
              </View>
            )}

            {data.fields.length > 0 && (
              <View style={styles.grid}>
                {data.fields.map((field) => (
                  <View key={field.key} style={[styles.gridCell, field.wide && styles.gridCellWide]}>
                    <Text variant="caption" color={color.text.body}>{field.key}</Text>
                    <Text variant="caption" weight="bold" numberOfLines={field.wide ? 2 : 1}>{field.value}</Text>
                  </View>
                ))}
              </View>
            )}

            {/* 저장한 조건이 지켜졌는지 — 확인 표와 같은 값(UI 캔버스 ⑤-6). 없으면 칸째 안 그린다. */}
            {data.conditions.length > 0 && (
              <View style={styles.conditions}>
                <Text variant="caption" weight="bold" color={color.text.muted}>{tx('이 조건을 지켜서 만들었어요', 'Built to keep these conditions')}</Text>
                <Text variant="caption" weight="bold">{data.conditions.join(' · ')}</Text>
              </View>
            )}
          </View>

          <TearLine />

          <View style={styles.stub}>
            {/* 입국 도장 — 여행표가 「부산에 도착했다」는 표시. 팀이 고른 2b 안(BUSAN · 날짜 · GAB동백이LLE).
                QR(가운데 96~120)과 겹치지 않도록 오른쪽 귀퉁이에 비스듬히 찍는다. 장식이라 낭독기에는 안 읽힌다. */}
            {/* 🔴 가짜 바코드는 뺐다(2026-09-21 실기, S15P21E201-1400) — QR 과 바코드가 둘이라 「왜 둘인지」를 물었다.
                남는 것은 진짜로 읽히는 QR 하나. 도장은 QR 옆 제 칸에 크게 — 귀퉁이에 겹쳐 찍었을 때 글자가 안 읽혔다. */}
            <View style={[styles.codeRow, onOpenItinerary && styles.codeRowButton]}>
              {/* 🔴 일정이 다 되면 QR 자리가 「내 일정 보기」 단추가 된다(S15P21E201-1562). 티켓을 눌러 뒤집고
                  뒷면의 「일정 보기」를 눌러야 다음으로 가는데, 화면이 그걸 말해 주지 않아 사람이 티켓 앞에서 멈췄다.
                  뒤집어 상세를 보는 것은 그대로다 — 이 단추 밖의 티켓을 누르면 된다. */}
              {onOpenItinerary ? (
                // 🔴 단추는 종이에 처음부터 인쇄돼 나온다(UI 캔버스 ⑤-6 PassAfter). 전에는 QR 처럼 인쇄가 끝난 뒤 따로 커지며
                //    나타나서, 다 나온 승차권 위에 단추가 «튀어나오는» 것으로 보였다(사용자 지적, S15P21E201-1866).
                <View style={styles.nextButtonRow}>
                  <Pressable accessibilityRole="button" tabIndex={frontTab} onPress={onOpenItinerary} style={({ pressed }) => [styles.nextButton, wide && styles.nextButtonWide, styles.nextButtonFull, pressed && styles.backPressed]}>
                    <Text weight="bold" color={color.text.onAction} style={styles.nextLabel}>{tx('내 일정 보기', 'View my itinerary')}</Text>
                    <Text variant="title" weight="bold" color={color.text.onAction}>→</Text>
                  </Pressable>
                </View>
              ) : !!data.url && (
                <Animated.View style={{ opacity: codeMark, transform: [{ scale: markScale }] }}>
                  <QrCode value={data.url} size={wide ? 120 : 104} />
                </Animated.View>
              )}
              {/* 🔴 날짜는 그림에 안 박혀 있다 — 「12 · SEP · 2026」이 모든 여행에 찍히던 것(실기 빌드 28, S15P21E201-1437). 빈 칸에 출발일을 앱이 찍는다. */}
              {/* 🔴 일정이 다 되면 「내 일정 보기」가 이 줄을 다 쓴다(UI 캔버스 ⑤-6) — 도장과 나란히 서면 단추가 좁고 도장이 단추처럼 보였다.
                  도장(팀이 고른 2b 안)은 인쇄되는 동안 QR 옆에 그대로 찍힌다. */}
              {onOpenItinerary ? null : (
                <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.stamp, wide && styles.stampWide]}>
                  <Image source={stamp} resizeMode="contain" style={styles.stampImage} />
                  {data.stampDate ? <Text weight="bold" color={color.action.outline} style={[styles.stampDate, wide && styles.stampDateWide]}>{data.stampDate}</Text> : null}
                </View>
              )}
            </View>
            <Text variant="caption" color={color.text.muted} style={styles.validText}>{data.validText}</Text>
            {!!data.code && (
              <Text variant="caption" weight="bold" color={color.text.body}>{data.code}</Text>
            )}
          </View>

          <TearLine />
          {canFlip ? (
            <Pressable accessibilityRole="button" accessibilityLabel={tx('여행표 상세 보기', 'See trip pass details')} tabIndex={frontTab} onPress={() => setFlipped(true)} style={styles.flipHint}>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('눌러서 여행표 상세 보기 ↻', 'Tap to see trip pass details ↻')}</Text>
            </Pressable>
          ) : null}
          </Pressable>
        </Animated.View>

        {/* 🔴 뒷면에 overflow:hidden 을 주지 않는다. 3D 가 평면으로 눌리면서 **앞면 위에
            거울상으로 비친다** — 실제로 났던 결함이라 인계 문서가 따로 적어 두었다.
            보이고 안 보이고는 backfaceVisibility 와 pointerEvents 로만 가른다. */}
        {canFlip ? (
          <Animated.View
            style={[
              styles.back,
              {
                transform: [
                  { translateY },
                  { perspective: 1200 },
                  { rotateY: backRotate },
                  { scale: flipScale },
                ],
              },
              flipped ? null : styles.faceHidden,
            ]}
            pointerEvents={flipped ? 'auto' : 'none'}
            aria-hidden={!flipped}
          >
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={tx('앞면으로 돌리기', 'Flip back')}
              tabIndex={backTab}
              onPress={() => setFlipped(false)}
              style={styles.backHead}
            >
              <Text variant="caption" weight="bold" color={color.text.muted} style={styles.backMark}>TRIP PASS</Text>
              {!!data.code && <Text variant="caption" weight="bold" color={color.text.muted}>{data.code}</Text>}
            </Pressable>

            {coverUrl ? <Image source={{ uri: coverUrl }} resizeMode="cover" accessibilityLabel="" style={styles.backCover} /> : null}
            <Text variant="title" weight="bold" numberOfLines={1}>
              {[data.fromLabel, data.toLabel].filter(Boolean).join(' → ')}
            </Text>
            {data.dateRange ? <Text variant="caption" color={color.text.muted}>{data.dateRange}</Text> : null}

            {(details ?? []).map((row) => (
              <View key={row.key} style={styles.backRow}>
                <Text variant="caption" color={color.text.muted}>{row.key}</Text>
                <Text variant="caption" weight="bold" numberOfLines={1} style={styles.backValue}>{row.value}</Text>
              </View>
            ))}
            <View style={styles.backRow}>
              <Text variant="caption" color={color.text.muted}>{tx('진행 상태', 'Status')}</Text>
              <View style={styles.backStatus}>
                <View style={styles.backDot} />
                <Text variant="caption" weight="bold">{tx('생성 완료', 'Ready')}</Text>
              </View>
            </View>

            <View style={styles.backActions}>
              {onOpenItinerary ? (
                <Pressable accessibilityRole="button" tabIndex={backTab} onPress={onOpenItinerary} style={({ pressed }) => [styles.backPrimary, pressed && styles.backPressed]}>
                  <Text weight="bold" color={color.text.onAction}>{tx('일정 보기 →', 'View itinerary →')}</Text>
                </Pressable>
              ) : null}
              {onOpenMap ? (
                <Pressable accessibilityRole="button" tabIndex={backTab} onPress={onOpenMap} style={({ pressed }) => [styles.backGhost, pressed && styles.backPressed]}>
                  <Text weight="bold">{tx('지도에서 보기', 'See on the map')}</Text>
                </Pressable>
              ) : null}
            </View>
          </Animated.View>
        ) : null}
      </View>

      {!!onReprint && (
        <Pressable onPress={onReprint} style={styles.reprint} accessibilityRole="button">
          <Text variant="caption" weight="bold" color={color.action.secondary}>
            {tx('다시 출력', 'Print again')}
          </Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { alignItems: 'center', width: '100%' },
  printer: {
    zIndex: 3,
    width: PRINTER_WIDTH,
    height: PRINTER_HEIGHT,
    borderRadius: radius.md,
    backgroundColor: color.action.secondary,
    alignItems: 'center',
    justifyContent: 'center',
    ...Platform.select({
      web: { boxShadow: '0 8px 20px rgba(25,25,25,.28)' } as object,
      default: { shadowColor: color.brand.navy, shadowOpacity: 0.28, shadowRadius: 20, shadowOffset: { width: 0, height: 8 }, elevation: 6 },
    }),
  },
  slot: { width: RECEIPT_WIDTH, height: 14, borderRadius: radius.full, backgroundColor: color.brand.navy },
  // 종이가 프린터 뒤에서 나오는 것처럼 보이게 창을 잘라 둔다. overflow 를 빼면
  // 아직 안 나온 종이가 프린터 위에 떠 보인다.
  // 🔴 잘라내기(overflow)는 **인쇄 중에만** 켠다. 켜 둔 채로 뒤집으면 커진 카드의
  //    가장자리가 잘리고, 뒷면이 3D 가 아니라 평면으로 눌려 앞면 위에 비친다.
  paperWindow: { width: RECEIPT_WIDTH, marginTop: -PRINTER_HEIGHT / 2, paddingTop: PRINTER_HEIGHT / 2 },
  clipWhilePrinting: { overflow: 'hidden' },
  // 🔴 젖힘과 뒤집기의 축을 **종이 윗변**에 둔다. 가운데를 축으로 돌리면 종이가 프린터
  //    슬롯에서 떨어져 나와 허공에서 도는 것처럼 보인다 — 인쇄물이 아니라 카드가 된다.
  //    (웹만 지킨다. 네이티브는 이 속성이 없어 가운데 축으로 돈다 — 그래도 안 깨진다.)
  paper: { width: RECEIPT_WIDTH, backfaceVisibility: 'hidden', transformOrigin: 'top center' },
  faceHidden: { opacity: 0 },
  // 🔴 이 줄도 «종이»다. 흰 바탕은 머리(sheet)·스터브(stub)가 각자 칠하는데 여기만
  //    안 칠해서, 마지막 절취선 뒤의 「눌러서 여행표 상세 보기」가 페이지 바탕 위에 떠
  //    «승차권 밖으로 튀어나간» 것처럼 보였다(실기 빌드 29, S15P21E201-1460).
  //    stub 과 같은 marginTop: 4 로 절취선 틈도 위와 같게 둔다.
  flipHint: { backgroundColor: color.surface.card, marginTop: 4, alignItems: 'center', paddingVertical: spacing[2] },

  // 뒷면 — 앞면과 **같은 자리**에 겹쳐 둔다. 크기가 다르면 뒤집는 동안 자리가 튄다.
  back: {
    position: 'absolute', left: 0, right: 0, top: PRINTER_HEIGHT / 2,
    width: RECEIPT_WIDTH, gap: spacing[2], padding: spacing[4],
    // 🔴 위쪽을 더 띄운다. 프린터가 종이 윗부분 26px 을 덮고 있어서, 그대로 두면
    //    뒷면의 첫 줄(TRIP PASS · 코드)이 프린터 뒤로 들어가 안 읽힌다.
    paddingTop: spacing[8],
    borderRadius: radius.sm, backgroundColor: color.surface.card,
    backfaceVisibility: 'hidden', transformOrigin: 'top center',
    shadowColor: color.brand.navy, shadowOpacity: 0.16, shadowRadius: 10, shadowOffset: { width: 0, height: 6 }, elevation: 4,
  },
  backCover: { width: '100%', height: 120, borderRadius: radius.md, backgroundColor: color.surface.soft },
  backHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 28 },
  backMark: { letterSpacing: 1.5 },
  backRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing[3], minHeight: 36,
    borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: color.surface.border,
  },
  backValue: { flexShrink: 1 },
  backStatus: { flexDirection: 'row', alignItems: 'center', gap: spacing[1] },
  backDot: { width: 8, height: 8, borderRadius: radius.full, backgroundColor: color.state.success },
  backActions: { gap: spacing[2], marginTop: spacing[2] },
  nextButton: { width: 120, height: 104, alignItems: 'center', justifyContent: 'center', gap: spacing[1], paddingHorizontal: spacing[2], borderRadius: radius.md, backgroundColor: color.action.primary },
  nextButtonWide: { width: 136, height: 120 },
  nextLabel: { textAlign: 'center' },
  backPrimary: { minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.brand.navy },
  backGhost: { minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  backPressed: { opacity: 0.8 },
  sheet: { backgroundColor: color.surface.card, paddingHorizontal: 20, paddingTop: 18, paddingBottom: 12, gap: spacing[3] },
  wordmark: { width: 116, height: 21, marginLeft: -2 },
  // 도장 — 스터브 오른쪽, QR 옆 빈 자리. 유효기간 글줄(바코드 바로 아래) 위에 얹히지 않게 그 밑에서 시작한다.
  codeRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing[4] },
  conditions: { gap: 2, paddingVertical: spacing[2], paddingHorizontal: spacing[3], borderRadius: radius.md, backgroundColor: color.surface.soft },
  legNameRight: { textAlign: 'right' },
  nextButtonRow: { width: '100%' },
  codeRowButton: { alignSelf: 'stretch', justifyContent: 'center' },
  nextButtonFull: { width: '100%', height: 56, flexDirection: 'row', gap: spacing[2] },
  stamp: { width: 112, height: 112, transform: [{ rotate: '-8deg' }], pointerEvents: 'none' },
  stampImage: { width: '100%', height: '100%' },
  // 그림의 빈 띠(1024 기준 y 486~620)에 맞춘 자리 — 112px 에서는 위 53px, 글자 7px.
  stampDate: { position: 'absolute', left: 0, right: 0, top: 53, textAlign: 'center', fontSize: 7, lineHeight: 9, letterSpacing: 1 },
  stampDateWide: { top: 61, fontSize: 8, lineHeight: 10 },
  stampWide: { width: 128, height: 128 },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  legRow: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  legEnd: { flexShrink: 1 },
  legEndRight: { alignItems: 'flex-end' },
  legMiddle: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 10, paddingBottom: 22 },
  dashed: { flex: 1, borderTopWidth: 1, borderColor: color.surface.field, borderStyle: 'dashed' },
  modeBadge: { paddingVertical: 4, paddingHorizontal: 10, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field },
  grid: { flexDirection: 'row', flexWrap: 'wrap', paddingTop: 10, borderTopWidth: 1, borderColor: color.surface.field, borderStyle: 'dashed' },
  gridCell: { width: '33.33%', paddingTop: spacing[1], paddingRight: spacing[1] },
  // 장소 이름은 3분의 1 칸에 안 들어간다 — 「충무동 새벽…」으로 잘려 일정이 안 보인다고 했다(2026-09-21 실기).
  gridCellWide: { width: '100%' },
  stub: { backgroundColor: color.surface.card, marginTop: 4, paddingHorizontal: 20, paddingTop: 16, paddingBottom: 12, alignItems: 'center', gap: spacing[3] },
  validText: { letterSpacing: 0.1, textAlign: 'center' },
  tear: { backgroundColor: 'transparent' },
  reprint: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], marginTop: spacing[3] },
});
