// 일정 진행 — 시안 ⑤, 인계 §6·§7⑤.
//
// 🔴 이 시험이 지키는 것은 **멈췄을 때 정말 멈추는가**와 **모르는 것을 말하지 않는가**이다.
//    둘 다 화면에서는 멀쩡해 보인다. 중지해 놓고 기록이 쌓이면 「멈췄다」가 거짓이 되고,
//    예정 시각을 모르는데 「정시」라고 적으면 늦고 있는 사람에게 괜찮다고 말하게 된다.
import {
  EMPTY_PROGRESS, arrive, drift, isToday, localDateKey, needsManualArrival, pause, skip, start, stepStates,
} from '@/plan/tripProgress';

const STOPS = ['a', 'b', 'c'];
const NOW = '2026-10-03T11:06:00';

describe('출발과 중지', () => {
  it('출발하면 달린다', () => {
    expect(start(EMPTY_PROGRESS).status).toBe('RUNNING');
  });

  it('중지하면 멈춘다', () => {
    expect(pause(start(EMPTY_PROGRESS)).status).toBe('PAUSED');
  });

  it('🔴 중지가 다녀온 것을 지우지 않는다 — 중지는 되돌리기가 아니다', () => {
    const running = arrive(start(EMPTY_PROGRESS), STOPS, 'a', NOW, 'auto');

    const paused = pause(running);

    expect(paused.outcomes.a).toEqual({ kind: 'ARRIVED', at: NOW, how: 'auto' });
  });

  it('🔴 멈춰 있으면 도착이 안 찍힌다 — 기록이 쌓이면 「멈췄다」가 거짓이 된다', () => {
    const paused = pause(start(EMPTY_PROGRESS));

    expect(arrive(paused, STOPS, 'a', NOW, 'auto')).toBe(paused);
    expect(skip(paused, STOPS, 'a', NOW)).toBe(paused);
  });

  it('출발 전에도 안 찍힌다', () => {
    expect(arrive(EMPTY_PROGRESS, STOPS, 'a', NOW, 'manual')).toBe(EMPTY_PROGRESS);
  });
});

describe('다음으로 넘어간다', () => {
  it('도착하면 다음 정차지를 향한다', () => {
    const next = arrive(start(EMPTY_PROGRESS), STOPS, 'a', NOW, 'auto');

    expect(next.currentStopIndex).toBe(1);
    expect(next.status).toBe('RUNNING');
  });

  it('건너뛰어도 다음으로 간다', () => {
    expect(skip(start(EMPTY_PROGRESS), STOPS, 'a', NOW).currentStopIndex).toBe(1);
  });

  it('마지막까지 가면 끝난다', () => {
    let progress = start(EMPTY_PROGRESS);
    for (const id of STOPS) progress = arrive(progress, STOPS, id, NOW, 'auto');

    expect(progress.status).toBe('DONE');
  });

  it('같은 곳을 두 번 찍어도 한 번만 센다', () => {
    const once = arrive(start(EMPTY_PROGRESS), STOPS, 'a', NOW, 'auto');

    expect(arrive(once, STOPS, 'a', NOW, 'manual')).toBe(once);
  });
});

describe('단계의 모습', () => {
  it('출발 전에는 첫 곳이 「다음」이고 나머지는 「이후」다', () => {
    expect(stepStates(STOPS, EMPTY_PROGRESS)).toEqual(['next', 'later', 'later']);
  });

  it('달리는 중에는 지금 향하는 곳이 「현재」다', () => {
    expect(stepStates(STOPS, start(EMPTY_PROGRESS))).toEqual(['current', 'next', 'later']);
  });

  /** 🔴 건너뛴 곳도 「지나간 것」이다. 「다음」으로 남으면 영영 그 자리를 가리킨다. */
  it('건너뛴 곳은 지나간 것으로 그린다', () => {
    const progress = skip(start(EMPTY_PROGRESS), STOPS, 'a', NOW);

    expect(stepStates(STOPS, progress)).toEqual(['done', 'current', 'next']);
  });
});

