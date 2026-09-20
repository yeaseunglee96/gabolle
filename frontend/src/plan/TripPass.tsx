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
/** 시안의 gbPrint 와 같은 길이. 프린터에서 종이가 다 나오는 데 걸리는 시간이다. */
const PRINT_MS = 1600;
/** QR 은 종이가 다 나온 뒤에 인쇄된다 — 시안의 1.7s 딜레이. */
const QR_DELAY_MS = 1700;
const QR_MS = 700;
/** 뒤집는 데 걸리는 시간. 시안의 800ms 스프링. */
const FLIP_MS = 800;
/**
 * 출력이 끝난 뒤 이 간격으로 한 번씩 **살짝 젖힌다**(시안 gbTease).
 *
 * 🔴 「눌러서 뒤집을 수 있다」를 글자로만 적으면 아무도 안 읽는다. 카드가 스스로 조금
 *    움직이는 것이 그 자리에서 가장 짧은 설명이다.
 */
const TEASE_EVERY_MS = 3600;

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

/**
 * 바코드. 굵기가 일정하면 「그림」으로 보이므로 코드 글자에서 굵기를 만든다
 * 같은 여행이면 언제나 같은 무늬가 나온다.
 */
function Barcode({ seed }: { seed: string }) {
  const bars = useMemo(() => {
    const source = seed || 'GABOLLE';
    return Array.from({ length: 60 }).map((_, index) => {
      const charCode = source.charCodeAt(index % source.length) + index * 7;
      return { width: (charCode % 3) + 1, ink: charCode % 3 !== 0 };
    });
  }, [seed]);
  return (
    <View style={styles.barcode}>
      {bars.map((bar, index) => (
        <View
          key={index}
          style={{ width: bar.width, height: 48, backgroundColor: bar.ink ? color.text.heading : color.surface.card }}
        />
      ))}
    </View>
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
    <Svg width={16} height={16} viewBox="0 0 24 24">
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
  tx: (ko: string, en: string) => string;
};

export function TripPass({ data, wide = false, onReprint, details, onOpenItinerary, onOpenMap, tx }: TripPassProps) {
  // 종이는 프린터 뒤에서 내려온다. 시안의 gbPrint 와 같은 값이다.
  const [printed, setPrinted] = useState(false);
  const slide = useRef(new Animated.Value(0)).current;
  const codeMark = useRef(new Animated.Value(0)).current;
  /** 다시 출력할 때마다 애니메이션을 처음부터 돌리려고 센다. */
  const printKey = `${data.code}:${data.dateRange ?? ''}`;

  useEffect(() => {
    slide.setValue(0);
    codeMark.setValue(0);
    const print = Animated.timing(slide, {
      toValue: 1,
      duration: PRINT_MS,
      easing: Easing.bezier(0.25, 0.7, 0.25, 1),
      useNativeDriver: true,
    });
    const mark = Animated.timing(codeMark, {
      toValue: 1,
      duration: QR_MS,
      delay: QR_DELAY_MS - PRINT_MS,
      easing: Easing.out(Easing.quad),
      useNativeDriver: true,
    });
    setPrinted(false);
    const sequence = Animated.sequence([print, mark]);
    // 🔴 다 나온 뒤에야 뒤집을 수 있다. 나오는 중에 뒤집으면 종이가 프린터 안에서
    //    돌아가는 꼴이 되고, 창이 잘라 내고 있어서 반쪽만 보인다.
    sequence.start(({ finished }) => { if (finished) setPrinted(true); });
    return () => sequence.stop();
  }, [printKey, slide, codeMark]);

  const translateY = slide.interpolate({ inputRange: [0, 1], outputRange: [-520, 0] });
  const markScale = codeMark.interpolate({ inputRange: [0, 1], outputRange: [0.6, 1] });

  // ── 뒤집기 ────────────────────────────────────────────────────────────────
  const canFlip = printed && Boolean(details && details.length);
  const [flipped, setFlipped] = useState(false);
  const flip = useRef(new Animated.Value(0)).current;
  const tease = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (!canFlip) return;
    Animated.spring(flip, { toValue: flipped ? 1 : 0, damping: 14, stiffness: 120, mass: 1, useNativeDriver: true }).start();
  }, [canFlip, flip, flipped]);

  // 🔴 뒤집힌 뒤에는 젖히지 않는다. 뒷면에는 단추가 있어서, 움직이는 카드 위에서
  //    누르면 손가락 아래에서 자리가 바뀐다.
  useEffect(() => {
    if (!canFlip || flipped) return;
    const timer = setInterval(() => {
      Animated.sequence([
        Animated.timing(tease, { toValue: 1, duration: 260, easing: Easing.out(Easing.quad), useNativeDriver: true }),
        Animated.timing(tease, { toValue: 0, duration: 420, easing: Easing.bezier(0.34, 1.3, 0.64, 1), useNativeDriver: true }),
      ]).start();
    }, TEASE_EVERY_MS);
    return () => clearInterval(timer);
  }, [canFlip, flipped, tease]);

  const frontRotate = flip.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '180deg'] });
  const backRotate = flip.interpolate({ inputRange: [0, 1], outputRange: ['180deg', '360deg'] });
  const flipScale = flip.interpolate({ inputRange: [0, 1], outputRange: [1, wide ? 1.18 : 1.06] });
  const teaseRotate = tease.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '-16deg'] });

  return (
    <View style={styles.root}>
      <View style={styles.printer}>
        <View style={styles.slot} />
      </View>

      <View style={[styles.paperWindow, printed ? null : styles.clipWhilePrinting]}>
        <Animated.View
          style={[
            styles.paper,
            {
              transform: [
                { translateY },
                { perspective: 1200 },
                { rotateY: frontRotate },
                { rotateZ: teaseRotate },
                { scale: flipScale },
              ],
            },
            // 🔴 뒤집힌 동안 앞면을 못 누르게 한다. RN 웹은 backfaceVisibility 를 지키지만
            //    안드로이드는 판마다 다르다 — 눌림까지 막아야 확실하다.
            canFlip && flipped ? styles.faceHidden : null,
          ]}
          pointerEvents={canFlip && flipped ? 'none' : 'auto'}
        >
          <Pressable
            accessibilityRole={canFlip ? 'button' : undefined}
            accessibilityLabel={canFlip ? tx('여행표 상세 보기', 'See trip pass details') : undefined}
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
                  <View key={field.key} style={styles.gridCell}>
                    <Text variant="caption" color={color.text.body}>{field.key}</Text>
                    <Text variant="caption" weight="bold" numberOfLines={1}>{field.value}</Text>
                  </View>
                ))}
              </View>
            )}
          </View>

          <TearLine />

          <View style={styles.stub}>
            {/* 입국 도장 — 여행표가 「부산에 도착했다」는 표시. 팀이 고른 2b 안(BUSAN · 날짜 · GAB동백이LLE).
                QR(가운데 96~120)과 겹치지 않도록 오른쪽 귀퉁이에 비스듬히 찍는다. 장식이라 낭독기에는 안 읽힌다. */}
            <Image source={stamp} resizeMode="contain" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.stamp} />
            <Barcode seed={data.code} />
            <Text variant="caption" color={color.text.muted} style={styles.validText}>{data.validText}</Text>
            {!!data.url && (
              <Animated.View style={{ opacity: codeMark, transform: [{ scale: markScale }] }}>
                <QrCode value={data.url} size={wide ? 120 : 96} />
              </Animated.View>
            )}
            {!!data.code && (
              <Text variant="caption" weight="bold" color={color.text.body}>{data.code}</Text>
            )}
          </View>

          <TearLine />
          {canFlip ? (
            <View style={styles.flipHint}>
              <Text variant="caption" weight="bold" color={color.text.muted}>{tx('눌러서 여행표 상세 보기 ↻', 'Tap to see trip pass details ↻')}</Text>
            </View>
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
          >
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={tx('앞면으로 돌리기', 'Flip back')}
              onPress={() => setFlipped(false)}
              style={styles.backHead}
            >
              <Text variant="caption" weight="bold" color={color.text.muted} style={styles.backMark}>TRIP PASS</Text>
              {!!data.code && <Text variant="caption" weight="bold" color={color.text.muted}>{data.code}</Text>}
            </Pressable>

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
                <Pressable accessibilityRole="button" onPress={onOpenItinerary} style={({ pressed }) => [styles.backPrimary, pressed && styles.backPressed]}>
                  <Text weight="bold" color={color.text.onAction}>{tx('일정 보기 →', 'View itinerary →')}</Text>
                </Pressable>
              ) : null}
              {onOpenMap ? (
                <Pressable accessibilityRole="button" onPress={onOpenMap} style={({ pressed }) => [styles.backGhost, pressed && styles.backPressed]}>
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
  flipHint: { alignItems: 'center', paddingVertical: spacing[2] },

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
  backPrimary: { minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, backgroundColor: color.brand.navy },
  backGhost: { minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md, borderWidth: 1, borderColor: color.surface.border, backgroundColor: color.surface.card },
  backPressed: { opacity: 0.8 },
  sheet: { backgroundColor: color.surface.card, paddingHorizontal: 20, paddingTop: 18, paddingBottom: 12, gap: spacing[3] },
  wordmark: { width: 90, height: 30, marginLeft: -2 },
  // 도장 — 스터브 오른쪽, QR 옆 빈 자리. 유효기간 글줄(바코드 바로 아래) 위에 얹히지 않게 그 밑에서 시작한다.
  stamp: { position: 'absolute', right: 0, top: 104, width: 84, height: 84, transform: [{ rotate: '-12deg' }], opacity: 0.92, zIndex: 1, pointerEvents: 'none' },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  legRow: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  legEnd: { flexShrink: 1 },
  legEndRight: { alignItems: 'flex-end' },
  legMiddle: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 10, paddingBottom: 22 },
  dashed: { flex: 1, borderTopWidth: 1, borderColor: color.surface.field, borderStyle: 'dashed' },
  modeBadge: { paddingVertical: 4, paddingHorizontal: 10, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field },
  grid: { flexDirection: 'row', flexWrap: 'wrap', paddingTop: 10, borderTopWidth: 1, borderColor: color.surface.field, borderStyle: 'dashed' },
  gridCell: { width: '33.33%', paddingTop: spacing[1], paddingRight: spacing[1] },
  stub: { backgroundColor: color.surface.card, marginTop: 4, paddingHorizontal: 20, paddingTop: 16, paddingBottom: 12, alignItems: 'center', gap: spacing[3] },
  barcode: { flexDirection: 'row', alignItems: 'stretch', height: 48, width: '100%', overflow: 'hidden' },
  validText: { letterSpacing: 0.1, textAlign: 'center' },
  tear: { backgroundColor: 'transparent' },
  reprint: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], marginTop: spacing[3] },
});
