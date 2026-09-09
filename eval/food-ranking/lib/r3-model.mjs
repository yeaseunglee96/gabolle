/**
 * 라운드3 모델. 라운드2 의 `lib/r2-model.mjs` 와 **입력 칸 하나만 다르다** —
 * 해안선까지의 거리가 들어왔다. 나머지는 그대로 두었다: 바꾼 것이 둘이면
 * 좋아지거나 나빠진 것이 정답표 덕인지 신호 덕인지 못 가른다.
 *
 * 🔴 정답표에서 나온 값은 입력에 하나도 없다. 미쉐린 가격대·블루리본 리본수는
 *    **정답의 강도(가중치)** 로만 쓴다 — r3-07-fit.mjs. 신호로 쓰면 누출이다.
 */
export function numericFeatures(r) {
  const l = (x) => Math.log1p(Math.max(0, x));
  return [
    l(r.foodDens100),
    l(r.foodDens300),
    l(r.soDens300),
    r.soShare300,
    l(r.subwayM),
    l(r.transitM),
    l(r.touristM),
    l(r.bldgFoodCount),
    l(r.roadFoodCount),
    l(r.bdongFoodCount),
    l(r.nameCount),
    r.nameLen / 10,
    r.hasBranch ? 1 : 0,
    r.isFloor1 ? 1 : 0,
    r.isUpper ? 1 : 0,
    r.isBasement ? 1 : 0,
    r.floorMissing ? 1 : 0,
    r.hasBldgName ? 1 : 0,
    r.hasHo ? 1 : 0,
    r.daejiSan ? 1 : 0,
    r.osmListed ? 1 : 0,
    r.osmTagRichness / 5,
    r.osmHasPhone ? 1 : 0,
    r.osmHasOpeningHours ? 1 : 0,
    l(r.lodgeDens500),
    l(r.lodgeM),
    l(r.leisureDens500),
    // ── 라운드3 신규 ──────────────────────────────────────────────────────────
    l(r.coastM),
    r.isSeaside ? 1 : 0,
  ];
}
export const NUMERIC_NAMES = [
  "밀집100m(log)", "밀집300m(log)", "같은업종밀집300m(log)", "같은업종비중300m",
  "지하철거리(log)", "정류장거리(log)", "관광지거리(log)",
  "같은건물음식점수(log)", "같은도로음식점수(log)", "같은법정동음식점수(log)",
  "같은상호개수(log)", "상호명길이", "지점명있음",
  "1층", "2층이상", "지하", "층정보없음", "건물명있음", "호수있음", "산지번",
  "OSM등재", "OSM태그촘촘함", "OSM전화", "OSM영업시간",
  "숙박밀집500m(log)", "가장가까운숙박(log)", "예술·스포츠밀집500m(log)",
  "🌊해안선거리(log)", "🌊바다300m안",
];

export function buildEncoder(rows) {
  const soCodes = [...new Set(rows.map((r) => r.soCode))].sort();
  const guCodes = [...new Set(rows.map((r) => r.sigungu))].sort();
  const soIdx = new Map(soCodes.map((c, i) => [c, i]));
  const guIdx = new Map(guCodes.map((c, i) => [c, i]));
  const names = [...NUMERIC_NAMES, ...soCodes.map((c) => `업종:${c}`), ...guCodes.map((c) => `구군:${c}`)];
  const dim = names.length;
  function encode(r) {
    const v = new Float64Array(dim);
    const num = numericFeatures(r);
    for (let i = 0; i < num.length; i++) v[i] = num[i];
    const si = soIdx.get(r.soCode);
    if (si !== undefined) v[NUMERIC_NAMES.length + si] = 1;
    const gi = guIdx.get(r.sigungu);
    if (gi !== undefined) v[NUMERIC_NAMES.length + soCodes.length + gi] = 1;
    return v;
  }
  return { encode, names, dim, nNumeric: NUMERIC_NAMES.length };
}

export function fitScaler(X, nNumeric) {
  const dim = X[0].length;
  const mean = new Float64Array(dim);
  const sd = new Float64Array(dim).fill(1);
  for (let j = 0; j < nNumeric; j++) {
    let s = 0;
    for (const x of X) s += x[j];
    mean[j] = s / X.length;
    let v = 0;
    for (const x of X) v += (x[j] - mean[j]) ** 2;
    sd[j] = Math.sqrt(v / X.length) || 1;
  }
  return { mean, sd, nNumeric };
}
export function applyScaler(x, sc) {
  const out = new Float64Array(x.length);
  for (let j = 0; j < x.length; j++) out[j] = j < sc.nNumeric ? (x[j] - sc.mean[j]) / sc.sd[j] : x[j];
  return out;
}

const sigmoid = (z) => 1 / (1 + Math.exp(-z));

/**
 * L2 규제가 붙은 로지스틱 회귀. 라운드2 와 같은 코드에 **가중치 배열**만 더했다.
 * @param sampleWeight 정답마다 다른 무게를 줄 때 쓴다 (정답의 **강도**).
 *                     안 주면 정답은 전부 posWeight, 오답은 1 이다.
 */
export function trainLogistic(X, y, lambda, { iters = 800, lr = 0.5, posWeight = 1, sampleWeight = null } = {}) {
  const n = X.length;
  const dim = X[0].length;
  const w = new Float64Array(dim);
  let b = 0;
  const g = new Float64Array(dim);
  for (let it = 0; it < iters; it++) {
    g.fill(0);
    let gb = 0;
    let wsum = 0;
    for (let i = 0; i < n; i++) {
      const xi = X[i];
      let z = b;
      for (let j = 0; j < dim; j++) z += w[j] * xi[j];
      const p = sigmoid(z);
      const wt = y[i] ? posWeight * (sampleWeight ? sampleWeight[i] : 1) : 1;
      const e = wt * (p - y[i]);
      for (let j = 0; j < dim; j++) g[j] += e * xi[j];
      gb += e;
      wsum += wt;
    }
    for (let j = 0; j < dim; j++) w[j] -= lr * (g[j] / wsum + lambda * w[j]);
    b -= lr * (gb / wsum);
  }
  return { w, b };
}

export function score(model, x) {
  let z = model.b;
  for (let j = 0; j < x.length; j++) z += model.w[j] * x[j];
  return z;
}
