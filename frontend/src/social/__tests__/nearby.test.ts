// 「내 근처」 — 가까운 순, 좌표 없는 기록은 뒤에 원래 순서. S15P21E201-1431.
import { sortByDistance } from '@/social/nearby';

const here = { latitude: 35.158, longitude: 129.160 }; // 해운대
const item = (id: string, lat: number | null, lng: number | null) => ({ id, place: lat == null ? null : { lat, lng } });

it('가까운 순으로 늘어놓고, 좌표 없는 것은 뒤에 원래 순서대로', () => {
  const sorted = sortByDistance([item('none1', null, null), item('gamcheon', 35.097, 129.010), item('gwangalli', 35.153, 129.118), item('none2', null, null), item('haeundae', 35.159, 129.161)], here);
  expect(sorted.map((x) => x.id)).toEqual(['haeundae', 'gwangalli', 'gamcheon', 'none1', 'none2']);
});

it('원본을 바꾸지 않는다', () => {
  const list = [item('a', 35.1, 129.1), item('b', 35.2, 129.2)];
  sortByDistance(list, here);
  expect(list.map((x) => x.id)).toEqual(['a', 'b']);
});
