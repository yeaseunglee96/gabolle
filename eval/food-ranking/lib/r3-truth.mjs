/**
 * 라운드3 정답지 넷을 읽고 하나로 합치는 규칙.
 *
 * 🔴 라운드2 의 실패 원인은 모델이 아니라 **정답의 정의**였다. 정답표가 한국관광공사
 *    TourAPI 관광 안내 목록이라, 모델이 배운 것은 "맛" 이 아니라 "관광 안내에 실릴
 *    자리" 였다 (docs/FOOD-RANKING-EVAL.md 10.1). 라운드3 은 정답을 바꾼다.
 *
 * 넷의 성격이 서로 다르다. 섞기 전에 구분해서 다룬다.
 *   michelin    미쉐린 가이드 부산      — 평가기관의 **맛** 평가 (+ 가격대 ₩~₩₩₩₩)
 *   blueribbon  블루리본 서베이 6개 테마 — 평가기관의 **맛** 평가 (+ 리본수 1·2, 주소)
 *   blog100     네이버 블로그 부산맛집100 — **현지인이 실제로 가는 곳** (주소).
 *               작성자가 "평가기관의 평가에 전혀 신경쓰지 않은" 이라고 명시 → 독립 증거
 *   baengnyeon  백년가게 (중소벤처기업부) — 맛이 아니라 **오래 살아남음**. 별도 축
 *
 * 🔴 정답지 원본은 **내부 채점표로만** 쓴다. 앱 빌드(frontend/·backend/)에 넣지 않는다.
 */
import fs from "node:fs";
import path from "node:path";

