// ⋯ 메뉴가 어디에 뜨나 — 누른 버튼 바로 아래(S15P21E201-1576).
import { menuPosition } from '@/components/DropdownMenu';
import { spacing } from '@/design/tokens';

const PHONE = { width: 390, height: 844 };
const INSETS = { top: 47, bottom: 34 };

describe('menuPosition', () => {
  it('버튼 바로 아래, 버튼 오른쪽 끝에 맞춘다', () => {
    const button = { x: 300, y: 100, width: 32, height: 32 };
    expect(menuPosition(button, 2, PHONE, INSETS)).toEqual({ top: 136, right: 58 });
  });

  it('아래가 모자라면 버튼 위로 올린다 — 화면 바닥의 댓글 ⋯', () => {
    const button = { x: 300, y: 780, width: 32, height: 32 };
    const { top } = menuPosition(button, 2, PHONE, INSETS);
    expect(top + 2 * 49).toBeLessThanOrEqual(button.y);
  });

  it('버튼을 아직 안 쟀으면 예전 자리(화면 오른쪽 위)', () => {
    expect(menuPosition(null, 2, PHONE, INSETS)).toEqual({ top: 47 + 64, right: spacing[4] });
  });
});
