// 여행 하나에 딸린 화면들 — /[id]/map · /[id]/prepare … (S15P21E201-317)
//
// 비회원도 자기 여행을 만들므로, 그 여행의 «보기» 화면 셋은 연다. 지도는 일정의 장소만, 기념품은 주변 장소만,
// 여행 기록 견본은 고정 글만 읽어 사람에게 붙은 자원을 안 부른다.
// 나머지는 그대로 로그인을 요구한다 — 동행(collaborate)·공유(share)·돈(money)은 다른 사람과 나누는 것이고,
// 준비(prepare)는 공유 링크와 기록을 함께 부른다.
//
// ProtectedRoute 의 publicPaths 는 주소를 글자 그대로 맞춰서 /[id]/… 같은 자리를 못 연다. 그래서 끝 마디로 가른다.
import { Slot, usePathname } from 'expo-router';

import { ProtectedRoute } from '@/auth/ProtectedRoute';

const GUEST_TRIP_VIEWS = new Set(['map', 'souvenirs', 'log']);

export default function TripLayout() {
  const pathname = usePathname();
  const view = pathname.split('/').filter(Boolean).pop() ?? '';
  return GUEST_TRIP_VIEWS.has(view) ? <Slot /> : <ProtectedRoute />;
}
