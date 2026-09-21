// 한국어 조사 — S15P21E201-1372.
//
// 「누리마루로」·「해운대 해수욕장으로」. 「(으)로」라고 쓰면 사람이 안 쓰는 말이 화면에 남는다.

/** 마지막 글자가 한글이면 그 종성 번호(0 = 받침 없음), 아니면 null. 끝의 공백·괄호·따옴표는 건너뛴다. */
function finalConsonant(name: string): number | null {
  const trimmed = name.replace(/[\s)\]}」』"'.!?]+$/u, '');
  const code = trimmed.charCodeAt(trimmed.length - 1);
  if (Number.isNaN(code) || code < 0xac00 || code > 0xd7a3) return null;
  return (code - 0xac00) % 28;
}

/** 「로」 또는 「으로」 — 받침이 없거나 ㄹ 받침이면 「로」. 한글이 아닌 이름(영문 상호·숫자)은 「로」. */
export function koreanToward(name: string): '로' | '으로' {
  const final = finalConsonant(name);
  return final === null || final === 0 || final === 8 ? '로' : '으로';
}
