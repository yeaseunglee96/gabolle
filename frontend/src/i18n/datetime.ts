/**
 * 🔴 **날짜·시각을 손으로 조립하지 않는다** — S15P21E201-1355.
 *
 * 화면마다 날짜를 직접 만들고 있었고, 그 자리마다 언어가 «둘»뿐이었다.
 *
 * <pre>
 *   tx(`${d.getMonth() + 1}월 ${d.getDate()}일 (${weekday})`,
 *      d.toLocaleDateString('en-US', { month: 'long', day: 'numeric', weekday: 'short' }))
 * </pre>
 *
 * 한국어면 손으로 만든 글자, 그 밖이면 **무조건 `en-US`**. 그래서 일본어·중국어
 * 사용자는 「September 20 (Sat)」를 본다 — 자기 언어로 고른 화면 안에서.
 * 시각은 더 나빴다. {@code toLocaleTimeString('ko-KR', …)} 이 박혀 있어서
 * **어떤 언어를 골라도 한국식**으로 나왔다.
 *
 * <p>고치는 방법은 만들지 않는 것이다. 운영체제가 언어마다 올바른 꼴을 이미 안다.
 * 우리는 «무엇을 보여줄지»(월·일·요일)만 말하고 «어떻게 쓸지»는 맡긴다.
 *
 * <pre>
 *   ko     9월 20일 (토)
 *   en     September 20 (Sat)
 *   ja     9月20日(土)
 *   zh     9月20日周六
 * </pre>
 *
 * <h2>🔴 Intl 이 없을 수도 있다는 전제로 쓴다</h2>
 *
 * 앱은 Hermes 위에서 돈다. 지금 판에는 {@code Intl} 이 들어 있고 저장소의 다른 화면도
 * 이미 {@code toLocaleDateString(locale)} 을 쓰고 있지만, **엔진이나 판이 바뀌면 없을
 * 수도 있다.** 날짜 한 줄 때문에 화면이 죽으면 안 되므로 전부 {@code try} 로 감싸고,
 * 안 되면 숫자만이라도 보여 준다. 지어내지는 않는다 — 못 읽는 날짜는 {@code null} 이다.
 *
 * <h2>🔴 그런데 «없는 것»보다 «조용히 바뀌는 것»이 더 위험하다 — S15P21E201-1399</h2>
 *
 * {@code try}/{@code catch} 는 **던져질 때만** 잡는다. 그리고 {@code Intl} 은 자료가 없는
 * 로케일에 **던지지 않는다 — 조용히 딴 로케일로 갈아치운다.** 실측(Node 24):
 *
 * <pre>
 *   new Intl.DateTimeFormat('xx-YY', { month:'long', day:'numeric' })
 *     → 안 던진다. resolvedOptions().locale === 'ko-KR', 출력 '9월 20일'
 *   new Intl.DateTimeFormat('!!!')   → RangeError   (문법이 틀린 것«만» 던진다)
 * </pre>
 *
 * 그래서 기기의 엔진에 {@code zh-TW} 자료가 없으면 아래 대체가 **한 번도 안 걸리고**,
 * 번체를 고른 사람이 딴 언어 날짜를 본다. 오류도 로그도 안 남는다.
 * {@code supportedLocalesOf} 만이 사실을 말하므로 **쓰기 전에 묻는다.**
 *
 * <p>🔴 이것은 시험으로 못 막는다. 시험이 도는 Node 는 ICU 를 전부 갖고 있어서 다섯
 * 언어가 모두 지원된다고 답한다 — 기기에서 깨져 있어도 초록이다. 그래서 «시험»이 아니라
 * «런타임 판정»으로 막는다. 어느 언어가 실제로 자료가 없는지는 기기가 답한다.
 */

