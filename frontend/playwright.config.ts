import { defineConfig } from '@playwright/test';

// 실사용자 흐름 E2E — S15P21E201-775. 기존 tools/smoke-check.mjs("화면에 글자가
// 그려졌는가")와 분리한다 — 이쪽은 실제 백엔드가 있어야 돌고 훨씬 느리다.
export default defineConfig({
  testDir: './tests/e2e',
  // 🔴 실측(2026-09-11, 파이프라인 188579) — CI에서는 로컬과 달리 진짜 백엔드가
  //    같은 컨테이너 안에서 매 단계마다 실제 요청을 받는다. 기본 30초로는
  //    로그인부터 최종 확인까지 다 채우기 전에 Playwright의 "테스트 전체" 타임아웃이
  //    먼저 끊었다 — 각 단계 안의 10초·15초짜리 개별 대기(core-journey.spec.ts)는
  //    통과하고도 합계가 30초를 넘겼다. 60초로 늘린다.
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    // 🔴 실패했을 때만 남긴다 — 매번 남기면 통과하는 CI 실행마다 아티팩트가 쌓인다.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
