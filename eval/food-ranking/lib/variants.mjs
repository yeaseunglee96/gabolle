/**
 * 점수 변형들. **한 번에 하나만** 바꾼다.
 *
 * v0 는 지금 제품 코드(`ref/local-route/server/src/services/recommend.ts`)를
 * 그대로 옮긴 것이다. 제품이 3,039곳을 실제로 받았다면 어떤 순위가 나오는지를
 * 재기 위한 것이지, 제품 코드를 고치는 것이 아니다.
 *
 * 제품이 OSM POI 를 받았을 때 각 칸이 무엇이 되는가는 **제품 자신의 임포터**
 * (`prisma/import-tourapi.ts`) 가 정해 둔 그대로 따랐다:
 *   localScore = 0.5 (콜드스타트 중립값) · priceTier = 2 · tasteTags = ["food"]/["cafe"]
 *   hasEnglishMenu/foreignCardPayment = deriveForeignConvenience(...) || 영문이름 있음
 */

/** 제품 `foreignConvenience.ts` 의 상수 그대로 */
const TOURISTY_AREA_HINTS = ["해운대", "광안리", "남포동", "서면", "용두산", "송정", "자갈치"];

const CAFE_KINDS = new Set(["cafe", "ice_cream", "shop:bakery", "shop:confectionery", "shop:pastry", "shop:coffee"]);

/** 제품이 이 POI 를 받았다면 채웠을 칸들 */
export function toPlace(r) {
  const category = CAFE_KINDS.has(r.kind) ? "CAFE" : "RESTAURANT";
  const priceTier = 2; // import-tourapi.ts: contentTypeId 39 → 2
  const inTouristy = TOURISTY_AREA_HINTS.some((a) => r.addr.includes(a) || r.name.includes(a));
  const englishMenu = priceTier >= 3 || inTouristy;
  const cardPayment = priceTier >= 2;
  return {
    category,
    priceTier,
    localScore: 0.5, // import-tourapi.ts 의 콜드스타트 중립값
    tasteTags: category === "CAFE" ? ["cafe"] : ["food"],
    hasEnglishMenu: englishMenu || Boolean(r.nameEn),
    foreignCardPayment: cardPayment,
  };
}

/** 제품 `estimatePlaceCost` 그대로 */
function estimateCost(category, tier, party) {
  if (category === "RESTAURANT") return (tier === 1 ? 8000 : tier === 2 ? 14000 : tier === 3 ? 25000 : 45000) * party;
  if (category === "CAFE") return (tier === 1 ? 5000 : tier === 2 ? 7000 : tier === 3 ? 10000 : 15000) * party;
  return (tier === 1 ? 0 : tier === 2 ? 8000 : tier === 3 ? 20000 : 45000) * party;
}

/** 제품 `recommend.ts:146-150` 의 하드코딩 가중치 */
const HARDCODED_WEIGHTS = {
  ESSENTIAL: { taste: 0.45, local: 0.15, travel: 0.15, budget: 0.1, mode: 0.2, foreign: 0.05 },
  LOCAL: { taste: 0.45, local: 0.3, travel: 0.1, budget: 0.05, mode: 0.2, foreign: 0.05 },
  EASY: { taste: 0.35, local: 0.15, travel: 0.25, budget: 0.05, mode: 0.1, foreign: 0.1 },
};

/** `server/config/course_categories.json:6-11` 의 baseWeights — 🔴 제품 코드는 이걸 읽지 않는다 */
const CONFIG_BASE_WEIGHTS = {
  taste: 0.25, local: 0.2, travel: 0.3, budget: 0.1, foreign: 0.1, mode: 0.05,
};

function normalize(w) {
  const total = Object.values(w).reduce((a, b) => a + b, 0) || 1;
  return Object.fromEntries(Object.entries(w).map(([k, v]) => [k, v / total]));
}

/** 값이 클수록 좋은 신호를 0~1 로. 큰 쪽이 포화되도록 로그를 쓴다. */
export const sat = (x, half) => x / (x + half);
/** 거리처럼 작을수록 좋은 신호를 0~1 로 */
export const decay = (m, half) => half / (half + Math.max(0, m));

const PARTY = 2;
const DAY_BUDGET = 60000;

/**
 * 공통 뼈대. axes 를 바꿔 끼우는 방식이라 "한 번에 하나만 바꾼다" 를 코드로 강제한다.
 * axes 는 { taste, local, travel, budget, mode, foreign } 각각 0~1 을 돌려주는 함수.
 */
