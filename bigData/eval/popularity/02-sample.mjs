/**
 * 2단계 — **표본 500곳**을 뽑는다.
 *
 *   node bigData/eval/popularity/02-sample.mjs --repo <상가정보와 정답지가 있는 저장소 루트>
 *
 * 판별력(어떤 신호가 맛집과 아닌 곳을 얼마나 잘 가르는가)을 재려면 **정답과 비정답이
 * 둘 다** 필요하다. 정답만 모으면 "맛집은 리뷰가 많다" 같은 말은 할 수 있어도
 * "리뷰가 많으면 맛집이다" 는 못 한다. 뒤쪽이 우리가 알고 싶은 것이다.
 *
 *  - **정답 158곳** — 미쉐린·블루리본·블로그100 을 상가정보에 붙인 것 (S15P21E201-712 산출물)
 *  - **비정답 350곳** — 상가정보 부산 "음식" 53,716곳에서 무작위
 *
 * 🔴 **무작위 350곳은 업종 분포를 정답과 맞춰서 뽑는다.** 정답이 횟집·돼지국밥에
 *    몰려 있는데 비교군이 편의점 김밥집이면, 잰 것은 맛이 아니라 **업종**이다.
 *    그렇게 나온 높은 판별력은 전부 가짜다.
 *
 * 내놓는 것이 두 갈래다.
 *  - `RAW_DIR/sample-500.json` — **저장소 밖.** 가게별 목록(수집할 때 쓰는 작업지시서)
 *  - `findings/02-sample-stats.json` — **저장소 안.** 개수와 분포만. 가게 이름이 없다
 */
import fs from "node:fs";
import path from "node:path";
import readline from "node:readline";
import { OUT_DIR, RAW_DIR, inputs, repoRootFromArgs, truthRootFromArgs } from "./paths.mjs";

const SEED = 7054026; // 라운드2~4 가 쓴 것과 같은 씨앗. 같은 씨앗이면 같은 표본이 나온다
const N_RANDOM = 350;

const IN = inputs(repoRootFromArgs(process.argv), truthRootFromArgs(process.argv));

/** 씨앗을 주면 항상 같은 난수열을 내놓는 생성기 (mulberry32) */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function shuffle(arr, rand) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/** 큰따옴표로 감싼 칸이 섞인 CSV 한 줄을 가른다 */
function parseCsvLine(line) {
  const out = [];
  let cur = "";
  let q = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (q) {
      if (ch === '"') {
        if (line[i + 1] === '"') { cur += '"'; i++; } else q = false;
      } else cur += ch;
    } else if (ch === '"') q = true;
    else if (ch === ",") { out.push(cur); cur = ""; }
    else cur += ch;
  }
  out.push(cur);
  return out;
}

const COL = {
  storeId: 0, name: 1, dae: 4, jung: 6, so: 8,
  sigungu: 14, hdong: 16, bdong: 18, floor: 35, lon: 37, lat: 38,
};

function need(p, what) {
  if (!fs.existsSync(p)) {
    console.error(`🔴 없다: ${p}`);
    console.error(`   ${what}`);
    process.exit(1);
  }
}

need(IN.sbizCsv, "상가정보 원본이다. 커밋하지 않는 파일이라 clone 만으로는 없다.");
need(IN.matched, "맛 정답지 158곳을 상가정보에 붙인 것. S15P21E201-712 브랜치의 산출물이다.");
need(IN.tuneSplit, "조정용 111곳 명단. 판정용 47곳은 이 분석이 열지 않는다.");

// ─── 정답 쪽 ─────────────────────────────────────────────────────────────────
const matched = JSON.parse(fs.readFileSync(IN.matched, "utf8"));
const truth = matched.items.filter((it) => it.isTaste && it.storeId);
const tuneIds = new Set(JSON.parse(fs.readFileSync(IN.tuneSplit, "utf8")).items.map((x) => x.tid ?? x));

console.log(`맛 정답지: ${truth.length}곳 (상가정보에 붙은 것)`);
const truthSbiz = truth.filter((t) => t.source === "sbiz");
console.log(`  그중 상가정보 출신: ${truthSbiz.length}곳`);

const truthStoreIds = new Set(truth.map((t) => t.storeId));

/** 업종 소분류별 정답 개수 — 무작위 350곳을 이 비율로 맞춘다 */
const truthBySo = new Map();
for (const t of truth) truthBySo.set(t.so ?? "(없음)", (truthBySo.get(t.so ?? "(없음)") ?? 0) + 1);