/** 상호명 정규화 — 무엇을 지우는지 여기 한 곳에만 적는다 */
export function normName(s) {
  return String(s ?? "")
    .replace(/\(.*?\)/g, "") // 괄호와 그 안: "(주)해운대암소갈비집" · "이엘 (EL16.52)"
    .replace(/㈜|주식회사/g, "") // 법인 표기
    .replace(/[\s\-·.,'"’‘`&＆]/g, "") // 공백·붙임표·가운뎃점·따옴표
    .toLowerCase();
}

/**
 * 지점 접미사를 뗀 **대표이름**. 출처마다 표기가 다른 것을 한 줄로 모은다.
 *   "톤쇼우 광안점" → "톤쇼우"   ·   "초원복국 본점" → "초원복국"
 *
 * 🔴 `○○점` 을 무조건 떼면 "파밀리아제과점" 이 "파밀리아" 가 된다. 그래서 **부산 안의
 *    실제 지역명이 앞에 붙은 것만** 뗀다. 목록은 여기 다 적어 둔다 — 늘릴 때도 여기서만.
 */
const BRANCH_PLACES = [
  "본", "직영", "광안", "해운대", "서면", "남포", "센텀", "범일", "부산", "기장",
  "송정", "좌동", "동래", "온천장", "대연", "경성대", "전포", "사상", "덕천", "화명",
  "정관", "명지", "다대포", "자갈치", "부평", "초량", "수영", "민락", "마린시티",
  "청사포", "달맞이", "구서", "장전", "연산", "문현", "괴정", "하단",
];
const BRANCH_RE = new RegExp(`(${BRANCH_PLACES.join("|")})점$`);

export function coreName(s) {
  const n = normName(s);
  const stripped = n.replace(BRANCH_RE, "");
  // 지점명을 떼고 나서 2글자 미만이면 뗀 것이 상호 자체였다는 뜻이다. 되돌린다.
  return stripped.length >= 2 ? stripped : n;
}

/**
 * 주소를 { gu, kind, key, road, bon, bu } 로 쪼갠다.
 * 도로명("해운대구 달맞이길 22")과 지번("수영구 광안동 94-1") 둘 다 받는다.
 * 층·호·건물명 같은 꼬리는 번호를 만나는 순간 잘린다.
 */
export function parseAddr(raw) {
  if (!raw) return null;
  let t = String(raw).trim();
  if (!t) return null;
  t = t.replace(/^부산광역시|^부산시|^부산/, "").trim();
  const toks = t.split(/\s+/).filter(Boolean);
  if (!toks.length) return null;

  let i = 0;
  let gu = null;
  if (/(구|군)$/.test(toks[i])) gu = toks[i++];
  // 기장군은 아래에 읍·면이 한 단계 더 있다
  while (i < toks.length && /(읍|면)$/.test(toks[i])) i++;

  // 번호 토큰(123 · 43-10) 바로 앞의 토큰이 도로명 또는 법정동이다
  for (; i < toks.length - 1; i++) {
    const m = String(toks[i + 1]).match(/^(\d+)(?:-(\d+))?$/);
    if (!m) continue;
    const unit = toks[i];
    const bon = Number(m[1]);
    const bu = m[2] ? Number(m[2]) : 0;
    const isRoad = /(로|길)$/.test(unit);
    const isJibun = /(동|가|리)$/.test(unit);
    if (!isRoad && !isJibun) continue;
    const kind = isRoad ? "road" : "jibun";
    return { gu, kind, road: unit, bon, bu, key: `${gu ?? ""}|${kind}|${unit}|${bon}|${bu}` };
  }
  return null;
}

/** `#` 로 시작하는 머리말을 걷어내고 탭으로 자른다 */
function readTsv(file) {
  return fs
    .readFileSync(file, "utf8")
    .split(/\r?\n/)
    .filter((l) => l.trim() && !l.startsWith("#"))
    .map((l) => l.split("\t"));
}

/**
 * 정답지 넷을 읽어 **출처별 항목 배열**로 만든다. 아직 합치지 않는다.
 * @returns {{source:string, name:string, addrRaw:string|null, meta:object}[]}
 */
export function loadRawEntries(rawDir) {
  const out = [];

  // ── 미쉐린: 상호 / 가격대 / 음식종류 / 비고. 🔴 주소가 없다 ──────────────────
  for (const c of readTsv(path.join(rawDir, "michelin-busan-2026.tsv"))) {
    const name = (c[0] ?? "").trim();
    if (!name) continue;
    out.push({
      source: "michelin",
      name,
      addrRaw: null,
      meta: { price: (c[1] ?? "").trim(), cuisine: (c[2] ?? "").trim(), note: (c[3] ?? "").trim() },
    });
  }

  // ── 블루리본: 테마 / 상호 / 리본수 / 음식종류 / 전화 / 주소 ─────────────────
  for (const c of readTsv(path.join(rawDir, "blueribbon-busan-2026.tsv"))) {
    const name = (c[1] ?? "").trim();
    if (!name) continue;
    const ribbonRaw = (c[2] ?? "").trim();
    out.push({
      source: "blueribbon",
      name,
      addrRaw: (c[5] ?? "").trim() || null,
      meta: {
        theme: (c[0] ?? "").trim(),
        ribbonRaw,
        // "1👌" 는 원문 표기 그대로다. 수만 뽑는다. "new" 는 신규라 리본수가 없다
        ribbon: /^\d/.test(ribbonRaw) ? Number(ribbonRaw[0]) : null,
        cuisine: (c[3] ?? "").trim(),
        phone: (c[4] ?? "").trim(),
      },
    });
  }

  // ── 블로그100: 번호 / 상호 / 주소 / 예약수단 ────────────────────────────────
  for (const c of readTsv(path.join(rawDir, "blog-busan100-2026.tsv"))) {
    const name = (c[1] ?? "").trim();
    if (!name) continue;
    out.push({
      source: "blog100",
      name,
      addrRaw: (c[2] ?? "").trim() || null,
      meta: { no: (c[0] ?? "").trim(), booking: (c[3] ?? "").trim() },
    });
  }

  // ── 백년가게: 번호 / 상호 / 업종 / 주소 / 비고. 🔴 음식점만 쓴다 ────────────
  for (const c of readTsv(path.join(rawDir, "baengnyeon-busan.tsv"))) {
    const name = (c[1] ?? "").trim();
    const trade = (c[2] ?? "").trim();
    if (!name) continue;
    if (trade !== "음식점") continue; // 도소매·제조·서비스는 food 평가 대상이 아니다
    out.push({
      source: "baengnyeon",
      name,
      addrRaw: (c[3] ?? "").trim() || null,
      meta: { no: (c[0] ?? "").trim(), trade, note: (c[4] ?? "").trim() },
    });
  }

  return out;
}

/**
 * 출처별 항목을 **가게 단위**로 합친다.
 *
 * 합치는 규칙 (🔴 채점 점수를 보기 전에 정했다):
 *   1. 대표이름(coreName)이 **완전히 같으면** 한 덩이 후보다
 *   2. 그 덩이 안에서 **주소가 다르면 가르지 않고 나눈다.** 같은 상호의 다른 지점은
 *      다른 가게다 (예: 기장곰장어 — 기장군 본점과 해운대 지점은 별개)
 *   3. 주소가 없는 항목(미쉐린)은 덩이 안에 주소 묶음이 **딱 하나일 때만** 붙는다.
 *      둘 이상이면 어디에 붙일지 데이터가 말해주지 않으므로 **따로 남기고 표시**한다
 *   4. 주소가 같으면 이름이 조금 달라도 같은 가게로 본다 — 앞 3글자가 같거나
 *      한쪽이 다른 쪽을 품으면. (예: "브리앙" ↔ "프랑스과자점 브리앙",
 *      "(주)해운대암소갈비집" ↔ "해운대소문난암소갈비집")
 *   5. 대표이름이 **접두사**로 포함되면 합친다 — 짧은 쪽이 3글자 이상일 때만.
 *      (예: "소공간" ↔ "소공간 다이닝"). 🔴 아무 데나 포함되는 것은 안 된다 —
 *      "모리" 가 "아오모리" 에 붙어 버린다
 */
export function mergeEntries(entries) {
  const items = entries.map((e, i) => ({ ...e, i, core: coreName(e.name), addr: parseAddr(e.addrRaw) }));

  // 1 · 2 — 대표이름 + 주소키로 묶는다 (주소 없는 것은 따로 모아 둔다)
  const byCore = new Map();
  for (const it of items) {
    if (!byCore.has(it.core)) byCore.set(it.core, []);
    byCore.get(it.core).push(it);
  }

  /** @type {{key:string, core:string, addr:object|null, members:object[], ambiguous:boolean}[]} */
  const groups = [];
  for (const [core, list] of byCore) {
    const withAddr = list.filter((x) => x.addr);
    const noAddr = list.filter((x) => !x.addr);
    const byAddr = new Map();
    for (const x of withAddr) {
      if (!byAddr.has(x.addr.key)) byAddr.set(x.addr.key, []);
      byAddr.get(x.addr.key).push(x);
    }
    for (const [ak, members] of byAddr) {
      groups.push({ key: `${core}@${ak}`, core, addr: members[0].addr, members: [...members], ambiguous: false });
    }
    if (noAddr.length) {
      if (byAddr.size === 1) {
        // 3 — 주소 묶음이 하나뿐이라 붙일 곳이 분명하다
        groups[groups.length - 1].members.push(...noAddr);
      } else {
        // 주소가 아예 없는 덩이거나(전부 미쉐린), 주소 묶음이 둘 이상이라 못 고른다
        groups.push({
          key: `${core}@?`,
          core,
          addr: null,
          members: noAddr,
          ambiguous: byAddr.size > 1,
        });
      }
    }
  }

  // 4 — 주소가 같은 덩이끼리, 이름이 비슷하면 합친다
  const byAddrKey = new Map();
  for (const g of groups) {
    if (!g.addr) continue;
    if (!byAddrKey.has(g.addr.key)) byAddrKey.set(g.addr.key, []);
    byAddrKey.get(g.addr.key).push(g);
  }
  const dead = new Set();
  const addrMerges = [];
  for (const list of byAddrKey.values()) {
    for (let a = 0; a < list.length; a++) {
      for (let b = a + 1; b < list.length; b++) {
        const A = list[a];
        const B = list[b];
        if (dead.has(A.key) || dead.has(B.key)) continue;
        const sameHead = A.core.slice(0, 3) === B.core.slice(0, 3) && A.core.length >= 3 && B.core.length >= 3;
        const contains = A.core.includes(B.core) || B.core.includes(A.core);
        if (!sameHead && !contains) continue;
        addrMerges.push([A.core, B.core, A.addr.key]);
        A.members.push(...B.members);
        dead.add(B.key);
      }
    }
  }

  // 5 — 접두사 포함으로 합친다 (짧은 쪽 3글자 이상). 주소가 충돌하면 안 합친다
  const live = groups.filter((g) => !dead.has(g.key));
  const prefixMerges = [];
  for (let a = 0; a < live.length; a++) {
    for (let b = 0; b < live.length; b++) {
      if (a === b) continue;
      const A = live[a]; // 짧은 쪽
      const B = live[b]; // 긴 쪽
      if (dead.has(A.key) || dead.has(B.key)) continue;
      if (A.core.length < 3 || A.core.length >= B.core.length) continue;
      if (!B.core.startsWith(A.core)) continue;
      if (A.addr && B.addr && A.addr.key !== B.addr.key) continue; // 다른 지점이다
      prefixMerges.push([A.core, B.core]);
      B.members.push(...A.members);
      if (!B.addr && A.addr) B.addr = A.addr;
      dead.add(A.key);
    }
  }

  const finalGroups = groups.filter((g) => !dead.has(g.key));
  return { groups: finalGroups, addrMerges, prefixMerges };
}
