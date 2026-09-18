// S15P21E201-774 — jest-expo 프리셋 위에 tsconfig의 "@/" 경로 별칭만 더한다.
// Metro(번들러)는 tsconfig paths를 그대로 읽지만 Jest는 안 읽어서, 여기서 한 번 더
// moduleNameMapper로 맞춰 줘야 "@/..." import가 테스트에서도 풀린다.
// transformIgnorePatterns 등 나머지는 jest-expo 프리셋의 기본값을 그대로 쓴다 —
// 여기서 다시 적으면 프리셋이 최신 SDK에 맞춰 올린 값과 따로 놀게 된다.
module.exports = {
  preset: 'jest-expo',
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
  },
  setupFiles: ['<rootDir>/jest.setup.js'],
  // 🔴 S15P21E201-775 — tests/e2e/*.spec.ts는 @playwright/test의 test()를 쓴다(jest의
  //    test()가 아니다). 기본 testMatch에 걸려 jest가 이 파일을 집어 들면 "test is not
  //    a function" 류로 죽는다 — Playwright 전용 테스트 러너(npx playwright test)로만 돈다.
  // Jest가 Windows에서는 검사 경로를 역슬래시로 넘기므로 슬래시 하나만 적으면 제외가
  // 적용되지 않는다. 두 운영체제 경로 구분자를 모두 받는 정규식으로 고정한다.
  testPathIgnorePatterns: ['[\\\\/]node_modules[\\\\/]', '[\\\\/]tests[\\\\/]e2e[\\\\/]'],
};
