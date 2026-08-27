/**
 * 페이지 트리(routeTree.ts)를 사람이 읽는 형태로 출력한다.
 *
 *   node web/scripts/print-route-tree.mjs tree   # 들여쓰기 트리
 *   node web/scripts/print-route-tree.mjs table  # 마크다운 표 (기획서에 붙여넣기)
 *   node web/scripts/print-route-tree.mjs stats  # 마일스톤·상태별 집계
 *
 * 기획서의 페이지 트리 장은 이 스크립트 출력으로 갱신한다.
 * 문서와 코드가 어긋나는 것을 막기 위한 장치다.
 */
import { readFileSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { transformSync } from "esbuild";

const src = fileURLToPath(new URL("../src/routes/routeTree.ts", import.meta.url));
const js = transformSync(readFileSync(src, "utf8"), { loader: "ts", format: "esm" }).code;
const out = join(mkdtempSync(join(tmpdir(), "route-tree-")), "routeTree.mjs");
writeFileSync(out, js);
const { ROUTE_TREE, flattenRoutes } = await import(pathToFileURL(out).href);

const MILESTONE = { M1_WEB: "9/1 웹", M2_APP: "9/9 앱", M3_REVIEW: "심사 필수", M4_LATER: "후속" };
const STATUS = { DONE: "완료", PARTIAL: "부분", TODO: "예정" };
const PLATFORM = { BOTH: "웹·앱", WEB: "웹", APP: "앱" };

const mode = process.argv[2] ?? "tree";
const rows = flattenRoutes(ROUTE_TREE);

if (mode === "tree") {
  for (const node of rows) {
    const pad = "  ".repeat(node.depth);
    console.log(`${pad}${node.fullPath.padEnd(46 - pad.length)} ${node.titleKo}  [${STATUS[node.status]} · ${MILESTONE[node.milestone]} · ${node.owners.join("/")}]`);
  }
} else if (mode === "table") {
  console.log("| 경로 | 화면 | 플랫폼 | 권한 | 담당 | 마일스톤 | 상태 |");
  console.log("| --- | --- | :-: | :-: | :-: | :-: | :-: |");
  for (const node of rows) {
    const indent = "  ".repeat(node.depth);
    console.log(`| \`${node.fullPath}\` | ${indent}${node.titleKo} | ${PLATFORM[node.platform]} | ${node.access} | ${node.owners.join("·")} | ${MILESTONE[node.milestone]} | ${STATUS[node.status]} |`);
  }
} else if (mode === "stats") {
  const by = (key) => rows.reduce((acc, node) => { acc[node[key]] = (acc[node[key]] ?? 0) + 1; return acc; }, {});
  console.log("총 화면 수:", rows.length);
  console.log("마일스톤별:", by("milestone"));
  console.log("상태별:", by("status"));
  console.log("플랫폼별:", by("platform"));
} else {
  console.error("사용법: node web/scripts/print-route-tree.mjs [tree|table|stats]");
  process.exit(1);
}
