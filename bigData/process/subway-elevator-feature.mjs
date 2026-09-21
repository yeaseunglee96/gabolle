/**
 * 지하철 엘리베이터 — place-feature 로 쓸 수 있는 모양으로 정리한다.
 *
 * 원본은 `data/staged/subway-entrance.ndjson`(process/subway-access.mjs 산출, 이미 있음).
 * 출구 단위로 엘리베이터 유무·역 단위 집계가 섞여 있어서, 여기서는 **용도별로 갈라** 낸다.
 *
 * 🔴 "모르는 것"과 "없는 것"을 구분한다 — `exitElevator` 가 `null` 이면 원본 자료에
 *    아예 없어서 모르는 것이지, 엘리베이터가 없다는 뜻이 아니다. `evidenceStatus` 로
 *    갈라 둔다(이 저장소가 `place_feature` 에서 쓰는 것과 같은 원칙 — 이어받기 문서
 *    제안 ③).
 *
 * 입력  data/staged/subway-entrance.ndjson
 * 출력  data/staged/place-feature-subway-exit.ndjson       출구 단위 (좌표로 조인)
 *       data/staged/place-feature-subway-station.ndjson    역 단위 집계 (이름·호선으로 조인)
 *
 * 사용  node process/subway-elevator-feature.mjs
 */
import fs from "node:fs";
import path from "node:path";
import readline from "node:readline";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const IN = path.join(ROOT, "data/staged/subway-entrance.ndjson");
const OUT_EXIT = path.join(ROOT, "data/staged/place-feature-subway-exit.ndjson");
const OUT_STATION = path.join(ROOT, "data/staged/place-feature-subway-station.ndjson");

const rl = readline.createInterface({ input: fs.createReadStream(IN, { encoding: "utf8" }), crlfDelay: Infinity });

const exitRows = [];
const byStation = new Map();
let noCoord = 0;

for await (const line of rl) {
  if (!line.trim()) continue;
  const r = JSON.parse(line);

  // 🔴 좌표는 이 파일의 존재 이유다 — Number 변환 없이 원본이 이미 숫자지만, 혹시
  //    비정상 값(0,0 같은)이 섞여도 걸러지게 isFinite 로 한 번 더 본다.
  if (!Number.isFinite(r.lat) || !Number.isFinite(r.lon) || (r.lat === 0 && r.lon === 0)) {
    noCoord++;
    continue;
  }

  exitRows.push({
    id: r.id,
    lat: r.lat,
    lon: r.lon,
    stationName: r.stationName,
    stationLines: r.stationLines,
    exitRef: r.exitRef,
    exitLabel: r.exitLabel,
    feature: {
      key: "ELEVATOR_ACCESS",
      value: r.exitElevator, // true/false/null — null = 모른다(원본에 정보 없음)
      evidenceStatus: r.exitElevator === null ? "unknown" : "confirmed",
      elevatorCount: r.exitElevatorCount ?? null,
    },
  });

  if (!byStation.has(r.stationName)) {
    byStation.set(r.stationName, {
      stationName: r.stationName,
      stationNo: r.stationNo,
      stationLines: r.stationLines,
      lat: r.stationLat,
      lon: r.stationLon,
      elevatorInsideCount: r.stationElevatorInsideCount ?? 0,
      elevatorOutsideCount: r.stationElevatorOutsideCount ?? 0,
      escalatorCount: r.stationEscalatorCount ?? 0,
      wheelchairLiftCount: r.stationWheelchairLiftCount ?? 0,
      exitsWithElevator: 0,
      exitsTotal: 0,
      exitsUnknown: 0,
    });
  }
  const s = byStation.get(r.stationName);
  s.exitsTotal++;
  if (r.exitElevator === true) s.exitsWithElevator++;
  if (r.exitElevator === null) s.exitsUnknown++;
}

console.log(`출구 ${exitRows.length}개 (좌표 없음 ${noCoord}개 뺌) · 역 ${byStation.size}개`);

const stations = [...byStation.values()].map((s) => ({
  ...s,
  feature: {
    key: "ELEVATOR_ACCESS",
    // 역 안에 엘리베이터가 하나라도 있으면 true, 아무 정보가 없으면 unknown, 그 외 false
    value: s.elevatorInsideCount + s.elevatorOutsideCount > 0 || s.exitsWithElevator > 0,
    evidenceStatus: s.exitsUnknown === s.exitsTotal ? "unknown" : "confirmed",
  },
}));

fs.mkdirSync(path.dirname(OUT_EXIT), { recursive: true });
fs.writeFileSync(OUT_EXIT, exitRows.map((r) => JSON.stringify(r)).join("\n") + "\n");
fs.writeFileSync(OUT_STATION, stations.map((s) => JSON.stringify(s)).join("\n") + "\n");

const exitKnownTrue = exitRows.filter((r) => r.feature.value === true).length;
const exitUnknown = exitRows.filter((r) => r.feature.evidenceStatus === "unknown").length;
console.log(`출구 기준 — 엘리베이터 있음 ${exitKnownTrue} · 모름 ${exitUnknown} · 나머지(없음) ${exitRows.length - exitKnownTrue - exitUnknown}`);
const stationHas = stations.filter((s) => s.feature.value).length;
console.log(`역 기준 — 엘리베이터 있는 역 ${stationHas}/${stations.length}`);

console.log(`\n→ ${path.relative(ROOT, OUT_EXIT)} (${exitRows.length}줄, 좌표로 조인)`);
console.log(`→ ${path.relative(ROOT, OUT_STATION)} (${stations.length}줄, 역 이름·호선으로 조인)`);
