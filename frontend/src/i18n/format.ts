/**
 * 🔴 **값이 끼는 문구를 번역표에 올릴 수 있게 한다** — S15P21E201-1352.
 *
 * 번역표는 한국어 원문 자체를 열쇠로 쓴다(gettext 방식). 그래서 문구에 값이 끼면
 * 열쇠가 실행할 때마다 달라져 **표에 넣을 수가 없다.**
 *
 * <pre>
 *   tx(`${name}의 기록`, `${name}'s record`)   →  「민수의 기록」 「영희의 기록」 …
 * </pre>
 *
 * 숫자가 끼는 자리는 {@code pickLanguage} 가 「모양」으로 찾아 해결했다(S15P21E201-1344).
 * 하지만 **이름·장소·날짜처럼 글자가 끼는 자리는 그 방법이 안 통한다** — 다 그려진 뒤에는
 * 어디까지가 문구이고 어디부터가 값인지 알 수 없기 때문이다.
 *
 * <p>그래서 **값을 따로 받는다.** 문구에는 자리만 남기고({@code %s}), 값은 인자로 준다.
 * 열쇠가 고정되므로 표에 올라간다.
 *
 * <pre>
 *   txf(tx, '%s의 기록', "%s's record", name)   →  열쇠는 언제나 '%s의 기록'
 * </pre>
 *
 * <h2>🔴 왜 훅(useI18n)이 아니라 «함수»인가</h2>
 *
 * 값이 끼는 자리의 절반은 화면이 아니라 {@code src/discovery/places.ts} 같은 순수
 * 함수에 있고, 그쪽은 {@code tx} 를 **인자로 받아서** 쓴다. 훅으로 만들면 그 함수들에
 * {@code txf} 를 하나씩 더 흘려보내야 한다 — 부르는 곳마다 인자가 하나씩 늘어난다.
 * {@code tx} 를 첫 인자로 받으면 화면이든 순수 함수든 **같은 방식으로 쓴다.**
 *
 * <h2>🔴 값의 «순서»를 번역에서 바꾸지 않는다</h2>
 *
 * 끼우기는 앞에서부터 차례로 들어간다. 한국어가 「A 를 B 로」 순이면 번역도 그 순서여야
 * 한다. 뒤집으면 조용히 틀린 값이 들어간다 — 표를 고칠 때 가장 내기 쉬운 실수다.
 */

/** 문구의 `%s` 자리에 값을 앞에서부터 끼운다. 값이 모자라면 그 자리는 그대로 둔다. */
export function fillValues(template: string, values: readonly (string | number)[]): string {
  let index = 0;
  return template.replace(/%s/g, () => (index < values.length ? String(values[index++]) : '%s'));
}

/** 번역할 함수(`tx`) · 한국어 틀 · 영어 틀 · 값들 → 그려질 글자. */
export function txf(
  tx: (ko: string, en: string) => string,
  ko: string,
  en: string,
  ...values: Array<string | number>
): string {
  return fillValues(tx(ko, en), values);
}

/**
 * 영어 명사의 단수·복수 — 「traveler」·「travelers」 (S15P21E201-1683). 한국어에는 단·복수가 없어 영어 틀에만 쓴다.
 * 번역표의 한국어 틀(「%s명」)은 그대로 두고 영어 틀만 개수에 맞게 고를 때: txf(tx, '%s명', `%s ${enPlural(n, 'traveler', 'travelers')}`, n).
 */
export function enPlural(count: number, one: string, many: string): string {
  return count === 1 ? one : many;
}

/** 「1 traveler」·「2 travelers」 — 🔴 「1 travelers」가 심사 공지의 알려진 문제였다(S15P21E201-1683). */
export function enCount(count: number, one: string, many: string): string {
  return `${count} ${enPlural(count, one, many)}`;
}
