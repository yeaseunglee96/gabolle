import { indexFromOffset, ROW_HEIGHT } from '../WheelPicker';
import { categoryLabel, categoryWheelCodes, PLACE_CATEGORY_LABELS, sortCategoryCodes } from '@/discovery/placeCategoryLabels';

// 분류 라벨표와 회전 휠 (S15P21E201-1071). 시안 `design_handoff_collection`.
//
// 🔴 이 시험이 지키는 것은 둘이다.
//   ① **모르는 분류 코드를 버리지 않는다.** 버리면 서버에는 있는 분류를 사용자가 영영 못 고른다
//   ② **휠이 목록 밖으로 나가지 않는다.** 스크롤은 관성으로 끝을 넘어가는데, 그때 없는
//      줄을 집으면 화면이 죽는다

describe('분류 코드를 사람이 읽는 말로', () => {
  it('아는 코드는 한글로 바꾼다', () => {
    expect(categoryLabel('FOOD', 'ko')).toBe('맛집');
    expect(categoryLabel('FOOD', 'en')).toBe('Food');
  });

  // 🔴 「기타」나 「알 수 없음」으로 바꾸지 않는다. 그건 서버가 보낸 것을 우리가 지우는 것이다.
  it('모르는 코드는 코드 그대로 보여준다', () => {
    expect(categoryLabel('SOMETHING_NEW', 'ko')).toBe('SOMETHING_NEW');
    expect(categoryLabel('SOMETHING_NEW', 'en')).toBe('SOMETHING_NEW');
  });
});

describe('휠에 놓는 순서', () => {
  it('아는 코드를 표 순서대로 먼저 놓는다', () => {
    const sorted = sortCategoryCodes(['NATURE_WALK', 'FOOD', 'CAFE_HEALING']);
    expect(sorted).toEqual(['FOOD', 'CAFE_HEALING', 'NATURE_WALK']);
  });

  // 🔴 여기가 핵심이다. 모르는 코드를 버리면 그 분류는 화면에서 사라진다.
  it('모르는 코드를 버리지 않고 뒤에 붙인다', () => {
    const sorted = sortCategoryCodes(['UNKNOWN_A', 'FOOD', 'UNKNOWN_B']);
    expect(sorted).toEqual(['FOOD', 'UNKNOWN_A', 'UNKNOWN_B']);
  });

  it('표에 있어도 서버가 안 준 코드는 안 넣는다', () => {
    expect(sortCategoryCodes(['FOOD'])).toEqual(['FOOD']);
    expect(Object.keys(PLACE_CATEGORY_LABELS).length).toBeGreaterThan(1);
  });
});

describe('휠이 어느 줄을 골랐나', () => {
  it('한 줄 높이로 나눠 가까운 줄을 고른다', () => {
    expect(indexFromOffset(0, 5)).toBe(0);
    expect(indexFromOffset(ROW_HEIGHT, 5)).toBe(1);
    expect(indexFromOffset(ROW_HEIGHT * 2 + 4, 5)).toBe(2);
  });

  // 🔴 관성으로 끝을 넘어가도 목록 밖을 집지 않는다. 집으면 화면이 죽는다.
  it('위아래 끝을 넘어가지 않는다', () => {
    expect(indexFromOffset(-200, 5)).toBe(0);
    expect(indexFromOffset(ROW_HEIGHT * 99, 5)).toBe(4);
  });

  it('목록이 비어도 죽지 않는다', () => {
    expect(indexFromOffset(0, 0)).toBe(0);
    expect(indexFromOffset(ROW_HEIGHT * 3, 0)).toBe(0);
  });
});

describe("휠에 놓을 분류를 정한다", () => {
  // 🔴 여기가 핵심이다. 서버가 안 되는 날에도 사용자는 장소를 담는다.
  it("서버 목록을 못 받으면 아는 코드로 채운다 — 비우지 않는다", () => {
    expect(categoryWheelCodes(null).length).toBeGreaterThan(0);
    expect(categoryWheelCodes(undefined).length).toBeGreaterThan(0);
    expect(categoryWheelCodes([]).length).toBeGreaterThan(0);
  });

  it("서버가 준 코드를 쓴다", () => {
    expect(categoryWheelCodes(["FOOD", "CULTURE"])).toContain("FOOD");
    expect(categoryWheelCodes(["FOOD", "CULTURE"])).toContain("CULTURE");
  });

  // 🔴 서버가 준 것만 쓰면 우리가 아는 분류가 사라지고, 아는 것만 쓰면 서버의 새 분류가
  // 사라진다. 둘 다 남긴다.
  it("서버에만 있는 코드도 우리만 아는 코드도 둘 다 남는다", () => {
    const codes = categoryWheelCodes(["SERVER_ONLY_NEW"]);
    expect(codes).toContain("SERVER_ONLY_NEW");
    expect(codes).toContain("FOOD");
  });

  it("아는 코드를 앞에, 모르는 코드를 뒤에 놓는다", () => {
    const codes = categoryWheelCodes(["SERVER_ONLY_NEW", "FOOD"]);
    expect(codes.indexOf("FOOD")).toBeLessThan(codes.indexOf("SERVER_ONLY_NEW"));
  });

  it("같은 코드가 두 번 들어가지 않는다", () => {
    const codes = categoryWheelCodes(["FOOD", "FOOD"]);
    expect(codes.filter((c) => c === "FOOD")).toHaveLength(1);
  });
});
