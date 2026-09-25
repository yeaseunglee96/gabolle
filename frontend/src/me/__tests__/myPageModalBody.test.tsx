// 넓은 화면 마이페이지 창의 본문 바탕 — S15P21E201-1659.
//
// 🔴 이 시험이 지키는 것: 창 본문은 폰 창(MyPageSheet)처럼 연회색(canvas)이고 머리 아래 선이 있다.
//    전에는 창 전체가 아이보리라 흰 카드(저장한 기록·내 댓글)가 창 바탕에 묻혀 선 하나로만 갈렸다.
import { render, screen } from '@testing-library/react-native';
import { ScrollView, StyleSheet, Text } from 'react-native';

import { color } from '@/design/tokens';
import { MyPageModal } from '@/me/MyPageModal';

describe('넓은 화면 마이페이지 창', () => {
  it('🔴 본문은 연회색 바탕에 머리 아래 선 — 폰 창과 같다', () => {
    render(<MyPageModal open title="저장한 기록" description="설명" onClose={() => {}} tx={(ko) => ko}><Text>카드</Text></MyPageModal>);
    const body = StyleSheet.flatten(screen.UNSAFE_getByType(ScrollView).props.style) as Record<string, unknown>;
    expect(body).toMatchObject({ backgroundColor: color.canvas, borderTopWidth: 1, borderTopColor: color.surface.border });
    expect(screen.getByText('카드')).toBeTruthy();
  });
});
