// — 일정 화면이 이미 완성된 일정을 5초마다 다시 받는다.

/** 기본 간격. 변화가 감지되면 언제나 이 값으로 되돌아간다. */
export const SYNC_POLL_BASE_MS = 5_000;

/** 최대 간격 = 동행자 변경이 보이기까지의 최악 지연. */
export const SYNC_POLL_MAX_MS = 30_000;

/** 다음 주기까지 기다릴 시간. */
export function nextSyncPollDelay(current: number, changed: boolean): number {
	if (changed) return SYNC_POLL_BASE_MS;
	// 두 배씩, 최대치에서 멈춘다. 들어온 값이 이상해도 기본값 아래로는 안 내려간다.
	const doubled = Math.max(current, SYNC_POLL_BASE_MS) * 2;
	return Math.min(doubled, SYNC_POLL_MAX_MS);
}
