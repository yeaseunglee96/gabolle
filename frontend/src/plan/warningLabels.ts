// 일정 편집이 돌려주는 경고 코드를 사람 말로 옮긴다 — S15P21E201-1150.
//
// 🔴 이 파일이 생긴 이유는 화면에 `SIGHT_SLOT_UNFILLED` 가 그대로 떴기 때문이다.
//
//    2026-09-17 안드로이드 실기기(versionCode 9)에서 일정 화면 맨 위 경고 칸에
//    영문 대문자 식별자가 그대로 보였다. 바로 아래 칸은 사람 말이라 더 도드라졌다.
//
//      SIGHT_SLOT_UNFILLED                                        ← 이것
//      1곳이 하루를 넘길 위험이 있어요. (기록이 적어 추정값이에요)   ← 바로 아래
//
//    백엔드는 제 몫을 했다. 경고 코드를 하나 새로 내보냈을 뿐이다. 화면이 그것을
//    옮길 짝을 안 가지고 있었고, 짝이 없으면 **코드를 그대로 그리게** 돼 있었다.
//
// 🔴 그래서 규칙을 바꾼다 — 모르는 코드는 아예 안 그린다.
//
//    짝이 없는 코드를 보여 주는 것은 아무 말도 안 하는 것보다 나쁘다. 이 앱의 주
//    사용자는 부산에 온 외국인 관광객이고, 그들에게 SIGHT_SLOT_UNFILLED 는 한국어도
//    영어도 아니다. 그리고 이 칸은 「이 일정에 무슨 문제가 있는지」를 알리는 자리라,
//    거기 암호가 뜨면 칸 자체를 못 믿게 된다.
//
//    안 그리면 그 경고를 못 본다는 대가가 있다. 그래도 이쪽이 낫다 — 못 읽는 경고는
//    어차피 못 본 것과 같고, 코드를 늘릴 때마다 화면이 깨지지는 않게 된다.
//
// 🔴 판단을 화면 안에 조건문으로 두지 않고 여기로 꺼낸 이유는 시험이 붙들게 하려는
//    것이다. 「모르는 코드는 안 그린다」는 사람이 지키는 것이 아니라 시험이 지켜야 한다.

type Translate = (ko: string, en: string) => string;

