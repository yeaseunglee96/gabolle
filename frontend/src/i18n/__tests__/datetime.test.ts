import { formatClock, formatDayHeading, formatMonthDay, formatNumericDate } from '@/i18n/datetime';
import { toBcp47 } from '@/i18n/languages';

/**
 * 🔴 **날짜를 손으로 조립하면 언어가 둘밖에 안 된다** — S15P21E201-1355.
 *
 * 고치기 전에는 이랬다.
 *
 * <pre>
 *   tx(`${d.getMonth() + 1}월 ${d.getDate()}일 (${weekday})`,
 *      d.toLocaleDateString('en-US', …))   ← 한국어가 아니면 «무조건» en-US
 *   d.toLocaleTimeString('ko-KR', …)       ← 어떤 언어를 골라도 «무조건» 한국식
 * </pre>
 *
 * 일본어를 고른 사람이 「September 20 (Sat)」를 봤다. 이제는 운영체제에 맡긴다.
 *
 * <h2>🔴 이 시험이 «증명하지 못하는» 것</h2>
 *
 * 시험은 Node 위에서 돈다. Node 는 ICU(언어별 표기 자료)를 전부 갖고 있지만
 * **앱은 Hermes 위에서 돈다.** 그래서 여기서 통과해도 기기에서 같은 글자가 나온다는
 * 보장은 없다. 확인은 기기에서 눈으로 해야 한다(iOS 시험 문서 Z장).
 *
 * <p>그래서 시험이 붙드는 것은 «정확한 글자»가 아니라 **언어마다 달라진다는 것**과
 * **Intl 이 없어도 안 터진다는 것**이다. 글자를 통째로 박아 두면 ICU 판이 바뀔 때마다
 * 시험이 깨지고, 그러면 아무도 안 고치고 지워 버린다.
 */
