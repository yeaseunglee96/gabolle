// 안드로이드 — 글자 조각이 여럿인 글은 한 문자열로 합쳐 그린다(S15P21E201-1966).
//
// 고른 언어 칩은 {'✓ '}{'日本語'} 두 조각이라, 안드로이드에서는 한 글 안에 조각이 둘인 글이 된다. 갤럭시 탭에서
// 그 둘째 조각(일본어·중국어)이 자리만 차지하고 안 보였다 — 「✓」만 남았다. 한 문자열(「✓ 已确定路线」)은 보였다.
import { Platform, Text as RNText } from 'react-native';
import { render } from '@testing-library/react-native';

import { Text } from '../Text';

describe('Text — 안드로이드 글자 조각', () => {
  const original = Platform.OS;
  beforeAll(() => { Object.defineProperty(Platform, 'OS', { get: () => 'android', configurable: true }); });
  afterAll(() => { Object.defineProperty(Platform, 'OS', { get: () => original, configurable: true }); });

  it('🔴 글자 조각들은 한 문자열로 합친다 — 「✓ 日本語」의 일본어가 따로 떨어지지 않게', () => {
    const view = render(<Text weight="bold">{'✓ '}{'日本語'}</Text>);
    expect(view.UNSAFE_getByType(RNText).props.children).toBe('✓ 日本語');
  });

  it('빈 조각(조건이 거짓)은 빼고 합친다', () => {
    const selected = false;
    const view = render(<Text>{selected ? '✓ ' : ''}{'简体中文'}{3}</Text>);
    expect(view.UNSAFE_getByType(RNText).props.children).toBe('简体中文3');
  });

  it('다른 부품이 섞인 글은 그대로 둔다', () => {
    const view = render(<Text>{'앞 '}<Text>안</Text></Text>);
    expect(Array.isArray(view.UNSAFE_getAllByType(RNText)[0].props.children)).toBe(true);
  });
});
