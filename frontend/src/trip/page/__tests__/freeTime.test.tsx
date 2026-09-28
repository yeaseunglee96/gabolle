// 일정 두 곳 사이의 「자유 시간 · n분」 줄 — S15P21E201-1668 (백엔드 짝 S15P21E201-1667).
//
// 🔴 이 시험이 지키는 것:
//    ① 빈 시각은 계약대로 센다 — 다음 곳 시작 − 이번 곳 끝(endsAt) − 다음 곳까지 이동. 30분이 안 되면 안 그린다.
//    ② 끝 시각을 모르는 서버(지금 운영 · 옛 판)에서는 아무것도 안 그린다 — 지금 화면과 같아야 한다.
//       끝 시각 없이 「다음 시각 − 이동」으로 짐작하면 머무는 시간이 자유 시간으로 둔갑한다.
//    ③ 끝 시각이 있으면 「머무름」도 끝 − 시작이다. 전처럼 다음 시각에서 빼면 자유 시간까지 머무름으로 센다.
import { render } from '@testing-library/react-native';

import type { ItineraryItemDto } from '@/plan/itinerary';
import { FreeTimeRow } from '@/trip/page/FreeTimeRow';
import { FREE_TIME_MIN_MINUTES, freeTimeMinutes, stayMinutes } from '@/trip/page/tripPageModel';

const tx = (ko: string) => ko;
const at = (time: string) => `2026-10-15T${time}:00+09:00`;
const item = (id: string, start: string, end: string | null | undefined, travel: number | null = null): ItineraryItemDto => ({
  id, startsAt: at(start), ...(end === undefined ? {} : { endsAt: end === null ? null : at(end) }),
  title: id, locked: false, placeId: `p-${id}`, travelDurationMin: travel, travelDataStatus: travel === null ? null : 'ESTIMATED',
});

describe('자유 시간 — 세는 법', () => {
  it('있음 — 바다 09:20~10:50 → (이동 25분) → 문화 12:05: 50분', () => {
    const items = [item('바다', '09:20', '10:50'), item('문화', '12:05', '13:05', 25)];
    expect(freeTimeMinutes(items, 0)).toBe(50);
  });

  it('짧음 — 30분이 안 되면 안 그린다(계약의 예: 11:40 − 10:50 − 25 = 25분)', () => {
    const items = [item('바다', '09:20', '10:50'), item('문화', '11:40', '12:40', 25)];
    expect(freeTimeMinutes(items, 0)).toBeNull();
    expect(FREE_TIME_MIN_MINUTES).toBe(30);
  });

  it('딱 30분이면 그린다', () => {
    const items = [item('바다', '09:20', '10:50'), item('문화', '11:45', '12:45', 25)];
    expect(freeTimeMinutes(items, 0)).toBe(30);
  });

  it('🔴 없음 — 끝 시각 칸이 없거나(지금 운영) null 이면 안 그린다', () => {
    expect(freeTimeMinutes([item('바다', '09:20', undefined), item('문화', '12:05', '13:05', 25)], 0)).toBeNull();
    expect(freeTimeMinutes([item('바다', '09:20', null), item('문화', '12:05', '13:05', 25)], 0)).toBeNull();
  });

  it('없음 — 옛 일정(똑같이 나눈 판)은 끝 시각이 있어도 빈 시각이 0 이라 안 그린다', () => {
    const items = [item('바다', '09:00', '11:35'), item('문화', '12:00', '14:35', 25)];
    expect(freeTimeMinutes(items, 0)).toBeNull();
  });

  it('이동 시간을 모르면 0 으로 뺀다 — 서버도 그때 이동 0분으로 시각을 깔았다', () => {
    const items = [item('바다', '09:20', '10:50'), item('문화', '11:40', '12:40', null)];
    expect(freeTimeMinutes(items, 0)).toBe(50);
  });

  it('🔴 음수는 없는 것으로 본다', () => {
    const items = [item('바다', '09:20', '11:30'), item('문화', '11:40', '12:40', 25)];
    expect(freeTimeMinutes(items, 0)).toBeNull();
  });

  it('마지막 곳 뒤는 보지 않는다 — 끝 쪽 여유는 숙소로 돌아가는 길과 섞여 있다', () => {
    const items = [item('바다', '09:20', '10:50'), item('문화', '12:05', '13:05', 25)];
    expect(freeTimeMinutes(items, 1)).toBeNull();
  });
});

describe('머무름 — 끝 시각이 있으면 끝 − 시작', () => {
  it('🔴 자유 시간을 머무름에 섞지 않는다', () => {
    const items = [item('바다', '09:20', '10:50'), item('문화', '12:05', '13:05', 25)];
    // 전처럼 세면 12:05 − 09:20 − 25 = 140분(머무름 90 + 자유 시간 50)이 된다
    expect(stayMinutes(items, 0)).toBe(90);
  });

  it('끝 시각이 없으면 전과 같다 — 다음 시각 − 지금 시각 − 다음 구간 이동', () => {
    const items = [item('바다', '09:20', undefined), item('문화', '12:05', undefined, 25)];
    expect(stayMinutes(items, 0)).toBe(140);
  });
});

describe('자유 시간 줄', () => {
  it('50분 — 「자유 시간 · 50분」', () => {
    expect(render(<FreeTimeRow minutes={50} tx={tx} />).getByText('자유 시간 · 50분')).toBeTruthy();
  });

  it('한 시간이 넘으면 시간으로 — 「자유 시간 · 1시간 30분」', () => {
    expect(render(<FreeTimeRow minutes={90} tx={tx} />).getByText('자유 시간 · 1시간 30분')).toBeTruthy();
  });

  it('없으면 아무것도 안 그린다', () => {
    expect(render(<FreeTimeRow minutes={null} tx={tx} />).toJSON()).toBeNull();
  });
});