describe('예정과의 차이', () => {
  it('예정보다 이르면 이르다고 말한다', () => {
    expect(drift('2026-10-03T11:06:00', '2026-10-03T11:10:00')).toEqual({ minutes: 4, early: true });
  });

  it('늦으면 늦다고 말한다', () => {
    expect(drift('2026-10-03T11:15:00', '2026-10-03T11:10:00')).toEqual({ minutes: 5, early: false });
  });

  /** 🔴 모르는 것을 「정시」로 적으면 늦고 있는 사람에게 괜찮다고 말하게 된다. */
  it('🔴 예정을 모르면 아무 말도 안 한다', () => {
    expect(drift(NOW, null)).toBeNull();
    expect(drift(NOW, '말이 안 되는 값')).toBeNull();
  });

  it('차이가 없으면 굳이 적지 않는다', () => {
    expect(drift('2026-10-03T11:10:00', '2026-10-03T11:10:00')).toBeNull();
  });

  /**
   * 🔴 하루 넘게 벌어지면 「늦었다」가 아니라 **오늘 일정이 아니다**라는 뜻이다.
   * 그대로 적으면 「예정보다 20547분 빠름」 같은 말이 나온다 — 실제로 다음 달 여행을
   * 열어 보니 그렇게 나왔고, 사람은 그걸 읽고 화면이 고장 났다고 여긴다.
   */
  it('🔴 하루 넘게 벌어지면 아무 말도 안 한다', () => {
    expect(drift('2026-09-19T03:13:00', '2026-10-03T09:41:00')).toBeNull();
  });

  it('열두 시간 안쪽은 말한다 — 새벽에 여는 사람도 있다', () => {
    expect(drift('2026-10-03T01:00:00', '2026-10-03T09:00:00')).toEqual({ minutes: 480, early: true });
  });
});

describe('「지금」은 오늘만의 것', () => {
  /**
   * 🔴 다음 달 여행을 열어 「출발」을 누르면 **오늘 그 여행을 하고 있다고 기록된다.**
   * 일정표는 아무 날이나 볼 수 있어야 하지만 「지금」은 오늘만의 것이다.
   */
  it('오늘이면 그린다', () => {
    expect(isToday('2026-10-03', '2026-10-03T11:06:00')).toBe(true);
  });

  it('다른 날이면 안 그린다', () => {
    expect(isToday('2026-10-04', '2026-10-03T11:06:00')).toBe(false);
    expect(isToday('2026-10-02', '2026-10-03T11:06:00')).toBe(false);
  });

  it('날짜를 모르면 안 그린다', () => {
    expect(isToday(null, '2026-10-03T11:06:00')).toBe(false);
    expect(isToday(undefined, '2026-10-03T11:06:00')).toBe(false);
  });

  /**
   * 🔴 `toISOString()` 은 **UTC** 날짜다. 한국은 UTC+9 라 **오전 9시 전에는 하루 전**이고,
   * 그러면 아침에 「지금」 카드가 사라진다 — 하필 사람들이 일정을 시작하는 시간이다.
   * 실제로 이 화면에서 그렇게 났다.
   *
   * 🔴 **이 시험을 처음엔 시간대에 기대게 썼다가 CI 에서 빨개졌다.** 우리 러너는 UTC 라
   * 「UTC 와 지역이 다르다」가 거기서는 거짓이다. 그래서 둘로 나눈다 —
   * 앞의 둘은 어느 시간대에서도 참이고, 마지막 하나는 **UTC 가 아닌 기계에서만** 잰다.
   */
  it('🔴 새벽에도 오늘은 오늘이다 — UTC 가 아니라 그 지역 날짜로 센다', () => {
    // 지역 시각 2026-10-03 새벽 3시 13분. 어느 시간대에서 만들어도 그 지역 날짜는 10-03 이다.
    const earlyMorning = new Date(2026, 9, 3, 3, 13, 0);

    expect(localDateKey(earlyMorning)).toBe('2026-10-03');
    expect(isToday('2026-10-03', localDateKey(earlyMorning))).toBe(true);

    // UTC 기계(우리 CI)에서는 둘이 같아서 잴 것이 없다. 개발자 기계(한국)에서 잰다.
    if (earlyMorning.getTimezoneOffset() !== 0) {
      expect(localDateKey(earlyMorning)).not.toBe(earlyMorning.toISOString().slice(0, 10));
    }
  });

  it('한 자리 달·일에 0 을 채운다 — 「2026-9-3」 은 안 맞는다', () => {
    expect(localDateKey(new Date(2026, 8, 3, 12, 0, 0))).toBe('2026-09-03');
  });
});

describe('「도착 찍기」를 언제 보이나', () => {
  /**
   * 🔴 늘 보이면 사람은 그걸 정상 절차로 알고 매번 누른다. 그러면 자동 기록이 있으나
   * 마나가 된다 — 인계 §10-4 가 따로 적어 둔 것이다.
   */
  it('🔴 GPS 가 멀쩡하면 안 보인다', () => {
    expect(needsManualArrival('RUNNING', true)).toBe(false);
  });

  it('GPS 가 약하면 보인다', () => {
    expect(needsManualArrival('RUNNING', false)).toBe(true);
  });

  it('달리지 않을 때는 아예 안 보인다', () => {
    expect(needsManualArrival('PLANNED', false)).toBe(false);
    expect(needsManualArrival('PAUSED', false)).toBe(false);
  });
});
