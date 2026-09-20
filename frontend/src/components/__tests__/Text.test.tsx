// 한국어 낱말이 중간에서 끊기지 않게 하는 장치 — S15P21E201-1360.
import { Platform } from 'react-native';
import { render, screen } from '@testing-library/react-native';

import { joinHangulSyllables, Text } from '../Text';

const WJ = String.fromCharCode(0x2060);

describe('한글 낱말 이음표', () => {
  it('낱말 안의 음절 사이에만 넣는다 — 띄어쓰기·문장부호·숫자 앞뒤는 그대로', () => {
    expect(joinHangulSyllables('다시 시도해 주세요.')).toBe(`다${WJ}시 시${WJ}도${WJ}해 주${WJ}세${WJ}요.`);
    expect(joinHangulSyllables('3.2km 걸어요')).toBe(`3.2km 걸${WJ}어${WJ}요`);
  });

  it('한글이 없으면 손대지 않는다', () => {
    expect(joinHangulSyllables('Try again')).toBe('Try again');
    expect(joinHangulSyllables('')).toBe('');
  });

  it('🔴 안드로이드가 아니면 화면 글자는 원문 그대로다 — 글자로 찾는 시험과 자동화가 그대로 살아야 한다', () => {
    // jest 는 iOS 로 돈다. 여기서 이음표가 끼면 getByText('다시 시도') 류가 전부 죽는다.
    expect(Platform.OS).not.toBe('android');
    render(<Text>다시 시도해 주세요.</Text>);
    expect(screen.getByText('다시 시도해 주세요.')).toBeTruthy();
  });
});
