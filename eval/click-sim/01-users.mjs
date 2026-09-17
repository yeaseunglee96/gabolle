/**
 * 1단계 — 잠재 취향을 **아는** 가상 사용자를 만든다.
 *
 *   node 01-users.mjs [--n 200]
 *
 * 🔴 정답을 우리가 정한다는 것이 이 실험의 전부다. 실사용 데이터로는 "이 사람이 정말
 *    밀면을 좋아하나" 를 알 수 없어서 랭커가 맞혔는지도 알 수 없다. 여기서는 심어 둔
 *    값이 정답이라 (a) 랭커가 되찾는지 (b) 게이트가 망친 모델을 막는지를 **증명**할 수 있다.
 *
 * 🔴 취향은 **태그 축에만** 심는다. 점수형 다섯(로컬성·조용함·관광객·그늘·경사)은
 *    배포 서버에 값이 붙은 장소가 **0곳**이다(lib/axes.mjs 의 실측). 거기 심으면
 *    가상 사용자가 무엇을 좋아하든 맞출 장소가 없다.
 *
 * 🔴 맨 앞 둘은 일부러 정반대다. 질문 1(개인화가 순위를 움직이나)은 그 둘로 답한다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { rng } from "./lib/rng.mjs";
import { TAG_AXES, SCORE_AXES, FACET_SOURCE } from "./lib/axes.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const get = (k, d) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : d; };
const N = Number(get("--n", 200));

const plan = JSON.parse(fs.readFileSync(path.join(HERE, "findings", "00-plan.json"), "utf8"));
const next = rng(plan.seed);

/** 태그마다 -1~+1 가중치. mode 가 주어지면 그 방향으로 몰아 준다 (정반대 쌍). */
function makeTaste(mode) {
	const tag = {};
	for (const axis of TAG_AXES) {
		tag[axis.key] = {};
		axis.values.forEach((value, i) => {
			let w;
			if (mode === undefined) w = Math.round((next() * 2 - 1) * 100) / 100;
			// 정반대 쌍: a 는 짝수번째 값을 좋아하고 홀수번째를 싫어한다. b 는 정확히 반대.
			else w = (i % 2 === 0 ? 1 : -1) * (mode === "a" ? 1 : -1);
			tag[axis.key][value] = w;
		});
	}
	return { tag, score: {} };
}

const users = [
	{ userId: "sim-000-a", role: "opposite-a", note: "짝수번째 태그를 좋아함", taste: makeTaste("a") },
	{ userId: "sim-001-b", role: "opposite-b", note: "홀수번째 태그를 좋아함 (000 의 정반대)", taste: makeTaste("b") },
];
for (let i = 2; i < N; i++) {
	users.push({ userId: `sim-${String(i).padStart(3, "0")}`, role: "random", note: null, taste: makeTaste() });
}

fs.mkdirSync(path.join(HERE, "data"), { recursive: true });
fs.writeFileSync(path.join(HERE, "data", "users.json"), JSON.stringify({
	datasetVersion: plan.datasetVersion,
	seed: plan.seed,
	generatedAt: new Date().toISOString(),
	count: users.length,
	axes: { tag: TAG_AXES.map((a) => a.key), score: SCORE_AXES.map((a) => a.key) },
	// 🔴 축을 왜 이렇게 골랐는지의 출처를 사용자 파일에도 박아 둔다. 나중에 이 파일만
	//    보는 사람이 "왜 점수형이 없지" 를 되묻지 않게.
	facetSource: FACET_SOURCE,
	users,
}, null, 2) + "\n", "utf8");

console.log(`가상 사용자 ${users.length}명 → data/users.json`);
console.log(`  취향 축: ${TAG_AXES.map((a) => a.key).join(", ")}  (점수형 ${SCORE_AXES.length}개 — 배포 실측 0곳)`);
console.log(`  정반대 쌍: ${users[0].userId} ↔ ${users[1].userId}`);
