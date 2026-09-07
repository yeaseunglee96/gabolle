/**
 * 라운드4 2단계 — 새 정답표를 후보 풀(상가정보 ∪ OSM 합집합)에 붙인다.
 *
 * 🔴 라운드3 과 붙이는 **방식이 다르다.** 라운드2 의 정답표(TourAPI)에는 좌표가
 *    있어서 "반경 400m + 이름" 으로 붙였다. 새 정답지 넷에는 좌표가 없고 **주소**가
 *    있다. 그래서 주소를 열쇠로 쓴다 — 좌표 반경보다 훨씬 정확하다. 같은 건물번호에
 *    같은 이름이면 그 가게다.
 *
 * 붙이는 단계 (강한 것부터. 앞 단계가 차지한 행은 뒤 단계가 못 가져간다):
 *   T1 주소(구·도로·본번·부번) 일치 + 이름 일치/포함/앞3글자
 *   T2 주소(부번 무시) 일치      + 이름 일치/포함/앞3글자
 *   T3 주소(구·도로·본번) 일치   + 이름 앞2글자만 일치   ← 느슨하다. 따로 센다
 *   T4 🔴 이름만 일치 (미쉐린처럼 주소가 없는 것). 부산에 그 이름이 **하나뿐일 때만**.
 *      둘 이상이면 어느 쪽인지 데이터가 말해주지 않으므로 **안 붙이고 표시한다**
 *
 * 🔴 라운드3 과 달라진 것은 **후보 풀뿐**이다. 붙이는 규칙은 한 글자도 안 바꿨다 —
 *    한 번에 하나만 바꾼다는 규칙 그대로다. 그래서 매칭 수가 늘면 그것은 규칙을
 *    느슨하게 푼 결과가 아니라 **후보가 늘어서** 늘어난 것이다.
 *
 * 🔴 OSM 단독 행에는 주소가 없다. 그래서 주소 단계 T1~T3 은 그 행에 절대 닿지
 *    않고, 이름 단계 T4 만 닿는다. T4 는 **구·군이 같아야 한다**를 이미 강제하므로
 *    상호만 같은 남의 가게가 붙는 사고는 OSM 단독 행에도 똑같이 막혀 있다.
 *
 * 산출: data/r4-matched.json
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { normName, coreName, parseAddr } from "./lib/r3-truth.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const truth = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r3-truth.json"), "utf8"));
const pool = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r4-pool.json"), "utf8"));
const rows = pool.rows;
console.log(`후보 풀(상가정보 음식): ${rows.length}곳`);
console.log(`정답표: 가게 ${truth.items.length}곳 (맛 ${truth.tasteStores} · 백년가게 ${truth.baengnyeonStores})`);

// ── 색인 ─────────────────────────────────────────────────────────────────────
const byAddrFull = new Map(); // 구|road|본|부
const byAddrBon = new Map(); // 구|road|본
const byCore = new Map(); // 대표이름
const meta = rows.map((r, i) => {
  const a = parseAddr(r.roadAddr) ?? parseAddr(r.jibunAddr);
  const cn = coreName(r.name);
  const cnb = coreName(r.name + (r.branch ?? ""));
  if (a) {
    if (!byAddrFull.has(a.key)) byAddrFull.set(a.key, []);
    byAddrFull.get(a.key).push(i);
    const bk = `${a.gu ?? ""}|${a.kind}|${a.road}|${a.bon}`;
    if (!byAddrBon.has(bk)) byAddrBon.set(bk, []);
    byAddrBon.get(bk).push(i);
  }
  for (const k of new Set([cn, cnb])) {
    if (!k) continue;
    if (!byCore.has(k)) byCore.set(k, []);
    byCore.get(k).push(i);
  }
  return { a, cn, cnb, n: normName(r.name), nb: normName(r.name + (r.branch ?? "")) };
});
console.log(`주소를 읽어낸 후보 ${meta.filter((m) => m.a).length}곳 · 주소 열쇠 ${byAddrFull.size}개`);

function commonPrefix(a, b) {
  let i = 0;
  while (i < a.length && i < b.length && a[i] === b[i]) i++;
  return i;
}
/** 이름이 같은 가게로 볼 만한가 */
function nameOk(tCore, m, minPrefix) {
  for (const c of [m.cn, m.cnb, m.n, m.nb]) {
    if (!c) continue;
    if (c === tCore) return "완전";
    if (c.length >= 3 && tCore.length >= 3 && (c.startsWith(tCore) || tCore.startsWith(c))) return "접두";
    if (c.length >= 4 && tCore.length >= 4 && (c.includes(tCore) || tCore.includes(c))) return "포함";
    if (commonPrefix(c, tCore) >= minPrefix && c.length >= minPrefix && tCore.length >= minPrefix) return `앞${minPrefix}`;
  }
  return null;
}