/** 못 읽는 날짜는 지어내지 않는다. */
function parse(value: string): Date | null {
  const date = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/**
 * 이 엔진이 그 로케일의 «자료»를 실제로 갖고 있는가. 머리말의 조용한 치환을 막는 자리다.
 *
 * <p>{@code Intl} 이 아예 없는 판에서는 참조 자체가 터지므로 그것도 여기서 받는다 —
 * 부르는 쪽은 「맡길 수 있나」 하나만 알면 된다.
 */
function localeSupported(locale: string): boolean {
  try {
    return Intl.DateTimeFormat.supportedLocalesOf([locale]).length > 0;
  }
  catch {
    return false;
  }
}

/**
 * 맡길 수 있으면 맡기고, 아니면 {@code null} 을 낸다. 무엇으로 대신할지는 부르는 쪽이
 * 정한다 — 날짜는 「9. 20.」, 시각은 「14:30」 처럼 자리마다 다르기 때문이다.
 */
function intlFormat(locale: string, options: Intl.DateTimeFormatOptions, date: Date): string | null {
  if (!localeSupported(locale)) return null;
  try {
    return new Intl.DateTimeFormat(locale, options).format(date);
  }
  catch {
    return null;
  }
}

/**
 * 「9월 20일 (토)」 · 「September 20 (Sat)」 · 「9月20日(土)」.
 * 날짜를 못 읽으면 null — 부르는 쪽이 「n일차」 같은 것으로 대신한다.
 */
export function formatDayHeading(value: string, locale: string): string | null {
  const date = parse(value);
  if (!date) return null;
  return intlFormat(locale, { month: 'long', day: 'numeric', weekday: 'short' }, date)
    ?? `${date.getMonth() + 1}. ${date.getDate()}.`;
}

/**
 * 「토」 · 「Sat」 · 「土」 · 「周六」 — 요일만. 여행 페이지(폰) 타임라인의 날짜 원 위에 쓴다.
 * 맡길 수 없으면 null — 요일을 지어내지 않고 그 줄을 안 그린다.
 */
export function formatWeekdayShort(value: string, locale: string): string | null {
  const date = parse(value);
  if (!date) return null;
  return intlFormat(locale, { weekday: 'short' }, date);
}

/** 「9월 20일」 · 「Sep 20」 · 「9月20日」 — 요일 없이 짧게. */
export function formatMonthDay(value: string, locale: string): string | null {
  const date = parse(value);
  if (!date) return null;
  return intlFormat(locale, { month: 'short', day: 'numeric' }, date)
    ?? `${date.getMonth() + 1}. ${date.getDate()}.`;
}

/**
 * 「14:30」. 🔴 **24시간으로 고정한다.**
 *
 * 일정표에서 시각은 «읽는 것»이 아니라 «줄 세우는 것»이다. 「오후 2:30」과 「14:30」이
 * 한 화면에 섞이면 앞뒤를 눈으로 못 견준다. 그래서 여기만은 나라별 관습을 안 따른다 —
 * 날짜는 맡기고 시각은 고정하는 것이 일부러 하는 선택이다.
 */
export function formatClock(value: string, locale: string): string {
  const date = parse(value);
  if (!date) return value.slice(11, 16) || value;
  const pad = (n: number) => String(n).padStart(2, '0');
  return intlFormat(locale, { hour: '2-digit', minute: '2-digit', hour12: false }, date)
    ?? `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 「9. 20.」 · 「9/20」 — 자리가 좁은 칸(일차 딱지 등)에 쓴다. */
export function formatNumericDate(value: string, locale: string): string | null {
  const date = parse(value);
  if (!date) return null;
  return intlFormat(locale, { month: 'numeric', day: 'numeric' }, date)
    ?? `${date.getMonth() + 1}.${date.getDate()}`;
}

/**
 * 「2026. 9. 20.」 · 「9/20/2026」 — 연도까지 넣는다. 만료일처럼 «올해가 아닐 수도
 * 있는» 날짜에 쓴다(초대 링크·공유 일정 만료).
 *
 * 🔴 이 파일이 감싸기 전에는 화면 네 곳(초대 링크·공동 작성자 초대·공유 일정)이
 * `new Date(...).toLocaleDateString(locale)` / `.toLocaleString(locale)` 를 직접
 * 불렀다 — 머리말이 설명하는 조용한 치환에 그대로 노출돼 있었다(S15P21E201-1399
 * 후속). 날짜만 쓰려면 이 함수를, 날짜+시각을 같이 쓰려면 {@link formatDateTime} 을 쓴다.
 */
export function formatFullDate(value: string, locale: string): string {
  const date = parse(value);
  if (!date) return value;
  const pad = (n: number) => String(n).padStart(2, '0');
  return intlFormat(locale, { year: 'numeric', month: 'numeric', day: 'numeric' }, date)
    ?? `${date.getFullYear()}. ${pad(date.getMonth() + 1)}. ${pad(date.getDate())}.`;
}

/** 「2026. 9. 20. 14:30」 — 연도·시각을 다 보여준다. 만료 시각처럼 «날짜와 시각이 같이 중요한» 값에 쓴다. */
export function formatDateTime(value: string, locale: string): string {
  const date = parse(value);
  if (!date) return value;
  const pad = (n: number) => String(n).padStart(2, '0');
  return intlFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }, date)
    ?? `${date.getFullYear()}. ${pad(date.getMonth() + 1)}. ${pad(date.getDate())}. ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
