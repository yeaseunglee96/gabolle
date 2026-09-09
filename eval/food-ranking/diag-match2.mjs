/** 매칭 상한을 잰다 — 거리를 아예 무시하고 이름만으로 붙이면 몇 건까지 되나. */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadFoodPois, buildGrid, neighbors } from "./lib/pool.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const truth = JSON.parse(fs.readFileSync(path.join(HERE, "data", "truth-tourapi.json"), "utf8"));
const pois = loadFoodPois();
const norm = (s) =>
  String(s).replace(/\(.*?\)/g, "").replace(/[\s\-·.,'"’`]/g, "").toLowerCase();

const nameIdx = new Map();
for (const p of pois) {
  const n = p.tags["name:ko"] ?? p.tags.name;
  if (!n) continue;
  const k = norm(n);
  if (!nameIdx.has(k)) nameIdx.set(k, []);
  nameIdx.get(k).push(p);
}

let exactAnywhere = 0;
const distances = [];
const grid = buildGrid(pois);
for (const t of truth.items) {
  const k = norm(t.title);
  const hit = nameIdx.get(k);
  if (!hit) continue;
  exactAnywhere++;
  const d = Math.min(
    ...hit.map((p) => {
      const dx = (p.lon - t.lng) * 88000;
      const dy = (p.lat - t.lat) * 111320;
      return Math.hypot(dx, dy);
    }),
  );
  distances.push(Math.round(d));
}
distances.sort((a, b) => a - b);
console.log(`이름 완전일치(거리 무시): ${exactAnywhere}/${truth.items.length}`);
console.log(`  그때 좌표 차이 m — 중앙값 ${distances[Math.floor(distances.length / 2)]} · 최대 ${distances.at(-1)}`);
console.log(`  400m 이내: ${distances.filter((d) => d <= 400).length} · 1km 이내: ${distances.filter((d) => d <= 1000).length}`);

// 부분일치(한쪽이 다른 쪽을 포함)를 거리 무시로 세면?
let partialAnywhere = 0;
const keys = [...nameIdx.keys()];
for (const t of truth.items) {
  const k = norm(t.title);
  if (k.length < 3) continue;
  if (nameIdx.has(k)) continue;
  if (keys.some((kk) => kk.length >= 3 && (kk.includes(k) || k.includes(kk)))) partialAnywhere++;
}
console.log(`추가 부분일치(거리 무시, 3자 이상): ${partialAnywhere}건`);
