// 06·08·10 화면에 반복되는 단계 표시줄(track + active).
import { StyleSheet, View } from 'react-native';

import { color } from '@/design/tokens';

type ProgressBarProps = {
  /** 0~1. 범위를 벗어나면 그대로 잘라 쓴다(clamp). */
  progress: number;
  /** 10 화면(AI 일정 생성)처럼 굵은 바가 필요할 때만 올린다. */
  thickness?: number;
};

export function ProgressBar({ progress, thickness = 4 }: ProgressBarProps) {
  const clamped = Math.min(1, Math.max(0, progress));
  const rounded = thickness / 2;

  return (
    <View style={[styles.track, { height: thickness, borderRadius: rounded }]}>
      <View
        style={[
          styles.active,
          { width: `${clamped * 100}%`, height: thickness, borderRadius: rounded },
        ]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    width: '100%',
    backgroundColor: color.surface.field,
    overflow: 'hidden',
  },
  active: {
    backgroundColor: color.action.secondary,
  },
});
