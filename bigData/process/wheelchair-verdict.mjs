// 구간별 휠체어 통과 가능 판정기.
//
// 이 파일은 규칙을 **하나도 갖고 있지 않다.** 규칙도 임계값도 전부
// `bigData/config/ontology.jsonld`(기계가 읽는 온톨로지 판) 에서 읽어 온다.
// 여기 숫자를 박으면 온톨로지가 바뀌어도 판정이 안 바뀌고, 그때 둘 중
// 어느 쪽이 맞는지 아무도 모르게 된다.
//
// 판정 값은 셋뿐이다: 가능 · 불가 · 미상.
//
// 🔴 **미상을 가능으로 기본값 주지 않는다.** 보도가 있다고 말했는데 없으면
// 사용자가 차도를 걷게 된다. 모르면 모른다고 말한다.
//
// 쓰는 법:
//   node bigData/process/wheelchair-verdict.mjs            # 전체 구간 판정 분포를 찍는다
//   import { loadOntology, buildRuleset, judge } from './wheelchair-verdict.mjs'

import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// ── 좌표 ──────────────────────────────────────────────────────────────────
// 이 파일은 bigData/process/ 에 있다. 두 단계 위가 저장소 루트다.
export const REPO_ROOT = path.resolve(fileURLToPath(new URL('../..', import.meta.url)));
export const ONTOLOGY_PATH = path.join(REPO_ROOT, 'bigData', 'config', 'ontology.jsonld');
export const SEGMENT_SLOPE_PATH = path.join(REPO_ROOT, 'bigData', 'data', 'staged', 'segment-slope.ndjson');

// 온톨로지가 아직 bigData/dev 에 없다. 지금은 이 브랜치에만 있다 —
// 병합되면 위의 ONTOLOGY_PATH 가 바로 잡히고 이 되돌림 경로는 저절로 안 쓰인다.
const ONTOLOGY_FALLBACK_REF = 'origin/feat/bigData/S15P21E201-23-travel-ontology-base';
const ONTOLOGY_REPO_RELATIVE = 'bigData/config/ontology.jsonld';

// 판정 값. 이 셋 밖으로 나가면 안 된다.
export const VERDICT = Object.freeze({ OK: '가능', BLOCKED: '불가', UNKNOWN: '미상' });
export const VERDICTS = Object.freeze([VERDICT.OK, VERDICT.BLOCKED, VERDICT.UNKNOWN]);

// 온톨로지가 "지금 실제로 돌릴 수 있다" 고 표시한 규칙만 쓴다.
// 나머지는 선언은 돼 있지만 입력이나 임계가 없어서 멈춰 있다 (docs/ONTOLOGY.md 4절).
const EVIDENCE_RUNNABLE = '적용가능';

// ── 온톨로지 읽기 ─────────────────────────────────────────────────────────

function readOntologyText() {
  if (fs.existsSync(ONTOLOGY_PATH)) {
    return { text: fs.readFileSync(ONTOLOGY_PATH, 'utf8'), from: ONTOLOGY_PATH, viaFallback: false };
  }
  // 온톨로지가 아직 이 브랜치의 작업 트리에 없다. 형제 브랜치에서 읽어 본다.
  try {
    const text = execFileSync('git', ['show', `${ONTOLOGY_FALLBACK_REF}:${ONTOLOGY_REPO_RELATIVE}`], {
      cwd: REPO_ROOT, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'],
    });
    process.stderr.write(
      `⚠️  ${ONTOLOGY_REPO_RELATIVE} 가 작업 트리에 없어 ${ONTOLOGY_FALLBACK_REF} 에서 읽었습니다.\n` +
      `   그 브랜치가 bigData/dev 로 병합되면 이 경고는 저절로 사라집니다.\n`);
    return { text, from: `${ONTOLOGY_FALLBACK_REF}:${ONTOLOGY_REPO_RELATIVE}`, viaFallback: true };
  } catch {
    throw new Error(
      `온톨로지를 찾을 수 없습니다.\n` +
      `  찾은 곳: ${ONTOLOGY_PATH}\n` +
      `  되돌림:  git show ${ONTOLOGY_FALLBACK_REF}:${ONTOLOGY_REPO_RELATIVE}  (도 실패)\n\n` +
      `이 판정기는 규칙과 임계값을 전부 온톨로지에서 읽습니다. 파일 없이는 돌 수 없고,\n` +
      `숫자를 대신 지어내지 않습니다. 아래 한 줄로 가져올 수 있습니다:\n\n` +
      `  git fetch origin && git checkout ${ONTOLOGY_FALLBACK_REF} -- ${ONTOLOGY_REPO_RELATIVE}\n`);
  }
}

