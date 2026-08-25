/**
 * 불변식 검사기.
 *
 * docs/INVARIANTS.md 의 I1~I7 을 코드로 옮긴 것이다.
 * 모델 검증기(test/model.test.mjs), 카오스 테스트(demo/chaos.sh),
 * 사후 감사(axmap audit)가 **같은 검사기**를 쓴다.
 * 도구마다 다른 판정을 하면 무엇이 맞는지 알 수 없게 된다.
 *
 * 여기도 순수하다. git 도 파일시스템도 없다.
 */

import { activeClaims, claimExpiresAt, normalizePath, pathsOverlap } from './protocol.mjs'

// ---------------------------------------------------------------------------
// I1 · 상호배제 (상태 불변식)
// 어느 시점에도, 서로 겹치는 경로를 유효하게 소유한 에이전트는 최대 1명이다.
// ---------------------------------------------------------------------------

export function mutualExclusionViolations(claims, now) {
  const live = activeClaims(claims, now)
  const out = []
  for (let i = 0; i < live.length; i++) {
    for (let j = i + 1; j < live.length; j++) {
      const a = live[i]
      const b = live[j]
      if (a.agent === b.agent) continue
      for (const pa of a.paths.map(normalizePath)) {
        for (const pb of b.paths.map(normalizePath)) {
          if (pathsOverlap(pa, pb)) {
            out.push({
              invariant: 'I1',
              message: `${pa} 와 ${pb} 를 ${a.agent} 와 ${b.agent} 가 동시에 소유`,
              agents: [a.agent, b.agent],
              paths: [pa, pb],
              windows: [
                { agent: a.agent, since: a.since, until: new Date(claimExpiresAt(a)).toISOString() },
                { agent: b.agent, since: b.since, until: new Date(claimExpiresAt(b)).toISOString() },
              ],
            })
          }
        }
      }
    }
  }
  return out
}

// ---------------------------------------------------------------------------
// I2 · 무손실 (전이 불변식)
// claim 이 성공을 반환했으면, 요청한 경로는 반드시 내 레코드에 있다.
// ---------------------------------------------------------------------------

export function lossViolations({ requested, record }) {
  const have = new Set(record.paths.map(normalizePath))
  return requested
    .map(normalizePath)
    .filter((p) => !have.has(p))
    .map((p) => ({
      invariant: 'I2',
      message: `claim 이 성공했는데 ${p} 가 레코드에 없다`,
      agents: [record.agent],
      paths: [p],
    }))
}

// ---------------------------------------------------------------------------
// I6 · 비부활 (전이 불변식)
// 성공한 claim 의 경로는 (요청한 경로 ∪ 직전 "유효" claim 의 경로) 안에 있어야 한다.
// 만료된 내 레코드에서 넘어온 경로는 관문 2 를 거치지 않았으므로 부활이다.
// ---------------------------------------------------------------------------

export function resurrectionViolations({ before, after, requested, me, now }) {
  const prev = activeClaims(before, now).find((c) => c.agent === me)
  const allowed = new Set([
    ...requested.map(normalizePath),
    ...(prev?.paths ?? []).map(normalizePath),
  ])
  const mine = after.find((c) => c.agent === me)
  if (!mine) return []
  return mine.paths
    .map(normalizePath)
    .filter((p) => !allowed.has(p))
    .map((p) => ({
      invariant: 'I6',
      message: `${p} 가 만료된 레코드에서 검사 없이 부활했다`,
      agents: [me],
      paths: [p],
    }))
}

// ---------------------------------------------------------------------------
// I7 · 세션 이름 유일성 (상태 불변식)
// 한 슬롯 레지스트리 안에서 동시에 살아있는 두 세션은 서로 다른 AXMAP_AGENT 를 갖는다.
//
// 🔴 "모든 에이전트의 이름이 다르다" 는 여기에 적을 수 없다. 사람이 두 셸에
//    같은 AXMAP_AGENT 를 넣으면 시스템이 막을 방법이 없고, 검사할 수 없는 문장을
//    불변식이라고 적어두면 그 목록 전체의 신뢰가 떨어진다.
//    시스템이 **실제로 보장할 수 있는 것**은 자기가 띄운 세션들의 이름뿐이다.
// ---------------------------------------------------------------------------