// ─── 비정답 쪽 ───────────────────────────────────────────────────────────────
const poolBySo = new Map();
let foodCount = 0;
{
  const rl = readline.createInterface({
    input: fs.createReadStream(IN.sbizCsv, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  let first = true;
  for await (const line of rl) {
    if (!line.trim()) continue;
    const c = parseCsvLine(line);
    if (first) { first = false; continue; }
    if (c[COL.dae] !== "음식") continue;
    foodCount++;
    const id = c[COL.storeId];
    if (truthStoreIds.has(id)) continue; // 정답은 비교군에 넣지 않는다
    const lat = Number(c[COL.lat]);
    const lon = Number(c[COL.lon]);
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat === 0 || lon === 0) continue;
    const so = c[COL.so] || "(없음)";
    if (!truthBySo.has(so)) continue; // 정답에 없는 업종은 애초에 뽑지 않는다
    if (!poolBySo.has(so)) poolBySo.set(so, []);
    poolBySo.get(so).push({
      storeId: id, name: c[COL.name], so,
      sigungu: c[COL.sigungu], hdong: c[COL.hdong], bdong: c[COL.bdong],
      floor: c[COL.floor], lat, lon,
    });
  }
}
console.log(`상가정보 "음식": ${foodCount}곳`);
console.log(`정답과 같은 업종에 있는 비정답 후보: ${[...poolBySo.values()].reduce((a, b) => a + b.length, 0)}곳 (업종 ${poolBySo.size}종)`);

// ─── 업종 분포를 맞춰 350곳 ──────────────────────────────────────────────────
const rand = rng(SEED);
const alloc = [];
for (const [so, n] of truthBySo) {
  const avail = poolBySo.get(so)?.length ?? 0;
  alloc.push({ so, truthN: n, want: (N_RANDOM * n) / truth.length, avail });
}
// 큰 쪽부터 정수로 내려 깎고, 모자란 만큼을 여유 있는 업종에 되돌린다
alloc.sort((a, b) => b.want - a.want);
for (const a of alloc) a.take = Math.min(a.avail, Math.floor(a.want));
let short = N_RANDOM - alloc.reduce((s, a) => s + a.take, 0);
for (const a of alloc.sort((x, y) => (y.want - y.take) - (x.want - x.take))) {
  if (short <= 0) break;
  if (a.take < a.avail) { a.take++; short--; }
}
while (short > 0) {
  const room = alloc.filter((a) => a.take < a.avail);
  if (!room.length) break;
  room[Math.floor(rand() * room.length)].take++;
  short--;
}

const control = [];
for (const a of alloc) {
  if (!a.take) continue;
  const picked = shuffle(poolBySo.get(a.so), rand).slice(0, a.take);
  for (const p of picked) control.push({ ...p, role: "비정답" });
}

console.log(`무작위(업종 맞춤): ${control.length}곳`);
if (short > 0) console.log(`  🔴 ${short}곳 모자란다 — 정답 업종의 후보가 그만큼 없다`);

// ─── 내놓기 ──────────────────────────────────────────────────────────────────
const positives = truth.map((t) => ({
  storeId: t.storeId, name: t.storeName ?? t.name, so: t.so,
  sigungu: t.sigungu, lat: t.lat, lon: t.lon,
  role: "정답",
  tid: t.tid,
  split: tuneIds.has(t.tid) ? "조정용" : "판정용",
  sources: t.tasteSources,
  michelinPrice: t.michelinPrice ?? null,
  blueRibbon: t.blueRibbon ?? null,
}));

fs.mkdirSync(RAW_DIR, { recursive: true });
fs.writeFileSync(
  path.join(RAW_DIR, "sample-500.json"),
  JSON.stringify({
    madeAt: new Date().toISOString(),
    seed: SEED,
    주의: "🔴 저장소 밖 파일이다. 가게별 목록이라 커밋하지 않는다. destroy-raw.mjs 가 지운다.",
    정답: positives.length,
    비정답: control.length,
    items: [...positives, ...control],
  }, null, 1),
  "utf8",
);

/** 저장소 안에는 **분포만** 남긴다 — 어느 가게인지는 남지 않는다 */
function dist(arr, key) {
  const m = new Map();
  for (const x of arr) m.set(x[key] ?? "(없음)", (m.get(x[key] ?? "(없음)") ?? 0) + 1);
  return Object.fromEntries([...m].sort((a, b) => b[1] - a[1]));
}

const stats = {
  ranAt: new Date().toISOString(),
  seed: SEED,
  풀: { "상가정보 음식": foodCount, "정답 업종에 있는 비정답 후보": [...poolBySo.values()].reduce((a, b) => a + b.length, 0) },
  표본: {
    정답: positives.length,
    "  조정용": positives.filter((p) => p.split === "조정용").length,
    "  판정용(열지 않는다)": positives.filter((p) => p.split === "판정용").length,
    비정답: control.length,
    합계: positives.length + control.length,
  },
  업종분포: {
    정답: dist(positives, "so"),
    비정답: dist(control, "so"),
  },
  구분포: {
    정답: dist(positives, "sigungu"),
    비정답: dist(control, "sigungu"),
  },
  원본이간곳: RAW_DIR,
  파기: "node bigData/eval/popularity/destroy-raw.mjs",
};

fs.mkdirSync(OUT_DIR, { recursive: true });
fs.writeFileSync(path.join(OUT_DIR, "02-sample-stats.json"), JSON.stringify(stats, null, 1), "utf8");

console.log(`\n표본 ${positives.length + control.length}곳`);
console.log(`  가게별 목록 → ${path.join(RAW_DIR, "sample-500.json")}  (저장소 밖)`);
console.log(`  분포만      → ${path.join(OUT_DIR, "02-sample-stats.json")}  (저장소 안)`);
