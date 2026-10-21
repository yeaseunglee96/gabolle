// 일정 저장 뒤 영업시간 안내가 확인 못 한 장소 수만큼 같은 문장으로 반복되던 것(S15P21E201-1984, 폴드 점검).
jest.mock('react-native-webview', () => ({ WebView: () => null }));

import { describeOpeningHoursIssues } from '../trips/[id]/itinerary';

const tx = (ko: string) => ko;
const itinerary = { days: [{ items: [{ id: 'a', title: '부다면옥' }, { id: 'b', title: '고재' }, { id: 'c', title: '청춘식당' }] }] } as never;

test('확인 못 한 장소가 셋이어도 같은 안내는 한 줄', () => {
  const notChecked = ['a', 'b', 'c'].map((itemId) => ({ itemId, reason: 'NOT_COLLECTED' })) as never;
  expect(describeOpeningHoursIssues(itinerary, [], notChecked, tx)).toEqual(['일부 장소는 영업시간 정보가 없어 확인하지 못했어요.']);
});

test('문장이 다르면 둘 다 남는다', () => {
  const notChecked = [{ itemId: 'a', reason: 'NOT_COLLECTED' }, { itemId: 'b', reason: 'NO_TIME' }] as never;
  expect(describeOpeningHoursIssues(itinerary, [], notChecked, tx)).toHaveLength(2);
});
