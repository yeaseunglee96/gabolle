/**
 * 라운드2 점수 변형들. **한 번에 하나만** 바꾼다.
 *
 * 🔴 여기 쓰이는 상수(눈금·가중치)는 전부 **조정용 정답 199곳**만 보고 정했다.
 *    판정용 86곳은 이 파일을 쓰는 동안 한 번도 열지 않았다.
 *
 * 🔴 업종 소분류 사전확률(prior — 어떤 업종이 정답에 얼마나 자주 나오나)만은
 *    조정용 **정답을 세어서** 만든다. 이것은 지도학습(supervised — 정답을 보고
 *    배우는 것)이고, 조정용에서만 배우는 한 규칙 위반이 아니다. 다만 **판정용에서
 *    무너질 수 있는 1순위 후보**라 표시해 둔다.
 */

export const sat = (x, half) => x / (x + half);
export const decay = (m, half) => half / (half + Math.max(0, m));

/**
 * 업종 소분류 사전확률을 조정용 정답에서 굽는다.
 * 매끄럽게(smoothing — 표본이 적은 업종이 0% 나 100% 로 튀지 않게 가짜 관측을 섞는 것)
 * 하려고 전체 평균 쪽으로 500건어치를 당긴다.
 */
export function buildSoPrior(rows, posIds, PSEUDO = 500) {
  const base = [...posIds].length / rows.length;
  const agg = new Map();
  for (const r of rows) {
    const e = agg.get(r.soCode) ?? { n: 0, p: 0 };
    e.n++;
    if (posIds.has(r.id)) e.p++;
    agg.set(r.soCode, e);
  }
  const lift = new Map();
  for (const [k, e] of agg) {
    const rate = (e.p + PSEUDO * base) / (e.n + PSEUDO);
    lift.set(k, rate / base);
  }
  return { lift, base, pseudo: PSEUDO };
}

/** 변형 목록을 만든다. soPrior 가 필요해서 함수로 받는다. */
export function makeVariants(prior) {
  const soLift = (r) => prior.lift.get(r.soCode) ?? 1;
  /** 배수(×0 ~ ×5)를 0~1 로 눌러 담는다 */
  const soScore = (r) => sat(soLift(r), 1);

  const farSubway = (r) => sat(r.subwayM, 500);
  const farTransit = (r) => sat(r.transitM, 200);
  const lowDens300 = (r) => decay(r.foodDens300, 100);
  const lowDens100 = (r) => decay(r.foodDens100, 20);
  const standalone = (r) => decay(r.bldgFoodCount - 1, 2);
  const notGround = (r) => (r.floorMissing ? 1 : 0);
  const osm = (r) => (r.osmListed ? 1 : 0);
  const lowLeisure = (r) => decay(r.leisureDens500, 20);

  return {
    // ── 기준선 ────────────────────────────────────────────────────────────────
    w0: {
      label: "제품 로직 그대로 — 후보가 거의 전부 동점이라 무작위와 같다",
      fn: () => 0,
    },
    w1: {
      label: "라운드1 최종안 v13 이식 — OSM 전화번호 + 태그 촘촘함 + 관광지에서 멂",
      fn: (r) => 0.4 * (r.osmHasPhone ? 1 : 0) + 0.3 * sat(r.osmTagRichness, 3) + 0.3 * sat(r.touristM, 500),
    },

    // ── 신호 하나짜리 ─────────────────────────────────────────────────────────
    w2: { label: "지하철에서 멂 하나만 (AUC 0.669)", fn: farSubway },
    w3: { label: "식당 밀집이 낮음 하나만 — 300m (AUC 0.672)", fn: lowDens300 },
    w4: { label: "식당 밀집이 낮음 하나만 — 100m (AUC 0.691)", fn: lowDens100 },
    w5: { label: "업종 소분류 사전확률 하나만 (조정용에서 구움)", fn: soScore },
    w6: { label: "같은 건물에 음식점이 적음 하나만 (AUC 0.665)", fn: standalone },

    // ── 둘씩 ──────────────────────────────────────────────────────────────────
    w7: { label: "밀집 낮음(100m) + 지하철에서 멂", fn: (r) => 0.5 * lowDens100(r) + 0.5 * farSubway(r) },
    w8: { label: "밀집 낮음(100m) + 업종 사전확률", fn: (r) => 0.5 * lowDens100(r) + 0.5 * soScore(r) },

    // ── 셋 이상 ───────────────────────────────────────────────────────────────
    w9: {
      label: "밀집 낮음 + 지하철에서 멂 + 업종 사전확률 (1/3씩)",
      fn: (r) => (lowDens100(r) + farSubway(r) + soScore(r)) / 3,
    },
    w10: {
      label: "w9 + 같은 건물에 음식점이 적음 (1/4씩)",
      fn: (r) => (lowDens100(r) + farSubway(r) + soScore(r) + standalone(r)) / 4,
    },
    w11: {
      label: "w10 + 층정보 없음(독립 건물로 본다) (1/5씩)",
      fn: (r) => (lowDens100(r) + farSubway(r) + soScore(r) + standalone(r) + notGround(r)) / 5,
    },
    w12: {
      label: "w10 + OSM 에 그려져 있음 (🔴 맛이 아니라 유명세를 잰다)",
      fn: (r) => (lowDens100(r) + farSubway(r) + soScore(r) + standalone(r) + osm(r)) / 5,
    },
    w13: {
      label: "w10 에서 업종 사전확률에 무게를 더 준다 (0.4 : 0.2 : 0.2 : 0.2)",
      fn: (r) => 0.4 * soScore(r) + 0.2 * lowDens100(r) + 0.2 * farSubway(r) + 0.2 * standalone(r),
    },
    w14: {
      label: "w10 에서 밀집 낮음에 무게를 더 준다 (0.4 : 0.2 : 0.2 : 0.2)",
      fn: (r) => 0.4 * lowDens100(r) + 0.2 * soScore(r) + 0.2 * farSubway(r) + 0.2 * standalone(r),
    },
    w15: {
      label: "w10 을 곱셈으로 — 하나라도 나쁘면 떨어지게",
      fn: (r) => Math.pow(lowDens100(r) * farSubway(r) * soScore(r) * standalone(r), 0.25),
    },
    w17: { label: "예술·스포츠 상가가 드묾 하나만 (AUC 0.698 — 조정용 최강 단일 신호)", fn: lowLeisure },
    w18: {
      label: "w10 의 밀집 신호를 예술·스포츠 드묾으로 바꾼다",
      fn: (r) => (lowLeisure(r) + farSubway(r) + soScore(r) + standalone(r)) / 4,
    },
    w19: {
      label: "w10 + 예술·스포츠 드묾 (1/5씩)",
      fn: (r) => (lowDens100(r) + lowLeisure(r) + farSubway(r) + soScore(r) + standalone(r)) / 5,
    },
    w20: {
      label: "w19 를 곱셈으로 — 하나라도 나쁘면 떨어지게",
      fn: (r) => Math.pow(lowDens100(r) * lowLeisure(r) * farSubway(r) * soScore(r) * standalone(r), 0.2),
    },
    w16: {
      label: "w10 + 버스정류장에서도 멂 (1/5씩)",
      fn: (r) => (lowDens100(r) + farSubway(r) + soScore(r) + standalone(r) + farTransit(r)) / 5,
    },
  };
}
