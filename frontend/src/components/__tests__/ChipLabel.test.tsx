// 칩 안의 «이름 하나» 글자 — S15P21E201-2054.
//
// 🔴 갤럭시 S22 실기기(앱 1.0.3, 2026-10-14): 앱 언어 칩의 「繁體中文」이 「繁體中」으로 끝 글자가 잘렸다. S15P21E201-1988 의
//    «한 줄 · 넘치면 글자 줄이기»(numberOfLines + adjustsFontSizeToFit)가 들어간 앱에서도 그대로였다.
//    원인과 처방은 src/components/ChipLabel.tsx 머리말에 있다. 시험 환경(jest)에는 안드로이드 글자 그리기가 없어서 «잘림 자체»는 못 재고,
//    대신 처방 셋 — 칩 폭 전체로 펴기 · 굵기 낮추기 · 글자 줄이기를 못 쓰게 막기 — 을 박아 둔다. 안드로이드 실기기 확인이 필요하다.
import { render } from '@testing-library/react-native';
import { Platform, StyleSheet, Text as RNText } from 'react-native';

import { ChipLabel } from '../ChipLabel';

const styleOf = (node: { props: { style?: unknown } }) => StyleSheet.flatten(node.props.style as never) as Record<string, unknown>;

describe('ChipLabel', () => {
  it('🔴 칩 폭 전체로 펴고 가운데 맞춘다 — 잰 폭과 그려지는 폭 사이에 여유가 생긴다', () => {
    const view = render(<ChipLabel>繁體中文</ChipLabel>);
    const style = styleOf(view.getByText('繁體中文'));
    expect(style.alignSelf).toBe('stretch');
    expect(style.textAlign).toBe('center');
  });

  it('🔴 굵기는 medium(500) — 한자 대체 글꼴에 가짜 굵게가 붙는 문턱(600) 아래다', () => {
    const view = render(<ChipLabel>繁體中文</ChipLabel>);
    const style = styleOf(view.getByText('繁體中文'));
    expect(style.fontWeight).toBe('500');
    expect(Number(style.fontWeight)).toBeLessThan(600);
  });

  it('한글·영문은 Pretendard-Medium, 가나·한자는 기기 글꼴(fontFamily 없음) — 1942 의 약속 그대로', () => {
    const view = render(<><ChipLabel>English</ChipLabel><ChipLabel>한국어</ChipLabel><ChipLabel>日本語</ChipLabel><ChipLabel>简体中文</ChipLabel></>);
    expect(styleOf(view.getByText('English')).fontFamily).toBe('Pretendard-Medium');
    expect(styleOf(view.getByText('한국어')).fontFamily).toBe('Pretendard-Medium');
    expect(styleOf(view.getByText('日本語')).fontFamily).toBeUndefined();
    expect(styleOf(view.getByText('简体中文')).fontFamily).toBeUndefined();
  });

  it('🔴 한 줄 자르기와 글자 줄이기를 쓰지 않는다 — 잘림을 숨기지 않고, 넘치면 줄이 바뀐다', () => {
    const view = render(<ChipLabel>繁體中文</ChipLabel>);
    const props = view.getByText('繁體中文').props;
    expect(props.numberOfLines).toBeUndefined();
    expect(props.adjustsFontSizeToFit).toBeUndefined();
    expect(props.minimumFontScale).toBeUndefined();
  });

  it('부르는 쪽 스타일은 뒤에 얹는다 — 색을 따로 줄 수 있다', () => {
    const view = render(<ChipLabel color="#ffffff" style={{ marginTop: 2 }}>English</ChipLabel>);
    const style = styleOf(view.getByText('English'));
    expect(style.color).toBe('#ffffff');
    expect(style.marginTop).toBe(2);
    expect(style.alignSelf).toBe('stretch');
  });

  it('🔴 타입이 글자 줄이기·한 줄 자르기·굵기를 막는다 — tsc 가 돌려서 지킨다', () => {
    // @ts-expect-error numberOfLines 는 받지 않는다 — 잘림을 숨기는 처방이다
    render(<ChipLabel numberOfLines={1}>가</ChipLabel>);
    // @ts-expect-error adjustsFontSizeToFit 는 받지 않는다
    render(<ChipLabel adjustsFontSizeToFit>가</ChipLabel>);
    // @ts-expect-error weight 는 이 부품이 정한다(medium)
    render(<ChipLabel weight="bold">가</ChipLabel>);
  });
});

describe('ChipLabel — 안드로이드', () => {
  const original = Platform.OS;
  beforeAll(() => { Object.defineProperty(Platform, 'OS', { get: () => 'android', configurable: true }); });
  afterAll(() => { Object.defineProperty(Platform, 'OS', { get: () => original, configurable: true }); });

  it('「✓ 日本語」처럼 고른 칩의 글자는 한 문자열로 그려진다 — 1966 의 약속이 이 부품에서도 산다', () => {
    const view = render(<ChipLabel>{`✓ ${'日本語'}`}</ChipLabel>);
    expect(view.UNSAFE_getByType(RNText).props.children).toBe('✓ 日本語');
  });

  it('안드로이드에서도 굵기는 500 이고 줄 수·글자 크기 속성은 없다', () => {
    const view = render(<ChipLabel>繁體中文</ChipLabel>);
    const node = view.UNSAFE_getByType(RNText);
    expect(styleOf(node).fontWeight).toBe('500');
    expect(node.props.numberOfLines).toBeUndefined();
    expect(node.props.adjustsFontSizeToFit).toBeUndefined();
    // 줄 나누기는 «simple» — 1972 에서 balanced 가 마지막 낱말을 잘라 먹었다
    expect(node.props.textBreakStrategy).toBe('simple');
  });
});
