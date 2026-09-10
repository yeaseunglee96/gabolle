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
};
