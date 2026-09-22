// — 일정 화면에 경고 코드가 그대로 노출되던 것.
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
      // 어느 코드가 비었는지 알 수 있게 코드 이름을 값에 실어 비교한다
      // jest 의 expect 는 vitest 와 달리 설명 문구를 두 번째 인자로 안 받는다.
      expect([code, label[0].trim().length > 0]).toEqual([code, true]);
      expect([code, label[1].trim().length > 0]).toEqual([code, true]);
    }
  });
});

// — 서버가 보내는데 사전에 짝이 없어 조용히 사라지던 여덟 개.
describe('서버가 실제로 보내는 코드는 하나도 안 사라진다', () => {
  // 백엔드에서 직접 확인한 목록이다(RecommendationCodes · ItineraryWarningCodes · 채점기).
  // 여기 추가할 때는 이름을 짐작하지 말고 백엔드 코드를 열어 확인한다.
  const 서버가_보내는_코드 = [
    'STAIRS_PRESENT',
    'WALKING_OVER_LIMIT',
    'ACCESSIBILITY_UNVERIFIED',
    'CONSTRAINT_UNKNOWN',
    'UNKNOWN_CONSTRAINT_EXCLUDED',
    'UNKNOWN_SEVERITY_REQUIRED',
    'UNKNOWN_SEVERITY_PREFERRED',
    'SCORE_MISSING',
    'SIGHT_SLOT_UNFILLED',
    'RECALC_NO_CANDIDATE',
    'RECALC_TIMES_RESHUFFLED',
    'RECALC_DAY_PARTIALLY_FILLED',
    // 🔴 -1468 의 ㄴ. 서버가 «아직» warnings 로 안 보낸다 — 앱을 먼저 넓히는 것이 순서다.
    //    (BaselineCandidateScorer 가 지금은 unknownFacts 에 담는다: fact=DIET_SUPPORT_UNVERIFIED)
    'DIET_SUPPORT_UNVERIFIED',
  ];

  it.each(서버가_보내는_코드)('%s 에 한국어·영어 짝이 있다', (code) => {
    expect(describeWarningCodes([code], ko)).toHaveLength(1);
    expect(describeWarningCodes([code], en)).toHaveLength(1);
  });

  it('🔴 「안 재 봤다」를 「안 된다」로 옮기지 않는다', () => {
    // 이 넷은 확인을 못 한 것이지 조건에 어긋난 것이 아니다. 「못 간다」로 읽히면
    // 갈 수 있는 곳을 못 가게 만든다 — 접근성 모달에서 정한 선이다.
    for (const code of ['CONSTRAINT_UNKNOWN', 'UNKNOWN_CONSTRAINT_EXCLUDED', 'UNKNOWN_SEVERITY_REQUIRED', 'UNKNOWN_SEVERITY_PREFERRED']) {
      const [korean] = describeWarningCodes([code], ko);
      const [english] = describeWarningCodes([code], en);
      expect(korean).toMatch(/확인하지 못했|확인이 안 돼/);
      expect(english.toLowerCase()).toContain("couldn't check");
      // 「불가」·「안 됩니다」로 단정하지 않는다.
      expect(korean).not.toMatch(/불가|없습니다|안 돼요$/);
    }
  });

  it('영문 코드가 문장에 그대로 섞여 나가지 않는다', () => {
    for (const code of 서버가_보내는_코드) {
      const [korean] = describeWarningCodes([code], ko);
      expect(korean).not.toContain(code);
      expect(korean).not.toMatch(/[A-Z]{3,}_[A-Z]/);
    }
  });
});

// ── 식단 미확인 — S15P21E201-1468 의 ㄴ ──────────────────────────────────────
describe('식단을 확인 못 한 곳', () => {
  const tx = (ko: string) => ko;

  it('🔴 「안 된다」가 아니라 「안 재 봤다」로 말한다', () => {
    const [message] = describeWarningCodes(['DIET_SUPPORT_UNVERIFIED'], tx);
    expect(message).toContain('확인되지 않은');
    // 갈 수 있는 곳을 못 가게 만드는 말이 섞이면 안 된다.
    expect(message).not.toContain('안 돼요');
    expect(message).not.toContain('불가');
  });

  it('🔴 알레르기는 이 사전에 «없다» — 아예 묻지 않기로 했다 (-1497)', () => {
    // 접근성·식단이 틀리면 불편하고, 알레르기가 틀리면 사람이 다친다.
    // 경고로 내보내는 길을 열어 두면 「확인 못 했지만 추천함」이 되어 그 선이 무너진다.
    expect(describeWarningCodes(['ALLERGEN_UNVERIFIED'], tx)).toEqual([]);
  });
});
