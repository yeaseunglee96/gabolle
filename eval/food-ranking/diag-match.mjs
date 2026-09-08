/** 매칭이 왜 안 붙는지 들여다보는 일회용 진단. 결과는 문서에만 남기고 판정에 안 쓴다. */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadFoodPois, buildGrid, neighbors } from "./lib/pool.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const truth = JSON.parse(fs.readFileSync(path.join(HERE, "data", "truth-tourapi.json"), "utf8"));
const pois = loadFoodPois();
const grid = buildGrid(pois);

let noNeighborAt100 = 0, noNeighborAt400 = 0;
const samples = [];
for (const t of truth.items) {
  const n100 = neighbors(grid, t.lat, t.lng, 100);
  const n400 = neighbors(grid, t.lat, t.lng, 400);
  if (!n100.length) noNeighborAt100++;
  if (!n400.length) noNeighborAt400++;
  if (samples.length < 15 && n100.length) {
    samples.push({
      truth: t.title,
      near: n100
        .sort((a, b) => a.d - b.d)
        .slice(0, 4)
        .map((x) => `${pois[x.i].tags["name:ko"] ?? pois[x.i].tags.name ?? "(이름없음)"}@${Math.round(x.d)}m`),
    });
  }
}
console.log(`반경 100m 안에 식음료 POI 가 하나도 없는 정답: ${noNeighborAt100}/${truth.items.length}`);
console.log(`반경 400m 안에 식음료 POI 가 하나도 없는 정답: ${noNeighborAt400}/${truth.items.length}`);
console.log("\n100m 안에 이웃이 있는 사례 15건 — 이름이 정말 다른가:");
for (const s of samples) console.log(`  ${s.truth}  ←  ${s.near.join(" · ")}`);
