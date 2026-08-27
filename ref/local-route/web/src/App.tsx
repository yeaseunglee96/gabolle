/**
 * @deprecated 라우터 도입(2026-08) 이후 화면 전환은 routes/AppRouter.tsx 가 담당한다.
 *
 * 이전에는 이 파일이 useState 로 form / loading / result 화면을 직접 갈아끼웠다.
 * 그 구조에서는 URL 이 화면을 나타내지 못해 뒤로가기·새로고침·딥링크가 동작하지 않았고,
 * 팀원 4명이 화면 단위로 나눠 작업하기도 어려웠다.
 *
 * 현재 구조:
 *   routes/routeTree.ts  — 페이지 트리 정의 (단일 진실 공급원)
 *   routes/AppRouter.tsx — 위 트리를 react-router 라우팅으로 구성
 *   routes/AppShell.tsx  — 전역 조회와 백엔드 미연결 배너
 *   pages/**             — 라우트별 화면
 */
export { AppRouter as default } from "./routes/AppRouter";
