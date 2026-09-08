/**
 * 라운드2 0단계 — 상가정보 CSV 의 칸을 **실측**한다. 추측하지 않는다.
 * 산출: 표준출력만. 파일을 만들지 않는다.
 */
import fs from "node:fs";
import readline from "node:readline";
import { CSV_PATH, parseCsvLine } from "./lib/sbiz.mjs";

const rl = readline.createInterface({
  input: fs.createReadStream(CSV_PATH, { encoding: "utf8" }),
  crlfDelay: Infinity,
});

let header = null;
let total = 0;
const dae = new Map();
const jung = new Map();
const so = new Map();
const floor = new Map();
const daejigu = new Map();
let food = 0, foodNoCoord = 0, foodNoRoad = 0, foodNoBldgName = 0, foodNoFloor = 0;
const gu = new Map();
const bcode = new Set();
let sample = [];

for await (const line of rl) {
  if (!line.trim()) continue;
  const c = parseCsvLine(line);
  if (!header) { header = c; continue; }
  total++;
  const d = c[4];
  dae.set(d, (dae.get(d) ?? 0) + 1);
  if (d !== "음식") continue;
  food++;
  jung.set(c[6], (jung.get(c[6]) ?? 0) + 1);
  so.set(c[8], (so.get(c[8]) ?? 0) + 1);
  floor.set(c[35] || "(빈칸)", (floor.get(c[35] || "(빈칸)") ?? 0) + 1);
  daejigu.set(c[21] || "(빈칸)", (daejigu.get(c[21] || "(빈칸)") ?? 0) + 1);
  gu.set(c[14], (gu.get(c[14]) ?? 0) + 1);
  bcode.add(c[17]);
  const lon = Number(c[37]), lat = Number(c[38]);
  if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat === 0 || lon === 0) foodNoCoord++;
  if (!c[31]) foodNoRoad++;
  if (!c[30]) foodNoBldgName++;
  if (!c[35]) foodNoFloor++;
  if (sample.length < 3) sample.push(c);
}

console.log(`칸 ${header.length}개`);
header.forEach((h, i) => console.log(`  [${i}] ${h}`));
console.log(`\n총 ${total}행`);
console.log("\n대분류별:");
[...dae].sort((a, b) => b[1] - a[1]).forEach(([k, v]) => console.log(`  ${k}\t${v}`));
console.log(`\n음식 ${food}행 · 좌표없음 ${foodNoCoord} · 도로명주소없음 ${foodNoRoad} · 건물명없음 ${foodNoBldgName} · 층정보없음 ${foodNoFloor}`);
console.log(`법정동 코드 종류 ${bcode.size}`);
console.log("\n음식 중분류:");
[...jung].sort((a, b) => b[1] - a[1]).forEach(([k, v]) => console.log(`  ${k}\t${v}`));
console.log("\n음식 소분류 상위 30:");
[...so].sort((a, b) => b[1] - a[1]).slice(0, 30).forEach(([k, v]) => console.log(`  ${k}\t${v}`));
console.log(`소분류 종류 ${so.size}개`);
console.log("\n층정보 상위 12:");
[...floor].sort((a, b) => b[1] - a[1]).slice(0, 12).forEach(([k, v]) => console.log(`  ${k}\t${v}`));
console.log("\n대지구분:");
[...daejigu].sort((a, b) => b[1] - a[1]).forEach(([k, v]) => console.log(`  ${k}\t${v}`));
console.log("\n구군별 음식:");
[...gu].sort((a, b) => b[1] - a[1]).forEach(([k, v]) => console.log(`  ${k}\t${v}`));
