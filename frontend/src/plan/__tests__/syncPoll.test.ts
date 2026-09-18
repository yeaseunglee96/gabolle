// — 「바뀌면 빨라지고 안 바뀌면 느려진다」를 시험이 지킨다.
import { nextSyncPollDelay, SYNC_POLL_BASE_MS, SYNC_POLL_MAX_MS } from '../syncPoll';

describe('nextSyncPollDelay', () => {
	it('안 바뀌면 두 배씩 늘어난다', () => {
		expect(nextSyncPollDelay(5_000, false)).toBe(10_000);
		expect(nextSyncPollDelay(10_000, false)).toBe(20_000);
	});

	it('최대치에서 멈춘다 — 무한정 늘어나면 동기화가 죽는다', () => {
		expect(nextSyncPollDelay(20_000, false)).toBe(SYNC_POLL_MAX_MS);
		expect(nextSyncPollDelay(SYNC_POLL_MAX_MS, false)).toBe(SYNC_POLL_MAX_MS);
	});

	it('🔴 바뀌면 그 자리에서 기본값으로 되돌아간다 — 동행자가 손대기 시작한 순간이다', () => {
		expect(nextSyncPollDelay(SYNC_POLL_MAX_MS, true)).toBe(SYNC_POLL_BASE_MS);
		expect(nextSyncPollDelay(5_000, true)).toBe(SYNC_POLL_BASE_MS);
	});

	it('이상한 값이 들어와도 기본값 아래로는 안 내려간다', () => {
		expect(nextSyncPollDelay(0, false)).toBe(10_000);
		expect(nextSyncPollDelay(-1, false)).toBe(10_000);
	});

	it('🔴 최대치가 30초를 넘지 않는다 — 넘기면 「실시간 동기화」라는 화면 문구가 거짓이 된다', () => {
		expect(SYNC_POLL_MAX_MS).toBeLessThanOrEqual(30_000);
		expect(SYNC_POLL_BASE_MS).toBeLessThan(SYNC_POLL_MAX_MS);
	});

	it('3분을 놔두면 요청이 36회에서 7회로 준다', () => {
		let delay = SYNC_POLL_BASE_MS;
		let elapsed = 0;
		let calls = 0;
		while (elapsed + delay <= 180_000) {
			elapsed += delay;
			calls += 1;
			delay = nextSyncPollDelay(delay, false);
		}
		expect([calls, 180_000 / SYNC_POLL_BASE_MS]).toEqual([7, 36]);
	});
});
