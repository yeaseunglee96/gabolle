import { Slot } from 'expo-router';

// 여행 입력 draft는 게스트도 작성할 수 있다. 서버 저장 직전에만 인증을 요구한다.
//
// 🔴 넓은 화면 상단 바는 여기서 붙이지 않는다 (S15P21E201-994) — 앱 뼈대(app/_layout.tsx)가
// 모든 화면에 한 번만 붙인다. 여기서 또 붙이면 두 벌이 그려진다.
export default function PlanLayout() { return <Slot />; }
