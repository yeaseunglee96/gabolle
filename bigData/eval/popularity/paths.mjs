/**
 * 이 분석이 쓰는 경로를 한 곳에 모은다.
 *
 * 🔴 **수집 원본은 저장소 밖에 둔다.** 저장소 안에는 발견(판별력·상관·계수)만 남고,
 *    가게별 평점·리뷰수 같은 원본은 `RAW_DIR` 밑에만 있다가 `destroy-raw.mjs` 로 사라진다.
 *    파기가 약속이 아니라 구조여야 하기 때문이다 — 지우는 명령이 처음부터 있고,
 *    원본이 저장소에 들어갈 경로 자체가 없다.
 */
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));

/** bigData/eval/popularity */
export const EVAL_DIR = here;

/** 저장소 안 — 여기에는 **발견만** 남는다 (커밋된다) */
export const OUT_DIR = path.join(here, "findings");

/**
 * 저장소 **밖** — 수집 원본이 잠깐 머무는 곳. 커밋되지 않는다.
 * `POPULARITY_RAW_DIR` 로 덮어쓸 수 있다.
 */
export const RAW_DIR =
  process.env.POPULARITY_RAW_DIR ??
  path.join(os.homedir(), "Desktop", "raw-popularity-2026-09-07");

/**
 * 이 분석이 **읽기만** 하는 입력들. 어느 것도 이 분석이 만들지 않는다.
 *
 * - `sbizCsv` — 상가정보 부산 (159,689행, 그중 대분류 "음식" 53,716곳). 커밋 금지 대상이라
 *   저장소에 없을 수 있다. 없으면 스크립트가 그렇게 말하고 멈춘다
 * - `matched` — 맛 정답지 158곳을 상가정보에 붙여 놓은 것. S15P21E201-712 의 산출물이고
 *   아직 `bigData/dev` 에 머지되지 않았다 (브랜치 `feat/bigData/S15P21E201-712-candidate-pool`)
 * - `tuneSplit` — 그 158곳 중 **조정용 111곳**. 🔴 판정용 47곳 파일은 여기 적지 않는다.
 *   경로가 없으면 실수로도 못 연다
 */
export function inputs(dataRoot, truthRoot = dataRoot) {
  return {
    sbizCsv: path.join(dataRoot, "bigData", "data", "raw", "poi", "sbiz-poi-busan-202606.csv"),
    subwayStation: path.join(dataRoot, "bigData", "data", "raw", "subway", "station.csv"),
    subwayRidership: path.join(dataRoot, "bigData", "data", "raw", "subway", "ridership.csv"),
    matched: path.join(truthRoot, "eval", "food-ranking", "data", "r4-matched.json"),
    tuneSplit: path.join(truthRoot, "eval", "food-ranking", "data", "r4-split-tune.json"),
  };
}

/**
 * 입력이 이 저장소 밖(다른 worktree)에 있을 때 쓴다.
 *
 * - `--repo <경로>` — 상가정보·지하철 원본이 있는 저장소 루트 (`POPULARITY_REPO`)
 * - `--truth-repo <경로>` — 정답지 붙임표가 있는 저장소 루트 (`POPULARITY_TRUTH_REPO`).
 *   안 주면 `--repo` 와 같은 곳으로 본다
 *
 * 🔴 이 둘이 갈라져 있는 것은 임시 상태다. 정답지 붙임표(S15P21E201-712)가
 *    `bigData/dev` 로 머지되면 한 경로로 합쳐진다.
 */
function argOr(argv, flag, env, fallback) {
  const i = argv.indexOf(flag);
  if (i >= 0 && argv[i + 1]) return path.resolve(argv[i + 1]);
  if (process.env[env]) return path.resolve(process.env[env]);
  return fallback;
}

export function repoRootFromArgs(argv) {
  return argOr(argv, "--repo", "POPULARITY_REPO", path.resolve(here, "..", "..", ".."));
}

export function truthRootFromArgs(argv) {
  return argOr(argv, "--truth-repo", "POPULARITY_TRUTH_REPO", repoRootFromArgs(argv));
}
