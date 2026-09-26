// 버튼 글자가 알약 끝에 붙지 않는지 — S15P21E201-1717.
//
// 🔴 이 시험이 지키는 것은 「가로 여백이 있다」는 한 가지다. 안드로이드 실기(2026-09-26)에서 「내 여행」 빈 화면의
//    「첫 여행 만들기」 글자가 빨간 알약의 좌우 끝에 그대로 닿았다. 폭 100% 전폭 버튼에서는 안 보이고 글자 폭으로 줄어드는
//    자리에서만 보여서 타입·다른 시험·폭 검사가 전부 초록이었다.
import { render } from '@testing-library/react-native';
import { StyleSheet } from 'react-native';

import { Button } from '../Button';

const styleOf = (label: string, props: Partial<React.ComponentProps<typeof Button>> = {}) => {
  const view = render(<Button label={label} onPress={() => {}} {...props} />);
  return StyleSheet.flatten(view.getByRole('button').props.style) as Record<string, unknown>;
};

describe('Button 가로 여백', () => {
  it('기본 버튼에 가로 여백이 있다 — 글자 폭으로 줄어들어도 글자가 끝에 붙지 않는다', () => {
    expect(Number(styleOf('첫 여행 만들기').paddingHorizontal)).toBeGreaterThan(0);
  });

  it('갈래(primary·outline·tertiary)와 알약(pill)이 여백을 잃지 않는다', () => {
    for (const props of [{ variant: 'primary' }, { variant: 'outline' }, { variant: 'tertiary' }, { pill: true }] as const) {
      expect(Number(styleOf('로그인', props).paddingHorizontal)).toBeGreaterThan(0);
    }
  });

  it('전폭 버튼은 여전히 폭 100% 다 — 여백이 폭 안쪽이라 모습이 바뀌지 않는다', () => {
    expect(styleOf('다음').width).toBe('100%');
  });

  it('compact 는 폭만 글자 폭으로 바꾸고 가로 여백은 그대로 둔다', () => {
    const style = styleOf('수정', { compact: true });
    expect(style.width).toBe('auto');
    expect(Number(style.paddingHorizontal)).toBeGreaterThan(0);
  });
});
