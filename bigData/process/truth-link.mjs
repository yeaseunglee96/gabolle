/**
 * 정답지(맛집이라고 이미 알려진 목록)를 상가정보의 가게에 이어 붙인다.
 *
 * 🔴 **왜 필요한가 — 조사원을 채점하기 위해서다.**
 *   2,000곳을 조사원(사람이든 AI 든)에게 나눠 주고 *"사람들이 왜 간다고 하나"* 를 적게 하면,
 *   그 답이 **믿을 만한지 알 방법이 없다.** 그럴듯한 문장은 아무 근거 없이도 나온다.
 *
 *   그런데 정답지에 실린 집은 **우리가 답을 이미 안다.** 그래서 조사가 끝난 뒤 이렇게 물을 수 있다 —
 *   *"조사원이 「현지인이 많이 간다」 고 적은 집들이, 실제로 택슐랭(택시기사가 고른 원도심
 *   밥집 목록)에 많이 들어 있나?"* 맞으면 나머지 조사도 믿을 만하다는 뜻이고, 안 맞으면
 *   **믿으면 안 된다는 것을 싸게 알게 된다.**
 *
 *   🔴 **그래서 조사원에게는 어느 집이 정답지인지 알려주지 않는다.** 알면 그쪽으로 답을
 *   맞춘다. 이 파일은 **우리 쪽 파일**이고 `research/` 안에 두지 않는다.
 *
 * ── 붙이는 방법 — 목록마다 다르다 ──────────────────────────────────────────────
 *   공개 글 448곳  이미 상가정보와 대조된 칸(`상가정보_상호`·`상가정보_주소`)이 파일 안에 있다.
 *                  그 둘로 정확히 되찾는다. **새로 매칭하지 않는다** — 남이 눈으로 확인한 것을 쓴다
 *   택슐랭 60곳    도로명주소와 **좌표**가 둘 다 있다. 좌표 50m → 120m → 250m 안에서
 *                  이름이 같거나/포함하거나/앞 3글자가 같은 것을 찾는다
 *   블루리본·블로그100·백년가게  주소에서 구·군만 뽑아, **같은 구 안에서 이름이 완전히 같고
 *                  그런 집이 그 구에 하나뿐일 때만** 붙인다
 *   🔴 미쉐린      **원문에 주소가 아예 없다.** 구·군을 강제할 수 없어 이름만으로 붙이게 되는데,
 *                  라운드3 이 그렇게 해서 남의 가게 5건을 정답으로 붙였다. **안 붙인다**
 *
 * 산출: data/staged/truth-linked.ndjson  (한 줄에 한 곳)
 * 사용: node process/truth-link.mjs
 */
import fs from "node:fs";
import path from "node:path";
import readline from "node:readline";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const CSV = process.env.SBIZ_CSV ?? path.join(ROOT, "data/raw/poi/sbiz-poi-busan-202606.csv");
const TRUTH = path.join(ROOT, "data/truth/raw");
const LINK = path.join(ROOT, "data/staged/place-link.ndjson");
const SELECTED = path.join(ROOT, "data/staged/selected-2000.ndjson");
const OUT = path.join(ROOT, "data/staged/truth-linked.ndjson");

