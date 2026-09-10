/**
 * 조사 결과의 가격대 낱말(`descriptors.priceBand`)을 3단으로 모은다.
 *
 * ── 🔴 왜 원본을 안 고치고 새 파일을 만드는가 ────────────────────────────────
 *   `research/data/results/*.json` 은 **다시 만들 수 없다.** 조사원 일흔셋이 웹을
 *   실제로 검색해서 적은 것이고, 웹은 내일 달라진다. 다시 도는 데 실측 약 1,100만
 *   토큰이 든다. 같은 답이 나온다는 보장도 없다.
 *
 *   그래서 원본은 **읽기만 한다.** 정리한 결과는 **파생 산출물**로 옆에 새로 놓는다
 *   (`data/staged/` — `truth-linked.ndjson` 과 같은 자리다). 이러면 대조표가 틀린 것을
 *   나중에 알아도 **이 스크립트만 고쳐 다시 돌리면 된다.** 원본을 덮어썼다면
 *   틀린 것을 알아챈 순간 되돌릴 방법이 없다.
 *
 *   `.gitignore` 의 `data/` 가 이 산출물을 먹는다. 커밋하려면 `git add -f` 가 필요하다.
 *
 * ── 🔴 왜 `mid-high` 를 가운데로 뭉개지 않는가 ───────────────────────────────
 *   3단(`LOW`·`MID`·`HIGH`)에 **정직하게 안 들어가는 낱말이라서** 그렇다.
 *   `mid-high` 는 "보통보다 위, 비싸다고까지는 안 함" 이다. 이걸 `MID` 에 넣으면
 *   조사원이 **일부러 구별해서 적은 것**을 우리가 지운 것이 되고, `HIGH` 에 넣으면
 *   조사원이 안 한 말을 우리가 대신 한 것이 된다. 둘 다 조용히 틀린다.
 *
 *   그래서 `MID_HIGH` 로 **따로 둔다.** 3곳뿐이라 어느 쪽에 넣어도 합계는 거의 안
 *   움직이는데, 바로 그래서 뭉개기 쉽고 **뭉개면 다음 사람이 그 3곳이 있었다는 것을
 *   영영 모른다.** 쓰는 쪽에서 3단만 필요하면 그때 골라 접으면 된다 — 접는 것은
 *   나중에도 할 수 있지만, 펴는 것은 못 한다.
 *
 * ── 🔴 왜 대조표를 코드 안에 하나하나 적는가 ─────────────────────────────────
 *   `research/README.md` 가 조사원에게 준 값은 `cheap` · `mid` · `high` · `null`
 *   **셋뿐이었다.** 나머지 여덟은 지시문이 목록을 안 줘서 새어 나온 것이다
 *   (이유 칸에는 19가지 목록을 줘서 막았는데 가격대에는 안 줬다 — `HANDOVER.md` 0절).
 *   그래서 **자동으로 짐작하지 않는다.** 낱말 열하나를 손으로 적고, 왜 그 칸인지를
 *   옆에 적는다. 짐작하는 코드는 새 낱말이 들어와도 그럴듯하게 처리해 버린다.
 *
 *   🔴 **대조표에 없는 낱말이 나오면 종료 코드 1 로 죽는다.** 조용히 버리지 않는다.
 *   버리면 조사원이 내일 새 낱말을 쓸 때 **아무도 모르는 채로 개수만 줄어든다.**
 *   같은 이유로 **대소문자를 접거나 공백을 떼지 않는다** — `"Cheap"` 이나 `"cheap "`
 *   가 들어오면 그것도 새 낱말이고, 조용히 흡수하는 대신 멈춰서 사람에게 보여준다.
 *
 * 입력: research/data/results/*.json   (🔴 읽기만 한다)
 * 산출: data/staged/place-priceband.ndjson   (한 줄에 {placeId, raw, band})
 * 사용: node process/priceband-normalize.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const RESULTS = path.join(ROOT, "research/data/results");
const OUT = path.join(ROOT, "data/staged/place-priceband.ndjson");

// ── 대조표 ───────────────────────────────────────────────────────────────────
// 🔴 낱말 열하나를 하나하나 적는다. 규칙으로 줄이지 않는다 —
//    규칙은 아직 안 본 낱말까지 처리해 버리고, 그게 이 파일이 막으려는 것이다.
//    (개수는 2026-09-10 실측. 여기 주석의 숫자는 참고이고, 판정은 언제나 코드가 센다)
const BAND = {
  // ── LOW — 싸다고 말한 것 ──
  cheap: "LOW", //       341  🟢 조사원에게 준 셋 중 하나 (README 의 표준 낱말)
  affordable: "LOW", //   20  "부담 없다" — cheap 과 같은 방향. 새어 나온 낱말

  // ── MID — 보통이라고 말한 것 ──
  mid: "MID", //         140  🟢 조사원에게 준 셋 중 하나 (README 의 표준 낱말)
  moderate: "MID", //    278  🔴 mid 와 **같은 것이다.** 안 합치면 1등이 바뀐다
  medium: "MID", //       28  "중간" — moderate 의 다른 표기
  regular: "MID", //      12  "보통" — 특별할 것 없는 값이라는 뜻
  normal: "MID", //        5  "보통" — regular 와 같다

  // ── HIGH — 비싸다고 말한 것 ──
  high: "HIGH", //         9  🟢 조사원에게 준 셋 중 하나 (README 의 표준 낱말)
  expensive: "HIGH", //  128  "비싸다" — high 와 같은 방향. 새어 나온 낱말
  premium: "HIGH", //      3  "고급" — 값이 높다는 말로 쓰였다

  // ── MID_HIGH — 3단에 안 들어가는 것 ──
  // 🔴 뭉개지 않는다. 위 머리말 참고
  "mid-high": "MID_HIGH", // 3
};

// 산출물에 쓰는 칸 이름. 3단으로 접을 때 MID_HIGH 를 어디에 넣을지는
// **이 스크립트가 정하지 않는다.** 쓰는 쪽이 정한다
const BANDS = ["LOW", "MID", "HIGH", "MID_HIGH"];

// ── 읽는다 ───────────────────────────────────────────────────────────────────
if (!fs.existsSync(RESULTS)) {
  console.error(`🔴 조사 결과 폴더가 없습니다: ${path.relative(ROOT, RESULTS)}`);
  process.exit(1);
}
const files = fs.readdirSync(RESULTS).filter((f) => f.endsWith(".json")).sort();
if (files.length === 0) {
  console.error(`🔴 조사 결과가 한 개도 없습니다: ${path.relative(ROOT, RESULTS)}`);
  process.exit(1);
}

const rows = [];
const rawCount = new Map(); // 낱말별 개수 (검산용)
const unknown = new Map(); // 🔴 대조표에 없는 낱말 → 어느 파일에서 나왔나
const seen = new Map(); // placeId 중복 잡기 → {file, raw}
const broken = []; // 파일이 깨졌거나 id 가 없는 것
const renamed = []; // 파일 이름과 id 가 다른 것 (아래 주석 참고 — 멈추지는 않는다)
let noValue = 0; // priceBand 가 null 이거나 칸 자체가 없는 것

for (const f of files) {
  const full = path.join(RESULTS, f);
  let j;
  try {
    j = JSON.parse(fs.readFileSync(full, "utf8"));
  } catch (e) {
    broken.push(`${f} — JSON 이 깨졌습니다: ${e.message}`);
    continue;
  }

  // placeId 는 파일 안의 id 를 쓴다. 다른 산출물(truth-linked.ndjson)이 쓰는 것과 같은 칸이다
  const placeId = j?.id;
  if (typeof placeId !== "string" || placeId === "") {
    broken.push(`${f} — id 칸이 없거나 문자열이 아닙니다`);
    continue;
  }
  // 🔴 README 는 "파일 이름과 id 가 같아야 한다" 고 못박았는데, 실제로는 어긋난 것이 있다.
  //    2026-09-10 실측 — `MA0101202406A0512484_2.json` 한 개. 조사원 haiku-58 이 **서로 다른
  //    가게 둘**("태리태리식당"·"타케리아 뺀데호")을 같은 id 로 적고 뒤엣것을 `_2` 로 저장했다.
  //    HANDOVER 0절이 말하는 *"대기열에 없는 결과 파일 1개"* 가 이것이다.
  //
  //    이건 **가격대 문제가 아니라 조사 자체의 문제**라 여기서 고칠 수 없다 (원본은 안 고친다).
  //    그래서 **멈추지 않고 끝에 소리 내어 알린다** — 이 둘은 priceBand 가 둘 다 null 이라
  //    산출물에 한 줄도 안 들어가고, 개수에도 영향이 없기 때문이다.
  //    🔴 값이 있는 채로 겹치면 그때는 아래 중복 검사가 멈춘다. 그건 우리가 못 고르는 문제다.
  if (path.basename(f, ".json") !== placeId) renamed.push(`${f} (id=${placeId})`);

  const d = j?.descriptors;
  // descriptors 가 통째로 없는 것은 "못 찾은 가게" 다. 정상이다 (README: 그때 `descriptors: {}`)
  const raw = d && typeof d === "object" && !Array.isArray(d) ? d.priceBand : undefined;

  // 🔴 "모른다" 는 정상적인 답이다. 값이 없는 것은 산출물에 넣지 않는다 (버리는 게 아니라 답이 없는 것)
  if (raw === undefined || raw === null) {
    noValue++;
    continue;
  }
  if (typeof raw !== "string") {
    broken.push(`${f} — priceBand 가 문자열이 아닙니다 (${typeof raw}: ${JSON.stringify(raw)})`);
    continue;
  }

  rawCount.set(raw, (rawCount.get(raw) ?? 0) + 1);

  // 🔴 대조표에 없으면 조용히 버리지 않는다. 모아 두었다가 아래에서 종료 코드 1 로 죽는다
  const band = Object.prototype.hasOwnProperty.call(BAND, raw) ? BAND[raw] : null;
  if (band === null) {
    if (!unknown.has(raw)) unknown.set(raw, []);
    unknown.get(raw).push(f);
    continue;
  }

  // 🔴 같은 placeId 에 **값이 둘** 이면 우리가 고를 수 없다. 조용히 하나를 버리면
  //    개수는 맞는데 어느 쪽이 남았는지 아무도 모른다. 멈춘다
  if (seen.has(placeId)) {
    const p = seen.get(placeId);
    broken.push(
      `${placeId} — 같은 placeId 에 가격대가 두 번 (${p.file}=${JSON.stringify(p.raw)} · ${f}=${JSON.stringify(raw)})`,
    );
    continue;
  }
  seen.set(placeId, { file: f, raw });
  rows.push({ placeId, raw, band });
}

// ── 🔴 멈춰야 하는 것들 ──────────────────────────────────────────────────────
if (unknown.size > 0) {
  console.error(`\n🔴 대조표에 없는 가격대 낱말 ${unknown.size}종을 만났습니다. 아무것도 쓰지 않고 멈춥니다.\n`);
  for (const [word, fl] of [...unknown.entries()].sort((a, b) => b[1].length - a[1].length)) {
    console.error(`   ${JSON.stringify(word)} — ${fl.length}곳 (예: ${fl.slice(0, 3).join(" · ")})`);
  }
  console.error(
    `\n   조용히 버리면 개수만 줄고 아무도 못 알아챕니다. 이 파일 위쪽 BAND 표에\n` +
      `   낱말을 **뜻과 함께** 적어 넣은 뒤 다시 돌리십시오.\n` +
      `   3단에 정직하게 안 들어가는 낱말이면 mid-high 처럼 **따로 두십시오.**\n`,
  );
  process.exit(1);
}
if (broken.length > 0) {
  console.error(`\n🔴 읽을 수 없는 결과 파일 ${broken.length}개. 아무것도 쓰지 않고 멈춥니다.\n`);
  for (const b of broken.slice(0, 20)) console.error(`   ${b}`);
  if (broken.length > 20) console.error(`   … 그리고 ${broken.length - 20}개 더`);
  console.error("");
  process.exit(1);
}

// ── 검산 — 개수가 맞는지 코드가 직접 센다 ────────────────────────────────────
// 🔴 개수를 문서에 적지 않는다. 여기서 세고, 안 맞으면 종료 코드 1 로 죽는다
const withValue = [...rawCount.values()].reduce((a, b) => a + b, 0);
const byBand = new Map(BANDS.map((b) => [b, 0]));
for (const r of rows) byBand.set(r.band, byBand.get(r.band) + 1);
const folded = [...byBand.values()].reduce((a, b) => a + b, 0);

const fails = [];
if (rows.length !== withValue) fails.push(`산출 줄 수 ${rows.length} ≠ 가격대 값이 있는 곳 ${withValue}`);
if (folded !== withValue) fails.push(`3단으로 접은 합 ${folded} ≠ 가격대 값이 있는 곳 ${withValue}`);
if (noValue + withValue !== files.length) {
  fails.push(`값 있음 ${withValue} + 값 없음 ${noValue} = ${noValue + withValue} ≠ 결과 파일 ${files.length}개`);
}
if (fails.length > 0) {
  console.error(`\n🔴 검산이 안 맞습니다. 아무것도 쓰지 않고 멈춥니다.\n`);
  for (const f of fails) console.error(`   ${f}`);
  console.error("");
  process.exit(1);
}

// ── 쓴다 ─────────────────────────────────────────────────────────────────────
// placeId 로 정렬해 둔다. 돌릴 때마다 같은 파일이 나와야 diff 가 뜻을 갖는다
rows.sort((a, b) => (a.placeId < b.placeId ? -1 : a.placeId > b.placeId ? 1 : 0));
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, rows.map((r) => JSON.stringify(r)).join("\n") + "\n");

// ── 알린다 ───────────────────────────────────────────────────────────────────
console.log(`\n결과 파일 ${files.length}개 중 가격대 값이 있는 곳 ${withValue} · 값이 없는 곳 ${noValue}`);
console.log(`낱말 ${rawCount.size}종 → 칸 ${BANDS.filter((b) => byBand.get(b) > 0).length}개\n`);

const label = { LOW: "싼 편", MID: "보통", HIGH: "비싼 편", MID_HIGH: "보통보다 위 (3단에 안 넣는다)" };
for (const b of BANDS) {
  const n = byBand.get(b);
  const words = [...rawCount.entries()]
    .filter(([w]) => BAND[w] === b)
    .sort((x, y) => y[1] - x[1])
    .map(([w, c]) => `${w} ${c}`)
    .join(" · ");
  console.log(`  ${b.padEnd(8)} ${String(n).padStart(4)}곳   ${label[b]}`);
  console.log(`  ${" ".repeat(8)} ${" ".repeat(4)}     ← ${words}`);
}
console.log(`\n  합계 ${folded}곳 — 가격대 값이 있는 곳과 같습니다 ✅`);
console.log(`→ ${path.relative(ROOT, OUT).replace(/\\/g, "/")}`);

// 🔴 멈출 일은 아니지만 묻어 두지도 않는다. 다음 사람이 이걸 보고 판단한다
if (renamed.length > 0) {
  console.log(`\n⚠ 파일 이름과 id 가 다른 결과 파일 ${renamed.length}개 — 조사 쪽 문제라 여기서 안 고칩니다`);
  for (const r of renamed) console.log(`   ${r}`);
  console.log(`   (가격대 값이 없어 이 산출물에는 영향이 없습니다. research/HANDOVER.md 0절 참고)`);
}