function makeScorer({ weights, axes }) {
  const w = normalize(weights);
  return (r) => {
    const p = toPlace(r);
    return (
      w.taste * axes.taste(r, p) +
      w.local * axes.local(r, p) +
      w.travel * axes.travel(r, p) +
      w.budget * axes.budget(r, p) +
      w.mode * axes.mode(r, p) +
      w.foreign * axes.foreign(r, p)
    );
  };
}

/** 제품 그대로의 축들 */
const productAxes = {
  // 사용자가 취향을 안 골랐다 → preferenceSimilarity 가 0 을 돌려준다 (모든 후보 동일)
  taste: () => 0,
  local: (_r, p) => p.localScore, // 시드 상수 0.5
  travel: () => 0.5, // 🔴 TRAVEL_EFFICIENCY_PLACEHOLDER
  budget: (_r, p) => Math.max(0, 1 - estimateCost(p.category, p.priceTier, PARTY) / DAY_BUDGET),
  // LOCAL 모드의 modeFit = min(1, localScore*0.7 + (hidden_local ? 0.3 : 0))
  mode: (_r, p) => Math.min(1, p.localScore * 0.7),
  foreign: (_r, p) => ((p.hasEnglishMenu ? 1 : 0) + (p.foreignCardPayment ? 1 : 0)) / 2,
};

