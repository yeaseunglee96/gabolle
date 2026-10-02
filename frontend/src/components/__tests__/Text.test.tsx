// 한국어 낱말이 중간에서 끊기지 않게 하는 장치 — S15P21E201-1360.
import { Platform } from 'react-native';
import { render, screen } from '@testing-library/react-native';

import { joinHangulSyllables, nativeFontFamily, Text, webLineBreakStyle } from '../Text';

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

describe('웹 감싸기 힌트 — S15P21E201-1372', () => {
  it('🔴 줄 수를 정한 글에는 text-wrap: pretty 를 넣지 않는다 — 넣으면 nowrap 이 풀려 한 줄 자르기가 죽는다', () => {
    const original = Platform.OS;
    Object.defineProperty(Platform, 'OS', { value: 'web', configurable: true });
    try {
      expect(webLineBreakStyle(1)).not.toHaveProperty('textWrap');
      expect(webLineBreakStyle(2)).toHaveProperty('wordBreak', 'keep-all');
      expect(webLineBreakStyle(undefined)).toHaveProperty('textWrap', 'pretty');
      // 작은 안내 글은 줄을 고르게 — 끝 낱말 두셋만 넘어가지 않게. 줄 수를 정했으면 여전히 없음
      expect(webLineBreakStyle(undefined, 'caption')).toHaveProperty('textWrap', 'balance');
      expect(webLineBreakStyle(undefined, 'body')).toHaveProperty('textWrap', 'pretty');
      expect(webLineBreakStyle(1, 'caption')).not.toHaveProperty('textWrap');
    } finally {
      Object.defineProperty(Platform, 'OS', { value: original, configurable: true });
    }
    expect(webLineBreakStyle(undefined)).toBeNull();
  });
});

describe('폰 앱의 가나·한자 글 — S15P21E201-1942', () => {
  // 🔴 Pretendard 에는 가나·한자가 없다. 굵기별 이름 + 굵기를 함께 박으면 일부 안드로이드에서 글자가 빈칸이 됐다.
  it('가나·한자가 든 글은 기기 글꼴(fontFamily 없음)로 — 한글·영문만이면 Pretendard 그대로', () => {
    expect(Platform.OS).not.toBe('web');
    expect(nativeFontFamily('Pretendard-Bold', '日本語')).toBeUndefined();
    expect(nativeFontFamily('Pretendard-Bold', '简体中文')).toBeUndefined();
    expect(nativeFontFamily('Pretendard-Bold', ['✓ ', '繁體中文'])).toBeUndefined();
    expect(nativeFontFamily('Pretendard-Bold', '釜山駅 (부산역)')).toBeUndefined();
    expect(nativeFontFamily('Pretendard-Bold', '한국어')).toBe('Pretendard-Bold');
    expect(nativeFontFamily('Pretendard-Bold', 'English')).toBe('Pretendard-Bold');
    expect(nativeFontFamily('Pretendard-Bold', 12000)).toBe('Pretendard-Bold');
  });

  it('그려진 글의 스타일에도 그대로 — 일본어 글에는 굵기만 남는다', () => {
    render(<Text weight="bold">旅行のお金</Text>);
    const style = [screen.getByText('旅行のお金').props.style].flat(3).reduce((all, each) => ({ ...all, ...(each ?? {}) }), {});
    expect(style.fontFamily).toBeUndefined();
    expect(style.fontWeight).toBe('700');
  });

  it('웹은 그대로 — CSS 대체 글꼴 목록이 이미 있다', () => {
    const original = Platform.OS;
    Object.defineProperty(Platform, 'OS', { value: 'web', configurable: true });
    try {
      expect(nativeFontFamily('Pretendard-Bold, sans-serif', '日本語')).toBe('Pretendard-Bold, sans-serif');
    } finally {
      Object.defineProperty(Platform, 'OS', { value: original, configurable: true });
    }
  });
});
