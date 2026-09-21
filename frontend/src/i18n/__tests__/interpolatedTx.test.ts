import { fillNumbers, numericShape, pickLanguage } from '@/i18n';

/**
 * 🔴 **숫자가 끼는 문구가 일본어·중국어에서 영어로 떨어지던 것** — S15P21E201-1344.
 *
 * 번역표는 한국어 원문을 열쇠로 쓴다. 그런데 문구에 값이 끼면 열쇠가 실행할 때마다
 * 달라져서 **표에 넣을 수가 없다.**
 *
 * <pre>
 *   tx(`사진 ${images.length}/3`, …)  →  「사진 1/3」 「사진 2/3」 「사진 3/3」
 * </pre>
 *
 * 「사진 1/3」을 표에 넣어 봐야 두 장째부터 또 샌다. 이런 자리가 앱에 **208곳**이고
 * (2026-09-20 실측) 전부 영어로 떨어져 있었다. 그래서 숫자를 뺀 «모양»으로 찾는다.
 *
 * <p>🔴 이 시험이 지키는 것은 세 가지다.
 * <ol>
 *   <li>모양으로 찾아서 <b>숫자를 도로 끼운다</b> — 「사진 2/3」이 「写真 2/3」이 된다</li>
 *   <li>글자가 똑같은 줄이 표에 있으면 <b>그것이 언제나 이긴다</b> — 모양 찾기는
 *       지금까지 영어로 떨어지던 자리만 받는다</li>
 *   <li><b>숫자 순서가 안 뒤집힌다</b> — 번역에서 두 숫자의 앞뒤를 바꾸면 조용히
 *       틀린 값이 나온다. 표를 고칠 때 가장 내기 쉬운 실수라 여기서 잡는다</li>
 * </ol>
 */
describe('숫자가 끼는 문구도 번역표에서 찾는다', () => {
  const en = '(영어 문구)';

  describe('모양으로 찾아 숫자를 도로 끼운다', () => {
    const cases: ReadonlyArray<[string, string, string]> = [
      ['사진 2/3', 'ja', '写真 2/3'],
      ['사진 3/3', 'ja', '写真 3/3'],
      ['사진 1/3', 'zh-Hans', '照片 1/3'],
      ['질문 7 / 10', 'ja', '質問 7 / 10'],
      ['3일 · 2명', 'ja', '3日 · 2名'],
      ['5일차', 'ja', '5日目'],
      ['2일차만 보기', 'zh-Hant', '只看第2天'],
      ['📍 12곳', 'ja', '📍 12か所'],
      ['좌표 있는 기록 4개', 'zh-Hans', '有坐标的记录 4条'],
      ['8정류장 전', 'ja', '8停留所前'],
      ['강수확률 30%', 'ja', '降水確率 30%'],
      ['사진이 흐리거나 잘려서 못 읽은 줄이 1개 있어요.', 'ja', '写真がぼやけていたり切れていて、読み取れなかった行が 1 行あります。'],
    ];
    for (const [ko, language, expected] of cases) {
      it(`${language}: 「${ko}」 → 「${expected}」`, () => {
        expect(pickLanguage(language as never, { ko, en })).toBe(expected);
      });
    }
  });

  it('🔴 숫자 순서가 안 뒤집힌다 — 「3개 중 1번째」의 3과 1이 자리를 안 바꾼다', () => {
    // 한국어는 「전체 · 몇 번째」 순이다. 번역도 그 순서여야 한다.
    expect(pickLanguage('ja' as never, { ko: '3개 중 1번째', en })).toBe('3件中 1番目');
    expect(pickLanguage('zh-Hans' as never, { ko: '3개 중 1번째', en })).toBe('共 3 项中的第 1 项');
    // 뒤집혔다면 「1件中 3番目」이 나왔을 것이다.
    expect(pickLanguage('ja' as never, { ko: '3개 중 1번째', en })).not.toBe('1件中 3番目');
  });

  it('천 단위 쉼표가 있어도 한 덩어리로 본다', () => {
    expect(numericShape('반경을 1,000m로 넓혔습니다')).toEqual({
      shape: '반경을 %dm로 넓혔습니다',
      numbers: ['1,000'],
    });
  });

  it('숫자가 없으면 모양을 만들지 않는다 — 쓸데없이 표를 두 번 뒤지지 않는다', () => {
    expect(numericShape('접기')).toBeNull();
  });

  it('🔴 글자가 똑같은 줄이 표에 있으면 그것이 이긴다', () => {
    // 「접기」는 숫자가 없으니 원문 그대로 찾힌다.
    expect(pickLanguage('ja' as never, { ko: '접기', en })).toBe('閉じる');
  });

  it('표에 없는 모양은 예전처럼 영어로 떨어진다 — 조용히 한국어가 새지 않는다', () => {
    expect(pickLanguage('ja' as never, { ko: '이런 문구는 표에 없어요 42개', en })).toBe(en);
  });

  it('한국어·영어는 이 길을 타지 않는다', () => {
    expect(pickLanguage('ko' as never, { ko: '사진 2/3', en })).toBe('사진 2/3');
    expect(pickLanguage('en' as never, { ko: '사진 2/3', en })).toBe(en);
  });

  describe('fillNumbers 는 자리와 값이 안 맞아도 안 터진다', () => {
    it('값이 모자라면 그 자리는 %d 로 남긴다 — 엉뚱한 숫자를 지어내지 않는다', () => {
      expect(fillNumbers('%d/%d', ['2'])).toBe('2/%d');
    });
    it('값이 남으면 버린다', () => {
      expect(fillNumbers('%d장', ['2', '3'])).toBe('2장');
    });
  });
});