export const VARIANTS = {
  v0: {
    label: "기준선 — 제품 로직 그대로 (LOCAL 모드, 취향 미선택)",
    fn: makeScorer({ weights: HARDCODED_WEIGHTS.LOCAL, axes: productAxes }),
  },

  v1: {
    label: "v0 + 이동 효율 축을 상수 0.5 대신 실제 대중교통 접근성으로 (배관 잇기)",
    fn: makeScorer({
      weights: HARDCODED_WEIGHTS.LOCAL,
      axes: { ...productAxes, travel: (r) => decay(r.transitM, 150) },
    }),
  },

  v2: {
    label: "v1 + 로컬 점수를 시드 상수 0.5 대신 맛집 골목 밀집도로",
    fn: makeScorer({
      weights: HARDCODED_WEIGHTS.LOCAL,
      axes: {
        ...productAxes,
        travel: (r) => decay(r.transitM, 150),
        local: (r) => sat(r.dens150, 8),
        mode: (r) => Math.min(1, sat(r.dens150, 8) * 0.7),
      },
    }),
  },

  v3: {
    label: "v2 + 관광 상권 근접을 로컬 축에 절반 섞는다 (가까울수록 가점)",
    fn: makeScorer({
      weights: HARDCODED_WEIGHTS.LOCAL,
      axes: {
        ...productAxes,
        travel: (r) => decay(r.transitM, 150),
        local: (r) => 0.5 * sat(r.dens150, 8) + 0.5 * decay(r.touristM, 400),
        mode: (r) => Math.min(1, sat(r.dens150, 8) * 0.7),
      },
    }),
  },

  v4: {
    label: "v2 의 축 + 가중치를 course_categories.json 의 baseWeights 로 (코드가 안 읽는 그 값)",
    fn: makeScorer({
      weights: CONFIG_BASE_WEIGHTS,
      axes: {
        ...productAxes,
        travel: (r) => decay(r.transitM, 150),
        local: (r) => sat(r.dens150, 8),
        mode: (r) => Math.min(1, sat(r.dens150, 8) * 0.7),
      },
    }),
  },

  v5: {
    label: "v2 + 밀집도 눈금을 300m 로 넓힌다 (150m 는 4곳에서 포화)",
    fn: makeScorer({
      weights: HARDCODED_WEIGHTS.LOCAL,
      axes: {
        ...productAxes,
        travel: (r) => decay(r.transitM, 150),
        local: (r) => sat(r.dens300, 20),
        mode: (r) => Math.min(1, sat(r.dens300, 20) * 0.7),
      },
    }),
  },

  v6: {
    label: "v2 + 지하철 접근성을 이동 효율 축에 절반 섞는다 (버스는 78%가 300m 안이라 변별이 없다)",
    fn: makeScorer({
      weights: HARDCODED_WEIGHTS.LOCAL,
      axes: {
        ...productAxes,
        travel: (r) => 0.5 * decay(r.transitM, 150) + 0.5 * decay(r.subwayM, 400),
        local: (r) => sat(r.dens150, 8),
        mode: (r) => Math.min(1, sat(r.dens150, 8) * 0.7),
      },
    }),
  },

  v7: {
    label: "v2 + OSM 태그 촘촘함을 취향 축에 넣는다 (🔴 '누가 이 동네를 그렸나' 를 재는 위험한 시도)",
    fn: makeScorer({
      weights: HARDCODED_WEIGHTS.LOCAL,
      axes: {
        ...productAxes,
        taste: (r) => sat(r.tagRichness, 3),
        travel: (r) => decay(r.transitM, 150),
        local: (r) => sat(r.dens150, 8),
        mode: (r) => Math.min(1, sat(r.dens150, 8) * 0.7),
      },
    }),
  },

  v8: {
    label: "v2 + 프랜차이즈(brand 태그)를 깎는다 ×0.7",
    fn: (() => {
      const base = makeScorer({
        weights: HARDCODED_WEIGHTS.LOCAL,
        axes: {
          ...productAxes,
          travel: (r) => decay(r.transitM, 150),
          local: (r) => sat(r.dens150, 8),
          mode: (r) => Math.min(1, sat(r.dens150, 8) * 0.7),
        },
      });
      return (r) => base(r) * (r.hasBrand ? 0.7 : 1);
    })(),
  },

  v9: {
    label: "v2 + 밀집도만 남기고 나머지 축을 뺀다 (신호 하나짜리 대조군)",
    fn: (r) => sat(r.dens150, 8),
  },

  // ── 여기서부터는 조정용 정답표의 AUC 진단(diag-signals.mjs)을 보고 고른 것이다.
  //    🔴 정답 26곳을 보고 신호를 고르는 것 자체가 과적합의 시작이다. 그래서
  //    이 넷은 판정용에서 무너질 가능성이 높다는 것을 미리 적어 둔다.
  v10: {
    label: "OSM 태그 촘촘함 하나만 (AUC 0.565 — 조정용에서 가장 나은 축 중 하나)",
    fn: (r) => sat(r.tagRichness, 3),
  },

  v11: {
    label: "전화번호 있음 하나만 (AUC 0.613 — 조정용에서 가장 높다)",
    fn: (r) => (r.hasPhone ? 1 : 0) + 0.001 * sat(r.tagRichness, 3),
  },

  v12: {
    label: "🔴 관광 기준점에서 **멀수록** 가점 (조정용이 그 방향을 가리켰다)",
    fn: (r) => sat(r.touristM, 500),
  },

  v13: {
    label: "약한 신호 셋을 더한다 — 전화번호 + 태그 촘촘함 + 관광지에서 멂",
    fn: (r) =>
      0.4 * (r.hasPhone ? 1 : 0) + 0.3 * sat(r.tagRichness, 3) + 0.3 * sat(r.touristM, 500),
  },

  v15: {
    label: "v13 의 가중치를 1/3씩 균등하게 (0.4/0.3/0.3 이 우연인지 본다)",
    fn: (r) =>
      (1 / 3) * (r.hasPhone ? 1 : 0) + (1 / 3) * sat(r.tagRichness, 3) + (1 / 3) * sat(r.touristM, 500),
  },

  v16: {
    label: "v13 + 맛집 골목 밀집도를 다시 넣는다 (AUC 0.491 이라 안 될 것 같다 — 확인용)",
    fn: (r) =>
      0.3 * (r.hasPhone ? 1 : 0) +
      0.25 * sat(r.tagRichness, 3) +
      0.25 * sat(r.touristM, 500) +
      0.2 * sat(r.dens150, 8),
  },

  v17: {
    label: "v13 의 전화번호를 '연락처가 있나'(전화·홈페이지·영업시간 중 하나) 로 넓힌다",
    fn: (r) =>
      0.4 * ((r.hasPhone || r.hasWebsite || r.hasOpeningHours) ? 1 : 0) +
      0.3 * sat(r.tagRichness, 3) +
      0.3 * sat(r.touristM, 500),
  },

  v18: {
    label: "v13 에서 관광지 거리 눈금을 500m → 1,000m 로 (부산은 관광지와 원도심이 겹친다)",
    fn: (r) =>
      0.4 * (r.hasPhone ? 1 : 0) + 0.3 * sat(r.tagRichness, 3) + 0.3 * sat(r.touristM, 1000),
  },

  v14: {
    label: "v13 + 이동 효율·예산 축을 다시 얹어 제품 뼈대 안으로 넣는다",
    fn: makeScorer({
      weights: HARDCODED_WEIGHTS.LOCAL,
      axes: {
        ...productAxes,
        taste: (r) => sat(r.tagRichness, 3),
        local: (r) => (r.hasPhone ? 1 : 0),
        travel: (r) => decay(r.transitM, 150),
        mode: (r) => sat(r.touristM, 500),
      },
    }),
  },
};