/** 온톨로지를 읽어 @id 로 찾을 수 있게 색인한다. */
export function loadOntology() {
  const { text, from, viaFallback } = readOntologyText();
  const doc = JSON.parse(text);
  const graph = doc['@graph'];
  if (!Array.isArray(graph)) throw new Error(`온톨로지에 @graph 배열이 없습니다: ${from}`);
  const byId = new Map();
  for (const node of graph) if (node && node['@id']) byId.set(node['@id'], node);
  return { doc, graph, byId, from, viaFallback };
}

/** 이름 있는 임계 파라미터의 값을 꺼낸다. 값이 없으면 null — 지어내지 않는다. */
function parameterValue(onto, id) {
  const node = onto.byId.get(id);
  if (!node) return { ok: false, why: `온톨로지에 ${id} 가 없습니다` };
  if (Object.hasOwn(node, 'value')) return { ok: true, value: node.value, node };
  if (Array.isArray(node.codeList)) return { ok: true, value: node.codeList, node };
  return { ok: false, why: `${id} 에 값이 없습니다 (valueStatus: ${node.valueStatus ?? '없음'})`, node };
}

// ── 구간 사실(fact) 만들기 ────────────────────────────────────────────────
//
// 온톨로지의 속성 이름(bm:…) 에 실제 데이터를 붙이는 곳. 온톨로지의
// sourceField 가 어느 파일 어느 필드인지 말해 주고, 여기가 그걸 따른다.

/**
 * segment-slope.ndjson 의 한 줄을 온톨로지 속성으로 옮긴다.
 * 값을 모르면 undefined 로 둔다 — 0 이나 false 로 채우지 않는다.
 */
export function segmentFacts(seg) {
  // bm:hasSteps 는 파생 속성이다. 온톨로지 note:
  // "networkTopic 이 stairs 이거나, osmHighwayClass 가 steps 이거나, stepCountTotal 이 0 보다 크면 참이다."
  const hasSteps =
    seg.topic === 'stairs' ||
    seg.highway === 'steps' ||
    (typeof seg.stepCount === 'number' && seg.stepCount > 0);

  return {
    'bm:osmHighwayClass': seg.highway ?? undefined,
    'bm:hasSteps': hasSteps,
    // 🔴 대표 경사는 p90 이다. maxSlope 는 쓰지 않는다 — DEM 잡음 표본 하나가
    //    멀쩡한 길을 통행 불가로 만든다 (실측: 134개가 100% 를 넘고 최악이 2,368%).
    //    ascent(누적상승) 도 쓰지 않는다 — 평지에서도 100m 당 1.1m 의 가짜 상승이 쌓인다.
    'bm:slopeP90': typeof seg.p90Slope === 'number' ? seg.p90Slope : undefined,
    // bm:widthM 은 아직 아무도 수집하지 않았다. 그래서 폭 규칙이 못 돈다.
    'bm:widthM': undefined,
  };
}

// ── 규칙 고르기 ───────────────────────────────────────────────────────────

/**
 * 주어진 프로파일에 대해 **지금 실제로 돌릴 수 있는** 구간 규칙만 고른다.
 * appliesTo 가 bm:UserProfile 인 규칙은 프로파일과 무관하게 전부에 걸린다.
 */
export function buildRuleset(onto, profile = 'bm:Wheelchair', onClass = 'bm:Segment') {
  const runnable = [];
  const parked = [];
  for (const node of onto.graph) {
    if (node?.broader !== 'bm:FeasibilityRule') continue;
    if (node.onClass !== onClass) continue;
    if (node.appliesTo !== profile && node.appliesTo !== 'bm:UserProfile') continue;
    (node.evidenceStatus === EVIDENCE_RUNNABLE ? runnable : parked).push(node);
  }
  return { runnable, parked, profile, onClass };
}

