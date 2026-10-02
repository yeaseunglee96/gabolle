// 여행 화면 머리의 도구 아이콘 — 선 그림 한 벌(UI 캔버스 ④).
//
// 🔴 전에는 같은 크기 회색 알약 다섯 개가 두 줄(3+2)로 쌓여, 일정보다 버튼이 먼저 보였다.
//    한 줄 아이콘으로 줄여 일정이 한 화면 위로 올라오게 한다. 글자 아이콘은 기기 글꼴마다 모양이
//    달라지므로 PencilIcon · NoticeIcon 과 같이 선 그림으로 둔다.
import Svg, { Circle, Path } from 'react-native-svg';

export type TripActionKind = 'map' | 'invite' | 'share' | 'record' | 'weather' | 'money';

export function TripActionIcon({ kind, tint, size = 22 }: { kind: TripActionKind; tint: string; size?: number }) {
  const line = { stroke: tint, strokeWidth: 2, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  return (
    // fill 을 안 주면 react-native-svg 가 검게 채운다 — 선만 있는 그림이라 none.
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      {kind === 'map' ? <>
        <Path d="M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z" {...line} />
        <Path d="M9 4v14M15 6v14" {...line} />
      </> : null}
      {kind === 'invite' ? <>
        <Circle cx={9} cy={8} r={3.5} {...line} />
        <Path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" {...line} />
        <Path d="M19 8v6M16 11h6" {...line} />
      </> : null}
      {kind === 'share' ? <>
        <Path d="M12 3v12" {...line} />
        <Path d="M8 7l4-4 4 4" {...line} />
        <Path d="M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7" {...line} />
      </> : null}
      {kind === 'record' ? <>
        {/* 끝이 왼쪽 아래인 연필 — PencilIcon 과 같은 그림 */}
        <Path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" {...line} />
        <Path d="M15 5l4 4" {...line} />
      </> : null}
      {kind === 'weather' ? <>
        <Circle cx={8} cy={8} r={3} {...line} />
        <Path d="M8 2v1.5M2 8h1.5M3.8 3.8l1 1M12.2 3.8l-1 1" {...line} />
        <Path d="M9 20h9a3.5 3.5 0 0 0 0-7 5 5 0 0 0-9.6 1.2A3 3 0 0 0 9 20z" {...line} />
      </> : null}
      {kind === 'money' ? <>
        {/* 지갑 — 여행 돈(S15P21E201-1935). 동전(원) 그림은 나라마다 읽는 법이 달라 지갑으로 둔다 */}
        <Path d="M4 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a1 1 0 0 1-1-1V7z" {...line} />
        <Path d="M4 7l11-3v3" {...line} />
        <Path d="M16 13h4" {...line} />
      </> : null}
    </Svg>
  );
}
