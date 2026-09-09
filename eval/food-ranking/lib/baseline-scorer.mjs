/**
 * **실제로 배포되는 채점기**를 그대로 옮겨 온 것.
 *
 * 원본: `backend/src/main/java/com/gabolle/backend/recommendation/adapter/
 *        BaselineCandidateScorer.java` (브랜치 `origin/back/dev`)
 *
 * 🔴 **왜 옮겨 오나.** 라운드1~3 의 성적은 전부 참고 구현
 *    (`ref/local-route/recommend.ts`, SQLite) 을 잰 것이다. 실제로 서버에 올라가는
 *    것은 Spring 의 `BaselineCandidateScorer`(PostgreSQL) 인데 **그건 한 번도
 *    채점된 적이 없다.** 다른 것을 재고 좋다 나쁘다 말해 온 셈이다.
 *
 * 🔴 **옮겨 오는 것의 위험.** 옮겨 적다가 틀리면 "배포될 것을 쟀다" 는 말 자체가
 *    거짓이 된다. 그래서 자바 원본의 항 이름·기본값·null 처리를 **한 줄씩 그대로**
 *    옮기고, 옮긴 근거를 각 함수 위에 적었다. 고칠 일이 생기면 자바를 먼저 보고
 *    여기를 맞춘다 — 반대로 하지 않는다.
 *
 * ── 총점 (자바 원본 그대로) ──────────────────────────────────────────────────
 *   total  = w.distance            × clamp01(1 − distanceM / radiusM)
 *          + w.interest            × (사용자 CATEGORY 코드 중 장소에도 있는 비율)
 *          + w.atmosphere          × (ATMOSPHERE 코드 …)
 *          + w.cuisine             × (FOOD_PREFERENCE 코드 …)
 *          + w.preferenceAlignment × (정렬 다섯 차원의 가중평균)
 *          + w.popularity          × POPULARITY_SCORE 값 그대로
 *          − 0.05 × 이동 경고 수
 *   total  = max(0, total)
 *
 * 🔴 **구할 수 없는 항은 0 이 아니라 아예 빠진다.** 장소에 그 피처가 없으면 그 항을
 *    더하지 않는다. 그래서 피처가 하나도 없는 장소의 총점은 거리 항 하나뿐이고,
 *    최대 0.30 이다 — 1.0 이 아니다. 이것은 결함이 아니라 설계다(데이터가 없다고
 *    점수를 깎으면 데이터 없는 장소가 전부 밑으로 가라앉는다).
 */

/** 자바 `BaselineEngineProperties.Weights` 의 기본값. 설정으로 덮을 수 있다 */
export const DEFAULT_WEIGHTS = {
  distance: 0.30,
  interest: 0.20,
  atmosphere: 0.15,
  cuisine: 0.15,
  preferenceAlignment: 0.10,
  popularity: 0.10,
};

/** 자바 `PreferenceAlignmentWeights` 의 기본값 — 다섯 다 1.0 (절대 가중치가 아니라 **비율**) */
export const DEFAULT_ALIGNMENT_WEIGHTS = {
  LOCALITY: 1.0,
  QUIETNESS: 1.0,
  TOURIST_PREFERENCE: 1.0,
  SHADE_PREFERENCE: 1.0,
  SLOPE_PREFERENCE: 1.0,
};

/** 자바 `BaselineEngineProperties` 기본값 */
export const DEFAULT_RADIUS_M = 5000;
export const DEFAULT_CANDIDATE_LIMIT = 200;

/** 자바 `BaselineCandidateScorer.MOBILITY_WARNING_PENALTY` — 설정이 아니라 상수다 */
export const MOBILITY_WARNING_PENALTY = 0.05;

/**
 * 자바의 `user_place_code_map` 표 중 `TAG_OVERLAP` 세 줄.
 * (마이그레이션 `V20260904020000__place_feature_codes_and_code_map.sql` 의 INSERT)
 */
export const TAG_DIMENSIONS = [
  { dimension: "CATEGORY", featureType: "INTEREST_TAG", weightKey: "interest" },
  { dimension: "ATMOSPHERE", featureType: "ATMOSPHERE_TAG", weightKey: "atmosphere" },
  { dimension: "FOOD_PREFERENCE", featureType: "CUISINE_TAG", weightKey: "cuisine" },
];

