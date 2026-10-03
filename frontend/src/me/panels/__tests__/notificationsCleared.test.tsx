// 알림을 모두 지운 뒤 빈 화면 문구(S15P21E201-1976).
// 지운 것인데 「아직 도착한 알림이 없어요」라고 하면 알림이 사라진 줄로 안다.
import { emptyNoticeState } from '../NotificationsBody';
import { TRANSLATIONS } from '@/i18n/translations';

describe('알림 빈 화면 — 지워서 빈 것과 원래 없는 것을 가른다', () => {
  it('받은 알림이 있는데 다 지워서 비었으면 cleared', () => {
    expect(emptyNoticeState(3, 0)).toBe('cleared');
  });
  it('받은 알림이 하나도 없으면 none', () => {
    expect(emptyNoticeState(0, 0)).toBe('none');
  });
  it('「모두 지웠어요」 문구는 일본어·중국어 번역이 있다', () => {
    for (const key of ['알림을 모두 지웠어요', '새 알림이 오면 여기에 보여요.']) {
      const row = (TRANSLATIONS as Record<string, { ja?: string; zhHans?: string; zhHant?: string }>)[key];
      expect(row?.ja && row?.zhHans && row?.zhHant).toBeTruthy();
    }
  });
});
