// 목록 카드 미리보기 — S15P21E201-1649.
//
// 🔴 이 시험이 지키는 것(사용자 결정 (다)):
//    ① 제목(#·##·###) 줄이었던 곳은 굵게, 크기는 본문 그대로다.
//    ② 한 글자 상자 안에 굵기만 섞는다 — 줄 수 자르기(numberOfLines)가 바깥 상자 하나에 그대로 걸린다.
//    ③ 본문은 문자열 그대로 넘긴다 — 안드로이드에서 한글 낱말이 중간에 끊기지 않게 하는 처리가 문자열에만 걸린다.
import { render, screen } from '@testing-library/react-native';
import { StyleSheet } from 'react-native';

import { MarkdownPreview } from '../MarkdownPreview';

const flat = (style: unknown) => StyleSheet.flatten(style as never) as Record<string, unknown>;

describe('목록 카드 미리보기', () => {
  it('🔴 제목 줄은 굵게, 크기는 본문과 같다', () => {
    render(<MarkdownPreview source={'# 해운대 하루\n\n바다가 좋았어요'} variant="body" numberOfLines={2} />);
    const heading = flat(screen.getByText('해운대 하루').props.style);
    const outer = screen.getByText(/바다가 좋았어요/);
    expect(heading.fontWeight).toBe('700');
    expect(flat(outer.props.style).fontWeight).toBe('400');
    expect(heading.fontSize).toBe(flat(outer.props.style).fontSize);
  });

  it('🔴 줄 수 자르기는 바깥 상자 하나에 걸린다 — 제목과 본문이 한 상자 안에 있다', () => {
    render(<MarkdownPreview source={'## 먹은 것\n\n돼지국밥'} variant="body" numberOfLines={2} />);
    const outer = screen.getByText(/돼지국밥/);
    expect(outer.props.numberOfLines).toBe(2);
    // 제목 글자의 윗줄을 따라 올라가면 바깥 상자를 만난다
    let node = screen.getByText('먹은 것').parent;
    while (node && node !== outer) node = node.parent;
    expect(node).toBe(outer);
    expect(outer.props.children).toEqual(expect.arrayContaining([' 돼지국밥']));
  });

  it('제목이 없는 글은 굵은 글자 없이 문자열 하나다', () => {
    render(<MarkdownPreview source="오늘 해운대에 다녀왔어요." variant="body" numberOfLines={2} />);
    const children = [screen.getByText('오늘 해운대에 다녀왔어요.').props.children].flat();
    expect(children).toEqual(['오늘 해운대에 다녀왔어요.']);
  });
});