export function sessionIdentityViolations(sessions) {
  const out = []
  const seen = new Map()
  for (const s of sessions ?? []) {
    const id = s?.id ?? null
    const name = typeof s?.agent === 'string' ? s.agent.trim() : ''
    /**
     * 이름이 없는 것을 건너뛰지 않는다. 이름이 없는 세션은 자식이 서버의 환경을
     * 그대로 물려받는다는 뜻이고, 그것이 이 불변식을 만든 버그 그 자체다.
     * 여기서 조용히 넘기면 "위반 0" 이 "아무 문제 없음" 으로 읽힌다.
     */
    if (!name) {
      out.push({
        invariant: 'I7',
        message: `세션 ${id} 에 이름이 없다 — 자식이 서버의 AXMAP_AGENT 를 그대로 물려받는다`,
        agents: [],
        sessions: [id],
      })
      continue
    }
    const prev = seen.get(name)
    if (prev !== undefined && prev !== id) {
      out.push({
        invariant: 'I7',
        message: `세션 ${prev} 와 ${id} 가 같은 이름 "${name}" 을 쓴다 — 서로를 겹침으로 보지 못한다`,
        agents: [name],
        sessions: [prev, id],
      })
      continue
    }
    seen.set(name, id)
  }
  return out
}

// ---------------------------------------------------------------------------
// 사후 감사
//
// 장부 브랜치는 모든 claim 변화를 커밋으로 기록한 완전한 감사 로그다.
// 스냅샷을 순서대로 재생하면 상호배제가 깨진 적이 있는지 사후에 증명할 수 있다.
// 구현을 믿을 필요가 없어진다는 것이 이 검사의 핵심이다.
// ---------------------------------------------------------------------------

/**
 * 스냅샷의 논리 시각.
 *
 * 커밋 시각을 그대로 쓰지 않는 이유: 테스트가 AXMAP_NOW 로 시계를 옮기면
 * 커밋 시각(실제 벽시계)과 레코드의 since(논리 시각)가 어긋난다.
 * since 는 논리적으로 미래일 수 없으므로, 둘 중 늦은 쪽이 그 스냅샷의 시각이다.
 * 실제 사용에서는 항상 since <= 커밋 시각이므로 커밋 시각과 같다.
 */
export function snapshotTime(snapshot) {
  const sinces = snapshot.claims.map((c) => Date.parse(c.since)).filter((n) => !Number.isNaN(n))
  return Math.max(snapshot.time, ...(sinces.length ? sinces : [snapshot.time]))
}

/**
 * 시간이 흐르는 것만으로는 겹침이 생기지 않는다 (만료는 claim 을 없앨 뿐이다).
 * 겹침은 오직 claim 이 추가될 때 생기고, claim 은 커밋으로만 추가된다.
 * 따라서 각 커밋 시점만 검사하면 전체 구간을 덮는다.
 */
export function auditLedger(snapshots) {
  const violations = []
  for (const snap of snapshots) {
    const t = snapshotTime(snap)
    for (const v of mutualExclusionViolations(snap.claims, t)) {
      violations.push({ ...v, commit: snap.commit, subject: snap.subject, at: new Date(t).toISOString() })
    }
  }
  return { ok: violations.length === 0, checked: snapshots.length, violations }
}

export function formatAudit(report) {
  if (report.ok) {
    return (
      `감사 통과 - 스냅샷 ${report.checked}개\n` +
      '장부 이력 전체에서 상호배제(I1)가 깨진 시점이 없습니다.'
    )
  }
  const lines = [`감사 실패 - 스냅샷 ${report.checked}개 중 위반 ${report.violations.length}건\n`]
  for (const v of report.violations) {
    lines.push(`  x [${v.invariant}] ${v.at}  commit ${v.commit.slice(0, 7)}  ${v.subject}`)
    lines.push(`      ${v.message}`)
    for (const w of v.windows ?? []) lines.push(`      ${w.agent}: ${w.since} ~ ${w.until}`)
    lines.push('')
  }
  return lines.join('\n')
}
