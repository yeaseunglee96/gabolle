/**
 * 2단계 — 순위표에 **노출과 클릭**을 입힌다.
 *
 *   node 02-expose.mjs
 *
 * 노출 확률은 1/log2(rank+1) — mlops/CLAUDE.md 6절이 적어 둔 그대로다. 위에 있을수록
 * 많이 보이고, 그래서 많이 눌린다. **이 편향을 일부러 넣는 것이 핵심이다** — 실사용
 * 데이터에도 똑같이 들어 있고, 이것 없이 만든 학습 데이터로는 위치 편향 보정을
 * 연습할 수 없다.
 *
 * 🔴 클릭은 두 가지가 곱해진 결과다.
 *      본다(노출)      = 1/log2(finalRank+1)
 *      좋아한다(취향)  = 심어 둔 취향과 장소 피처의 일치도
 *    둘을 곱해야 "위에 있어서 눌렸다" 와 "취향이라 눌렸다" 가 데이터 안에서 섞인다.
 *    섞여 있어야 랭커가 그것을 **가르는 연습**을 할 수 있다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { rng } from "./lib/rng.mjs";
import { TAG_AXES } from "./lib/axes.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const read = (f) => JSON.parse(fs.readFileSync(path.join(HERE, f), "utf8"));

const plan = read("findings/00-plan.json");
const users = read("data/users.json");

const rankingsPath = path.join(HERE, "data", "rankings.json");
if (!fs.existsSync(rankingsPath)) {
	console.error("data/rankings.json 이 없다.");
	console.error("이 파일은 여기서 만들지 않는다 — backend 하네스가 진짜 채점기로 찍는다.");
	console.error("계약: data/rankings.schema.md");
	process.exit(1);
}
const rankings = read("data/rankings.json");

if (rankings.datasetVersion !== plan.datasetVersion) {
	// 🔴 표시가 어긋나면 멈춘다. 합성 데이터가 진짜와 섞이는 사고를 이 한 줄이 막는다.
	console.error(`datasetVersion 이 다르다: 계획=${plan.datasetVersion} 순위표=${rankings.datasetVersion}`);
	process.exit(1);
}

const tasteOf = new Map(users.users.map((u) => [u.userId, u.taste]));
const next = rng(plan.seed + 1);

/**
 * 심은 취향과 장소 태그의 일치도 — 0~1. 1 에 가까울수록 이 사람 취향이다.
 *
 * 🔴 장소의 <b>태그 원본</b>을 본다. 채점기가 남기는 featureValues 에는 겹침 비율만
 *    있어서 어떤 낱말이 겹쳤는지를 알 수 없다 — 그래서 순위표 계약이 tags 를 함께
 *    싣는다 (data/rankings.schema.md).
 *
 * 🔴 태그가 하나도 없는 장소는 null 이다. 0 이 아니다 — 안 좋아한다와 좋아할 근거가
 *    없다는 다른 사실이고, 0 으로 뭉개면 데이터가 비어 있다는 사실 자체가 클릭률에
 *    녹아 사라진다.
 */
function affinity(taste, tags) {
	let sum = 0;
	let counted = 0;
	for (const axis of TAG_AXES) {
		const want = taste.tag?.[axis.key];
		const have = tags?.[axis.key];
		if (!want || !Array.isArray(have) || have.length === 0) continue;
		for (const value of have) {
			if (want[value] === undefined) continue;
			sum += want[value];
			counted++;
		}
	}
	if (counted === 0) return null;
	// -1~+1 평균을 0~1 확률로 옮긴다.
	return Math.min(1, Math.max(0, (sum / counted + 1) / 2));
}

const events = [];
let impressions = 0;
let clicks = 0;
let unscored = 0;

for (const row of rankings.rankings) {
	const taste = tasteOf.get(row.userId);
	if (!taste) continue;
	for (const c of row.candidates) {
		const pSeen = 1 / Math.log2((c.finalRank ?? c.originalRank) + 1);
		if (next() > pSeen) continue;
		impressions++;
		events.push({
			type: "recommendation_impression", userId: row.userId, requestId: row.requestId,
			placeId: c.placeId, finalRank: c.finalRank, originalRank: c.originalRank,
			datasetVersion: plan.datasetVersion,
		});
		const a = affinity(taste, c.tags);
		if (a === null) { unscored++; continue; }   // 피처가 없으면 좋아할 근거도 없다
		if (next() < a) {
			clicks++;
			events.push({
				type: "place_like", userId: row.userId, requestId: row.requestId,
				placeId: c.placeId, finalRank: c.finalRank,
				datasetVersion: plan.datasetVersion,
			});
		}
	}
}

fs.writeFileSync(path.join(HERE, "data", "events.json"),
	JSON.stringify({ datasetVersion: plan.datasetVersion, generatedAt: new Date().toISOString(), events }, null, 2) + "\n", "utf8");

console.log(`노출 ${impressions} · 클릭 ${clicks} → data/events.json`);
console.log(`  클릭률 ${impressions ? ((clicks / impressions) * 100).toFixed(1) : "0.0"}%`);
if (unscored) console.log(`  🔴 피처가 없어 취향을 잴 수 없던 노출 ${unscored}건 — 장소 데이터가 비어 있다는 뜻이다`);
