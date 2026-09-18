/**
 * 0단계 — **지표를 먼저 못 박는다.** 숫자를 하나도 보기 전에.
 *
 *   node 00-plan.mjs
 *
 * 🔴 왜 이것이 0단계인가. 결과를 본 뒤에 지표를 고르면, 고른 사람이 무엇을 골랐든
 *    그 지표는 "이번에 잘 나온 것" 이 된다. `eval/food-ranking` 도 같은 순서를 지켰고,
 *    라운드2 에서 눈금을 밀면 성적이 오르는 것을 **보고도 안 고쳤다**(11.3절) —
 *    고치면 판정이 아니게 되기 때문이다.
 *
 * 이 파일이 만드는 findings/00-plan.json 이 그 약속이다. 03단계는 **여기 적힌 것만**
 * 잰다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { SCORE_AXES, TAG_AXES, EXCLUDED } from "./lib/axes.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));

const PLAN = {
	planVersion: 1,
	pinnedAt: new Date().toISOString(),
	datasetVersion: "synthetic-v1",
	seed: 20260917,

	// ── 질문 1 — 모델보다 먼저 답해야 하는 것 ──────────────────────────
	question1: {
		asks: "개인화가 순위를 실제로 움직이는가",
		why: "여기서 '거의 안 움직인다' 가 나오면 그건 모델 문제가 아니라 서비스 문제다. 랭커를 붙여도 안 고쳐진다",
		metrics: [
			{
				key: "top10_jaccard_opposite",
				label: "취향이 정반대인 두 사람의 상위 10위 겹침(자카드)",
				reads: "1.0 이면 개인화가 순위에 아무 영향이 없다는 뜻이다",
				// 🔴 합격선을 지금 적는다. 나중에 결과를 보고 정하면 그건 합격선이 아니다.
				threshold: { max: 0.7, meaning: "0.7 을 넘으면 개인화가 사실상 안 먹는 것으로 본다" },
			},
			{
				key: "rank_shift_median",
				label: "같은 장소가 두 사람 사이에서 움직인 순위 차이의 중앙값",
				reads: "0 에 가까우면 목록이 사람과 무관하게 거의 고정되어 있다",
				threshold: { min: 5, meaning: "상위 200 후보에서 중앙값 5위 미만이면 움직임이 없다고 본다" },
			},
		],
	},

	// ── 질문 2 — 랭커가 생긴 뒤에 답한다 ──────────────────────────────
	question2: {
		asks: "랭커가 심어 둔 취향을 되찾는가 · 승격 게이트가 망친 모델을 막는가",
		status: "BLOCKED",
		blockedBy: "랭커가 아직 없다. 질문 1 의 답이 '움직인다' 여야 의미가 생긴다",
		metrics: [
			{ key: "taste_recovery_corr", label: "심은 취향과 학습된 계수의 상관", threshold: null },
			{ key: "gate_rejects_broken", label: "일부러 망친 모델을 게이트가 막는가", threshold: { equals: true } },
		],
	},

	axes: {
		score: SCORE_AXES.map((a) => a.key),
		tag: TAG_AXES.map((a) => a.key),
		excluded: EXCLUDED,
	},

	// 🔴 노출 모형. mlops/CLAUDE.md 6절이 적어 둔 것 그대로.
	exposure: {
		model: "1 / log2(rank + 1)",
		appliedTo: "finalRank",
		why: "사용자가 실제로 보는 것은 다양성 재정렬 뒤의 순서다. originalRank 에 적용하면 화면에 없던 자리를 봤다고 치는 셈이다",
	},
};

fs.mkdirSync(path.join(HERE, "findings"), { recursive: true });
const out = path.join(HERE, "findings", "00-plan.json");

if (fs.existsSync(out)) {
	// 🔴 덮어쓰지 않는다. 지표를 다시 못 박는 것은 앞의 약속을 무르는 것이라,
	//    사람이 파일을 지우는 동작으로만 되게 둔다.
	console.error("이미 못 박혀 있다: findings/00-plan.json");
	console.error("지표를 바꾸려면 그 파일을 손으로 지우고 다시 돌린다 — 그 흔적이 남아야 한다.");
	process.exit(1);
}

fs.writeFileSync(out, JSON.stringify(PLAN, null, 2) + "\n", "utf8");
console.log("지표를 못 박았다 → findings/00-plan.json");
console.log(`  질문1 지표 ${PLAN.question1.metrics.length}개 (합격선 포함)`);
console.log(`  질문2 는 ${PLAN.question2.status} — ${PLAN.question2.blockedBy}`);
