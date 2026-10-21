// 웹 배포 묶음의 첫 HTML 언어를 앱 기본 언어(한국어)로 — S15P21E201-1980.
//
// 🔴 `expo export --platform web` 이 만드는 dist/index.html 은 `<html lang="en">` 이다. 앱이 뜬 뒤
//    app/_layout.tsx 의 HtmlLangSync 가 고른 언어로 바꾸지만, 크롬은 그 전에 첫 HTML 을 읽고
//    한국어 화면을 「영어 → 한국어로 번역할까요?」라고 물었다(10/3 탭 점검). 그래서 처음부터 ko 로 굽는다.
const fs = require('node:fs');
const path = require('node:path');

function setHtmlLang(html, lang = 'ko') {
  if (/<html\b[^>]*\blang=/i.test(html)) return html.replace(/(<html\b[^>]*\blang=)(["'])[^"']*\2/i, `$1$2${lang}$2`);
  return html.replace(/<html\b/i, `<html lang="${lang}"`);
}

module.exports = { setHtmlLang };

if (require.main === module) {
  const file = path.resolve(process.argv[2] || 'dist/index.html');
  const before = fs.readFileSync(file, 'utf8');
  const after = setHtmlLang(before);
  fs.writeFileSync(file, after);
  console.log(`set-html-lang: ${file} → lang="ko"${before === after ? ' (이미 ko)' : ''}`);
}
