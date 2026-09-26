// 아래에서 올라오는 창의 아래 여백 — S15P21E201-1765.
//
// 🔴 창은 Modal 이라 앱 화면(Screen)의 안전영역 처리 바깥에 뜬다. 안드로이드는 화면을 끝까지 쓰므로
//    (edge-to-edge) 3버튼 탐색 막대가 창 위에 겹친다. 여백을 고정값으로만 두면 맨 아래 버튼이 막대 뒤로
//    반쯤 들어갔다(Play 35 실기기 — 「이 여행에 이름 붙이기」의 취소/날짜로 둘게요).
//    기기의 아래 안전영역만큼 더한다. 막대가 없는 기기(제스처 탐색·웹)는 0 이라 지금과 같다.
import { useContext } from 'react';
import { SafeAreaInsetsContext } from 'react-native-safe-area-context';

export function sheetBottomPadding(base: number, bottomInset: number): number {
  return base + Math.max(0, bottomInset || 0);
}

/** 제공자(SafeAreaProvider)가 없으면 0 으로 본다 — useSafeAreaInsets 는 그때 오류를 내서, 제공자 없이 그리는 시험과 부품이 깨진다. */
// 시험이 이 모듈을 가짜로 바꾸면 SafeAreaInsetsContext 가 없을 수 있다. 모듈이 읽힐 때 한 번 정해지는 값이라
// 아래의 조건부 useContext 는 그리는 사이에 바뀌지 않는다(훅 순서가 흔들리지 않는다).
const INSETS_CONTEXT = SafeAreaInsetsContext ?? null;

export function useSheetBottomPadding(base: number): number {
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const insets = INSETS_CONTEXT ? useContext(INSETS_CONTEXT) : null;
  return sheetBottomPadding(base, insets?.bottom ?? 0);
}
