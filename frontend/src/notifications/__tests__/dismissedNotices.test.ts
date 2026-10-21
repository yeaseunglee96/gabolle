// 알림 지우기 — S15P21E201-1907.
jest.mock('@react-native-async-storage/async-storage', () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'));
import { dismissAll, dismissAllUpTo, dismissNotices, loadDismissed, visibleNotices } from '@/notifications/dismissedNotices';

const n = (id: string, at: string) => ({ id, at });
const items = [n('a', '2026-10-01T01:00:00Z'), n('b', '2026-10-01T02:00:00Z'), n('c', '2026-10-01T03:00:00Z')];

it('하나씩 지운 것은 빠지고, 다시 불러와도 기억한다', async () => {
  const d = await dismissNotices('u1', await loadDismissed('u1'), ['b']);
  expect(visibleNotices(items, d).map((x) => x.id)).toEqual(['a', 'c']);
  expect(visibleNotices(items, await loadDismissed('u1')).map((x) => x.id)).toEqual(['a', 'c']);
});

it('🔴 모두 지우면 그 시각까지가 빠지고, 그 뒤에 생긴 알림은 다시 뜬다', async () => {
  const d = await dismissAllUpTo('u2', '2026-10-01T03:00:00Z');
  expect(visibleNotices(items, d)).toEqual([]);
  expect(visibleNotices([...items, n('d', '2026-10-01T04:00:00Z')], d).map((x) => x.id)).toEqual(['d']);
});

it('계정마다 따로 기억한다', async () => {
  await dismissAllUpTo('u3', '2026-10-01T03:00:00Z');
  expect(visibleNotices(items, await loadDismissed('u4'))).toHaveLength(3);
});

it('🔴 가장 최근 알림을 먼저 지운 뒤 모두 지워도 그 알림이 되살아나지 않는다 (S15P21E201-1909)', async () => {
  const one = await dismissNotices('u5', await loadDismissed('u5'), ['c']);
  expect(visibleNotices(items, one).map((x) => x.id)).toEqual(['a', 'b']);
  const all = await dismissAll('u5', items);
  expect(visibleNotices(items, all!)).toEqual([]);
});
