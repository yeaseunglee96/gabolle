import { Slot } from 'expo-router';

// 넓은 화면 상단 바는 여기서 붙이지 않는다 — 앱 뼈대(app/_layout.tsx)가
// 모든 화면에 한 번만 붙인다.
export default function PublicPlanLayout() { return <Slot />; }
