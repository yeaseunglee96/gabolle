// 알림 종류 아이콘 — 선 그림 한 벌(UI 캔버스 ⑦).
//
// 🔴 전에는 글자 둘뿐이었다(「✦」 만들어짐 · 「⇄」 그 밖 전부). 고정·빼기·더하기·순서·되돌리기가 다 「⇄」라
//    무엇이 바뀌었는지 아이콘으로는 못 읽었다. 글자 아이콘은 기기 글꼴마다 모양도 달라진다 — PencilIcon 과 같은 이유로 선 그림.
import Svg, { Circle, Path } from 'react-native-svg';

export type NoticeIconKind = 'created' | 'lock' | 'remove' | 'add' | 'reorder' | 'replan' | 'revert' | 'change';

export function NoticeIcon({ kind, tint, size = 20 }: { kind: NoticeIconKind; tint: string; size?: number }) {
  const line = { stroke: tint, strokeWidth: 2, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  return (
    // fill 을 안 주면 react-native-svg 가 검게 채운다 — 선만 있는 그림이라 none.
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      {kind === 'created' ? <>
        {/* 지도 — 일정이 새로 만들어졌다 */}
        <Path d="M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z" {...line} />
        <Path d="M9 4v14M15 6v14" {...line} />
      </> : null}
      {kind === 'lock' ? <>
        {/* 핀 — 일정 화면의 「고정」 */}
        <Path d="M12 17v4" {...line} />
        <Path d="M8 3h8l-1.5 5 3 4H6.5l3-4z" {...line} />
      </> : null}
      {kind === 'remove' ? <>
        <Circle cx={12} cy={12} r={8} {...line} />
        <Path d="M8.5 12h7" {...line} />
      </> : null}
      {kind === 'add' ? <>
        <Circle cx={12} cy={12} r={8} {...line} />
        <Path d="M12 8.5v7M8.5 12h7" {...line} />
      </> : null}
      {kind === 'reorder' ? <>
        {/* 위아래 화살표 — 순서 */}
        <Path d="M8 4v16M4.5 7.5 8 4l3.5 3.5" {...line} />
        <Path d="M16 20V4M12.5 16.5 16 20l3.5-3.5" {...line} />
      </> : null}
      {kind === 'replan' ? <>
        {/* 요술봉 — 다시 계획 */}
        <Path d="M5 19 16 8" {...line} />
        <Path d="M15 4v2M19 8h2M18 5l1.5-1.5M20 11l-1-1" {...line} />
      </> : null}
      {kind === 'revert' ? <>
        <Path d="M8 7 4 11l4 4" {...line} />
        <Path d="M4 11h9a5 5 0 0 1 0 10h-3" {...line} />
      </> : null}
      {kind === 'change' ? <>
        {/* 연필 — 여러 가지를 고쳤다. 전에는 빈 네모(Rect)만 그려 폴드에서 「□」로 보였다(S15P21E201-1985) */}
        <Path d="M4 20h4L19 9l-4-4L4 16z" {...line} />
        <Path d="M13.5 6.5l4 4" {...line} />
      </> : null}
    </Svg>
  );
}
