// 알림 묶기 — UI 캔버스 ⑦. 동행이 연달아 바꾼 것은 한 장, 무엇이 몇 곳 바뀌었는지를 센다.
jest.mock('@react-native-async-storage/async-storage', () => ({ getItem: jest.fn(), setItem: jest.fn() }));

import { groupLines, groupNotices, noticeKind, type ActivityNotice } from '../activityFeed';

const tx = (ko: string) => ko;
let seq = 0;
const notice = (over: Partial<ActivityNotice>): ActivityNotice => ({
  id: `n${seq += 1}`, tripId: 't1', itineraryId: 'i1', tripTitle: '버터플라이케익 여행', operation: 'REMOVE_ITEM',
  actorName: '박재현', isMe: false, at: '2026-09-29T10:00:00Z', warningCodes: [], ...over,
});

describe('알림 묶기', () => {
  it('🔴 같은 여행·같은 사람·한 시간 안의 변경은 한 장 — 무엇을 몇 곳 바꿨는지 센다', () => {
    const groups = groupNotices([
      notice({ operation: 'REMOVE_ITEM', at: '2026-09-29T10:30:00Z' }),
      notice({ operation: 'LOCK_ITEM', at: '2026-09-29T10:20:00Z' }),
      notice({ operation: 'REMOVE_ITEM', at: '2026-09-29T10:00:00Z' }),
    ]);
    expect(groups).toHaveLength(1);
    expect(groupLines(groups[0], tx).map((line) => line.text)).toEqual(['장소 2곳을 뺐어요', '장소 1곳을 고정했어요']);
  });

  it('사람·여행이 다르거나 한 시간이 넘으면 따로', () => {
    const groups = groupNotices([
      notice({ at: '2026-09-29T12:00:00Z' }),
      notice({ at: '2026-09-29T10:30:00Z' }),
      notice({ actorName: '이예승', at: '2026-09-29T10:20:00Z' }),
      notice({ tripId: 't2', at: '2026-09-29T10:10:00Z' }),
    ]);
    expect(groups).toHaveLength(4);
  });

  it('일정이 새로 만들어진 것은 묶지 않는다 — 그 자체가 소식이다', () => {
    const groups = groupNotices([
      notice({ operation: 'LOCK_ITEM', isMe: true, actorName: null, at: '2026-09-29T10:10:00Z' }),
      notice({ operation: 'CREATE', isMe: true, actorName: null, at: '2026-09-29T10:00:00Z' }),
    ]);
    expect(groups).toHaveLength(2);
  });

  it('종류마다 다른 아이콘 — 전에는 만들어짐 말고 전부 「⇄」 하나였다', () => {
    expect(['CREATE', 'LOCK_ITEM', 'REMOVE_ITEM', 'ADD_ITEM', 'REORDER', 'REPLAN_DAY', 'REVERT'].map((op) => noticeKind(op as never)))
      .toEqual(['created', 'lock', 'remove', 'add', 'reorder', 'replan', 'revert']);
  });
});