/** 코드 → [한국어, 영어]. 여기 없는 코드는 화면에 안 나간다. */
export const WARNING_LABEL: Record<string, [string, string]> = {
  RECALC_NO_CANDIDATE: [
    '뺀 자리를 채울 다른 장소를 찾지 못해 비워 뒀어요.',
    "We couldn't find another place to fill the removed spot, so it's left empty.",
  ],
  RECALC_TIMES_RESHUFFLED: [
    '다시 계산하면서 고정된 장소의 시각도 함께 조정됐어요.',
    'Recalculating also adjusted the times of locked places.',
  ],
  // 2026-09-17 백엔드 추가(장효준). 관광지 자리를 못 채웠을 때, 그 자리를 식당으로
  // 억지로 메우지 않고 비워 둔다는 뜻이다 (S15P21E201-1129 와 같은 줄기).
  SIGHT_SLOT_UNFILLED: [
    '관광지 자리를 다 채우지 못했어요. 대신 식당으로 메우지는 않았어요.',
    "We couldn't fill every sightseeing slot — and we didn't pad them with restaurants.",
  ],
  // 2026-09-17 추가(S15P21E201-1160). 지금은 추천 완료 창이 이 코드를 읽어 안내 창을
  // 띄우고, 일정 화면 경고 칸에는 아직 안 나온다. 그래도 미리 넣어 둔다 — 서버가 이 값을
  // 일정 상세에도 싣기 시작하는 날(S15P21E201-1158) 짝이 없으면 조용히 안 그려진다.
  //
  // 🔴 "못 간다" 가 아니라 "안 재 봤다" 로 옮긴다. 영어도 Not verified 이지
  //    Not accessible 이 아니다 — 갈 수 있는 곳을 못 가게 만드는 말이 된다.
  ACCESSIBILITY_UNVERIFIED: [
    '휠체어로 들어갈 수 있는지 아직 확인되지 않은 곳이에요.',
    'Wheelchair access here has not been checked yet.',
  ],

  // ── 2026-09-17 (S15P21E201-1171) — 여기 여덟을 한꺼번에 넣는다 ───────────────────
  //
  // 🔴 서버는 이것들을 **이미 보내고 있었다.** 사전에 짝이 없어서 화면에 아무것도 안 나갔다.
  //    아침(S15P21E201-1150)엔 암호가 **보이는 것**이 문제였는데, 그걸 「안 보이게」로 고친
  //    뒤로는 **진짜 경고가 같이 삼켜지는 것**이 문제가 됐다. 안전망은 목표가 아니다.
  //
  // 뜻은 백엔드 코드에서 직접 읽었다(RecommendationCodes · ItineraryWarningCodes ·
  // 채점기). 코드 이름을 보고 지어내지 않았다.

  /** 채점기: 계단 회피를 원했는데 그 장소에 계단이 있다. FAIL 이 아니라 경고 + 감점이다. */
  STAIRS_PRESENT: [
    '이곳엔 계단이 있어요. 계단을 피하고 싶다고 하셨죠.',
    'This place has stairs — you asked to avoid them.',
  ],
  /** 채점기: 정한 걷기 한도(MAX_WALKING_METERS)를 넘는다. 역시 FAIL 이 아니다. */
  WALKING_OVER_LIMIT: [
    '정하신 걷는 거리보다 멀어요.',
    'Farther than the walking distance you set.',
  ],

  // 🔴 아래 넷은 전부 「안 재 봤다」이지 「안 된다」가 아니다.
  //    「못 간다」로 읽히면 갈 수 있는 곳을 못 가게 만든다 — 접근성 모달(S15P21E201-1160)
  //    에서 정한 선과 같다. 그래서 「안 맞는다는 뜻은 아니에요」를 일부러 붙였다.

  /** 「제약을 확인하지 못했다. PASS 로 바꾼 것이 아니고 제외하지도 않는다」 */
  CONSTRAINT_UNKNOWN: [
    '조건에 맞는지 확인하지 못했어요. 안 맞는다는 뜻은 아니에요.',
    "We couldn't check this against your conditions — it doesn't mean it fails them.",
  ],
  /** 「몰라서 뺐다」 — CONSTRAINT_UNKNOWN 과 함께 붙는다. */
  UNKNOWN_CONSTRAINT_EXCLUDED: [
    '확인이 안 돼서 뺐어요. 안 맞아서가 아니라 알 수 없어서예요.',
    "Left out because we couldn't check it — not because it failed.",
  ],
  /** 못 확인한 제약이 «필수» 등급이었다. warningForSeverity() 가 만든다. */
  UNKNOWN_SEVERITY_REQUIRED: [
    '꼭 필요하다고 하신 조건인데 확인하지 못했어요.',
    "We couldn't check a condition you marked as required.",
  ],
  /** 못 확인한 제약이 «선호» 등급이었다. */
  UNKNOWN_SEVERITY_PREFERRED: [
    '선호하신다고 한 조건인데 확인하지 못했어요.',
    "We couldn't check a condition you preferred.",
  ],

  /**
   * 「점수가 없어 순위를 매기지 못했다」.
   *
   * 🔴 「점수가 없습니다」로 옮기지 않는다 — 사용자는 점수가 무엇인지 모른다. 사전에 넣고도
   * 아무 말도 안 한 것이 된다.
   */
  SCORE_MISSING: [
    '비교할 자료가 모자라 순서를 정하지 못했어요.',
    'Not enough data to rank this one.',
  ],
  /** 「일부만 채웠다. 후보가 자리보다 적었다」 */
  RECALC_DAY_PARTIALLY_FILLED: [
    '그날 일정을 일부만 채웠어요. 넣을 곳이 모자랐어요.',
    'Only part of that day was filled — not enough places to add.',
  ],
};

/**
 * 경고 코드 목록을 화면에 쓸 문장으로 바꾼다.
 *
 * <p>짝이 없는 코드는 **뺀다**. 그 자리에 코드를 그대로 두지 않는다.
 */
export function describeWarningCodes(codes: string[] | undefined | null, tx: Translate): string[] {
  if (!Array.isArray(codes)) return [];
  const seen = new Set<string>();
  const messages: string[] = [];
  for (const code of codes) {
    const label = WARNING_LABEL[code];
    if (!label) continue;
    // 같은 코드가 두 번 와도 한 번만 말한다 — 같은 문장이 두 줄이면 고장으로 보인다.
    if (seen.has(code)) continue;
    seen.add(code);
    messages.push(tx(...label));
  }
  return messages;
}
