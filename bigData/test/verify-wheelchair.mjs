// 휠체어 판정 데모 검사. **판정은 종료 코드가 한다 — 0 성공 / 1 실패.**
//
//   node bigData/test/verify-wheelchair.mjs
//   node bigData/test/verify-wheelchair.mjs --self-test   # 검사기 자신이 실패하는지 본다
//
// 🔴 검사기가 아무것도 못 잡으면 없는 것보다 나쁘다 — 통과했다고 믿게 만들기 때문이다.
// 그래서 `--self-test` 가 일부러 틀린 판본을 만들어 넣고, **각 검사가 실제로
// 실패하는지**를 확인한다. 잡아내지 못하는 검사가 하나라도 있으면 그것도 실패다.

import fs from 'node:fs';
import path from 'node:path';
import {
  REPO_ROOT, VERDICT, VERDICTS,
  loadOntology, buildRuleset, judge, loadSegments,
} from '../process/wheelchair-verdict.mjs';

const GEOJSON_PATH = path.join(REPO_ROOT, 'bigData', 'data', 'staged', 'wheelchair-demo.geojson');
const VERDICT_SRC = path.join(REPO_ROOT, 'bigData', 'process', 'wheelchair-verdict.mjs');
const ROUTE_SRC = path.join(REPO_ROOT, 'bigData', 'process', 'wheelchair-route.mjs');
const SLOPE_PARAM = 'bm:maxSlopeWheelchair';

class Fail extends Error {}
const fail = (msg) => { throw new Fail(msg); };

// ── 검사들 ────────────────────────────────────────────────────────────────
// 전부 ctx 를 받는다. --self-test 가 망가뜨린 ctx 를 넣어 볼 수 있어야 하기 때문이다.

