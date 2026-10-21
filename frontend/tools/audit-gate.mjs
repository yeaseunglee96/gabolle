// frontend:dependency-scan 의 판정 — `npm audit --audit-level=high` 를 대신한다(S15P21E201-1977).
//
// 🔴 검사를 끄는 장치가 아니다. npm audit 결과에서 HIGH·CRITICAL 권고를 모으고,
//    tools/audit-allowlist.json 에 **만료 전**으로 적힌 권고만 뺀다. 목록에 없는 권고가
//    하나라도 있거나, 적힌 권고의 만료가 지났으면 그대로 실패(종료 코드 1)한다.
//    목록에는 고친 판이 아직 없는 권고만 넣는다 — 고친 판이 있으면 판을 올린다.
//
// 쓰는 법: node tools/audit-gate.mjs            (npm audit --json 을 직접 부른다)
//          node tools/audit-gate.mjs <audit.json> (저장해 둔 결과로 판정 — 시험용)
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const BLOCKING = new Set(['high', 'critical']);

/** npm audit --json 결과에서 HIGH·CRITICAL 권고를 권고 번호(GHSA) 하나씩 모은다. */
export function blockingAdvisories(audit) {
  const found = new Map();
  for (const vuln of Object.values(audit.vulnerabilities ?? {})) {
    for (const via of vuln.via ?? []) {
      if (typeof via !== 'object' || !BLOCKING.has(via.severity)) continue;
      const id = String(via.url ?? '').split('/').pop() || `${via.name}@${via.range}`;
      if (!found.has(id)) found.set(id, { id, package: via.name, severity: via.severity, title: via.title });
    }
  }
  return [...found.values()];
}

/** 예외 목록으로 가른다. today 는 'YYYY-MM-DD'. 만료일 당일까지 예외다. */
export function judge(audit, allowlist, today) {
  const allowed = new Map((allowlist.advisories ?? []).map((a) => [a.id, a]));
  const failing = [];
  const excused = [];
  for (const adv of blockingAdvisories(audit)) {
    const entry = allowed.get(adv.id);
    if (!entry) failing.push({ ...adv, why: '예외 목록에 없음' });
    else if (!entry.expires || entry.expires < today) failing.push({ ...adv, why: `예외 만료(${entry.expires ?? '날짜 없음'})` });
    else excused.push({ ...adv, expires: entry.expires });
  }
  return { failing, excused, ok: failing.length === 0 };
}

function readAudit(path) {
  if (path) return JSON.parse(readFileSync(path, 'utf8'));
  try {
    return JSON.parse(execFileSync('npm', ['audit', '--json'], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, shell: process.platform === 'win32' }));
  } catch (error) {
    // npm audit 는 취약점이 있으면 0 이 아닌 코드로 끝난다 — 결과는 stdout 에 그대로 있다.
    if (error.stdout) return JSON.parse(error.stdout);
    throw error;
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const allowlist = JSON.parse(readFileSync(new URL('./audit-allowlist.json', import.meta.url), 'utf8'));
  const today = new Date().toISOString().slice(0, 10);
  const result = judge(readAudit(process.argv[2]), allowlist, today);
  for (const a of result.excused) console.log(`예외 ${a.id} ${a.package} (${a.severity}) — ${a.expires} 까지`);
  for (const a of result.failing) console.error(`실패 ${a.id} ${a.package} (${a.severity}) — ${a.why}: ${a.title}`);
  console.log(result.ok ? `통과 — 막는 권고 0건, 예외 ${result.excused.length}건` : `실패 — 막는 권고 ${result.failing.length}건`);
  process.exit(result.ok ? 0 : 1);
}
