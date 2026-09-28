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

/** 「범위를」·「예산을」 — 받침이 있으면 「을」. 한글이 아니면 「를」. */
export function koreanObject(name: string): '을' | '를' {
  const final = finalConsonant(name);
  return final === null || final === 0 ? '를' : '을';
}

/** 「쇼진이」·「카페오뜨가」 — 받침이 있으면 「이」. 한글이 아니면 「가」 (S15P21E201-1535, 여행 페이지 「확인할 것」). */
export function koreanSubject(name: string): '이' | '가' {
  const final = finalConsonant(name);
  return final === null || final === 0 ? '가' : '이';
}

/** 「인원은」·「출발지는」 — 받침이 있으면 「은」. 한글이 아니면 「는」 (S15P21E201-1677, 공유 링크 「…는(은) 공유되지 않아요」). */
export function koreanTopic(name: string): '은' | '는' {
  const final = finalConsonant(name);
  return final === null || final === 0 ? '는' : '은';
}
