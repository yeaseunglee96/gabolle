import { render, screen } from '@testing-library/react-native';

import { Eyebrow } from './Eyebrow';

// 컴포넌트 테스트 패턴 예시 — S15P21E201-774. 표시용 컴포넌트 하나를 렌더링해서
// 전달한 문구가 화면에 그대로 나오는지만 본다. 스타일·토큰 값은 여기서 재확인하지
// 않는다 — 그건 Text 컴포넌트 자신의 몫이다.
describe('Eyebrow', () => {
  it('전달한 문구를 그대로 보여준다', () => {
    render(<Eyebrow>06 · 여행 만들기</Eyebrow>);

    expect(screen.getByText('06 · 여행 만들기')).toBeTruthy();
  });
});
