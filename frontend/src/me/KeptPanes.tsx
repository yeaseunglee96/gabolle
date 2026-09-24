// 한 자리에서 갈아 끼우는 판들 — 한 번 만든 판은 붙여 두고 보이는 것만 바꾼다 (S15P21E201-1603).
//
// 🔴 마이페이지 「기록 | 설정」은 누를 때마다 보이던 판을 지우고 다른 판을 새로 만들었다. 기록 판은 카드 24장과
//    달력이라 그 일이 길게 걸리고, 그동안 화면이 멈춰 0.32초짜리 전환 애니메이션이 통째로 묻혔다.
//    한 번 만든 판은 남겨 두면 다시 누를 때는 보이기만 바꾸면 된다.
import { useEffect, useState, type ReactNode } from 'react';
import { InteractionManager, StyleSheet, View } from 'react-native';

export function KeptPanes<K extends string>({ active, panes }: { active: K; panes: Record<K, ReactNode> }) {
  // 첫 화면에는 보이는 판만 만든다 — 첫 화면을 무겁게 하지 않는다.
  const [opened, setOpened] = useState<K[]>([active]);
  if (!opened.includes(active)) setOpened([...opened, active]);
  // 🔴 한가해지면 나머지 판도 미리 만든다. 안 그러면 처음 누를 때 그 판을 새로 만드느라 첫 전환만 여전히 끊긴다.
  useEffect(() => {
    const task = InteractionManager.runAfterInteractions(() => setOpened(Object.keys(panes) as K[]));
    return () => task.cancel();
    // 판의 이름은 바뀌지 않는다 — 처음 한 번만 건다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return <>
    {(Object.keys(panes) as K[]).map((key) => (
      // 숨긴 판은 display:none — 자리도 안 차지하고 낭독기에도 안 읽힌다.
      <View key={key} testID={`pane-${key}`} style={key === active ? null : styles.hidden}>
        {opened.includes(key) ? panes[key] : null}
      </View>
    ))}
  </>;
}

const styles = StyleSheet.create({ hidden: { display: 'none' } });
