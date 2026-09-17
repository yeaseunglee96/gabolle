/**
 * 1단계 — 잠재 취향을 **아는** 가상 사용자를 만든다.
 *
 *   node 01-users.mjs [--n 200]
 *
 * 🔴 정답을 우리가 정한다는 것이 이 실험의 전부다. 실사용 데이터로는 "이 사람이
 *    정말 조용한 곳을 좋아하나" 를 알 수 없어서, 랭커가 맞혔는지도 알 수 없다.
 *    여기서는 심어 둔 값이 정답이라 (a) 랭커가 되찾는지 (b) 게이트가 망친 모델을
 *    막는지를 **증명**할 수 있다.
 *
 * 🔴 맨 앞 둘은 일부러 정반대로 만든다. 질문 1(개인화가 순위를 움직이나)은 그 둘만
 *    있으면 답이 나온다 — 나머지는 랭커 학습용이다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { rng, taste } from "./lib/rng.mjs";
import { SCORE_AXES, TAG_AXES } from "./lib/axes.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const get = (k, d) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : d; };
const N = Number(get("--n", 200));

const plan = JSON.parse(fs.readFileSync(path.join(HERE, "findings", "00-plan.json"), "utf8"));
const next = rng(plan.seed);

/** 점수형은 선호값(0~1), 태그형은 낱말마다 가중치(-1~+1). */
function makeTaste(sign) {
	const score = {};
	for (const axis of SCORE_AXES) {
		// sign 이 주어지면 그 방향으로 몰아 준다 — 정반대 쌍을 만들 때 쓴다.
		const v = sign === undefined ? next() : (sign > 0 ? 0.85 : 0.15);
		score[axis.key] = Math.round(v * 100) / 100;
	}
	const tag = {};
	for (const axis of TAG_AXES) tag[axis.key] = {};
	return { score, tag };
}

const users = [];

// 🔴 0번과 1번은 정반대. 질문 1 은 이 둘로 답한다.
users.push({ userId: "sim-000-high", role: "opposite-a", note: "점수형 다섯을 전부 높게 선호", taste: makeTaste(+1) });
users.push({ userId: "sim-001-low", role: "opposite-b", note: "점수형 다섯을 전부 낮게 선호", taste: makeTaste(-1) });

for (let i = 2; i < N; i++) {
	users.push({
		userId: `sim-${String(i).padStart(3, "0")}`,
		role: "random",
		note: null,
		taste: makeTaste(),
	});
}

const out = {
	datasetVersion: plan.datasetVersion,
	seed: plan.seed,
	generatedAt: new Date().toISOString(),
	count: users.length,
	// 🔴 축 목록을 함께 적는다. 나중에 축이 늘면 옛 사용자 파일과 구분돼야 한다.
	axes: { score: SCORE_AXES.map((a) => a.key), tag: TAG_AXES.map((a) => a.key) },
	users,
};

fs.mkdirSync(path.join(HERE, "data"), { recursive: true });
fs.writeFileSync(path.join(HERE, "data", "users.json"), JSON.stringify(out, null, 2) + "\n", "utf8");
console.log(`가상 사용자 ${users.length}명 → data/users.json`);
console.log(`  정반대 쌍: ${users[0].userId} ↔ ${users[1].userId}`);