describe('날짜·시각을 고른 언어에 맞춘다', () => {
  const DATE = '2026-09-20';
  const TIME = '2026-09-20T14:30:00';

  it('🔴 언어마다 다른 글자가 나온다 — 하나로 뭉치지 않는다', () => {
    const headings = (['ko', 'en', 'ja', 'zh-Hans', 'zh-Hant'] as const)
      .map((language) => formatDayHeading(DATE, toBcp47(language)));
    for (const heading of headings) expect(heading).toBeTruthy();
    // 한국어와 일본어가 같은 글자면 언어를 안 보고 있다는 뜻이다.
    expect(headings[0]).not.toBe(headings[2]);
    expect(headings[0]).not.toBe(headings[1]);
  });

  it('한국어는 예전 시안대로 「9월 20일」이 들어간다', () => {
    const heading = formatDayHeading(DATE, toBcp47('ko'));
    expect(heading).toContain('9월');
    expect(heading).toContain('20일');
  });

  it('일본어·중국어에 더는 영어 달 이름이 안 섞인다', () => {
    for (const language of ['ja', 'zh-Hans', 'zh-Hant'] as const) {
      expect(formatDayHeading(DATE, toBcp47(language))).not.toMatch(/September|Sep/);
    }
  });

  it('🔴 시각은 24시간으로 고정한다 — 일정표는 눈으로 견주는 것이라 꼴이 섞이면 안 된다', () => {
    for (const language of ['ko', 'en', 'ja', 'zh-Hans', 'zh-Hant'] as const) {
      expect(formatClock(TIME, toBcp47(language))).toBe('14:30');
    }
  });

  it('짧은 날짜와 숫자 날짜도 언어를 본다', () => {
    expect(formatMonthDay(DATE, toBcp47('ko'))).toBeTruthy();
    expect(formatMonthDay(DATE, toBcp47('ja'))).not.toMatch(/Sep/);
    expect(formatNumericDate(DATE, toBcp47('ko'))).toBeTruthy();
  });

  describe('못 읽는 값에 지어내지 않는다', () => {
    it('날짜가 아니면 null 이다 — 부르는 쪽이 「n일차」로 대신한다', () => {
      expect(formatDayHeading('내일쯤', 'ko-KR')).toBeNull();
      expect(formatMonthDay('', 'ko-KR')).toBeNull();
      expect(formatNumericDate('2026-13-45', 'ko-KR')).toBeNull();
    });
    it('시각은 못 읽어도 화면을 비우지 않는다 — 원래 글자에서 시:분만 꺼낸다', () => {
      expect(formatClock('2026-09-20T09:05', 'ko-KR')).toBe('09:05');
      expect(formatClock('아무거나', 'ko-KR')).toBe('아무거나');
    });
  });

  /**
   * 🔴 «없는 것»보다 «조용히 바뀌는 것» — S15P21E201-1399.
   *
   * <p>여기서 재는 것은 «글자»가 아니라 **판정**이다. 「다섯 언어가 잘 나온다」는 시험은
   * 일부러 안 쓴다 — 시험이 도는 Node 는 ICU 를 전부 갖고 있어서 **기기에서 깨져 있어도
   * 초록**이기 때문이다. 그래서 엔진이 「그 자료 없다」고 답하는 상황을 만들어 놓고,
   * 그때 우리가 숫자로 내려가는지만 본다.
   */
  describe('엔진에 그 언어 자료가 없을 때', () => {
    const DATE_OUT = '9. 20.';

    /** supportedLocalesOf 가 「없다」고 답하게 만든다. 실제 기기에서 나는 일을 흉내 낸다. */
    function withUnsupportedLocale(run: () => void) {
      const real = Intl.DateTimeFormat.supportedLocalesOf;
      try {
        Intl.DateTimeFormat.supportedLocalesOf = () => [];
        run();
      }
      finally {
        Intl.DateTimeFormat.supportedLocalesOf = real;
      }
    }

    it('🔴 숫자로 내려간다 — 딴 언어 날짜를 보여주지 않는다', () => {
      withUnsupportedLocale(() => {
        expect(formatDayHeading(DATE, toBcp47('zh-Hant'))).toBe(DATE_OUT);
        expect(formatMonthDay(DATE, toBcp47('zh-Hant'))).toBe(DATE_OUT);
        expect(formatNumericDate(DATE, toBcp47('zh-Hant'))).toBe('9.20');
        expect(formatClock(TIME, toBcp47('zh-Hant'))).toBe('14:30');
      });
    });

    /**
     * 🔴 이 시험이 이 티켓의 근거다. Intl 은 자료가 없어도 **던지지 않는다.** 그래서
     * try/catch 만 두면 대체가 한 번도 안 걸린 채 딴 언어 날짜가 나간다.
     */
    it('🔴 Intl 은 이때 던지지 않는다 — try/catch 만으로는 못 잡았다', () => {
      expect(() => new Intl.DateTimeFormat('xx-YY', { month: 'long', day: 'numeric' })).not.toThrow();
      expect(Intl.DateTimeFormat.supportedLocalesOf(['xx-YY'])).toEqual([]);
    });

    it('supportedLocalesOf 자체가 터지는 판에서도 안 죽는다', () => {
      const real = Intl.DateTimeFormat.supportedLocalesOf;
      try {
        Intl.DateTimeFormat.supportedLocalesOf = () => { throw new Error('없는 판'); };
        expect(formatDayHeading(DATE, 'ko-KR')).toBe(DATE_OUT);
        expect(formatClock(TIME, 'ko-KR')).toBe('14:30');
      }
      finally {
        Intl.DateTimeFormat.supportedLocalesOf = real;
      }
    });
  });

  /**
   * 🔴 Hermes 판이 바뀌어 Intl 이 없어질 수 있다. 날짜 한 줄 때문에 화면이 죽으면 안 된다.
   */
  it('🔴 Intl 이 없어도 안 터진다', () => {
    const real = globalThis.Intl;
    try {
      // @ts-expect-error — 일부러 없앤다
      delete globalThis.Intl;
      expect(formatClock(TIME, 'ko-KR')).toBe('14:30');
      expect(formatDayHeading(DATE, 'ko-KR')).toBeTruthy();
      expect(formatMonthDay(DATE, 'ko-KR')).toBeTruthy();
      expect(formatNumericDate(DATE, 'ko-KR')).toBeTruthy();
    }
    finally {
      globalThis.Intl = real;
    }
  });
});