const used = new Set(); // 한 후보 행에는 정답 하나만
const matched = [];
const unmatched = [];
const remaining = new Set(truth.items.map((t) => t.tid));
const byTid = new Map(truth.items.map((t) => [t.tid, t]));

function claim(t, i, tier, how) {
  used.add(i);
  remaining.delete(t.tid);
  const r = rows[i];
  matched.push({
    tid: t.tid,
    name: t.name,
    sources: t.sources,
    nSources: t.nSources,
    tasteSources: t.tasteSources,
    nTaste: t.nTaste,
    isTaste: t.isTaste,
    isBaengnyeon: t.isBaengnyeon,
    michelinPrice: t.michelinPrice,
    blueRibbon: t.blueRibbon,
    storeId: r.id,
    storeName: r.name + (r.branch ? ` ${r.branch}` : ""),
    so: r.so,
    sigungu: r.sigungu,
    source: r.source,
    lat: r.lat,
    lon: r.lon,
    tier,
    how,
  });
}

// ── T1 · T2 · T3 — 주소를 열쇠로 ──────────────────────────────────────────────
const tiers = [
  { name: "T1", pick: (t) => (t.addr ? byAddrFull.get(t.addr.key) ?? [] : []), minPrefix: 3 },
  { name: "T2", pick: (t) => (t.addr ? byAddrBon.get(`${t.addr.gu ?? ""}|${t.addr.kind}|${t.addr.road}|${t.addr.bon}`) ?? [] : []), minPrefix: 3 },
  { name: "T3", pick: (t) => (t.addr ? byAddrBon.get(`${t.addr.gu ?? ""}|${t.addr.kind}|${t.addr.road}|${t.addr.bon}`) ?? [] : []), minPrefix: 2 },
];
for (const tier of tiers) {
  for (const tid of [...remaining]) {
    const t = byTid.get(tid);
    const cands = tier.pick(t).filter((i) => !used.has(i));
    if (!cands.length) continue;
    const scored = [];
    for (const i of cands) {
      const how = nameOk(t.core, meta[i], tier.minPrefix);
      if (how) scored.push({ i, how });
    }
    if (!scored.length) continue;
    const rank = { 완전: 0, 접두: 1, 포함: 2 };
    scored.sort((a, b) => (rank[a.how] ?? 9) - (rank[b.how] ?? 9));
    claim(t, scored[0].i, tier.name, scored[0].how);
  }
}

// ── T4 — 이름만 ──────────────────────────────────────────────────────────────
// 🔴 처음 짠 규칙("부산에 그 이름이 하나뿐이면 붙인다")은 **틀렸다.** 눈으로 확인해
//    보니 36건 중 5건이 엉뚱한 가게였다:
//      팔레트(미쉐린 ₩₩₩₩ 컨템퍼러리) → 동래구의 **일반 유흥 주점** 팔레트
//      모즈(블루리본, 해운대 마린시티) → **사상구의 카페** 모즈
//      남풍(블루리본, 해운대해변로)     → **강서구의 중국집** 남풍
//      무궁화(블루리본, 부산진구 호텔) → **중구의 돼지고기집** 무궁화
//      토오루(블로그, 해운대 구남로)   → **부산진구의 요리 주점** 토오루
//    상호가 겹치는 다른 가게를 정답으로 삼으면 **정답표가 오염된다.** 조용히 틀린
//    정답 5건은 못 맞힌 정답 5건보다 나쁘다 — 모델이 그것을 배우기 때문이다.
//
//    그래서 규칙을 조인다:
//      · 정답에 **주소가 있으면 구군이 같아야 한다** (T1~T3 이 실패해도 구는 맞아야 한다)
//      · 정답이 **주소를 못 고른 것**(같은 이름이 서로 다른 두 주소에 있는 것)이면
//        아예 안 붙인다 — 이미 그 이름이 여러 가게를 가리킨다는 것을 아는 상태다
const t4Ambiguous = [];
for (const tid of [...remaining]) {
  const t = byTid.get(tid);
  if (t.ambiguous) {
    t4Ambiguous.push({ tid: t.tid, name: t.name, n: 0, why: "같은 이름이 서로 다른 주소 둘 이상 — 이름만으로 못 고른다" });
    continue;
  }
  let uniq = [...new Set((byCore.get(t.core) ?? []).filter((i) => !used.has(i)))];
  if (!uniq.length) continue;
  if (t.addr?.gu) {
    const inGu = uniq.filter((i) => rows[i].sigungu === t.addr.gu);
    if (!inGu.length) {
      t4Ambiguous.push({ tid: t.tid, name: t.name, n: uniq.length, why: `이름은 있는데 전부 다른 구군 (${[...new Set(uniq.map((i) => rows[i].sigungu))].join(",")} · 정답은 ${t.addr.gu})` });
      continue;
    }
    if (inGu.length > 1) {
      t4Ambiguous.push({ tid: t.tid, name: t.name, n: inGu.length, why: `같은 구군에 그 이름이 ${inGu.length}곳` });
      continue;
    }
    claim(t, inGu[0], "T4", "이름+구군");
    continue;
  }
  // 주소가 아예 없는 것 = 미쉐린 단독. 부산에 그 이름이 하나뿐일 때만 붙인다
  if (uniq.length > 1) {
    t4Ambiguous.push({ tid: t.tid, name: t.name, n: uniq.length, why: `주소가 없고 이름이 부산에 ${uniq.length}곳` });
    continue;
  }
  claim(t, uniq[0], "T4", "이름유일(주소없음)");
}

