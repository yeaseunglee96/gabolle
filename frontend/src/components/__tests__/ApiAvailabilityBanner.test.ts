import { shouldHideApiBanner } from '../ApiAvailabilityBanner';

describe('shouldHideApiBanner', () => {
  it.each(['/', '/app-intro', '/age-gate', '/sign-in', '/sign-up', '/permissions'])(
    '서버가 필요 없는 첫 진입 화면 %s에서는 전역 배너를 숨긴다',
    (pathname) => expect(shouldHideApiBanner(pathname)).toBe(true),
  );

  it.each(['/home', '/feed', '/trips', '/explore'])(
    '서버 데이터를 쓰는 화면 %s에서는 연결 안내를 유지한다',
    (pathname) => expect(shouldHideApiBanner(pathname)).toBe(false),
  );
});
