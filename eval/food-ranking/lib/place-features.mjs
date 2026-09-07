/**
 * 후보 한 줄에서 **`place_feature` 행들을 만든다** — 배포될 채점기가 읽는 그 모양으로.
 *
 * 🔴 **왜 우리가 만드나.** `place_feature`(장소마다 붙는 14~16종의 속성 — 관심 태그·
 *    음식 태그·인기 점수 등)를 **채우는 코드가 저장소 어디에도 없다.** 표도, 제약도,
 *    대조표(`user_place_code_map`)도 다 있는데 행을 넣는 운영 코드·배치·시드·DAG 가
 *    하나도 없다. 마이그레이션 주석이 스스로 그렇게 적어 두었다:
 *      *"지금 사고가 안 난 이유는 장소 피처를 채우는 코드가 아직 없기 때문이다"*
 *      (`V20260907160000__place_local_discovery.sql`)
 *    유일한 INSERT 는 테스트 픽스처(`backend/src/test/.../PlaceFixture.java`) 뿐이다.
 *
 * 그래서 이 파일은 두 가지를 만든다.
 *
 *   `emptyFeatures()`   🔴 **지금 배포하면 실제로 이렇게 된다** — 빈 배열.
 *                       피처가 없으면 태그 셋·정렬·인기 항이 전부 빠지고
 *                       **거리 항 하나만 남는다.**
 *   `derivedFeatures()` 상가정보에서 **실제로 알 수 있는 것만** 채웠을 때.
 *
 * 🔴 **채우지 않는 것을 분명히 적는다. 없는 값을 지어내는 것은 추정이 아니라 창작이다.**
 *
 * | place_feature | 우리가 가진 자료 | 채우나 |
 * |---|---|---|
 * | `CUISINE_TAG`      | 상가정보 `상권업종중분류명` (한식·일식·주점 …) | 🟢 채운다 |
 * | `INTEREST_TAG`     | 후보가 전부 "음식" 대분류다 | 🟢 채우되 **값이 하나뿐이라 순위를 못 가른다** |
 * | `ATMOSPHERE_TAG`   | 🔴 없다 | 비운다 |
 * | `POPULARITY_SCORE` | 🔴 **출처가 없다.** 방문수·리뷰수·조회수 어느 것도 우리에게 없다 | 비운다 |
 * | `LOCALITY_SCORE`   | 🔴 없다 | 비운다 |
 * | `QUIETNESS_SCORE`  | 🔴 없다 | 비운다 |
 * | `TOURIST_RATIO`    | 🔴 없다 | 비운다 |
 * | `SHADE_SCORE`      | bigData 에 **도로 구간별** 그늘은 있지만 장소에 붙이는 작업이 없다 | 비운다 |
 * | `SLOPE_PERCENT`    | 위와 같다 | 비운다 |
 * | `STAIRS_PRESENT`   | 🔴 없다 | 비운다 |
 *
 * 인기 점수를 비워 두는 것이 특히 아프다 — 채점기가 0.10 을 배정해 둔 자리인데
 * 곱할 상대가 없어서 **조용히** 빠진다. 화면에도 로그에도 그 사실이 안 남는다.
 */

/**
 * 음식 태그 = 상가정보의 **중분류를 그대로 쓴다.**
 *
 * 🔴 왜 소분류(46종)가 아니라 중분류(10종)인가 — 소분류를 묶어 "한식/일식/양식" 을
 *    만들려면 **내가 어디에 묶을지 판단해야 한다**(치킨은 간식인가 한 끼인가?).
 *    중분류는 자료가 스스로 지어 둔 묶음이라 **내 판단이 한 방울도 안 들어간다.**
 *    사용자가 고르는 음식 종류와 눈금이 딱 맞지는 않지만, 지어낸 눈금보다는 낫다.
 *
 * 🔴 `"비알코올 "` 은 원본에 **꼬리 공백이 있다.** 지우고 쓴다.
 */
export const CUISINE_CODES = [
  "한식", "기타 간이", "주점", "비알코올", "중식", "일식",
  "서양식", "구내식당·뷔페", "동남아시아", "기타 외국",
];

/**
 * OSM 태그 → 중분류. OSM 단독 후보에는 상가정보의 업종 칸이 없어서 여기서 옮긴다.
 * 🔴 `restaurant` 는 **무슨 음식인지 모른다** — 비운다. 지어내지 않는다.
 */
const OSM_KIND_TO_CUISINE = {
  restaurant: null,
  cafe: "비알코올",
  "shop:coffee": "비알코올",
  fast_food: "기타 간이",
  ice_cream: "기타 간이",
  "shop:bakery": "기타 간이",
  "shop:confectionery": "기타 간이",
  "shop:pastry": "기타 간이",
  pub: "주점",
  bar: "주점",
  biergarten: "주점",
  food_court: null,
  "shop:deli": null,
};

/** 후보 한 줄의 음식 태그. 모르면 null */
export function cuisineOf(row) {
  if (row.jung) {
    const t = String(row.jung).trim();
    return CUISINE_CODES.includes(t) ? t : null;
  }
  if (row.osmKind) return OSM_KIND_TO_CUISINE[row.osmKind] ?? null;
  return null;
}

/** 🔴 배포 그대로 — `place_feature` 에 아무도 행을 안 넣은 상태 */
export function emptyFeatures() {
  return [];
}

/**
 * 상가정보에서 **실제로 알 수 있는 것만** 채운 `place_feature` 행들.
 *
 * `evidenceStatus` 는 `ESTIMATED`(추정) 다 — 업종 칸에서 옮긴 것이지 가게에 직접
 * 확인한 것이 아니다. 🔴 안전 항목(알레르기·식단·접근성·계단)은 DB 가 `ESTIMATED`
 * 저장을 아예 금지하고 있어서, 여기서도 손대지 않는다.
 */
export function derivedFeatures(row) {
  const out = [{
    featureType: "INTEREST_TAG",
    featureKey: "FOOD",
    evidenceStatus: "ESTIMATED",
    value: true,
  }];
  const cuisine = cuisineOf(row);
  if (cuisine) {
    out.push({
      featureType: "CUISINE_TAG",
      featureKey: cuisine,
      evidenceStatus: "ESTIMATED",
      value: true,
    });
  }
  return out;
}
