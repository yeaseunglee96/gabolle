// 손님으로 채운 여행 초안을 로그인한 계정이 이어받는가 — S15P21E201-1541.
//
// 🔴 웹의 소셜 로그인은 페이지를 통째로 제공자에게 넘겼다가 돌아온다(oauth.ts 의 location.assign).
//    돌아오면 앱이 처음부터 다시 켜져서, 로그인 흐름 «안»에서 넘겨주던 장치(PlanProvider 의
//    hydratedKey === ANONYMOUS_KEY 갈래)가 안 걸린다. 그래서 손님으로 날짜·출발지·질문까지 답하고
//    「이 조건으로 일정 만들기」 → 로그인 → 돌아오면 **빈 여행 만들기**가 떴다. 손님 초안은 기기에
//    그대로 남은 채 버려졌다(2026-09-23 웹 재현).
//
// 손님 초안이 남아 있다는 것은 «이 기기에서 가장 최근에 하던 일»이라는 뜻이다 — 로그인 흐름 안에서
// 이어받으면 그 자리에서 지운다. 그래서 무언가 채워져 있으면 그것을 계정 초안으로 쓴다.

type StoredDraft = { version?: number; draft?: Record<string, unknown> };

/** 저장된 글자를 초안으로. 판(version)이 다르거나 깨졌으면 null. */
export function parseStoredDraft<T>(raw: string | null, version: number): T | null {
  if (!raw) return null;
  try {
    const stored = JSON.parse(raw) as StoredDraft;
    return stored.version === version && stored.draft ? (stored.draft as T) : null;
  } catch {
    return null;
  }
}

/** 사람이 무언가 채운 초안인가 — 빈 초안(기본값 그대로)을 이어받아 계정 초안을 덮지 않으려고. */
export function hasPlanInput(draft: { startDate?: unknown; origin?: unknown; lodging?: unknown; travelAreas?: unknown } | null): boolean {
  if (!draft) return false;
  const filled = (value: unknown) => typeof value === 'string' && value.trim().length > 0;
  return filled(draft.startDate) || filled(draft.origin) || filled(draft.lodging) || (Array.isArray(draft.travelAreas) && draft.travelAreas.length > 0);
}
