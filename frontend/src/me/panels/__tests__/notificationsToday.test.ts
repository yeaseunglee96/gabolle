// 알림 「오늘」 묶음은 기기 현지 날짜로 (S15P21E201-1771). 시험은 TZ=Asia/Seoul 로 돈다고 가정하지 않는다 —
// 「지금」과 알림 시각을 같은 현지 시각으로 만들어 비교한다.
import { splitToday } from '@/me/panels/NotificationsBody';

const local = (y: number, m: number, d: number, h: number) => new Date(y, m - 1, d, h, 0, 0);

it('🔴 현지 새벽 4시에 어제 오후 1시 알림은 「오늘」이 아니다', () => {
  const now = local(2026, 9, 27, 4);
  const { today, earlier } = splitToday([{ at: local(2026, 9, 26, 13).toISOString() }], now);
  expect(today).toHaveLength(0);
  expect(earlier).toHaveLength(1);
});

it('같은 현지 날짜면 「오늘」', () => {
  const now = local(2026, 9, 27, 23);
  const { today } = splitToday([{ at: local(2026, 9, 27, 0).toISOString() }], now);
  expect(today).toHaveLength(1);
});
