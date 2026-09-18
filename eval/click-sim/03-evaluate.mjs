/**
 * 3단계 — **0단계에서 못 박은 것만** 잰다.
 *
 *   node 03-evaluate.mjs
 *
 * 🔴 여기서 지표를 새로 만들지 않는다. 결과를 본 뒤에 고른 지표는 고른 사람이
 *    무엇을 골랐든 "이번에 잘 나온 것" 이 된다. 재고 싶은 것이 생기면 findings/00-plan.json
 *    을 지우고 0단계를 다시 돌린다 — 그 흔적이 남아야 한다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const read = (f) => JSON.parse(fs.readFileSync(path.join(HERE, f), "utf8"));

const plan = read("findings/00-plan.json");
const rankings = read("data/rankings.json");
const users = read("data/users.json");

// 🔴 진짜 채점기가 찍은 순위표인지 먼저 본다.
//    findings/ 는 커밋되는 자리다(eval/food-ranking 과 같다). 스모크용 가짜 순위표로
//    나온 숫자가 그 자리에 남으면, 다음 사람은 그것을 실측으로 읽는다. 합성을 진짜로
//    착각하는 사고는 dataset_version 한 칸으로 막는 것과 같은 종류라 여기서도 막는다.
if (!rankings.scorerVersion || /^SMOKE/i.test(rankings.scorerVersion)) {
	console.error(`scorerVersion 이 ${rankings.scorerVersion || "(없음)"} 다 — 진짜 채점기의 순위표가 아니다.`);
	console.error("findings/ 에 남기지 않는다. backend 하네스로 다시 찍는다 (data/rankings.schema.md).");
	process.exit(1);
}

const byUser = new Map(rankings.rankings.map((r) => [r.userId, r.candidates]));
// 🔴 id 를 박아 두지 않는다. 축이 바뀌어 사용자 이름이 바뀐 적이 있고, 그때 이 검사는
//    "순위표가 없다" 로 죽었다. 역할(role)로 찾으면 이름이 바뀌어도 따라간다.
const opposites = users.users.filter((u) => u.role === "opposite-a" || u.role === "opposite-b");
if (opposites.length !== 2) {
	console.error(`정반대 쌍을 찾지 못했다 (role=opposite-a/opposite-b 가 ${opposites.length}명). 01단계를 확인한다.`);
	process.exit(1);
}
const [a, b] = opposites.map((u) => u.userId);
const ca = byUser.get(a);
const cb = byUser.get(b);
if (!ca || !cb) {
	console.error(`정반대 쌍의 순위표가 없다 (${a} · ${b}). 01단계 사용자로 순위를 찍었는지 확인한다.`);
	process.exit(1);
}

const topN = (cands, n) => new Set(
	[...cands].sort((x, y) => (x.finalRank ?? x.originalRank) - (y.finalRank ?? y.originalRank))
		.slice(0, n).map((c) => c.placeId));

// ── 지표 1 — 상위 10위 겹침 ────────────────────────────────────────
const ta = topN(ca, 10);
const tb = topN(cb, 10);
const inter = [...ta].filter((p) => tb.has(p)).length;
const union = new Set([...ta, ...tb]).size;
const jaccard = union === 0 ? null : inter / union;

// ── 지표 2 — 같은 장소의 순위 이동 중앙값 ──────────────────────────
const rankOf = (cands) => new Map(cands.map((c) => [c.placeId, c.finalRank ?? c.originalRank]));
const ra = rankOf(ca);
const rb = rankOf(cb);
const shifts = [];
for (const [placeId, rankA] of ra) {
	const rankB = rb.get(placeId);
	if (rankB !== undefined) shifts.push(Math.abs(rankA - rankB));
}
shifts.sort((x, y) => x - y);
const median = shifts.length ? shifts[Math.floor(shifts.length / 2)] : null;

// ── 판정 ───────────────────────────────────────────────────────────
const m1 = plan.question1.metrics.find((m) => m.key === "top10_jaccard_opposite");
const m2 = plan.question1.metrics.find((m) => m.key === "rank_shift_median");
const pass1 = jaccard === null ? null : jaccard <= m1.threshold.max;
const pass2 = median === null ? null : median >= m2.threshold.min;

const verdict = (pass1 === null || pass2 === null) ? "INCONCLUSIVE"
	: (pass1 && pass2) ? "PERSONALIZATION_MOVES_RANKING"
	: "PERSONALIZATION_BARELY_MOVES_RANKING";

const result = {
	planVersion: plan.planVersion,
	datasetVersion: plan.datasetVersion,
	evaluatedAt: new Date().toISOString(),
	comparedUsers: [a, b],
	comparedPlaces: shifts.length,
	metrics: {
		top10_jaccard_opposite: { value: jaccard, threshold: m1.threshold, pass: pass1 },
		rank_shift_median: { value: median, threshold: m2.threshold, pass: pass2 },
	},
	verdict,
	// 🔴 숫자만 남기면 다음 사람이 이것을 실제보다 세게 읽는다. 한계를 결과 옆에 붙여
	//    둔다 — 파일은 대화보다 오래 남고, 대화에서 한 말은 같이 안 간다.
	limitations: [
		"장소가 합성이다. 거리와 음식 태그를 우리가 퍼뜨린 방식에 결과가 크게 좌우된다",
		"실제 표본의 음식 갈래는 한쪽으로 쏠려 있다(queue-sample.ndjson 200곳 중 백반/한정식만 66곳). 여기서는 세 갈래를 고르게 뿌렸으므로 실제보다 가르기 쉬운 세계다",
		"두 사람을 같은 장소 집합에서 견주므로 거리 항은 양쪽에 똑같이 들어가 상쇄된다. 즉 이 지표는 '순서가 달라지는가' 를 재지 '사용자가 체감할 만큼 다른가' 를 재지 않는다",
		"배포에서 행이 0인 다섯 축(분위기·로컬성·조용함·그늘·관광객비율)은 안 심었다. 채워지면 이 수는 다시 재야 한다",
		"경사는 심었다 — 배포에 2682곳 있고 채점에도 들어간다. facets 가 0 으로 내는 것은 그 엔드포인트가 점수형을 못 세기 때문이다(feature_key 가 NULL)",
	],
	// 🔴 결과를 어떻게 읽어야 하는지를 결과 옆에 적어 둔다. 숫자만 남기면
	//    다음 사람이 "낮으니 좋은 것" 처럼 거꾸로 읽는다.
	howToRead: verdict === "PERSONALIZATION_BARELY_MOVES_RANKING"
		? "개인화가 순위를 거의 안 움직인다. 랭커를 붙여도 안 고쳐진다 — 먼저 볼 곳은 S15P21E201-1108(붙을 장소가 없는 취향 낱말 넷)과 가중치 배분이다"
		: "개인화가 순위를 움직인다. 이제 질문 2(랭커가 심은 취향을 되찾는가)로 갈 수 있다",
};

fs.writeFileSync(path.join(HERE, "findings", "03-separation.json"), JSON.stringify(result, null, 2) + "\n", "utf8");

console.log(`정반대 쌍 비교 — 공통 후보 ${shifts.length}곳`);
console.log(`  상위10 겹침(자카드) ${jaccard === null ? "-" : jaccard.toFixed(3)}  (합격 ≤ ${m1.threshold.max})  ${pass1 ? "통과" : "미달"}`);
console.log(`  순위 이동 중앙값    ${median === null ? "-" : median}  (합격 ≥ ${m2.threshold.min})  ${pass2 ? "통과" : "미달"}`);
console.log(`\n판정: ${verdict}`);
console.log(result.howToRead);
console.log("");
console.log("🔴 한계 — 이 수를 인용하기 전에 읽는다:");
for (const line of result.limitations) console.log("  · " + line);
