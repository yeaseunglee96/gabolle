// 「쓰기」 연필 — 끝이 **왼쪽 아래**다(S15P21E201-1531).
//
// 예전에는 글자 「✎」(U+270E, LOWER RIGHT PENCIL)를 썼다. 이름 그대로 끝이 오른쪽 아래라,
// 쓰기·편집 아이콘의 흔한 방향(끝이 왼쪽 아래)과 좌우가 반대였다(팀원 실사용 2026-09-23).
// 글자 아이콘은 기기 글꼴마다 모양도 달라진다 — 그래서 선 그림으로 한 곳에 둔다.
import Svg, { Path } from 'react-native-svg';

export function PencilIcon({ tint, size = 22 }: { tint: string; size?: number }) {
  return (
    // fill 을 안 주면 react-native-svg 가 검게 채운다 — 선만 있는 그림이라 none 이 필요하다.
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <Path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" stroke={tint} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
      <Path d="M15 5l4 4" stroke={tint} strokeWidth={2} strokeLinecap="round" />
    </Svg>
  );
}
