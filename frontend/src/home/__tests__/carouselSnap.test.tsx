// 가로 카드 목록이 카드 «한 장» 단위로 멈춘다 (S15P21E201-1797).
//
// 🔴 전에는 멈추는 자리를 정하지 않아서(decelerationRate 만 있었다) 갤럭시 S10 홈의
//    「지금 부산에서 남긴 기록」이 첫 카드가 반쯤 잘린 채 섰다.
//    snapToInterval(**손을 뗀 뒤 이 값의 배수 자리에서만 서게 하는 설정**)이
//    카드 폭 + 간격과 같아야 한 장씩 맞아 선다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');

import { render } from '@testing-library/react-native';
import { ScrollView, Text } from 'react-native';

import { HomeRow, homeCardWidth } from '@/home/HomeRow';
import { spacing } from '@/design/tokens';

jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

const row = (cardWidth?: number) =>
  render(
    <HomeRow title="기록" onOpen={jest.fn()} openLabel="전체" width={360} gutter={16} arrows={false} cardWidth={cardWidth}>
      <Text>카드</Text>
    </HomeRow>,
  ).UNSAFE_getByType(ScrollView).props;

describe('가로 카드 목록 — 카드 단위로 멈춘다', () => {
  it('🔴 홈 줄: 멈추는 간격이 카드 폭 + 간격이다', () => {
    const props = row();
    expect(props.snapToInterval).toBe(homeCardWidth(360) + spacing[3]);
    expect(props.snapToAlignment).toBe('start');
    expect(props.decelerationRate).toBe('fast');
  });

  it('🔴 카드 폭을 따로 준 줄(마이페이지 4열)도 그 폭으로 멈춘다', () => {
    expect(row(240).snapToInterval).toBe(240 + spacing[3]);
  });

  it('🔴 추천 화면의 정차지 줄: 한 칸 단위로 멈추고, 굴리는 동안 같은 화면이면 다시 그리지 않는다', () => {
    const source: string = readFileSync(`${__dirname}/../../../app/trips/[id]/recommendations.tsx`, 'utf8');
    expect(source).toMatch(/const STRIP_STEP = STRIP_CARD_WIDTH \+ STRIP_LINK_WIDTH \+ spacing\[2\] \* 2;/);
    expect(source).toMatch(/snapToInterval=\{STRIP_STEP\}/);
    // 전에는 굴릴 때마다 left 를 무조건 새 객체로 넣어 화면 전체가 초당 60번 다시 그려졌다.
    expect(source).not.toMatch(/onScroll=\{\(event\) => setStrip\(\(prev\) => \(\{ \.\.\.prev, left:/);
    expect(source).toMatch(/\? prev\s*: \{ \.\.\.prev, left \}/);
  });
});
