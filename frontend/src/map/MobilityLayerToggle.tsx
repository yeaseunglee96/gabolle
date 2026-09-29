// 지도 위 「경사」·「그늘」 켜고 끄기 + 켠 것의 한 줄 풀이 (S15P21E201-1569).
// 하나만 켠다 — 빨강·파랑 선이 겹치면 어느 것이 무엇인지 못 읽는다.
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { Text } from '@/components/Text';
import { color, radius, spacing } from '@/design/tokens';
import { txf } from '@/i18n/format';
import type { MobilityLayerKind, MobilityLayerResult } from '@/map/mobilityLayers';

type Tx = (ko: string, en: string) => string;

/**
 * 파일이 적은 기준 한 줄(public/layers/<지역>.json 의 slopeBasis·shadowBasis — 한국어 원문뿐이다)을 고른 언어로.
 *
 * 🔴 파일에는 그 원문밖에 없다(나눠 적은 칸이 없다 — tools/build-mobility-layers.mjs). 그래서 아는 원문을 옮긴다.
 *    그늘 기준은 날짜·시각이 바뀔 수 있어 모양으로 읽어 값만 끼운다.
 * 🔴 모르는 원문이면 한국어 화면에서만 그대로 내고, 다른 언어에서는 뺀다 — 영어·일본어 화면 한가운데 한국어 한 줄이
 *    끼는 것보다 없는 편이 낫다(tx 는 번역표에 없는 문구를 영어 쪽으로 떨어뜨리므로 영어 쪽을 비워 둔다).
 */
export function layerBasisText(raw: string | null | undefined, tx: Tx): string | null {
  if (!raw) return null;
  if (raw === '경사 중앙값 · 30m 이상 길 · 고도 자료로 잰 추정치') {
    return tx('경사 중앙값 · 30m 이상 길 · 고도 자료로 잰 추정치', 'Median slope · paths 30 m or longer · estimated from elevation data');
  }
  const shadow = /^건물 그림자 · (\d{4}-\d{2}-\d{2}) · (\d{1,2})–(\d{1,2})시 평균$/.exec(raw);
  if (shadow) return txf(tx, '건물 그림자 · %s · %s–%s시 평균', 'Building shadows · %s · %s:00–%s:00 average', shadow[1], shadow[2], shadow[3]);
  return tx(raw, '') || null;
}

/**
 * 켠 겹의 한 줄 풀이. 🔴 그린 것이 없으면 「빨간 길은 …」 같은 범례를 내지 않는다 — 안 그린 선의 뜻을 읽히면
 *    「이 둘레엔 가파른 길이 없구나」와 「아직 못 받았구나」가 구분되지 않는다. 형편(status)마다 말이 따로 있다.
 */
export function layerNote(kind: MobilityLayerKind, layer: Pick<MobilityLayerResult, 'status' | 'basis' | 'partial'> & { drawn: boolean }, tx: Tx): string | null {
  const slope = kind === 'slope';
  const basis = layerBasisText(layer.basis, tx);
  const withBasis = (text: string) => (basis ? `${text} · ${basis}` : text);
  switch (layer.status) {
    case 'off': return null;
    case 'loading': return slope ? tx('경사 자료를 불러오는 중…', 'Loading slope data…') : tx('그늘 자료를 불러오는 중…', 'Loading shade data…');
    case 'outside': return slope
      ? tx('경사 자료는 해운대·광안리·남포·서면·영도·송정만 있어요. 이 일정의 장소는 그 밖이에요.', 'Slope data covers only Haeundae, Gwangalli, Nampo, Seomyeon, Yeongdo and Songjeong. These stops are outside it.')
      : tx('그늘 자료는 해운대·광안리·남포·서면·영도·송정만 있어요. 이 일정의 장소는 그 밖이에요.', 'Shade data covers only Haeundae, Gwangalli, Nampo, Seomyeon, Yeongdo and Songjeong. These stops are outside it.');
    case 'failed': return slope
      ? tx('경사 자료를 불러오지 못했어요. 잠시 뒤 다시 켜 보세요.', "Couldn't load slope data. Try turning it on again later.")
      : tx('그늘 자료를 불러오지 못했어요. 잠시 뒤 다시 켜 보세요.', "Couldn't load shade data. Try turning it on again later.");
    case 'ready': {
      const partial = layer.partial ? ` · ${tx('일부 지역 자료는 못 불러왔어요', 'some areas could not be loaded')}` : '';
      if (!layer.drawn) {
        return withBasis(slope
          ? tx('장소 둘레에 휠체어 경사로 기준(1:12)보다 가파른 길이 없어요', 'No paths near these stops are steeper than the 1:12 wheelchair ramp standard')
          : tx('장소 둘레에 그늘 자료가 있는 길이 없어요', 'No paths with shade data near these stops')) + partial;
      }
      return withBasis(slope
        ? tx('빨간 길: 경사 8.33% 초과 — 휠체어 경사로 기준(1:12)보다 가팔라요', 'Red: slope over 8.33% — steeper than the 1:12 wheelchair ramp standard')
        : tx('파란 길이 짙을수록 그늘이 많아요', 'Darker blue = more shade')) + partial;
    }
  }
}

export function MobilityLayerToggle({ value, onChange, layer, tx, style }: {
  value: MobilityLayerKind | null;
  onChange: (next: MobilityLayerKind | null) => void;
  /** 켠 겹의 형편과 선 — useMobilityLayer 가 준다. */
  layer: MobilityLayerResult;
  tx: Tx;
  style?: StyleProp<ViewStyle>;
}) {
  const note = value ? layerNote(value, { ...layer, drawn: layer.lines.length > 0 }, tx) : null;
  const chip = (kind: MobilityLayerKind, label: string) => {
    const on = value === kind;
    return (
      <Pressable
        accessibilityRole="switch"
        accessibilityState={{ checked: on }}
        onPress={() => onChange(on ? null : kind)}
        style={({ pressed }) => [styles.chip, on && styles.chipOn, pressed && styles.pressed]}
      >
        <View style={[styles.swatch, { backgroundColor: kind === 'slope' ? color.state.danger : color.state.info }]} />
        <Text variant="caption" weight="bold" color={on ? color.text.onAction : color.text.heading}>{label}</Text>
      </Pressable>
    );
  };
  return (
    <View style={[styles.wrap, style]}>
      <View style={styles.row}>
        {chip('slope', tx('경사', 'Slope'))}
        {chip('shade', tx('그늘', 'Shade'))}
      </View>
      {note ? (
        <Text
          variant="micro"
          color={layer.status === 'failed' ? color.state.danger : color.text.body}
          accessibilityLiveRegion="polite"
          style={styles.legend}
        >{note}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing[1], alignItems: 'flex-start' },
  row: { flexDirection: 'row', gap: spacing[2] },
  chip: {
    minHeight: 32, flexDirection: 'row', alignItems: 'center', gap: spacing[1], paddingHorizontal: spacing[3], borderRadius: radius.full, backgroundColor: color.surface.card,
    shadowColor: color.brand.navy, shadowOpacity: 0.08, shadowRadius: 8, shadowOffset: { width: 0, height: 2 }, elevation: 2,
  },
  chipOn: { backgroundColor: color.action.secondary },
  pressed: { opacity: 0.8 },
  swatch: { width: 10, height: 3, borderRadius: 2 },
  legend: { maxWidth: 280, paddingHorizontal: spacing[2], paddingVertical: spacing[1], borderRadius: radius.md, backgroundColor: color.surface.card },
});