const checks = [
  {
    id: 'steps-never-ok',
    what: '계단은 절대 "가능" 으로 나오지 않는다',
    run({ segments, judgeFn }) {
      let stairs = 0, blocked = 0;
      const bad = [];
      for (const seg of segments) {
        const isStairs = seg.topic === 'stairs' || seg.highway === 'steps' ||
                         (typeof seg.stepCount === 'number' && seg.stepCount > 0);
        if (!isStairs) continue;
        stairs++;
        const v = judgeFn(seg).verdict;
        if (v === VERDICT.OK) bad.push(seg.id);
        else if (v === VERDICT.BLOCKED) blocked++;
      }
      if (!stairs) fail('계단 구간이 한 개도 없다 — 검사할 것이 없으면 통과가 아니다');
      if (bad.length) fail(`계단이 "가능" 으로 나왔다: way ${bad.slice(0, 5).join(', ')} (총 ${bad.length}개)`);
      if (blocked !== stairs) fail(`계단 ${stairs}개 중 ${stairs - blocked}개가 "불가" 가 아니다`);
      return `계단 ${stairs}개 전부 불가`;
    },
  },

  {
    id: 'threshold-from-ontology',
    what: '경사 상한값을 온톨로지에서 읽는다 (코드에 박혀 있지 않다)',
    run({ onto, judgeFn, rebuild }) {
      const param = onto.byId.get(SLOPE_PARAM);
      if (!param || typeof param.value !== 'number') fail(`온톨로지에 ${SLOPE_PARAM} 의 값이 없다`);
      const T = param.value;

      const steep = { id: -1, topic: 'road', highway: 'residential', name: '검사용', p90Slope: T * 1.5, stepCount: null };
      const flat = { id: -2, topic: 'road', highway: 'residential', name: '검사용', p90Slope: T * 0.5, stepCount: null };

      if (judgeFn(steep).verdict !== VERDICT.BLOCKED) fail(`상한의 1.5배 경사가 "불가" 로 나오지 않았다`);
      if (judgeFn(flat).verdict === VERDICT.BLOCKED) fail(`상한의 0.5배 경사가 "불가" 로 나왔다`);

      // 🔴 여기가 진짜 검사다. 온톨로지의 값만 바꿨을 때 판정이 따라 바뀌어야 한다.
      // 안 바뀌면 그 숫자는 온톨로지가 아니라 코드 어딘가에서 온 것이다.
      const original = param.value;
      try {
        param.value = T * 10;
        const after = rebuild().judgeFn(steep).verdict;
        if (after === VERDICT.BLOCKED) {
          fail('온톨로지의 상한을 10배로 올렸는데도 판정이 "불가" 그대로다 — ' +
               '임계값이 코드에 박혀 있다');
        }
      } finally { param.value = original; }

      return `상한 ${(T * 100).toFixed(2)}% 를 온톨로지에서 읽고, 값을 바꾸면 판정도 바뀐다`;
    },
  },

  {
    id: 'no-threshold-literal-in-source',
    what: '판정 코드 안에 임계값 숫자가 적혀 있지 않다',
    run({ onto, sources }) {
      const T = onto.byId.get(SLOPE_PARAM)?.value;
      if (typeof T !== 'number') fail(`온톨로지에 ${SLOPE_PARAM} 의 값이 없다`);
      // 금지할 문자열도 온톨로지에서 만든다 — 검사기가 숫자를 갖지 않기 위해서다.
      const forbidden = [String(T), (T * 100).toFixed(2), (T * 100).toFixed(1), '1/12', '1:12'];
      const hits = [];
      for (const [name, text] of Object.entries(sources)) {
        for (const needle of forbidden) {
          const re = new RegExp(needle.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
          if (re.test(text)) hits.push(`${name} 에 "${needle}"`);
        }
      }
      if (hits.length) fail(`임계값이 코드에 적혀 있다: ${hits.join(' · ')}`);
      return `금지 문자열 ${forbidden.length}개 모두 없음`;
    },
  },

  {
    id: 'geojson-lines-have-verdict-and-reason',
    what: 'geojson 의 모든 선이 verdict 와 reason 을 갖는다',
    run({ geojson }) {
      const lines = geojson.features.filter((f) => f.geometry?.type === 'LineString');
      if (!lines.length) fail('geojson 에 LineString 이 하나도 없다');
      const bad = [];
      for (const [i, f] of lines.entries()) {
        const p = f.properties ?? {};
        if (!p.verdict) bad.push(`#${i} verdict 없음`);
        else if (typeof p.reason !== 'string' || !p.reason.trim()) bad.push(`#${i} (${p.verdict}) reason 없음`);
      }
      if (bad.length) fail(`${bad.length}개: ${bad.slice(0, 3).join(', ')}`);
      return `선 ${lines.length}개 전부 verdict·reason 있음`;
    },
  },

  {
    id: 'verdict-enum',
    what: 'verdict 값이 가능·불가·미상 셋 밖으로 나가지 않는다',
    run({ geojson }) {
      const seen = new Set();
      for (const f of geojson.features) {
        if (f.geometry?.type !== 'LineString') continue;
        seen.add(f.properties?.verdict);
      }
      const stray = [...seen].filter((v) => !VERDICTS.includes(v));
      if (stray.length) fail(`셋 밖의 값: ${stray.map((v) => JSON.stringify(v)).join(', ')}`);
      return `쓰인 값: ${[...seen].join(' · ')}`;
    },
  },

  {
    id: 'geojson-lines-match-engine',
    what: 'geojson 의 판정이 지금 판정기가 내는 것과 같다',
    run({ geojson, segIndex, judgeFn }) {
      const mismatched = [];
      let compared = 0;
      for (const f of geojson.features) {
        if (f.geometry?.type !== 'LineString') continue;
        const p = f.properties;
        const seg = segIndex.get(p.wayId);
        if (!seg) continue;              // staged 표에 없는 way 는 태그만으로 판정했다
        compared++;
        const now = judgeFn(seg).verdict;
        if (now !== p.verdict) mismatched.push(`way ${p.wayId}: 파일 ${p.verdict} ≠ 지금 ${now}`);
      }
      if (!compared) fail('대조할 수 있는 선이 없다');
      if (mismatched.length) {
        fail(`geojson 이 낡았다 (${mismatched.length}개): ${mismatched.slice(0, 3).join(' · ')} — ` +
             'node bigData/process/wheelchair-route.mjs 를 다시 돌리세요');
      }
      return `${compared}개 대조, 전부 일치`;
    },
  },

  {
    id: 'unknown-is-not-silently-ok',
    what: '미상이 가능으로 둔갑하지 않는다',
    run({ onto, ruleset, judgeFn }) {
      // 유효폭 규칙이 멈춰 있는 동안에는, 불가에 안 걸린 구간이 "가능" 이 될 수 없다.
      const widthRule = ruleset.parked.find((r) => r['@id'] === 'bm:RuleWheelchairWidth');
      const anyFeasibleRule = onto.graph.some(
        (n) => n.broader === 'bm:FeasibilityRule' && n.verdict && n.verdict !== 'infeasible');
      if (!widthRule || anyFeasibleRule) return '온톨로지가 바뀌었다 — 이 검사는 건너뛴다';

      const plain = { id: -3, topic: 'walk', highway: 'footway', name: '검사용', p90Slope: 0.01, stepCount: null };
      const v = judgeFn(plain).verdict;
      if (v === VERDICT.OK) {
        fail('평범한 보도가 "가능" 으로 나왔다 — 통과 가능을 확인해 줄 규칙이 아직 없는데도 ' +
             '가능이 나오면, 없는 보도를 있다고 말하게 된다');
      }
      if (v !== VERDICT.UNKNOWN) fail(`평범한 보도가 "${v}" 로 나왔다 (미상이어야 한다)`);
      return '불가 아닌 것은 미상으로 남는다';
    },
  },
];

// ── 실행 틀 ───────────────────────────────────────────────────────────────

function buildContext() {
  if (!fs.existsSync(GEOJSON_PATH)) {
    fail(`경로 파일이 없습니다: ${GEOJSON_PATH}\n` +
         `  먼저: node bigData/process/wheelchair-route.mjs`);
  }
  const onto = loadOntology();
  const make = () => {
    const ruleset = buildRuleset(onto, 'bm:Wheelchair');
    return { ruleset, judgeFn: (seg) => judge(seg, ruleset, onto) };
  };
  const { ruleset, judgeFn } = make();
  return {
    onto, ruleset, judgeFn, rebuild: make,
    geojson: JSON.parse(fs.readFileSync(GEOJSON_PATH, 'utf8')),
    sources: {
      'wheelchair-verdict.mjs': fs.readFileSync(VERDICT_SRC, 'utf8'),
      'wheelchair-route.mjs': fs.readFileSync(ROUTE_SRC, 'utf8'),
    },
  };
}

function runChecks(ctx, { quiet = false } = {}) {
  const results = [];
  for (const c of checks) {
    try {
      const note = c.run(ctx);
      results.push({ id: c.id, ok: true, note });
      if (!quiet) console.log(`  ✅ ${c.what}\n       ${note}`);
    } catch (err) {
      results.push({ id: c.id, ok: false, note: err.message });
      if (!quiet) console.log(`  ❌ ${c.what}\n       ${err.message}`);
    }
  }
  return results;
}

// ── 자기 검사: 일부러 틀린 판본을 넣어 본다 ───────────────────────────────

function sabotages(base) {
  const clone = (o) => JSON.parse(JSON.stringify(o));
  const T = base.onto.byId.get(SLOPE_PARAM).value;

  return [
    {
      target: 'steps-never-ok',
      what: '계단을 "가능" 이라고 말하는 판정기',
      make: () => ({ ...base, judgeFn: (seg) => (seg.topic === 'stairs' || seg.highway === 'steps')
        ? { verdict: VERDICT.OK, reason: '거짓말' } : base.judgeFn(seg) }),
    },
    {
      target: 'threshold-from-ontology',
      what: '임계값을 코드에 박아 둔 판정기 (온톨로지를 바꿔도 안 따라온다)',
      make: () => {
        const frozen = T;   // 만들 때 값을 붙잡는다 = 코드에 박은 것과 같다
        const judgeFn = (seg) => (typeof seg.p90Slope === 'number' && seg.p90Slope >= frozen)
          ? { verdict: VERDICT.BLOCKED, reason: '박힌 값으로 판정' }
          : { verdict: VERDICT.UNKNOWN, reason: '박힌 값으로 판정' };
        return { ...base, judgeFn, rebuild: () => ({ judgeFn }) };
      },
    },
    {
      target: 'no-threshold-literal-in-source',
      what: '임계값 숫자가 적힌 소스',
      make: () => ({ ...base, sources: { 'sabotage.mjs': `const MAX_SLOPE = ${T}; // 박아 버렸다` } }),
    },
    {
      target: 'geojson-lines-have-verdict-and-reason',
      what: 'reason 이 빠진 geojson',
      make: () => {
        const g = clone(base.geojson);
        const line = g.features.find((f) => f.geometry.type === 'LineString');
        delete line.properties.reason;
        return { ...base, geojson: g };
      },
    },
    {
      target: 'verdict-enum',
      what: 'verdict 가 "아마도" 인 geojson',
      make: () => {
        const g = clone(base.geojson);
        g.features.find((f) => f.geometry.type === 'LineString').properties.verdict = '아마도';
        return { ...base, geojson: g };
      },
    },
    {
      target: 'geojson-lines-match-engine',
      what: '판정기와 어긋난 낡은 geojson',
      make: () => {
        const g = clone(base.geojson);
        for (const f of g.features) {
          if (f.geometry.type !== 'LineString') continue;
          if (base.segIndex.has(f.properties.wayId) && f.properties.verdict === VERDICT.UNKNOWN) {
            f.properties.verdict = VERDICT.BLOCKED;
            break;
          }
        }
        return { ...base, geojson: g };
      },
    },
    {
      target: 'unknown-is-not-silently-ok',
      what: '모르는 것을 "가능" 으로 기본값 주는 판정기',
      make: () => ({ ...base, judgeFn: (seg) => {
        const r = base.judgeFn(seg);
        return r.verdict === VERDICT.UNKNOWN ? { verdict: VERDICT.OK, reason: '기본값 가능' } : r;
      } }),
    },
  ];
}

async function selfTest(base) {
  console.log('\n── 자기 검사: 일부러 틀린 판본을 넣어 본다 ──');
  console.log('   각 검사가 자기 담당 고장을 실제로 잡아내야 한다.\n');
  let blind = 0;
  for (const s of sabotages(base)) {
    const results = runChecks(s.make(), { quiet: true });
    const target = results.find((r) => r.id === s.target);
    if (target && !target.ok) {
      console.log(`  ✅ 잡음  ${s.what}\n       → ${s.target}: ${target.note.split('\n')[0]}`);
    } else {
      console.log(`  ❌ 못 잡음  ${s.what}\n       → ${s.target} 이 통과해 버렸다. 이 검사는 눈이 멀었다.`);
      blind++;
    }
  }
  if (blind) {
    console.log(`\n❌ 눈먼 검사 ${blind}개. 검사기를 고치세요.`);
    return 1;
  }
  console.log(`\n✅ 검사 ${checks.length}개 전부 자기 담당 고장을 잡아냈다.`);
  return 0;
}

// ── main ──────────────────────────────────────────────────────────────────

async function main() {
  const selfOnly = process.argv.includes('--self-test');

  const ctx = buildContext();
  ctx.segIndex = await loadSegments();
  ctx.segments = [...ctx.segIndex.values()];

  console.log(`온톨로지: ${ctx.onto.from}`);
  console.log(`경로 파일: ${GEOJSON_PATH}`);
  console.log(`구간 표: ${ctx.segments.length.toLocaleString()}개\n`);

  if (selfOnly) return selfTest(ctx);

  console.log('── 검사 ──');
  const results = runChecks(ctx);
  const failed = results.filter((r) => !r.ok);

  const self = await selfTest(ctx);

  if (failed.length || self !== 0) {
    console.log(`\n❌ 실패 ${failed.length}개 / 검사 ${results.length}개`);
    return 1;
  }
  console.log(`\n✅ 검사 ${results.length}개 통과.`);
  return 0;
}

main()
  .then((code) => process.exit(code))
  .catch((err) => { console.error(`\n❌ ${err.message}`); process.exit(1); });
