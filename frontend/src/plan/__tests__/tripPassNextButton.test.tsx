// 여행표 앞면의 「내 일정 보기」 — S15P21E201-1562.
//
// 🔴 이 시험이 지키는 것은 **다음으로 가는 문이 앞면에 보이는가**다. 전에는 티켓을 눌러 뒤집어야
//    뒷면에 「일정 보기」가 나왔는데, 화면이 그걸 말해 주지 않아 사람이 티켓 앞에서 멈췄다.
import { fireEvent, render } from '@testing-library/react-native';

import { TripPass } from '@/plan/TripPass';
import type { TripPassData } from '@/plan/tripPassData';

const tx = (ko: string) => ko;
const PASS_URL = 'https://example.test/trips/1';

const data: TripPassData = {
  code: 'GB-ABC123',
  stampDate: '20 · SEP · 2026',
  fromLabel: '부산역',
  toLabel: '광안리',
  startTime: '09:30',
  endTime: '17:30',
  dateRange: '10.3(금) – 10.5(일)',
  mode: '대중교통',
  owner: '진미리',
  fields: [{ key: '방문지', value: '9곳' }],
  url: PASS_URL,
  validText: '이 승차권은 10.3 여행에만 쓸 수 있어요', firstStop: null, lastStop: null, conditions: []
};

describe('여행표 앞면의 「내 일정 보기」', () => {
  it('일정이 다 되면 QR 자리에 단추가 있고, 누르면 일정으로 간다', () => {
    const open = jest.fn();
    const view = render(<TripPass data={data} tx={tx} onOpenItinerary={open} />);

    expect(view.queryByLabelText(PASS_URL)).toBeNull();
    fireEvent.press(view.getByText('내 일정 보기'));
    expect(open).toHaveBeenCalledTimes(1);
  });

  it('만드는 중(갈 곳이 없을 때)에는 단추 대신 QR 이다 — 눌러도 갈 데 없는 단추를 그리지 않는다', () => {
    const view = render(<TripPass data={data} tx={tx} />);

    expect(view.queryByText('내 일정 보기')).toBeNull();
    expect(view.getByLabelText(PASS_URL)).toBeTruthy();
  });
});