// ── 상가정보 ─────────────────────────────────────────────────────────────────
function parseCsvLine(line) {
  const out = [];
  let cur = "", q = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (q) {
      if (ch === '"') { if (line[i + 1] === '"') { cur += '"'; i++; } else q = false; }
      else cur += ch;
    } else if (ch === '"') q = true;
    else if (ch === ",") { out.push(cur); cur = ""; }
    else cur += ch;
  }
  out.push(cur);
  return out;
}
const C = {
  id: 0, name: 1, branch: 2, jung: 6, soCode: 7, so: 8,
  sigungu: 14, hdong: 16, bdong: 18, roadAddr: 31, lon: 37, lat: 38,
};
/** 공백·괄호·기호를 지운다 — 정답지 매칭에 쓰는 이름 정규화 */
function norm(s) {
  return String(s ?? "")
    .replace(/\(.*?\)/g, "")
    .replace(/[\s\-·.,'"’`]/g, "")
    .toLowerCase();
}
function commonPrefix(a, b) {
  let i = 0;
  while (i < a.length && i < b.length && a[i] === b[i]) i++;
  return i;
}

async function loadFood() {
  const rl = readline.createInterface({
    input: fs.createReadStream(CSV, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  const out = [];
  let first = true;
  for await (const line of rl) {
    if (!line.trim()) continue;
    const c = parseCsvLine(line);
    if (first) { first = false; continue; }
    if (c[4] !== "음식") continue;
    const lat = Number(c[C.lat]), lon = Number(c[C.lon]);
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat === 0 || lon === 0) continue;
    out.push({
      id: c[C.id], name: c[C.name], branch: c[C.branch],
      soCode: c[C.soCode], so: c[C.so], jung: c[C.jung],
      gu: c[C.sigungu], hdong: c[C.hdong], bdong: c[C.bdong],
      roadAddr: c[C.roadAddr], lat, lon,
    });
  }
  return out;
}

// ── TSV ──────────────────────────────────────────────────────────────────────
function readTsv(name) {
  const p = path.join(TRUTH, `${name}.tsv`);
  if (!fs.existsSync(p)) return [];
  return fs.readFileSync(p, "utf8")
    .split("\n")
    .filter((l) => l.trim() && !l.startsWith("#"))
    .map((l) => l.split("\t"));
}

const R = 6371000;
function haversine(aLat, aLon, bLat, bLon) {
  const dLat = ((bLat - aLat) * Math.PI) / 180;
  const dLon = ((bLon - aLon) * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 +
    Math.cos((aLat * Math.PI) / 180) * Math.cos((bLat * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

// ── 본체 ─────────────────────────────────────────────────────────────────────
const food = await loadFood();
console.log(`상가정보 음식 ${food.length}곳`);

const byNameAddr = new Map();     // norm(상호)|도로명주소 → 가게
const byNameGu = new Map();       // norm(상호)|구군 → [가게]
for (const r of food) {
  byNameAddr.set(`${norm(r.name)}|${r.roadAddr}`, r);
  for (const n of new Set([norm(r.name), norm(r.name + r.branch)])) {
    if (!n) continue;
    const k = `${n}|${r.gu}`;
    if (!byNameGu.has(k)) byNameGu.set(k, []);
    byNameGu.get(k).push(r);
  }
}
// 좌표 격자 (택슐랭용)
const CELL = 0.005;
const grid = new Map();
food.forEach((r, i) => {
  const k = `${Math.floor(r.lat / CELL)}|${Math.floor(r.lon / CELL)}`;
  if (!grid.has(k)) grid.set(k, []);
  grid.get(k).push(i);
});
function within(lat, lon, radiusM) {
  const span = Math.ceil(radiusM / (111320 * CELL * Math.cos((lat * Math.PI) / 180))) + 1;
  const cy = Math.floor(lat / CELL), cx = Math.floor(lon / CELL);
  const out = [];
  for (let y = cy - span; y <= cy + span; y++)
    for (let x = cx - span; x <= cx + span; x++)
      for (const i of grid.get(`${y}|${x}`) ?? []) {
        const d = haversine(lat, lon, food[i].lat, food[i].lon);
        if (d <= radiusM) out.push({ r: food[i], d });
      }
  return out.sort((a, b) => a.d - b.d);
}
/** 이름이 같거나 · 서로 포함하거나 · 앞 3글자가 같은가 */
function nameOk(sbizRow, wanted) {
  const w = norm(wanted);
  if (w.length < 2) return false;
  for (const n of [norm(sbizRow.name), norm(sbizRow.name + sbizRow.branch)]) {
    if (!n || n.length < 2) continue;
    if (n === w) return true;
    if (n.includes(w) || w.includes(n)) return true;
    if (n.length >= 3 && w.length >= 3 && commonPrefix(n, w) >= 3) return true;
  }
  return false;
}
const GU = /(강서구|금정구|기장군|남구|동구|동래구|부산진구|북구|사상구|사하구|서구|수영구|연제구|영도구|중구|해운대구)/;

/** id → { lists:Set, whyTypes:Set, sourceCount, how } */
const hit = new Map();
function add(row, list, how, extra = {}) {
  if (!hit.has(row.id)) hit.set(row.id, { row, lists: new Set(), whyTypes: new Set(), sourceCount: 0, how: {} });
  const h = hit.get(row.id);
  h.lists.add(list);
  h.how[list] = how;
  if (extra.whyTypes) for (const t of extra.whyTypes) h.whyTypes.add(t);
  if (extra.sourceCount) h.sourceCount = Math.max(h.sourceCount, extra.sourceCount);
}

// ① 공개 글 448곳 — 이미 대조된 칸을 그대로 쓴다
{
  const rows = readTsv("public-lists-busan-2026");
  let ok = 0, miss = 0;
  for (const c of rows) {
    const status = c[8], sName = c[9], sAddr = c[10];
    if (!(status === "이름일치" || status === "부분일치")) continue;
    if (!sName || !sAddr) continue;
    const r = byNameAddr.get(`${norm(sName)}|${sAddr}`);
    if (!r) { miss++; continue; }
    add(r, "공개글448", status, {
      whyTypes: (c[5] ?? "").split(";").map((s) => s.trim()).filter(Boolean),
      sourceCount: Number(c[7]) || 1,
    });
    ok++;
  }
  console.log(`공개 글 448곳: ${rows.length}줄 → 붙음 ${ok} · 대조 칸이 있는데 되찾기 실패 ${miss}`);
}

// ② 택슐랭 — 좌표 사다리
{
  const rows = readTsv("taxseulrang-busan-2026");
  let ok = 0;
  const failed = [];
  for (const c of rows) {
    const name = c[0], addr = c[2] ?? "", lat = Number(c[3]), lon = Number(c[4]);
    let found = null, how = null;
    if (Number.isFinite(lat) && Number.isFinite(lon)) {
      for (const radius of [50, 120, 250]) {
        const cands = within(lat, lon, radius).filter((x) => nameOk(x.r, name));
        if (cands.length) { found = cands[0].r; how = `좌표${radius}m`; break; }
      }
    }
    // 좌표가 빗나간 것(구글 지도에 손으로 찍은 핀이라 수백 m 어긋나기도 한다)은
    // 🔴 구·군을 강제한 채 **이름이 완전히 같고 그 구에 하나뿐일 때만** 붙인다.
    if (!found) {
      const g = addr.match(GU);
      const cands = g ? byNameGu.get(`${norm(name)}|${g[1]}`) ?? [] : [];
      if (cands.length === 1) { found = cands[0]; how = "구군+이름완전일치"; }
    }
    if (!found) { failed.push(name); continue; }
    add(found, "택슐랭", how);
    ok++;
  }
  console.log(`택슐랭: ${rows.length}줄 → 붙음 ${ok} (문서 실측 51곳) · 못 붙음 ${failed.length}`);
  if (failed.length) console.log(`  못 붙은 것: ${failed.join(", ")}`);
}

// ③ 블루리본 · 블로그100 · 백년가게 — 같은 구 안에서 이름이 완전히 같고 그런 집이 하나뿐일 때만
for (const [file, list, nameCol, addrCol] of [
  ["blueribbon-busan-2026", "블루리본", 1, 5],
  ["blog-busan100-2026", "블로그100", 1, 2],
  ["baengnyeon-busan", "백년가게", 1, 3],
]) {
  const rows = readTsv(file);
  let ok = 0, ambiguous = 0, noGu = 0, noHit = 0;
  for (const c of rows) {
    const name = c[nameCol], addr = c[addrCol] ?? "";
    const g = addr.match(GU);
    if (!g) { noGu++; continue; }
    const cands = byNameGu.get(`${norm(name)}|${g[1]}`) ?? [];
    if (!cands.length) { noHit++; continue; }
    if (cands.length > 1) { ambiguous++; continue; }   // 🔴 같은 구에 같은 이름이 둘 — 안 붙인다
    add(cands[0], list, "구군+이름완전일치");
    ok++;
  }
  console.log(
    `${list}: ${rows.length}줄 → 붙음 ${ok} · 같은 구에 동명이 둘 이상이라 뺌 ${ambiguous} · ` +
      `주소에 구군 없음 ${noGu} · 상가정보에 없음 ${noHit}`,
  );
}
console.log(`🔴 미쉐린: 원문에 주소가 없어 붙이지 않았습니다 (이름만 붙이면 남의 가게가 섞입니다)`);

// ── 인허가·선정 결과를 붙인다 ────────────────────────────────────────────────
const permitOf = new Map();
if (fs.existsSync(LINK)) {
  const rl = readline.createInterface({
    input: fs.createReadStream(LINK, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  for await (const line of rl) {
    if (!line.trim()) continue;
    const r = JSON.parse(line);
    if (r.permit) permitOf.set(r.sbizId, r.permit);
  }
}
const selected = new Set();
if (fs.existsSync(SELECTED)) {
  for (const line of fs.readFileSync(SELECTED, "utf8").split("\n")) {
    if (line.trim()) selected.add(JSON.parse(line).id);
  }
} else {
  console.log(`  ⚠ ${path.relative(ROOT, SELECTED)} 이 없습니다 — inSelected2000 을 못 채웁니다`);
}

// ── 쓴다 ─────────────────────────────────────────────────────────────────────
let ghosts = 0, inSel = 0;
const lines = [];
for (const h of hit.values()) {
  const r = h.row;
  const p = permitOf.get(r.id) ?? null;
  if (p?.state === "폐업") ghosts++;
  if (selected.has(r.id)) inSel++;
  lines.push(JSON.stringify({
    id: r.id,
    name: r.name,
    branch: r.branch || null,
    roadAddr: r.roadAddr,
    gu: r.gu,
    hdong: r.hdong,
    bdong: r.bdong,
    category: { code: r.soCode, name: r.so, mid: r.jung },
    lon: r.lon,
    lat: r.lat,
    // 🔴 아래 셋은 **우리 쪽 정보**다. 조사원에게 주는 대기열에는 들어가지 않는다.
    truth: {
      lists: [...h.lists],
      how: h.how,
      whyTypes: [...h.whyTypes],   // 공개 글이 든 이유 유형 (19가지 중)
      sourceCount: h.sourceCount || null,
    },
    inSelected2000: selected.has(r.id),
    permit: p ? { state: p.state, openedOn: p.openedOn, category: p.category } : null,
  }));
}
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, lines.join("\n") + "\n");

console.log(
  `\n정답지에 붙은 가게 ${hit.size}곳 (중복 제거) · 그중 2,000곳 안에 이미 있는 것 ${inSel} · ` +
    `밖에 있어 대기열에 더해야 하는 것 ${hit.size - inSel}`,
);
if (ghosts) console.log(`  ⚠ 그중 인허가가 폐업인 것 ${ghosts}곳 (정답지가 낡았거나 매칭이 틀렸을 수 있습니다)`);
const perList = new Map();
for (const h of hit.values()) for (const l of h.lists) perList.set(l, (perList.get(l) ?? 0) + 1);
console.log(`목록별: ${[...perList.entries()].map(([k, v]) => `${k} ${v}`).join(" · ")}`);
console.log(`→ ${path.relative(ROOT, OUT)}`);
