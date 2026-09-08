/**
 * 라운드3 손으로 만든 점수 변형들. **한 번에 하나만** 바꾼다.
 *
 * 🔴 여기 상수는 전부 **조정용 정답 115곳**만 보고 정했다. 판정용 49곳은
 *    이 파일을 쓰는 동안 한 번도 열지 않았다.
 *
 * 라운드2 의 변형(`r2-variants.mjs`)과 **방향이 반대인 것들이 많다.** 그게 이번
 * 라운드의 요점이다 — 새 정답표에서 신호 방향이 뒤집혔기 때문이다 (r3-05-diag).
 */
export const sat = (x, half) => x / (x + half);
export const decay = (m, half) => half / (half + Math.max(0, m));

/** 업종 소분류 사전확률 — 조정용 정답만 세어서 만든다 (라운드2 와 같은 코드) */
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
  for (const [k, e] of agg) lift.set(k, (e.p + PSEUDO * base) / (e.n + PSEUDO) / base);
  return { lift, base, pseudo: PSEUDO };
}

export function makeVariants(prior) {
  const soScore = (r) => sat(prior.lift.get(r.soCode) ?? 1, 1);

  // 🔴 라운드2 와 방향이 반대인 것들
  const nearSea = (r) => decay(r.coastM, 800); // 바다에 가까울수록 (AUC 0.667)
  const nearTourist = (r) => decay(r.touristM, 400); // 관광 기준점에 가까울수록 (0.627)
  const lodging = (r) => sat(r.lodgeDens500, 30); // 숙박이 많을수록 (0.668)
  const foodyDong = (r) => sat(r.bdongFoodCount, 400); // 음식점 많은 동일수록 (0.636)
  const dens300 = (r) => sat(r.foodDens300, 100); // 식당이 많을수록 (0.545)

  return {
    v0: { label: "제품 로직 그대로 — 후보가 거의 전부 동점이라 무작위와 같다", fn: () => 0 },
    v1: {
      label: "🔴 라운드2 손조합 w18 이식 (예술·스포츠 드묾 + 지하철 멂 + 독립 건물)",
      fn: (r) => (decay(r.leisureDens500, 20) + sat(r.subwayM, 500) + soScore(r) + decay(r.bldgFoodCount - 1, 2)) / 4,
    },
    v2: { label: "🌊 해안선에 가까움 하나만 (라운드3 신규 신호)", fn: nearSea },
    v3: { label: "숙박 상가가 많음 하나만 (조정용 최강 단일 신호 AUC 0.668)", fn: lodging },
    v4: { label: "음식점 많은 법정동 하나만 (AUC 0.636)", fn: foodyDong },
    v5: { label: "관광 기준점에 가까움 하나만 (AUC 0.627)", fn: nearTourist },
    v6: { label: "업종 소분류 사전확률 하나만 (조정용에서 구움)", fn: soScore },
    v7: { label: "숙박 많음 + 바다 가까움 (반씩)", fn: (r) => 0.5 * lodging(r) + 0.5 * nearSea(r) },
    v8: { label: "숙박 많음 + 업종 사전확률 (반씩)", fn: (r) => 0.5 * lodging(r) + 0.5 * soScore(r) },
    v9: { label: "숙박 + 바다 + 업종 (1/3씩)", fn: (r) => (lodging(r) + nearSea(r) + soScore(r)) / 3 },
    v10: { label: "v9 + 음식점 많은 동 (1/4씩)", fn: (r) => (lodging(r) + nearSea(r) + soScore(r) + foodyDong(r)) / 4 },
    v11: { label: "v10 + 관광 기준점 가까움 (1/5씩)", fn: (r) => (lodging(r) + nearSea(r) + soScore(r) + foodyDong(r) + nearTourist(r)) / 5 },
    v12: { label: "v11 + 식당 밀집 높음 (1/6씩)", fn: (r) => (lodging(r) + nearSea(r) + soScore(r) + foodyDong(r) + nearTourist(r) + dens300(r)) / 6 },
    v13: { label: "v10 에서 업종에 무게를 더 준다 (0.4 : 0.2 : 0.2 : 0.2)", fn: (r) => 0.4 * soScore(r) + 0.2 * lodging(r) + 0.2 * nearSea(r) + 0.2 * foodyDong(r) },
    v14: { label: "v10 을 곱셈으로 — 하나라도 나쁘면 떨어지게", fn: (r) => Math.pow(lodging(r) * nearSea(r) * soScore(r) * foodyDong(r), 0.25) },
    v15: { label: "v11 을 곱셈으로", fn: (r) => Math.pow(lodging(r) * nearSea(r) * soScore(r) * foodyDong(r) * nearTourist(r), 0.2) },
    v16: { label: "숙박 + 관광 가까움 (반씩) — '관광객이 실제로 걸어 다니는 구역'", fn: (r) => 0.5 * lodging(r) + 0.5 * nearTourist(r) },
    v17: { label: "라운드1 최종안 v13 이식 — OSM 전화 + 태그 촘촘함 + 관광지에서 멂", fn: (r) => 0.4 * (r.osmHasPhone ? 1 : 0) + 0.3 * sat(r.osmTagRichness, 3) + 0.3 * sat(r.touristM, 500) },
  };
}