// ── 조건 평가 ─────────────────────────────────────────────────────────────

const OPERATORS = {
  eq: (a, b) => a === b,
  lt: (a, b) => a < b,
  gte: (a, b) => a >= b,
  in: (a, b) => Array.isArray(b) && b.includes(a),
};

/**
 * 조건 하나를 잰다.
 * 반환: 'fired' | 'not-fired' | 'undecidable'
 * 입력이 없으면 거짓이 아니라 **판정 불가**다. 이 구분이 미상을 만든다.
 */
function evaluateCondition(cond, facts, onto) {
  const op = OPERATORS[cond.operator];
  if (!op) return { state: 'undecidable', why: `모르는 연산자 ${cond.operator}` };

  const left = facts[cond.property];
  if (left === undefined || left === null) {
    return { state: 'undecidable', why: `${cond.property} 값이 없습니다`, missing: cond.property };
  }

  let right, rightLabel;
  if (Object.hasOwn(cond, 'constant')) {
    right = cond.constant; rightLabel = String(cond.constant);
  } else if (cond.parameter) {
    const p = parameterValue(onto, cond.parameter);
    if (!p.ok) return { state: 'undecidable', why: p.why, missing: cond.parameter };
    right = p.value; rightLabel = cond.parameter;
  } else {
    return { state: 'undecidable', why: '조건에 비교 대상이 없습니다' };
  }

  return { state: op(left, right) ? 'fired' : 'not-fired', left, right, rightLabel };
}

// ── 사람이 읽는 이유 ──────────────────────────────────────────────────────

const pct = (ratio) => `${(ratio * 100).toFixed(1)}%`;
const labelOf = (node) => node?.prefLabel?.ko ?? node?.['@id'] ?? '이름 없는 규칙';

/**
 * 왜 이 판정인지 한 문장으로 만든다. 데모의 핵심은 색이 아니라
 * **이유를 말할 수 있다는 것**이다.
 */
function reasonFor(rule, results, seg, facts) {
  switch (rule['@id']) {
    case 'bm:RuleWheelchairSlope': {
      const r = results.find((x) => x.left !== undefined && typeof x.right === 'number');
      if (r) return `대표 경사(p90) ${pct(r.left)} — 휠체어 상한 ${pct(r.right)} 이상이라 통과 불가`;
      break;
    }
    case 'bm:RuleWheelchairSteps': {
      const n = seg?.stepCount;
      return typeof n === 'number' && n > 0
        ? `계단 ${n}단 — 휠체어는 한 단만 있어도 통과 불가`
        : '계단 구간 — 휠체어는 한 단만 있어도 통과 불가';
    }
    case 'bm:RuleMotorFootBan':
      return `자동차 전용도로(${facts['bm:osmHighwayClass']}) — 보행 자체가 금지된 등급`;
  }
  return `${labelOf(rule)} — 통과 불가`;
}

/** 무엇이 없어서 확정을 못 했는지 말한다. "모른다" 를 구체적으로 말하는 것이 요점이다. */
function unknownReason(ruleset, undecided) {
  const missing = [...new Set(undecided.map((u) => u.missing).filter(Boolean))];
  const widthParked = ruleset.parked.some((r) => r['@id'] === 'bm:RuleWheelchairWidth');
  if (widthParked || missing.includes('bm:widthM')) {
    return '통과 가능을 확인해 줄 규칙이 없다 — 유효폭(bm:widthM)이 수집되지 않아 폭 규칙이 멈춰 있다';
  }
  if (missing.length) return `판정에 필요한 값이 없다: ${missing.join(', ')}`;
  return '불가 조건에는 걸리지 않았지만, 통과 가능을 확인해 줄 규칙이 없다';
}

// ── 판정 ──────────────────────────────────────────────────────────────────

/**
 * 구간 하나를 판정한다.
 * @returns {{verdict:string, reason:string, rule:string|null}}
 */
