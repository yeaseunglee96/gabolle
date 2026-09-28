// 카드 사진 위 출처 띠 — S15P21E201-1682.
//
// 🔴 이 시험이 지키는 것(조율 세션 결정): 출처는 꾸밈이 아니라 이용 조건이다.
//    ① 한 줄로 잘려 「사진: 한국관광공사 공…」처럼 이용 조건(공공누리 몇 유형)이 안 보였다 — 두 줄까지 보인다.
//    ② 위키미디어(CC BY-SA 등)처럼 라이선스가 따로 있으면 라이선스를 둘째 줄에 따로 둔다 — 출처가 길어도 잘리지 않는다.
//    ③ 홈과 둘러보기가 문구(「사진 제공:」/「사진:」)와 배색이 달랐다 — 둘 다 이 띠 하나를 쓴다.
import { Linking, StyleSheet } from 'react-native';
import { fireEvent, render, screen } from '@testing-library/react-native';

import { color } from '@/design/tokens';
import { PhotoCreditBar } from '@/components/PhotoCreditBar';

// 소스를 읽는 시험은 이 저장소의 방식대로 require 로 부른다 — 앱 tsconfig 에 node 타입이 없다(tripCoverField.test.ts 와 같다).
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const tx = (ko: string) => ko;
type Node = { props: { numberOfLines?: number; style?: unknown } };
const lines = (text: string) => (screen.getByText(text) as unknown as Node).props.numberOfLines;

describe('카드 사진 출처 띠', () => {
  it('🔴 라이선스가 따로 없으면 「사진: …」 한 덩어리를 두 줄까지 — 한 줄에서 자르지 않는다', () => {
    render(<PhotoCreditBar source="한국관광공사 공공누리 제1유형" license={null} licenseUrl={null} tx={tx} />);
    expect(lines('사진: 한국관광공사 공공누리 제1유형')).toBe(2);
  });

  it('🔴 라이선스가 있으면 둘째 줄에 따로 — 출처가 길어도 이용 조건이 잘리지 않는다', () => {
    render(<PhotoCreditBar source="Wikimedia Commons / 아주 긴 작성자 이름이 들어간 경우" license="CC BY-SA 3.0" licenseUrl="https://commons.wikimedia.org/wiki/File:x.jpg" tx={tx} />);
    expect(lines('사진: Wikimedia Commons / 아주 긴 작성자 이름이 들어간 경우')).toBe(1);
    expect(lines('CC BY-SA 3.0')).toBe(1);
  });

  it('라이선스가 있으면 띠를 누르면 파일 페이지가 열린다 — 읽는 이름은 출처와 라이선스를 함께', () => {
    const open = jest.spyOn(Linking, 'openURL').mockResolvedValue(true);
    render(<PhotoCreditBar source="Wikimedia Commons" license="CC BY-SA 3.0" licenseUrl="https://commons.wikimedia.org/wiki/File:x.jpg" tx={tx} />);
    fireEvent.press(screen.getByRole('link', { name: '사진: Wikimedia Commons · CC BY-SA 3.0' }));
    expect(open).toHaveBeenCalledWith('https://commons.wikimedia.org/wiki/File:x.jpg');
    open.mockRestore();
  });

  it('어두운 띠에 흰 글자 — 어떤 사진 위에서도 읽힌다', () => {
    render(<PhotoCreditBar source="한국관광공사" license={null} licenseUrl={null} tx={tx} />);
    const text = screen.getByText('사진: 한국관광공사') as unknown as Node;
    expect((StyleSheet.flatten(text.props.style as never) as { color?: string }).color).toBe(color.text.onAction);
  });

  it('🔴 홈(공용 사진 부품)과 둘러보기가 같은 띠를 쓴다 — 흰 알약·「사진 제공:」이 남아 있지 않다', () => {
    const root = join(__dirname, '..', '..', '..');
    for (const file of ['src/components/PlaceVisual.tsx', 'app/explore.tsx']) {
      const source = readFileSync(join(root, file), 'utf8') as string;
      expect(source).toContain('<PhotoCreditBar');
      expect(source).not.toContain('사진 제공');
    }
  });
});
