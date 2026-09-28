// 하루 끝에 돌아가는 줄 — S15P21E201-1566.
//
// 🔴 이 시험이 지키는 것은 「어디로 돌아가는지」를 틀리게 말하지 않는 것이다. 마지막 날에 「숙소로」라고
//    적으면 체크아웃한 사람을 숙소로 돌려보내고, 모르는 날에 줄을 그리면 없는 숙소를 지어낸다.
import { render } from '@testing-library/react-native';

import { DayReturnRow } from '@/trip/page/DayReturnRow';

const tx = (ko: string) => ko;

describe('하루 끝에 돌아가는 줄', () => {
  it('숙소로 — 동네 이름과 어림 시간을 적는다', () => {
    const view = render(<DayReturnRow tx={tx} leg={{ kind: 'LODGING', label: '해운대', lat: 35.1587, lng: 129.1604, durationMin: 18, distanceM: 2100, travelDataStatus: 'ESTIMATED' }} />);
    expect(view.getByText('숙소로 돌아가기')).toBeTruthy();
    expect(view.getByText('해운대 · 18분 (어림)')).toBeTruthy();
  });

  it('🔴 마지막 날은 출발지로 — 숙소라고 적지 않는다', () => {
    const view = render(<DayReturnRow tx={tx} leg={{ kind: 'ORIGIN', label: null, lat: 35.1152, lng: 129.0422, durationMin: 52, distanceM: 14000, travelDataStatus: 'VERIFIED' }} />);
    expect(view.getByText('출발지로 돌아가기')).toBeTruthy();
    expect(view.getByText('52분')).toBeTruthy();
    expect(view.queryByText('숙소로 돌아가기')).toBeNull();
  });

  it('🔴 서버가 안 보내면(돌아갈 자리를 모르면) 아무것도 안 그린다', () => {
    expect(render(<DayReturnRow tx={tx} leg={null} />).toJSON()).toBeNull();
    expect(render(<DayReturnRow tx={tx} leg={undefined} />).toJSON()).toBeNull();
  });
});
