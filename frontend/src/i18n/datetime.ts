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
 */

/** 못 읽는 날짜는 지어내지 않는다. */
function parse(value: string): Date | null {
  const date = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/**
 * 「9월 20일 (토)」 · 「September 20 (Sat)」 · 「9月20日(土)」.
 * 날짜를 못 읽으면 null — 부르는 쪽이 「n일차」 같은 것으로 대신한다.
 */
export function formatDayHeading(value: string, locale: string): string | null {
  const date = parse(value);
  if (!date) return null;
  try {
    return new Intl.DateTimeFormat(locale, { month: 'long', day: 'numeric', weekday: 'short' }).format(date);
  }
  catch {
    return `${date.getMonth() + 1}. ${date.getDate()}.`;
  }
}

/** 「9월 20일」 · 「Sep 20」 · 「9月20日」 — 요일 없이 짧게. */
export function formatMonthDay(value: string, locale: string): string | null {
  const date = parse(value);
  if (!date) return null;
  try {
    return new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric' }).format(date);
  }
  catch {
    return `${date.getMonth() + 1}. ${date.getDate()}.`;
  }
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
  try {
    return new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', hour12: false }).format(date);
  }
  catch {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
}

/** 「9. 20.」 · 「9/20」 — 자리가 좁은 칸(일차 딱지 등)에 쓴다. */
export function formatNumericDate(value: string, locale: string): string | null {
  const date = parse(value);
  if (!date) return null;
  try {
    return new Intl.DateTimeFormat(locale, { month: 'numeric', day: 'numeric' }).format(date);
  }
  catch {
    return `${date.getMonth() + 1}.${date.getDate()}`;
  }
}
