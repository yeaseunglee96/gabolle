// 여행 카드 제목 규칙 (S15P21E201-1023).
//
// 🔴 이 시험이 지키는 것은 한 가지다 — **"이름이 없다" 와 "이름이 빈 문자열" 을 같게 그리지
// 않는다.** 서버는 이름이 없을 때 `title` 을 null 로 두고 날짜를 대신 채워 보내지 않는다.
// 그래야 "사용자가 붙인 이름" 과 "서버가 만든 이름" 이 한 칸에서 섞이지 않는다.
//
// 그래서 화면 쪽 구현이 `||` 여야 하고 `??` 면 안 된다. `??` 로 "단순화" 하면 공백만 있는
// 이름이 그대로 그려져서, 카드가 **이름을 잃은 것처럼** 보인다. 눈에 띄는 오류는 안 난다.

import { tripDisplayTitle } from '../trips';

const 날짜 = '2026-09-20 – 2026-09-22';

describe('tripDisplayTitle', () => {
  it('사용자가 붙인 이름이 있으면 그 이름을 쓴다', () => {
    expect(tripDisplayTitle({ title: '부산 3박 4일' }, 날짜)).toBe('부산 3박 4일');
  });

  it('이름이 없으면(null) 대신 줄 문구를 쓴다 — 지금은 날짜다', () => {
    expect(tripDisplayTitle({ title: null }, 날짜)).toBe(날짜);
  });

  // 🔴 여기가 핵심이다. `??` 로 바꾸면 이 둘이 깨진다.
  it('빈 문자열은 이름이 없는 것으로 본다', () => {
    expect(tripDisplayTitle({ title: '' }, 날짜)).toBe(날짜);
  });

  it('공백만 있는 이름도 이름이 없는 것으로 본다', () => {
    expect(tripDisplayTitle({ title: '   ' }, 날짜)).toBe(날짜);
  });

  it('이름 앞뒤의 공백은 떼고 쓴다', () => {
    expect(tripDisplayTitle({ title: '  해운대 가는 길  ' }, 날짜)).toBe('해운대 가는 길');
  });

  // 서버가 아직 이 칸을 안 싣는 환경(배포 시점이 어긋난 경우)에서도 화면이 안 죽어야 한다.
  // 타입은 title 을 요구하지만 실제 응답에는 없을 수 있다 — 그때도 날짜로 떨어진다.
  it('칸 자체가 없어도(undefined) 날짜로 떨어진다', () => {
    expect(tripDisplayTitle({ title: undefined as unknown as null }, 날짜)).toBe(날짜);
  });
});
