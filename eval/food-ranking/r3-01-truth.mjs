/**
 * 라운드3 1단계 — 정답지 넷을 읽어 **하나의 정답표**로 만든다.
 *
 * 산출: data/r3-truth.json
 *   · 가게마다 어느 출처에 실렸는지(sources)를 남긴다 — 한 가게가 여러 곳에 실릴 수 있다
 *   · 겹침 수(overlap)를 센다. 🔴 여러 출처가 겹치는 가게가 정답으로서 값이 크다
 *   · 백년가게는 **맛이 아니라 존속기간**이라 `taste` 집합에서 빼고 별도 축으로 둔다
 *
 * 이 단계는 후보 풀(상가정보)을 보지 않는다. 붙이는 것은 r3-02-match.mjs 가 한다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadRawEntries, mergeEntries } from "./lib/r3-truth.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const RAW_DIR =
  process.env.TRUTH_RAW_DIR ?? path.join(HERE, "..", "..", "bigData", "data", "truth", "raw");

const entries = loadRawEntries(RAW_DIR);
const bySource = new Map();
for (const e of entries) bySource.set(e.source, (bySource.get(e.source) ?? 0) + 1);
console.log("── 출처별 원본 항목 수 ──");
for (const [s, n] of bySource) console.log(`  ${s}\t${n}`);
console.log(`  합계\t${entries.length} (백년가게는 음식점만 센 것이다)`);

const noAddr = entries.filter((e) => !e.addrRaw).length;
const badAddr = entries.filter((e) => e.addrRaw).length;
console.log(`주소가 아예 없는 항목: ${noAddr}건 (미쉐린은 원문에 주소가 없다)`);

const { groups, addrMerges, prefixMerges } = mergeEntries(entries);

console.log(`\n── 합치기 ──`);
console.log(`원본 ${entries.length}건 → 가게 ${groups.length}곳`);
console.log(`  같은 주소·비슷한 이름으로 합친 것 ${addrMerges.length}쌍:`);
for (const [a, b, k] of addrMerges) console.log(`    ${a} ↔ ${b}  @${k}`);
console.log(`  접두사 포함으로 합친 것 ${prefixMerges.length}쌍:`);
for (const [a, b] of prefixMerges) console.log(`    ${a} ⊂ ${b}`);

const ambiguous = groups.filter((g) => g.ambiguous);
console.log(`  🔴 주소를 못 고른 항목 ${ambiguous.length}건 (같은 이름의 주소 묶음이 둘 이상):`);
for (const g of ambiguous) console.log(`    ${g.core} — ${g.members.map((m) => m.source).join(",")}`);

// ── 가게 단위 레코드 ─────────────────────────────────────────────────────────
const TASTE = new Set(["michelin", "blueribbon", "blog100"]);
const stores = groups.map((g, gi) => {
  const sources = [...new Set(g.members.map((m) => m.source))].sort();
  const names = [...new Set(g.members.map((m) => m.name))];
  const mich = g.members.find((m) => m.source === "michelin");
  const blue = g.members.find((m) => m.source === "blueribbon");
  const blog = g.members.find((m) => m.source === "blog100");
  const baek = g.members.find((m) => m.source === "baengnyeon");
  const tasteSources = sources.filter((s) => TASTE.has(s));
  return {
    tid: `r3-${String(gi).padStart(4, "0")}`,
    core: g.core,
    names,
    // 화면에 쓸 대표 표기 — 주소가 있는 출처의 이름을 먼저 쓴다
    name: (blue ?? blog ?? baek ?? mich).name,
    addr: g.addr,
    addrRaw: (blue ?? blog ?? baek)?.addrRaw ?? null,
    sources,
    nSources: sources.length,
    tasteSources,
    nTaste: tasteSources.length,
    isTaste: tasteSources.length > 0,
    isBaengnyeon: Boolean(baek),
    ambiguous: g.ambiguous,
    // 🔴 정답의 **강도**로만 쓴다. 신호(입력)로 쓰면 누출이다
    michelinPrice: mich?.meta.price ?? null,
    blueRibbon: blue?.meta.ribbon ?? null,
    blueTheme: blue?.meta.theme ?? null,
    cuisine: mich?.meta.cuisine ?? blue?.meta.cuisine ?? null,
  };
});

// ── 겹침 분포 ────────────────────────────────────────────────────────────────
const taste = stores.filter((s) => s.isTaste);
const overlapDist = new Map();
for (const s of taste) overlapDist.set(s.nTaste, (overlapDist.get(s.nTaste) ?? 0) + 1);
console.log(`\n── 🔴 출처 겹침 분포 (맛 정답지 셋 기준: 미쉐린·블루리본·블로그100) ──`);
console.log(`맛 정답 가게 ${taste.length}곳`);
for (const k of [...overlapDist.keys()].sort()) {
  console.log(`  ${k}개 출처에 실림\t${overlapDist.get(k)}곳`);
}
const triples = taste.filter((s) => s.nTaste === 3);
console.log(`  셋 다: ${triples.map((s) => s.name).join(" · ")}`);

const pairCount = new Map();
for (const s of taste) {
  for (let a = 0; a < s.tasteSources.length; a++)
    for (let b = a + 1; b < s.tasteSources.length; b++)
      pairCount.set(`${s.tasteSources[a]}∩${s.tasteSources[b]}`, (pairCount.get(`${s.tasteSources[a]}∩${s.tasteSources[b]}`) ?? 0) + 1);
}
console.log("  두 출처 겹침:");
for (const [k, v] of [...pairCount].sort((x, y) => y[1] - x[1])) console.log(`    ${k}\t${v}곳`);

const baeng = stores.filter((s) => s.isBaengnyeon);
console.log(`\n백년가게(음식점) ${baeng.length}곳 · 그중 맛 정답지와도 겹치는 것 ${baeng.filter((s) => s.isTaste).length}곳:`);
console.log(`  ${baeng.filter((s) => s.isTaste).map((s) => `${s.name}(${s.tasteSources.join(",")})`).join(" · ")}`);

const withAddr = stores.filter((s) => s.addr).length;
console.log(`\n주소를 읽어낸 가게 ${withAddr}곳 / ${stores.length}곳 · 도로명 ${stores.filter((s) => s.addr?.kind === "road").length} · 지번 ${stores.filter((s) => s.addr?.kind === "jibun").length}`);

fs.writeFileSync(
  path.join(HERE, "data", "r3-truth.json"),
  JSON.stringify(
    {
      builtAt: new Date().toISOString(),
      rawDir: RAW_DIR,
      rawEntries: entries.length,
      bySource: Object.fromEntries(bySource),
      stores: stores.length,
      tasteStores: taste.length,
      baengnyeonStores: baeng.length,
      overlapDist: Object.fromEntries([...overlapDist].sort()),
      addrMerges,
      prefixMerges,
      items: stores,
    },
    null,
    1,
  ),
);
console.log(`\n→ data/r3-truth.json`);
