// 공유·초대 링크가 브라우저가 아니라 앱으로 열리려면 안드로이드 앱 링크(intentFilters)에 그 경로가 있어야 한다
// (S15P21E201-1936). /oauth 만 있어서 /s/… 링크가 삼성 인터넷으로 열렸다.
const appJson = require('../../../app.json');

describe('안드로이드 앱 링크 경로', () => {
  const data: { host: string; pathPrefix: string }[] = appJson.expo.android.intentFilters[0].data;
  it.each(['/oauth', '/s/', '/invite/', '/story-invite/'])('%s 를 앱이 받는다', (p) => {
    expect(data.some((d) => d.host === 'j15e201.p.ssafy.io' && d.pathPrefix === p)).toBe(true);
  });
});