for (const tid of remaining) {
  const t = byTid.get(tid);
  unmatched.push({
    tid: t.tid,
    name: t.name,
    sources: t.sources,
    addrRaw: t.addrRaw,
    why: t4Ambiguous.find((x) => x.tid === tid)?.why ?? "상가정보에 없다",
  });
}

// ── 보고 ─────────────────────────────────────────────────────────────────────
const bySrc = {};
for (const s of ["michelin", "blueribbon", "blog100", "baengnyeon"]) {
  const all = truth.items.filter((t) => t.sources.includes(s)).length;
  const hit = matched.filter((m) => m.sources.includes(s)).length;
  bySrc[s] = { 정답: all, 매칭: hit, 비율: `${((hit / all) * 100).toFixed(1)}%` };
}
console.log("\n── 🔴 출처별 매칭 ──");
console.log("출처\t정답가게\t매칭\t비율");
for (const [s, v] of Object.entries(bySrc)) console.log(`${s}\t${v.정답}\t${v.매칭}\t${v.비율}`);

const tierCount = new Map();
for (const m of matched) tierCount.set(m.tier, (tierCount.get(m.tier) ?? 0) + 1);
console.log("\n단계별:", [...tierCount].sort().map(([k, v]) => `${k}:${v}`).join(" "));
const howCount = new Map();
for (const m of matched) howCount.set(m.how, (howCount.get(m.how) ?? 0) + 1);
console.log("이름 일치 방식:", [...howCount].map(([k, v]) => `${k}:${v}`).join(" "));

const bySource = new Map();
for (const m of matched) bySource.set(m.source, (bySource.get(m.source) ?? 0) + 1);
console.log("어느 출처의 후보에 붙었나:", [...bySource].map(([k, v]) => `${k}:${v}`).join(" "));

const tasteMatched = matched.filter((m) => m.isTaste);
console.log(`\n전체 ${truth.items.length}곳 중 ${matched.length}곳 매칭 (${((matched.length / truth.items.length) * 100).toFixed(1)}%)`);
console.log(`  맛 정답 ${truth.tasteStores}곳 중 ${tasteMatched.length}곳 (${((tasteMatched.length / truth.tasteStores) * 100).toFixed(1)}%)`);
console.log(`  기저율(아무 데나 찍어 맞을 확률): ${((tasteMatched.length / rows.length) * 100).toFixed(3)}%`);
console.log(`\n못 붙은 것 ${unmatched.length}곳 · 그중 이름이 여러 곳이라 못 고른 것 ${t4Ambiguous.length}곳`);
for (const u of unmatched.slice(0, 25)) console.log(`  ${u.name} [${u.sources.join(",")}] ${u.addrRaw ?? "(주소없음)"} — ${u.why}`);
if (unmatched.length > 25) console.log(`  … 그 외 ${unmatched.length - 25}곳`);

const ovl = new Map();
for (const m of tasteMatched) ovl.set(m.nTaste, (ovl.get(m.nTaste) ?? 0) + 1);
console.log("\n매칭된 맛 정답의 겹침 분포:", [...ovl].sort().map(([k, v]) => `${k}출처:${v}곳`).join(" "));

fs.writeFileSync(
  path.join(HERE, "data", "r4-matched.json"),
  JSON.stringify(
    {
      matchedAt: new Date().toISOString(),
      poolSize: rows.length,
      poolSbiz: rows.filter((r) => r.source === "sbiz").length,
      poolOsmOnly: rows.filter((r) => r.source === "osm").length,
      byPoolSource: Object.fromEntries(bySource),
      truthStores: truth.items.length,
      matched: matched.length,
      tasteMatched: tasteMatched.length,
      bySource: bySrc,
      tiers: Object.fromEntries([...tierCount].sort()),
      items: matched,
      unmatchedItems: unmatched,
      ambiguousItems: t4Ambiguous,
    },
    null,
    1,
  ),
);
console.log("\n→ data/r4-matched.json");
