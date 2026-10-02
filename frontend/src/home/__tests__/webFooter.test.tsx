// 웹 맨 아래 안내(S15P21E201-1930) — 링크가 실제로 있는 화면으로 가는가. 없는 화면으로 가는 링크는
// 「눌렀더니 찾을 수 없음」이라 맨 아래 안내 전체를 못 믿게 만든다. 화면을 옮기거나 지우면 여기서 걸린다.
import { APP_STORE_URL, FOOTER_GROUPS } from '../WebFooter';

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
  it.each(FOOTER_GROUPS.flatMap((group) => group.links.map((link) => [link.ko, String(link.href)])))('%s → %s 화면이 있다', (_label, href) => {
    expect(screenFileExists(href)).toBe(true);
  });

  it('앱 받기는 심사를 통과한 App Store 주소로 간다 — 포스터·팸플릿 QR 과 같다', () => {
    expect(APP_STORE_URL).toBe('https://apps.apple.com/app/id6811252919');
  });
});
