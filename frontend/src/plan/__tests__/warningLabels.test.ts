// S15P21E201-1150 — 일정 화면에 경고 코드가 그대로 노출되던 것.
//
// 🔴 이 시험이 지키는 것은 하나다 — **모르는 코드는 화면에 안 나간다.**
//    사람이 지킬 규칙이 아니라 시험이 지켜야 한다. 백엔드가 코드를 하나 늘릴 때마다
//    화면에 암호가 뜨는 일이 다시 생기면 여기가 빨개진다.
import { describeWarningCodes, WARNING_LABEL } from '../warningLabels';

const ko = (korean: string) => korean;
const en = (_korean: string, english: string) => english;

describe('describeWarningCodes', () => {
  it('아는 코드는 사람 말로 바꾼다', () => {
    expect(describeWarningCodes(['RECALC_NO_CANDIDATE'], ko))
      .toEqual(['뺀 자리를 채울 다른 장소를 찾지 못해 비워 뒀어요.']);
  });

  it('🔴 모르는 코드는 아예 빼 버린다 — 1150 이 난 자리다', () => {
    expect(describeWarningCodes(['NEVER_SEEN_BEFORE'], ko)).toEqual([]);
    // 실제로 화면에 떴던 값. 짝을 붙였으니 이제는 문장이 나와야 한다.
    expect(describeWarningCodes(['SIGHT_SLOT_UNFILLED'], ko))
      .toEqual(['관광지 자리를 다 채우지 못했어요. 대신 식당으로 메우지는 않았어요.']);
  });

  it('아는 것과 모르는 것이 섞여 오면 아는 것만 남긴다', () => {
    expect(describeWarningCodes(['RECALC_NO_CANDIDATE', 'WHAT_IS_THIS', 'SIGHT_SLOT_UNFILLED'], ko))
      .toHaveLength(2);
  });

  it('같은 코드가 두 번 와도 한 번만 말한다', () => {
    expect(describeWarningCodes(['SIGHT_SLOT_UNFILLED', 'SIGHT_SLOT_UNFILLED'], ko)).toHaveLength(1);
  });

  it('값이 없거나 모양이 아니면 빈 목록을 준다', () => {
    expect(describeWarningCodes(undefined, ko)).toEqual([]);
    expect(describeWarningCodes(null, ko)).toEqual([]);
    expect(describeWarningCodes([], ko)).toEqual([]);
  });

  it('영어 화면에서는 영어로 준다', () => {
    expect(describeWarningCodes(['SIGHT_SLOT_UNFILLED'], en))
      .toEqual(["We couldn't fill every sightseeing slot — and we didn't pad them with restaurants."]);
  });

  it('모든 코드에 한국어와 영어가 둘 다 있다 — 한쪽만 적으면 다른 화면이 빈다', () => {
    for (const [code, label] of Object.entries(WARNING_LABEL)) {
      expect(label).toHaveLength(2);
      // 어느 코드가 비었는지 알 수 있게 코드 이름을 값에 실어 비교한다 —
      // jest 의 expect 는 vitest 와 달리 설명 문구를 두 번째 인자로 안 받는다.
      expect([code, label[0].trim().length > 0]).toEqual([code, true]);
      expect([code, label[1].trim().length > 0]).toEqual([code, true]);
    }
  });
});
