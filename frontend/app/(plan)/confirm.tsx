// 확인 화면은 없어졌다 — S15P21E201-1245 (2026-09-18).
//
// 🔴 **시안에 이 화면이 없다.** Claude Design 의 여행 만들기 흐름은
//
//    홈 → /plan(조건 한 페이지) → /plan/generating(생성 + 여행표)
//       → /trips/[id]/recommendations(추천 요약) → /trips/[id]/itinerary(일정)
//
//    이고 사이에 확인 단계가 없다. 옛 4단계 흐름(기본 · 취향 · 제약 · 확인)의 마지막 칸이
//    남아 있던 것이다. 화면 위의 「1 기본 · 2 취향 · 3 제약 · 4 확인」 네 걸음 표시도
//    그 흐름의 것이라 같이 지웠다(`src/plan/PlanStepHeader.tsx`).
//
// 🔴 **이 화면이 하던 일은 사라지지 않았다.** 조건 페이지로 옮겼다:
//      · 일정 생성 요청 보내기                      → app/(plan)/questions.tsx 의 submitPlan
//      · 알레르기·식단 동의(HEALTH_CONSTRAINTS) 재요청 → 같은 파일의 grantHealthConsentAndRetry
//      · 미확인 필수 조건을 물어보기                  → 그 자리에서 조건 모달을 띄운다
//
// 🔴 **주소는 남긴다.** 이 주소로 들어오는 링크가 밖에 있을 수 있고(옛 화면의 「조건 다시
//    확인하기」, 로그인 뒤 복귀 주소 등), 파일을 지우면 그 링크가 「화면을 찾을 수 없어요」가
//    된다. 조건 페이지로 보낸다.
import { Redirect } from 'expo-router';

export default function ConfirmRetired() {
  return <Redirect href="/plan" />;
}
