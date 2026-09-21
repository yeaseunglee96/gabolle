// 버스 그림 — 현장 도구 허브(app/field/translate.tsx)와 AI 챗봇의 바로 실행 칸이 같은 그림을 쓴다(S15P21E201-1396).
import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { color } from '@/design/tokens';

export function BusIcon({ size = 24 }: { size?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <Rect x={4} y={3.5} width={16} height={15} rx={3} stroke={color.action.secondary} strokeWidth={1.9} />
      <Path d="M4 10.5h16M7 18.5v2M17 18.5v2" stroke={color.action.secondary} strokeWidth={1.9} strokeLinecap="round" />
      <Circle cx={8} cy={14.5} r={1.2} fill={color.action.secondary} />
      <Circle cx={16} cy={14.5} r={1.2} fill={color.action.secondary} />
    </Svg>
  );
}