/**
 * 같은 표의 `SCORE_COMPARE` 다섯 줄.
 * 🔴 `SLOPE_PERCENT` 만 0~100 이라 100 으로 나눈다 (자바 `placeValueIsPercent`).
 */
export const ALIGNMENT_DIMENSIONS = [
  { dimension: "LOCALITY", featureType: "LOCALITY_SCORE", isPercent: false },
  { dimension: "QUIETNESS", featureType: "QUIETNESS_SCORE", isPercent: false },
  { dimension: "TOURIST_PREFERENCE", featureType: "TOURIST_RATIO", isPercent: false },
  { dimension: "SHADE_PREFERENCE", featureType: "SHADE_SCORE", isPercent: false },
  { dimension: "SLOPE_PREFERENCE", featureType: "SLOPE_PERCENT", isPercent: true },
];

function clamp01(v) {
  return Math.max(0, Math.min(1, v));
}

/**
 * 자바 `FeaturePresence.indicatesPresence` 를 그대로 옮긴 것.
 * (`backend/src/main/java/com/gabolle/backend/place/domain/FeaturePresence.java`)
 *
 * ```java
 * indicatesPresence  = isConfirmed(ev) && !isConfirmedAbsence(ev, raw)
 * isConfirmed        = "VERIFIED".equals(ev) || "ESTIMATED".equals(ev)
 * isConfirmedAbsence = isConfirmed(ev) && raw != null && "false".equals(raw.trim())
 * ```
 *
 * 🔴 `rawValue` 는 **문자열**이다 (JSONB 를 글자로 꺼내 온 것). 자바가
 *    `"false".equals(raw.trim())` 로 비교하므로 JS 의 `false` 가 아니라 `"false"` 다.
 *    여기를 불리언으로 옮기면 "확인된 부재" 가 안 걸러진다 — 옮겨 적기의 함정이라
 *    일부러 적어 둔다.
 *
 * `UNKNOWN` 은 있다고 치지 않는다. **모른다는 것은 안전하다는 뜻이 아니다.**
 */
export function indicatesPresence(evidenceStatus, rawValue) {
  const confirmed = evidenceStatus === "VERIFIED" || evidenceStatus === "ESTIMATED";
  if (!confirmed) return false;
  const raw = rawValue == null ? null : String(rawValue);
  const confirmedAbsence = raw != null && raw.trim() === "false";
  return !confirmedAbsence;
}

/** 후보의 features 배열에서 한 줄을 찾는다 (자바 `findFeature`) */
function findFeature(candidate, featureType, featureKey) {
  const rows = candidate.features ?? [];
  for (const f of rows) {
    if (f.featureType !== featureType) continue;
    if (featureKey != null && f.featureKey !== featureKey) continue;
    return f;
  }
  return null;
}

/**
 * 자바 `extractPlaceScore` — 점수형 피처의 값.
 * 숫자 그대로거나 `{"score": 0.7}` 둘 다 받는다. `UNKNOWN` 이거나 값이 없으면 null.
 * 🔴 **0~1 로 정규화하지 않는다.** 자바도 안 한다 — 채우는 쪽이 0~1 로 넣는다는 전제다.
 */
function extractPlaceScore(candidate, featureType) {
  const row = findFeature(candidate, featureType, null);
  if (row == null || row.evidenceStatus === "UNKNOWN" || row.value == null) return null;
  if (typeof row.value === "number") return row.value;
  const s = row.value.score;
  return typeof s === "number" ? s : null;
}

/**
 * 자바 `applyTagComponent` 의 비율.
 *
 * 🔴 **자카드 계수가 아니다.** 분모가 합집합이 아니라 **사용자가 고른 코드 수**다:
 *      ratio = |사용자코드 ∩ 장소태그| / |사용자코드|
 *    그래서 태그가 많은 장소가 유리해지지 않는다. 대신 **사용자가 고른 것을 몇 개나
 *    갖고 있나**만 본다.
 *
 * 사용자가 그 차원을 안 골랐으면(코드 0개) null — 항이 아예 빠진다.
 */
