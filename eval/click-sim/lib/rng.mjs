/**
 * 고정 씨앗 난수 — 같은 씨앗이면 언제 돌려도 같은 결과가 나온다.
 *
 * 🔴 `Math.random()` 을 쓰지 않는 이유. 시뮬레이션 결과가 돌릴 때마다 달라지면
 *    "개선됐다" 와 "이번에 운이 좋았다" 를 가를 수 없다. `eval/food-ranking/03-split.mjs`
 *    가 분할에 고정 씨앗을 쓴 것과 같은 이유다.
 */

/** mulberry32 — 짧고 분포가 충분히 고른 32비트 PRNG. */
export function rng(seed) {
	let a = seed >>> 0;
	return function next() {
		a |= 0;
		a = (a + 0x6d2b79f5) | 0;
		let t = Math.imul(a ^ (a >>> 15), 1 | a);
		t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
		return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
	};
}

/** 평균 0 부근의 취향값을 뽑는다. -1~+1 로 자른다 — 벡터 성분의 범위와 같다. */
export function taste(next) {
	const u = next() * 2 - 1;
	return Math.round(u * 100) / 100;
}

/** 목록에서 하나. */
export function pick(next, items) {
	return items[Math.floor(next() * items.length)];
}
