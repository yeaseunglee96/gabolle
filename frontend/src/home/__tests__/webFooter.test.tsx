// 웹 맨 아래 안내(S15P21E201-1930) — 링크가 실제로 있는 화면으로 가는가. 없는 화면으로 가는 링크는
// 「눌렀더니 찾을 수 없음」이라 맨 아래 안내 전체를 못 믿게 만든다. 화면을 옮기거나 지우면 여기서 걸린다.
import { APP_STORE_URL, FOOTER_GROUPS, PLAY_STORE_URL } from '../WebFooter';

declare const require: (id: string) => any;
declare const __dirname: string;
const { existsSync } = require('fs');
const { join } = require('path');

const APP_DIR = join(__dirname, '..', '..', '..', 'app');

function screenFileExists(href: string): boolean {
  const path: string = href.replace(/^\//, '');
  return [`${path}.tsx`, join(path, 'index.tsx'), join('(tabs)', `${path}.tsx`), join('(tabs)', path, 'index.tsx')]
    .some((candidate) => existsSync(join(APP_DIR, candidate)));
}

describe('WebFooter', () => {
  it.each(FOOTER_GROUPS.flatMap((group) => group.links.filter((link) => link.href).map((link) => [link.ko, String(link.href)])))('%s → %s 화면이 있다', (_label, href) => {
    expect(screenFileExists(href)).toBe(true);
  });

  it('🔴 맨 아래에는 겹치는 길을 두지 않는다 — 문의는 도움말·문의 안, 축제는 로컬 탐색, 계정 삭제는 마이페이지(2026-10-17)', () => {
    const labels = FOOTER_GROUPS.flatMap((group) => group.links).map((link) => link.ko);
    expect(labels).not.toContain('문의하기');
    expect(labels).not.toContain('부산 축제');
    expect(labels).not.toContain('계정 삭제');
    expect(labels).toContain('도움말·문의');
  });

  it('앱 받기는 심사를 통과한 App Store 주소로 간다 — 포스터·팸플릿 QR 과 같다', () => {
    expect(APP_STORE_URL).toBe('https://apps.apple.com/app/id6811252919');
  });

  it('🔴 Google Play 받기는 프로덕션 심사를 통과한 앱 주소로 간다 — app.json 의 안드로이드 패키지와 같다(S15P21E201-2037)', () => {
    const appJson = require(join(__dirname, '..', '..', '..', 'app.json'));
    expect(PLAY_STORE_URL).toBe(`https://play.google.com/store/apps/details?id=${appJson.expo.android.package}`);
    expect(PLAY_STORE_URL).toBe('https://play.google.com/store/apps/details?id=com.gabolle.app');
  });
});