function tagRatio(candidate, userCodes, featureType) {
  if (!userCodes || userCodes.length === 0) return null;
  const placeTags = new Set();
  for (const f of candidate.features ?? []) {
    if (f.featureType !== featureType) continue;
    if (!indicatesPresence(f.evidenceStatus, f.value)) continue;
    if (f.featureKey != null) placeTags.add(f.featureKey);
  }
  let matched = 0;
  for (const c of userCodes) if (placeTags.has(c)) matched++;
  return matched / userCodes.length;
}

/**
 * 자바 `PreferenceAlignmentWeights.weightedAverage`.
 * 🔴 단순 평균이 아니다 — **값이 구해진 차원만으로** 다시 정규화한 가중평균이다.
 *    그래서 차원이 늘어도 총점에서 이 항이 차지하는 몫(0.10)은 안 변한다.
 *    하나도 못 구했으면 null → 항이 빠진다.
 */
function weightedAverage(alignments, alignmentWeights) {
  let num = 0;
  let den = 0;
  for (const [dim, value] of Object.entries(alignments)) {
    const w = alignmentWeights[dim];
    if (w == null) continue;
    num += w * value;
    den += w;
  }
  return den === 0 ? null : num / den;
}

/**
 * 한 후보의 점수. 자바 `score(...)` 와 같은 순서로 더한다.
 *
 * @param candidate `{ distanceM, features: [{featureType, featureKey, evidenceStatus, value}] }`
 * @param user      `{ codes: {CATEGORY:[...], ATMOSPHERE:[...], FOOD_PREFERENCE:[...]},
 *                     scores: {LOCALITY:0~1, ...}, mobilityWarnings: number }`
 * @param opts      `{ radiusM, weights, alignmentWeights }`
 * @returns `{ total, parts }` — parts 는 어느 항이 실제로 더해졌는지 (null = 빠짐)
 */
export function scoreCandidate(candidate, user, opts = {}) {
  const radiusM = opts.radiusM ?? DEFAULT_RADIUS_M;
  const w = opts.weights ?? DEFAULT_WEIGHTS;
  const aw = opts.alignmentWeights ?? DEFAULT_ALIGNMENT_WEIGHTS;

  const parts = {};
  let total = 0;

  // ── 거리 ─────────────────────────────────────────────────────────────────
  const distanceComponent = clamp01(1 - candidate.distanceM / radiusM);
  parts.distance = distanceComponent;
  total += w.distance * distanceComponent;

  // ── 태그 셋 (관심 · 분위기 · 음식) ────────────────────────────────────────
  for (const t of TAG_DIMENSIONS) {
    const ratio = tagRatio(candidate, user.codes?.[t.dimension], t.featureType);
    parts[t.weightKey] = ratio;
    if (ratio != null) total += w[t.weightKey] * ratio;
  }

  // ── 취향 정렬 다섯 ────────────────────────────────────────────────────────
  const alignments = {};
  for (const a of ALIGNMENT_DIMENSIONS) {
    const placeScore = extractPlaceScore(candidate, a.featureType);
    const prefScore = user.scores?.[a.dimension];
    if (placeScore == null || prefScore == null) continue; // 🔴 0 으로 안 채운다
    const normalized = a.isPercent ? placeScore / 100 : placeScore;
    alignments[a.dimension] = clamp01(1 - Math.abs(normalized - prefScore));
  }
  const alignmentAverage = weightedAverage(alignments, aw);
  parts.preferenceAlignment = alignmentAverage;
  if (alignmentAverage != null) total += w.preferenceAlignment * alignmentAverage;

  // ── 인기 ─────────────────────────────────────────────────────────────────
  const popularity = extractPlaceScore(candidate, "POPULARITY_SCORE");
  parts.popularity = popularity;
  if (popularity != null) total += w.popularity * popularity;

  // ── 이동 경고 감점 ────────────────────────────────────────────────────────
  const warnings = user.mobilityWarnings ?? 0;
  parts.mobilityWarnings = warnings;
  total -= MOBILITY_WARNING_PENALTY * warnings;

  return { total: Math.max(0, total), parts };
}
