// — 구분자를 앱이 넣을 때 실제로 무서운 것은 "지우다 갇히는 것" 이다.
// 앱이 구분자를 다시 붙여 백스페이스가 제자리걸음이 되면, 사용자는 칸을 비울 수 없다.
// 그래서 여기서는 넣는 것보다 지우는 것을 더 많이 잰다.
import { maskDateInput, maskTimeInput } from '@/plan/inputMasks';

/** 백스페이스를 한 번 누른 것과 같다 — 화면이 값을 넘기고 마스크가 다시 만든다. */
function backspace(value: string): string {
  return value.slice(0, -1);
}

describe('시간 입력 — HH:MM', () => {
  it('숫자만 쳐도 콜론이 생긴다', () => {
    expect(maskTimeInput('0900')).toBe('09:00');
    expect(maskTimeInput('1830')).toBe('18:30');
  });

  it('치는 동안 한 글자씩 자란다', () => {
    expect(maskTimeInput('0')).toBe('0');
    expect(maskTimeInput('09')).toBe('09');
    expect(maskTimeInput('090')).toBe('09:0');
    expect(maskTimeInput('0900')).toBe('09:00');
  });

  it('이미 형식에 맞는 값은 그대로다 (멱등)', () => {
    expect(maskTimeInput('09:00')).toBe('09:00');
    expect(maskTimeInput(maskTimeInput('09:00'))).toBe('09:00');
  });

  it('🔴 백스페이스로 끝까지 지울 수 있다 — 어느 자리에서도 갇히지 않는다', () => {
    const seen: string[] = [];
    let value = '09:00';
    for (let step = 0; step < 10 && value.length > 0; step += 1) {
      const next = maskTimeInput(backspace(value));
      // 한 번 눌렀는데 값이 그대로면 갇힌 것이다.
      expect(next).not.toBe(value);
      seen.push(next);
      value = next;
    }
    expect(value).toBe('');
    expect(seen).toEqual(['09:0', '09', '0', '']);
  });

  it('네 자리를 넘겨 쳐도 안 늘어난다', () => {
    expect(maskTimeInput('0900123')).toBe('09:00');
  });

  it('글자와 기호는 걷어낸다 — 붙여넣기로 들어온 것도 산다', () => {
    expect(maskTimeInput('오전 9시 00분')).toBe('90:0');
    expect(maskTimeInput('09시00분')).toBe('09:00');
  });
});

describe('날짜 입력 — YYYY-MM-DD', () => {
  it('숫자만 쳐도 하이픈이 생긴다', () => {
    expect(maskDateInput('20260916')).toBe('2026-09-16');
  });

  it('치는 동안 한 글자씩 자란다', () => {
    expect(maskDateInput('2026')).toBe('2026');
    expect(maskDateInput('20260')).toBe('2026-0');
    expect(maskDateInput('202609')).toBe('2026-09');
    expect(maskDateInput('2026091')).toBe('2026-09-1');
  });

  it('이미 형식에 맞는 값은 그대로다 (멱등)', () => {
    expect(maskDateInput('2026-09-16')).toBe('2026-09-16');
    expect(maskDateInput(maskDateInput('2026-09-16'))).toBe('2026-09-16');
  });

  it('🔴 백스페이스로 끝까지 지울 수 있다 — 하이픈 두 개를 다 지나간다', () => {
    let value = '2026-09-16';
    const seen: string[] = [];
    for (let step = 0; step < 20 && value.length > 0; step += 1) {
      const next = maskDateInput(backspace(value));
      expect(next).not.toBe(value);
      seen.push(next);
      value = next;
    }
    expect(value).toBe('');
    expect(seen).toEqual(['2026-09-1', '2026-09', '2026-0', '2026', '202', '20', '2', '']);
  });

  it('여덟 자리를 넘겨 쳐도 안 늘어난다', () => {
    expect(maskDateInput('2026091699')).toBe('2026-09-16');
  });

  it('붙여넣은 값의 구분자가 달라도 받아낸다', () => {
    expect(maskDateInput('2026.09.16')).toBe('2026-09-16');
    expect(maskDateInput('2026/09/16')).toBe('2026-09-16');
  });
});
