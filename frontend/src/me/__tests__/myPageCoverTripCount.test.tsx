// 커버의 「부산 여행 N번째」 — 시안 01.
//
// 🔴 이 줄이 지키는 것은 **「모른다」와 「없다」를 안 섞는가**이다.
//    아직 못 받았으면 null 이고 하나도 안 만들었으면 0 인데, 둘을 같이 다루면 받아 오는
//    동안 「부산 여행 0번째」가 깜빡였다가 사라진다. 화면을 계속 보고 있어야만 보이는 결함이다.
import { render } from '@testing-library/react-native';

import { MyPageCover } from '@/me/MyPageCover';

const tx = (ko: string) => ko;
const base = {
  name: '진미리',
  avatarUri: null,
  coverUri: null,
  counts: [],
  onEdit: () => {},
  tx,
};

function lineOf(tripCount: number | null, email: string | null = '***@redacted') {
  const view = render(<MyPageCover {...base} email={email} tripCount={tripCount} />);
  return view;
}

describe('커버의 여행 횟수', () => {
  it('시안대로 이메일과 가운뎃점으로 잇는다', () => {
    const view = lineOf(3);
    expect(view.getByText('***@redacted · 부산 여행 3번째')).toBeTruthy();
  });

  it('🔴 아직 못 받았으면(null) 안 그린다 — 「0번째」가 깜빡이면 안 된다', () => {
    const view = lineOf(null);
    expect(view.getByText('***@redacted')).toBeTruthy();
    expect(view.queryByText(/번째/)).toBeNull();
  });

  it('🔴 하나도 안 만들었으면(0) 안 그린다 — 「0번째」는 말이 안 된다', () => {
    const view = lineOf(0);
    expect(view.getByText('***@redacted')).toBeTruthy();
    expect(view.queryByText(/번째/)).toBeNull();
  });

  it('이메일이 없으면 가운뎃점도 안 찍는다', () => {
    const view = lineOf(2, null);
    expect(view.getByText('부산 여행 2번째')).toBeTruthy();
  });
});
