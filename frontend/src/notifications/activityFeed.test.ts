import { hasUnseen, noticeCopy, type ActivityNotice } from './activityFeed';

const tx = (ko: string) => ko;
const notice = (over: Partial<ActivityNotice>): ActivityNotice => ({ id: 'it1:1', tripId: 't1', itineraryId: 'it1', tripTitle: '부산 바다 2박 3일', operation: 'CREATE', actorName: '미리', isMe: true, at: '2026-09-21T10:00:00Z', warningCodes: [], ...over });

describe('알림 말 — S15P21E201-1380', () => {
  it('내가 만든 일정은 「만들어졌어요」, 동행이 바꾼 것은 이름을 붙인다', () => {
    expect(noticeCopy(notice({}), tx)).toEqual({ title: '일정이 만들어졌어요', body: '「부산 바다 2박 3일」 — 확인하고 저장해 주세요.' });
    expect(noticeCopy(notice({ operation: 'REORDER', actorName: '수민', isMe: false }), tx).body).toBe('「부산 바다 2박 3일」 — 수민님이 순서를 바꿨어요.');
    expect(noticeCopy(notice({ operation: 'REORDER' }), tx).body).toBe('「부산 바다 2박 3일」');
  });
  it('안 본 것 — 마지막으로 본 시각 뒤의 것만', () => {
    const items = [notice({ at: '2026-09-21T10:00:00Z' })];
    expect(hasUnseen(items, null)).toBe(true);
    expect(hasUnseen(items, '2026-09-21T09:00:00Z')).toBe(true);
    expect(hasUnseen(items, '2026-09-21T11:00:00Z')).toBe(false);
    expect(hasUnseen([], null)).toBe(false);
  });
});
