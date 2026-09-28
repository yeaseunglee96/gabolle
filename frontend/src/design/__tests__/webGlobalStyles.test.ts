// 웹에서 일본어·중국어 문장이 아무 데서나 끊기던 것 — S15P21E201-1525.
//
// 🔴 Text 가 모든 글에 주는 word-break: keep-all 은 띄어쓰기에서만 끊으라는 뜻이라, 띄어쓰기가 없는
//    일본어·중국어 문장은 통째로 한 낱말이 됐다. 그러면 overflow-wrap: anywhere 가 줄 끝에서 아무 데나
//    잘라 「。」가 줄 머리에 왔다. 전역 CSS 가 그 두 언어에서만 keep-all 을 푸는지 본다.
import { Platform } from 'react-native';

import { installWebGlobalStyles } from '@/design/webGlobalStyles';

function fakeDocument() {
  const appended: { id: string; textContent: string }[] = [];
  const doc = {
    getElementById: (id: string) => appended.find((el) => el.id === id) ?? null,
    createElement: () => ({ id: '', textContent: '' }),
    head: { appendChild: (el: { id: string; textContent: string }) => appended.push(el) },
  };
  return { doc: doc as unknown as Document, appended };
}

function installedCss(): string {
  const original = Platform.OS;
  Object.defineProperty(Platform, 'OS', { value: 'web', configurable: true });
  try {
    const { doc, appended } = fakeDocument();
    installWebGlobalStyles(doc);
    return appended[0]?.textContent ?? '';
  } finally {
    Object.defineProperty(Platform, 'OS', { value: original, configurable: true });
  }
}

describe('웹 줄바꿈 — 일본어·중국어', () => {
  it('🔴 일본어·중국어 화면에서만 keep-all 을 푼다', () => {
    const css = installedCss();
    expect(css).toContain('html:lang(ja) [style*="word-break: keep-all"]');
    expect(css).toContain('html:lang(zh) [style*="word-break: keep-all"]');
    // 인라인 style 로 붙은 keep-all 을 덮으려면 !important 가 있어야 한다
    expect(css).toMatch(/word-break: normal !important;/);
    expect(css).toMatch(/line-break: strict;/);
  });

  it('한국어는 건드리지 않는다 — 낱말 중간에서 끊기던 것(S15P21E201-1360)이 되살아나면 안 된다', () => {
    expect(installedCss()).not.toMatch(/:lang\(ko\)/);
  });
});
