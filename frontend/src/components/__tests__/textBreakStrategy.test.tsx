// 안드로이드 — 글을 잰 폭에 다시 그릴 때 마지막 낱말이 잘리지 않게 줄 나누기는 "simple" 로 한다(S15P21E201-1972).
//
// "balanced" 였을 때 갤럭시 탭에서 「Trip money」 단추가 「Trip」, 「旅行のお金」 제목이 「旅行の」로 보였다.
import { Text as RNText } from 'react-native';
import { render } from '@testing-library/react-native';

import { Text } from '../Text';

describe('Text — 줄 나누기 방식', () => {
  it('🔴 balanced 가 아니라 simple 로 그린다 — 굵은 글의 마지막 낱말이 사라지지 않게', () => {
    const view = render(<Text weight="bold">Trip money</Text>);
    expect(view.UNSAFE_getByType(RNText).props.textBreakStrategy).toBe('simple');
  });

  it('부르는 쪽이 정하면 그것을 따른다', () => {
    const view = render(<Text textBreakStrategy="highQuality">旅行のお金</Text>);
    expect(view.UNSAFE_getByType(RNText).props.textBreakStrategy).toBe('highQuality');
  });
});
