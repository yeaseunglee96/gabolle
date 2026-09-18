// 일본어·중국어 화면에 한국어가 새어 나오던 것 — S15P21E201-1296.
//
// 🔴 실기기(SM-G973N, 일본어)에서 여행 만들기 화면에 **세 나라 말이 한꺼번에** 나왔다.
//
//      제목      Tell us about your trip   (영어 — 번역이 없을 때의 정해진 대체)
//      질문 카드  여행 범위 / 해운대 …       (한국어 — 이게 버그다)
//      단추      次へ                      (일본어 — 번역표에 있는 것)
//
// 원인은 「영어가 아니면 한국어」로 가른 두 줄이었다.
//
//      const ko = language !== 'en';
//
// 일본어·중국어는 `uiTranslated: false` 라, 번역표에 없으면 **영어**로 떨어지기로
// 정해져 있다(resolveTextLanguage). 위처럼 가르면 그 규칙을 건너뛰고 한국어가 나온다.
//
// 언어가 늘 때마다 같은 실수를 다시 할 수 있어 기계가 지킨다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readdirSync, readFileSync } = require('fs');
const { join, relative, sep } = require('path');

const ROOT = join(__dirname, '..', '..', '..');

function sourceFiles(dir: string): string[] {
  return (readdirSync(dir, { withFileTypes: true }) as any[]).flatMap((entry: any) => {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === '__tests__') return [];
      return sourceFiles(full);
    }
    const ext = entry.name.endsWith('.ts') || entry.name.endsWith('.tsx');
    return ext && !entry.name.includes('.test.') ? [full] : [];
  });
}

const NEWLINE = new RegExp(String.fromCharCode(92) + 'r?' + String.fromCharCode(92) + 'n');

/**
 * `getApiLanguage()` 는 이미 `'ko' | 'en'` 으로 좁혀진 값이라
 * (`setApiLanguage(resolveTextLanguage(...))`) `!== 'en'` 이 `=== 'ko'` 와 같다.
 * 화면 언어(`LanguageCode`, 다섯 가지)와 헷갈리지 않도록 여기만 빼 둔다.
 */
const NARROWED_TO_KO_OR_EN = 'getApiLanguage()';

const offenders: string[] = [];
const scanned: string[] = [];

for (const file of [...sourceFiles(join(ROOT, 'app')), ...sourceFiles(join(ROOT, 'src'))]) {
  scanned.push(file);
  const lines = (readFileSync(file, 'utf8') as string).split(NEWLINE);
  lines.forEach((line: string, index: number) => {
    if (!line.includes(`!== 'en'`)) return;
    if (line.includes(NARROWED_TO_KO_OR_EN)) return;
    offenders.push(`${(relative(ROOT, file) as string).split(sep).join('/')}:${index + 1}`);
  });
}

describe('한국어는 한국어 사용자에게만 보여야 한다', () => {
  it("🔴 「영어가 아니면 한국어」로 가르는 곳이 없다 — resolveTextLanguage() === 'ko' 를 쓴다", () => {
    expect(offenders).toEqual([]);
  });

  it('🔴 이 검사가 실제로 파일을 읽고 있다 — 폴더가 바뀌어도 조용히 통과하지 않는다', () => {
    expect(scanned.length).toBeGreaterThan(100);
  });
});
