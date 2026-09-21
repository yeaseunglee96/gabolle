// 여행표 뒤집기 — 시안 ②.
//
// 🔴 이 시험이 지키는 것은 「예쁘게 도는가」가 아니라 **빈 뒷면이 안 나오는가**이다.
//    만드는 중에는 적을 것이 없다. 그때도 뒤집히면 사용자는 하얀 뒷면을 보고 고장으로 읽고,
//    다시 앞으로 돌리는 방법도 모른 채 멈춘다.
import { render } from '@testing-library/react-native';

import { TripPass } from '@/plan/TripPass';
import type { TripPassData, TripPassDetail } from '@/plan/tripPassData';

const tx = (ko: string) => ko;

const data: TripPassData = {
  code: 'GB-ABC123',
  fromLabel: '부산역',
  toLabel: '광안리',
  startTime: '09:30',
  endTime: '17:30',
  dateRange: '10.3(금) – 10.5(일)',
  mode: '대중교통',
  owner: '진미리',
  fields: [{ key: '방문지', value: '9곳' }],
  url: 'https://example.test/trips/1',
  validText: '이 승차권은 10.3 여행에만 쓸 수 있어요',
};

const details: TripPassDetail[] = [
  { key: '출발지', value: '부산역' },
  { key: '첫 일정', value: '09:30 · 해운대 바다 산책' },
];

describe('여행표 뒤집기', () => {
  it('🔴 적을 것이 없으면 뒤집기가 아예 없다 — 빈 뒷면을 보여 주지 않는다', () => {
    const view = render(<TripPass data={data} tx={tx} />);

    expect(view.queryByLabelText('여행표 상세 보기')).toBeNull();
    expect(view.queryByText('일정 보기 →')).toBeNull();
  });

  it('🔴 빈 목록도 「없음」이다 — 줄이 하나도 없으면 뒤집을 이유가 없다', () => {
    const view = render(<TripPass data={data} details={[]} tx={tx} />);

    expect(view.queryByLabelText('여행표 상세 보기')).toBeNull();
  });

  it('적을 것이 있어도 **다 나오기 전에는** 못 뒤집는다 — 프린터 안에서 돌아가면 반쪽만 보인다', () => {
    // 출력 애니메이션이 끝나야 뒤집기가 열린다. 막 그린 직후에는 아직이다.
    const view = render(<TripPass data={data} details={details} tx={tx} />);

    expect(view.queryByLabelText('여행표 상세 보기')).toBeNull();
  });

  it('앞면은 언제나 그린다 — 뒤집기가 없어도 티켓은 보여야 한다', () => {
    const view = render(<TripPass data={data} tx={tx} />);

    expect(view.getByText('부산역')).toBeTruthy();
    expect(view.getByText('광안리')).toBeTruthy();
  });
});
