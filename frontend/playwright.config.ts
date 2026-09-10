import { defineConfig } from '@playwright/test';

// 실사용자 흐름 E2E — S15P21E201-775. 기존 tools/smoke-check.mjs("화면에 글자가
// 그려졌는가")와 분리한다 — 이쪽은 실제 백엔드가 있어야 돌고 훨씬 느리다.
export default defineConfig({
  testDir: './tests/e2e',
  timeout: 30_000,
  retries: 0,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    // 🔴 실패했을 때만 남긴다 — 매번 남기면 통과하는 CI 실행마다 아티팩트가 쌓인다.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
