// — 일정 화면에 경고 코드가 그대로 노출되던 것.
import { pickLanguage } from '@/i18n/pick';

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
    // 휠체어 후속(백엔드 fix/back/wheelchair-followups) — 「되도록」 휠체어·유아차인데 못 간다고 확인된 곳을 빼지 않고 경고로 남긴다.
    'ACCESS_VERIFIED_UNAVAILABLE',
    // 둘레 길 경사 — 이제 코스의 이동 경고(mobilityWarnings)로도 온다.
    'SLOPE_OVER_LIMIT',
  ];

  it.each(['ACCESS_VERIFIED_UNAVAILABLE', 'SLOPE_OVER_LIMIT'])('🔴 %s 는 일본어·중국어 화면에서도 번역표로 옮긴다(영어로 새지 않는다)', (code) => {
    const [ko, enText] = WARNING_LABEL[code];
    for (const language of ['ja', 'zh-Hans', 'zh-Hant'] as const) {
      const shown = describeWarningCodes([code], (k, e) => pickLanguage(language, { ko: k, en: e }))[0];
      expect([code, language, shown !== enText && shown !== ko]).toEqual([code, language, true]);
    }
  });

  it('🔴 ACCESS_VERIFIED_UNAVAILABLE 는 「확인됐다」고 말한다 — 「아직 모른다」(ACCESSIBILITY_UNVERIFIED)와 섞지 않는다', () => {
    expect(describeWarningCodes(['ACCESS_VERIFIED_UNAVAILABLE'], ko)[0]).toContain('확인된');
    expect(describeWarningCodes(['ACCESS_VERIFIED_UNAVAILABLE'], ko)[0]).not.toContain('아직');
    expect(describeWarningCodes(['ACCESS_VERIFIED_UNAVAILABLE', 'ACCESSIBILITY_UNVERIFIED'], ko)).toHaveLength(2);
  });

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

  // 🔴 2026-09-25 정정(S15P21E201-1640) — 여기는 「알레르기는 이 사전에 없다(-1497)」였다. 사용자가 새로 정했다:
  //    예전에 알레르기를 「반드시」로 건 여행은 다시 짜면 후보가 전부 빠져 실패했으니, 빼지 않고 이 경고로 남긴다.
  //    들었다고 «확인된» 곳은 서버가 여전히 뺀다. 이 경고는 「모른다」이고, 사람이 다칠 수 있으니 「물어보라」를 붙인다.
  it('🔴 알레르기는 「들었다」가 아니라 「확인 안 됨」 — 그리고 가게에 물어보라고 말한다', () => {
    const [message] = describeWarningCodes(['ALLERGEN_UNVERIFIED'], tx);
    expect(message).toContain('확인되지 않은');
    expect(message).toContain('물어보세요');
    expect(message).not.toContain('들었어요');
  });
});

// ── 새 낱말 — S15P21E201-1640 ─────────────────────────────────────────────────
describe('주변 길이 가파른 곳 · 접근성 문구', () => {
  const tx = (ko: string) => ko;

  it('🔴 가파른 곳은 사실만 말한다 — 「못 간다」가 아니라 「힘들 수 있다」, 그리고 추정임을 밝힌다', () => {
    const [message] = describeWarningCodes(['SLOPE_OVER_LIMIT'], tx);
    expect(message).toContain('가팔라요');
    expect(message).toContain('추정');
    expect(message).not.toContain('못 가');
    expect(message).not.toContain('불가');
    // 🔴 휠체어만이 아니다 — 유아차를 고른 사람에게도 붙는 경고다.
    //    「큰 짐」은 뺐다(S15P21E201-1855) — 앱이 더는 그 조건을 묻지 않으므로, 적으면
    //    고른 적 없는 조건을 말하는 셈이 된다.
    expect(message).toContain('유아차');
    expect(message).not.toContain('큰 짐');
  });

  it('접근성 미확인은 휠체어만이 아니라 유아차를 고른 사람에게도 맞는 말이다', () => {
    const [message] = describeWarningCodes(['ACCESSIBILITY_UNVERIFIED'], tx);
    expect(message).toContain('유아차');
    expect(message).toContain('확인되지 않은');
    // 묻지 않는 조건은 말하지 않는다 — S15P21E201-1855.
    expect(message).not.toContain('큰 짐');
  });
});
