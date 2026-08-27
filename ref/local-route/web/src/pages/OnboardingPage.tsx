import { RoutePlaceholder } from "../components/RoutePlaceholder";

/**
 * /onboarding
 * 페이지 트리(routeTree.ts)의 "onboarding" 노드에 해당하는 화면.
 * 앱(Expo) 최초 실행 시 위치·알림 권한을 설명·요청하는 화면 전용이며, 웹의 첫 방문 소개는
 * WelcomePage(/welcome)가 담당한다 — 혼동하지 말 것.
 * 구현 시 RoutePlaceholder 를 실제 UI 로 교체하고, routeTree 의 status 를 DONE 으로 바꾼다.
 */
export function OnboardingPage() {
  return <RoutePlaceholder routeId="onboarding" />;
}