export function judge(seg, ruleset, onto) {
  const facts = segmentFacts(seg);
  const undecided = [];

  for (const rule of ruleset.runnable) {
    const conds = Array.isArray(rule.condition) ? rule.condition : [];
    if (!conds.length) continue;

    const results = conds.map((c) => evaluateCondition(c, facts, onto));
    if (results.some((r) => r.state === 'undecidable')) {
      undecided.push(...results.filter((r) => r.state === 'undecidable'));
      continue;
    }
    // 조건이 여럿이면 전부 참일 때 규칙이 걸린다 (AND).
    if (results.every((r) => r.state === 'fired')) {
      // 온톨로지의 모든 규칙은 verdict: infeasible 이다. 그대로 옮긴다.
      if (rule.verdict !== 'infeasible') {
        throw new Error(`모르는 규칙 결과 ${rule.verdict} (${rule['@id']}) — 판정을 지어내지 않습니다`);
      }
      return { verdict: VERDICT.BLOCKED, reason: reasonFor(rule, results, seg, facts), rule: rule['@id'] };
    }
  }

  // 🔴 여기가 중요하다. 불가 조건에 안 걸렸다고 "가능" 이 아니다.
  // 온톨로지에 통과 **가능**을 선언하는 규칙이 하나도 없다 (전부 infeasible).
  // 그래서 확정 가능한 것은 "불가" 뿐이고 나머지는 전부 미상이다.
  return { verdict: VERDICT.UNKNOWN, reason: unknownReason(ruleset, undecided), rule: null };
}

// ── 구간 표 읽기 ──────────────────────────────────────────────────────────

/** segment-slope.ndjson 을 way id 로 색인해 읽는다. */
export async function loadSegments(file = SEGMENT_SLOPE_PATH) {
  if (!fs.existsSync(file)) {
    throw new Error(
      `구간 표가 없습니다: ${file}\n` +
      `bigData/data/ 는 커밋되지 않습니다. collect/ 와 process/slope.mjs 가 다시 만듭니다.`);
  }
  const byId = new Map();
  const rl = readline.createInterface({ input: fs.createReadStream(file), crlfDelay: Infinity });
  for await (const line of rl) {
    if (!line.trim()) continue;
    const seg = JSON.parse(line);
    byId.set(seg.id, seg);
  }
  return byId;
}

// ── CLI ───────────────────────────────────────────────────────────────────

async function main() {
  const onto = loadOntology();
  const ruleset = buildRuleset(onto, 'bm:Wheelchair');

  console.log(`온톨로지: ${onto.from}`);
  console.log(`\n지금 돌릴 수 있는 규칙 ${ruleset.runnable.length}개:`);
  for (const r of ruleset.runnable) console.log(`  ✅ ${labelOf(r)}  (${r['@id']})`);
  console.log(`\n선언은 됐지만 멈춰 있는 규칙 ${ruleset.parked.length}개:`);
  for (const r of ruleset.parked) console.log(`  ⏸️  ${labelOf(r)}  — ${r.evidenceStatus}`);

  const segments = await loadSegments();
  const tally = Object.fromEntries(VERDICTS.map((v) => [v, { count: 0, metres: 0 }]));
  const byRule = new Map();
  const samples = [];

  for (const seg of segments.values()) {
    const { verdict, reason, rule } = judge(seg, ruleset, onto);
    tally[verdict].count += 1;
    tally[verdict].metres += seg.length ?? 0;
    if (rule) byRule.set(rule, (byRule.get(rule) ?? 0) + 1);
    if (verdict === VERDICT.BLOCKED && samples.length < 5) samples.push({ id: seg.id, name: seg.name, reason });
  }

  const total = segments.size;
  console.log(`\n구간 ${total.toLocaleString()}개 판정:`);
  for (const v of VERDICTS) {
    const t = tally[v];
    console.log(`  ${v}  ${String(t.count).padStart(6)}개 (${(t.count / total * 100).toFixed(1)}%)  ${Math.round(t.metres).toLocaleString()} m`);
  }
  console.log('\n불가를 만든 규칙:');
  for (const [id, n] of [...byRule].sort((a, b) => b[1] - a[1])) console.log(`  ${n.toString().padStart(6)}  ${id}`);
  console.log('\n불가 예시:');
  for (const s of samples) console.log(`  way ${s.id} ${s.name ?? '(이름 없음)'} — ${s.reason}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  main().catch((err) => { console.error(`\n${err.message}`); process.exit(1); });
}
