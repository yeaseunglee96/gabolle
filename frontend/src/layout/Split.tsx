// 폴드8 펼침(tablet) 때 무엇을 보여줄지는 아직 정해지지 않았다(열린 결정).
// 그래서 지금은 분기 자리만 만들어 두고 tablet 에서도 phone 레이아웃을 그대로 쓴다 —
// 나중에 2단 레이아웃이 정해지면 tablet 분기 안쪽만 채우면 되게 하는 것이 목적이다.
import { StyleSheet, View } from 'react-native';
import type { ReactNode } from 'react';

import { useLayout } from '@/layout/useLayout';

type SplitProps = {
  master: ReactNode;
  detail: ReactNode;
};

export function Split({ master }: SplitProps) {
  const { kind } = useLayout();

  if (kind === 'tablet') {
    // TODO(폴드8): 펼침 2단 레이아웃 미정 — master/detail 을 나란히 놓는 구성이 정해지면 여기를 채운다.
    return <View style={styles.container}>{master}</View>;
  }

  return <View style={styles.container}>{master}</View>;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
