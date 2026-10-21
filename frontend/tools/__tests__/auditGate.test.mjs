// node --test tools/__tests__/auditGate.test.mjs — CI 의 frontend:dependency-scan 이 판정 전에 돌린다.
import test from 'node:test';
import assert from 'node:assert/strict';
import { judge } from '../audit-gate.mjs';

const audit = {
  vulnerabilities: {
    braces: { via: [{ name: 'braces', severity: 'high', url: 'https://github.com/advisories/GHSA-aaaa', title: 't' }] },
    micromatch: { via: ['braces'] },
    other: { via: [{ name: 'other', severity: 'moderate', url: 'https://github.com/advisories/GHSA-mmmm', title: 'm' }] },
  },
};
const list = (expires) => ({ advisories: [{ id: 'GHSA-aaaa', package: 'braces', expires }] });

test('만료 전 예외는 통과', () => {
  assert.equal(judge(audit, list('2026-10-31'), '2026-10-05').ok, true);
});

test('만료일 당일까지는 예외, 다음 날 실패', () => {
  assert.equal(judge(audit, list('2026-10-31'), '2026-10-31').ok, true);
  const r = judge(audit, list('2026-10-31'), '2026-11-01');
  assert.equal(r.ok, false);
  assert.match(r.failing[0].why, /만료/);
});

test('목록에 없는 HIGH 는 실패', () => {
  const r = judge(audit, { advisories: [] }, '2026-10-05');
  assert.equal(r.ok, false);
  assert.equal(r.failing[0].id, 'GHSA-aaaa');
});

test('CRITICAL 도 막고, MODERATE 는 막지 않는다', () => {
  const crit = { vulnerabilities: { x: { via: [{ name: 'x', severity: 'critical', url: '.../GHSA-cccc', title: 'c' }] } } };
  assert.equal(judge(crit, list('2026-10-31'), '2026-10-05').ok, false);
  const mod = { vulnerabilities: { other: audit.vulnerabilities.other } };
  assert.equal(judge(mod, { advisories: [] }, '2026-10-05').ok, true);
});

test('만료일이 없는 예외는 예외가 아니다', () => {
  assert.equal(judge(audit, list(undefined), '2026-10-05').ok, false);
});
