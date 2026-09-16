import { shouldHideApiBanner, SHOW_AFTER_MS, watchApiUnavailable } from '../ApiAvailabilityBanner';

// S15P21E201-1107 — 폰에서 "서버 연결을 확인하고 있어요" 가 자꾸 떴다.
// -1081 에서 5xx 를 끊김으로 세기 시작하면서, 배포 중 502 한 번이나 백그라운드 요청 하나가
// 실패해도 배너가 즉시 떴다가 몇 초 뒤 사라졌다. 그 깜빡임이 "뭔가 고장났다" 로 읽힌다.
describe('잠깐 끊긴 것은 말하지 않는다', () => {
  /** 구독자를 손으로 흔들 수 있는 가짜 구독. */
  function fakeSubscribe() {
    let listener: ((v: boolean) => void) | null = null;
    const stop = jest.fn();
    const subscribe = (l: (v: boolean) => void) => { listener = l; return stop; };
    return { subscribe, emit: (v: boolean) => listener?.(v), stop };
  }

  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it('🔴 끊겼다 곧 붙으면 배너를 아예 안 띄운다', () => {
    const seen: boolean[] = [];
    const { subscribe, emit } = fakeSubscribe();
    watchApiUnavailable(subscribe, (v) => seen.push(v));

    emit(true);
    jest.advanceTimersByTime(SHOW_AFTER_MS - 1);
    emit(false);
    jest.advanceTimersByTime(SHOW_AFTER_MS * 2);

    expect(seen).toEqual([false]); // 참이 한 번도 안 갔다
  });

  it('끊긴 채로 그 시간이 지나면 띄운다 — 정말 안 되는 것은 반드시 말한다', () => {
    const seen: boolean[] = [];
    const { subscribe, emit } = fakeSubscribe();
    watchApiUnavailable(subscribe, (v) => seen.push(v));

    emit(true);
    jest.advanceTimersByTime(SHOW_AFTER_MS);

    expect(seen).toEqual([true]);
  });

  it('다시 붙으면 기다리지 않고 즉시 치운다', () => {
    const seen: boolean[] = [];
    const { subscribe, emit } = fakeSubscribe();
    watchApiUnavailable(subscribe, (v) => seen.push(v));

    emit(true);
    jest.advanceTimersByTime(SHOW_AFTER_MS);
    emit(false);

    expect(seen).toEqual([true, false]);
  });

  it('끊김이 여러 번 와도 타이머를 새로 잡지 않는다 — 마지막 것 하나만 센다', () => {
    const seen: boolean[] = [];
    const { subscribe, emit } = fakeSubscribe();
    watchApiUnavailable(subscribe, (v) => seen.push(v));

    emit(true);
    jest.advanceTimersByTime(SHOW_AFTER_MS - 100);
    emit(true); // 다시 끊김 — 여기서부터 다시 센다
    jest.advanceTimersByTime(SHOW_AFTER_MS - 100);
    expect(seen).toEqual([]); // 아직 한 번도 안 띄웠다
    jest.advanceTimersByTime(100);
    expect(seen).toEqual([true]);
  });

  it('정리하면 예약된 배너가 뜨지 않는다', () => {
    const seen: boolean[] = [];
    const { subscribe, emit, stop } = fakeSubscribe();
    const dispose = watchApiUnavailable(subscribe, (v) => seen.push(v));

    emit(true);
    dispose();
    jest.advanceTimersByTime(SHOW_AFTER_MS * 2);

    expect(seen).toEqual([]);
    expect(stop).toHaveBeenCalled();
  });
});

describe('shouldHideApiBanner', () => {
  it.each(['/', '/app-intro', '/age-gate', '/sign-in', '/sign-up', '/permissions'])(
    '서버가 필요 없는 첫 진입 화면 %s에서는 전역 배너를 숨긴다',
    (pathname) => expect(shouldHideApiBanner(pathname)).toBe(true),
  );

  it.each(['/home', '/feed', '/trips', '/explore'])(
    '서버 데이터를 쓰는 화면 %s에서는 연결 안내를 유지한다',
    (pathname) => expect(shouldHideApiBanner(pathname)).toBe(false),
  );
});
