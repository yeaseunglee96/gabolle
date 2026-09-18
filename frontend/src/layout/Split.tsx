// 폴드8 펼침(tablet) 때 master 좌 + detail 우를 나란히, 접힘(phone) 때는 위→아래
// 순서로 보여준다. 명세(§7 반응형)의 유일한 근거 문장 — "웹은 2열을 지원하고 좁은
// 폭에서는 순차 배치한다" — 를 그대로 따른 것이다.
//
// 🔴 phone 에서 detail 을 아예 숨기고 항목을 눌러야 열리는 방식(스택 push)도 고려했지만
// 고르지 않았다. 그러면 접었다 펼 때 detail 이 "없다가 생기는" 것처럼 보이고, 12 지도·동선의
// 실측 비교 카드처럼 반드시 보여야 하는 내용이 화면 폭에 따라 사라지는 경로가 생긴다.
// master/detail 을 항상 같이 렌더링하고 배치(row ↔ column)만 바꾸면 그 경로 자체가 없다 —
// 언마운트가 없으니 상태(state)도 저절로 유지된다. 이 판단은 사람 검토가 필요하다(보고서 참고).
import { StyleSheet, View } from 'react-native';
import type { ReactNode } from 'react';

import { color, spacing } from '@/design/tokens';
import { useLayout } from '@/layout/useLayout';

type SplitProps = {
  master: ReactNode;
  detail: ReactNode;
};

// master 고정 폭(dp). 비율(%)이 아니라 고정값을 쓴 이유 —
// 폴드8(717dp)보다 넓은 화면(더 큰 태블릿·웹)에서 비율로 나누면 목록(master)까지 같이
// 늘어나 줄 하나에 여백만 커진다. 넓어질수록 늘어나야 하는 건 detail(지도·요약·편집 폼)
// 쪽이라 master 는 고정폭, detail 은 flex:1 로 나머지를 채운다.
// 320 은 실측이 없어 고른 어림값이다 — Screen.tsx 의 폰 콘텐츠 최대폭(480 - 좌우 gutter
// 24*2 = 432)보다 좁게 잡아 목록 카드 한 줄(시간·제목)이 줄바꿈되지 않게 했다.
const MASTER_WIDTH = 320;

export function Split({ master, detail }: SplitProps) {
  const { kind } = useLayout();

  if (kind === 'tablet') {
    return (
      <View style={styles.row}>
        <View style={styles.master}>{master}</View>
        <View style={styles.detail}>{detail}</View>
      </View>
    );
  }

  return (
    <View style={styles.column}>
      {master}
      <View style={styles.detailStacked}>{detail}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
  },
  column: {
    flexDirection: 'column',
  },
  master: {
    width: MASTER_WIDTH,
    paddingRight: spacing[4],
    borderRightWidth: 1,
    borderRightColor: color.surface.field,
  },
  detail: {
    flex: 1,
    paddingLeft: spacing[6],
  },
  detailStacked: {
    marginTop: spacing[6],
  },
});
