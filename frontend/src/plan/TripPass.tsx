// 여행 티켓(TRIP PASS) — 프린터에서 영수증이 출력되는 컴포넌트 (S15P21E201-1233).
// 시안: docs/design_handoff_plan_flow/TripPassCard.dc.html
//
// 데스크톱과 폰이 **같은 컴포넌트**다. 시안이 그렇게 정했고, 둘로 나누면 한쪽만 고치는
// 날이 온다 — 이 저장소가 여러 번 겪은 고장이다.
//
// 🔴 찍히는 값은 여기서 만들지 않는다. `tripPassData.ts` 가 만들고 시험이 붙든다.
//    이 파일은 **그리기만** 한다.
import { useEffect, useMemo, useRef } from 'react';
import { Animated, Easing, Platform, Pressable, StyleSheet, View } from 'react-native';
import Svg, { Path, Rect } from 'react-native-svg';
import qrcodeGenerator from 'qrcode-generator';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import type { TripPassData } from '@/plan/tripPassData';

const RECEIPT_WIDTH = 286;
const PRINTER_WIDTH = 318;
const PRINTER_HEIGHT = 52;
/** 시안의 gbPrint 와 같은 길이. 프린터에서 종이가 다 나오는 데 걸리는 시간이다. */
const PRINT_MS = 1600;
/** QR 은 종이가 다 나온 뒤에 인쇄된다 — 시안의 1.7s 딜레이. */
const QR_DELAY_MS = 1700;
const QR_MS = 700;

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
 * 바코드. 굵기가 일정하면 「그림」으로 보이므로 코드 글자에서 굵기를 만든다 —
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

/**
 * 진짜로 읽히는 QR — 담는 것은 그 일정을 여는 주소다(`tripPassUrl`).
 *
 * 🔴 **주소가 없으면 아무것도 안 그린다.** 안 읽히거나 안 열리는 QR 은 사람에게
 * 「고장난 앱」으로 읽힌다 — 무늬만 그럴듯하게 그리느니 자리를 비운다.
 *
 * 🔴 **가장자리 여백(quiet zone)을 4칸 둔다.** 이게 없으면 리더가 못 읽는다.
 * 눈으로는 멀쩡해 보여서, 빼먹으면 「가끔 안 찍힌다」로만 나타난다.
 */
const QUIET_ZONE = 4;

function QrCode({ value, size }: { value: string; size: number }) {
  const matrix = useMemo(() => {
    // 형(0)은 자동, 오류 정정 M — 인쇄물처럼 작게 그려도 읽히는 쪽에 둔다.
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
          // 🔴 칸 사이에 틈이 생기면 리더가 못 읽는다. 반올림 오차를 덮으려고 조금 겹쳐 그린다.
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
  tx: (ko: string, en: string) => string;
};

export function TripPass({ data, wide = false, onReprint, tx }: TripPassProps) {
  // 🔴 종이는 프린터 뒤에서 내려온다. 시안의 gbPrint 와 같은 값이다.
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
    const sequence = Animated.sequence([print, mark]);
    sequence.start();
    return () => sequence.stop();
  }, [printKey, slide, codeMark]);

  const translateY = slide.interpolate({ inputRange: [0, 1], outputRange: [-520, 0] });
  const markScale = codeMark.interpolate({ inputRange: [0, 1], outputRange: [0.6, 1] });

  return (
    <View style={styles.root}>
      <View style={styles.printer}>
        <View style={styles.slot} />
      </View>

      <View style={styles.paperWindow}>
        <Animated.View style={[styles.paper, { transform: [{ translateY }] }]}>
          <View style={styles.sheet}>
            <View style={styles.rowBetween}>
              <Text variant="body" weight="bold" style={styles.wordmark}>
                GAB<Text variant="body" weight="bold" color={color.brand.orange}>O</Text>LLE
              </Text>
              {!!data.code && <Text variant="caption" weight="bold" color={color.text.body}>{data.code}</Text>}
            </View>

            <View style={styles.legRow}>
              {/* 🔴 출발지를 모를 때 「부산」으로 채우지 않는다. 그러면 티켓이
                  「부산 → 부산」이 되어 사람이 고장으로 읽는다. 모르면 칸을 접는다. */}
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
        </Animated.View>
      </View>

      {!!onReprint && (
        <Pressable onPress={onReprint} style={styles.reprint} accessibilityRole="button">
          <Text variant="caption" weight="bold" color={color.brand.orange}>
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
    backgroundColor: '#2b3037',
    alignItems: 'center',
    justifyContent: 'center',
    ...Platform.select({
      web: { boxShadow: '0 8px 20px rgba(11,29,58,.28)' } as object,
      default: { shadowColor: color.brand.navy, shadowOpacity: 0.28, shadowRadius: 20, shadowOffset: { width: 0, height: 8 }, elevation: 6 },
    }),
  },
  slot: { width: RECEIPT_WIDTH, height: 14, borderRadius: radius.full, backgroundColor: '#14171b' },
  // 🔴 종이가 프린터 **뒤에서** 나오는 것처럼 보이게 창을 잘라 둔다. overflow 를 빼면
  //    아직 안 나온 종이가 프린터 위에 떠 보인다.
  paperWindow: { width: RECEIPT_WIDTH, overflow: 'hidden', marginTop: -PRINTER_HEIGHT / 2, paddingTop: PRINTER_HEIGHT / 2 },
  paper: { width: RECEIPT_WIDTH },
  sheet: { backgroundColor: color.surface.card, paddingHorizontal: 20, paddingTop: 18, paddingBottom: 12, gap: spacing[3] },
  wordmark: { letterSpacing: 0.5 },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  legRow: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  legEnd: { flexShrink: 1 },
  legEndRight: { alignItems: 'flex-end' },
  legMiddle: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 10, paddingBottom: 22 },
  dashed: { flex: 1, borderTopWidth: 1, borderColor: '#c9c3ba', borderStyle: 'dashed' },
  modeBadge: { paddingVertical: 4, paddingHorizontal: 10, borderRadius: radius.full, borderWidth: 1, borderColor: color.surface.field },
  grid: { flexDirection: 'row', flexWrap: 'wrap', paddingTop: 10, borderTopWidth: 1, borderColor: color.surface.field, borderStyle: 'dashed' },
  gridCell: { width: '33.33%', paddingTop: spacing[1], paddingRight: spacing[1] },
  stub: { backgroundColor: color.surface.card, marginTop: 4, paddingHorizontal: 20, paddingTop: 16, paddingBottom: 12, alignItems: 'center', gap: spacing[3] },
  barcode: { flexDirection: 'row', alignItems: 'stretch', height: 48, width: '100%', overflow: 'hidden' },
  validText: { letterSpacing: 0.1, textAlign: 'center' },
  tear: { backgroundColor: 'transparent' },
  reprint: { minHeight: 40, justifyContent: 'center', paddingHorizontal: spacing[4], marginTop: spacing[3] },
});
