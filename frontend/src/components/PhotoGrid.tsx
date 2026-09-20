// 피드 사진 자리 — S15P21E201-1135.
import { useEffect, useMemo, useState } from 'react';
import { Image, Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { color, radius as radii, spacing } from '@/design/tokens';
import { photoGridSlots, planPhotoGrid, type PhotoGridPlan } from '@/social/photoGrid';

export type PhotoGridPhoto = {
  uri: string;
  /** 알고 있으면 넘긴다. 없으면 이 부품이 재 본다. */
  width?: number | null;
  height?: number | null;
};

type Props = {
  photos: PhotoGridPhoto[];
  /** 사진 위에 얹을 것 — 작성 화면의 「빼기」 단추 같은 것. 없으면 안 그린다. */
  renderOverlay?: (index: number) => React.ReactNode;
  onPressPhoto?: (index: number) => void;
  accessibilityLabel?: string;
  style?: StyleProp<ViewStyle>;
  /** 카드 안처럼 좁은 자리에서는 간격을 줄인다. */
  compact?: boolean;
};

// 한 장짜리는 원본 비율을 살리되 양끝을 자른다 — 세로로 너무 길면 글이 화면 밖으로
// 밀리고, 가로로 너무 넓으면 띠처럼 보인다. 트위터가 쓰는 범위와 같다.
const SINGLE_MIN_RATIO = 0.6;
const SINGLE_MAX_RATIO = 1.91;
const SINGLE_FALLBACK_RATIO = 4 / 3;

/** 사진 블록의 절대 최대 높이** */
const MAX_BLOCK_HEIGHT = 520;

/** 배치별 전체 블록의 가로세로. 세로 사진이 들어가는 배치는 조금 더 높다. */
const PLAN_RATIO: Record<PhotoGridPlan, number> = {
  single: SINGLE_FALLBACK_RATIO,
  pair: 16 / 9,
  leftBig: 4 / 3,
  topWide: 16 / 9,
  quad: 4 / 3,
};

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

export function PhotoGrid({ photos, renderOverlay, onPressPhoto, accessibilityLabel, style, compact }: Props) {
  // 크기를 모르는 사진은 여기서 재 본다. 못 재도 그리기를 멈추지 않는다
  // 배치가 기본값으로 갈 뿐이고, 사진은 그대로 보인다.
  const [measured, setMeasured] = useState<Record<string, { width: number; height: number }>>({});
  const unmeasured = photos.filter((p) => typeof p.width !== 'number' || typeof p.height !== 'number').map((p) => p.uri);
  const unmeasuredKey = unmeasured.join('|');

  useEffect(() => {
    if (!unmeasuredKey) return;
    let alive = true;
    for (const uri of unmeasuredKey.split('|')) {
      if (!uri) continue;
      Image.getSize(
        uri,
        (width, height) => { if (alive) setMeasured((prev) => (prev[uri] ? prev : { ...prev, [uri]: { width, height } })); },
        () => {},
      );
    }
    return () => { alive = false; };
  }, [unmeasuredKey]);

  const sized = useMemo(() => photos.map((p) => {
    const found = measured[p.uri];
    return { ...p, width: p.width ?? found?.width ?? null, height: p.height ?? found?.height ?? null };
  }), [measured, photos]);

  const plan = planPhotoGrid(sized);
  if (!plan) return null;

  const shown = sized.slice(0, photoGridSlots(plan));
  const gap = compact ? spacing[1] : spacing[2];

  const ratio = plan === 'single'
    ? (typeof shown[0].width === 'number' && typeof shown[0].height === 'number' && shown[0].height > 0
      ? clamp(shown[0].width / shown[0].height, SINGLE_MIN_RATIO, SINGLE_MAX_RATIO)
      : SINGLE_FALLBACK_RATIO)
    : PLAN_RATIO[plan];

  const tile = (index: number) => {
    const photo = shown[index];
    if (!photo) return null;
    const inner = <>
      <Image source={{ uri: photo.uri }} resizeMode="cover" style={styles.image} accessibilityIgnoresInvertColors />
      {renderOverlay?.(index)}
    </>;
    return onPressPhoto
      ? <Pressable key={photo.uri} accessibilityRole="imagebutton" onPress={() => onPressPhoto(index)} style={styles.tile}>{inner}</Pressable>
      : <View key={photo.uri} style={styles.tile}>{inner}</View>;
  };

  const body = plan === 'single' ? tile(0)
    : plan === 'pair' ? <View style={[styles.row, { gap }]}>{tile(0)}{tile(1)}</View>
    : plan === 'leftBig' ? <View style={[styles.row, { gap }]}>
        {tile(0)}
        <View style={[styles.column, { gap }]}>{tile(1)}{tile(2)}</View>
      </View>
    : plan === 'topWide' ? <View style={[styles.column, { gap }]}>
        {tile(0)}
        <View style={[styles.row, { gap }]}>{tile(1)}{tile(2)}</View>
      </View>
    : <View style={[styles.column, { gap }]}>
        <View style={[styles.row, { gap }]}>{tile(0)}{tile(1)}</View>
        <View style={[styles.row, { gap }]}>{tile(2)}{tile(3)}</View>
      </View>;

  return <View accessibilityLabel={accessibilityLabel} style={[styles.frame, { aspectRatio: ratio, maxHeight: MAX_BLOCK_HEIGHT }, style]}>{body}</View>;
}

const styles = StyleSheet.create({
  frame: { width: '100%', borderRadius: radii.md, overflow: 'hidden' },
  row: { flex: 1, flexDirection: 'row' },
  column: { flex: 1, flexDirection: 'column' },
  tile: { flex: 1, overflow: 'hidden', borderRadius: radii.sm, backgroundColor: color.surface.soft },
  image: { width: '100%', height: '100%' },
});
