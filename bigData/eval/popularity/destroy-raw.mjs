/**
 * 수집 원본을 지운다. **명령 하나.**
 *
 *   node bigData/eval/popularity/destroy-raw.mjs
 *
 * 지우는 것은 `paths.mjs` 의 `RAW_DIR` 폴더 통째다 — 가게별 평점·리뷰수·수집 응답이
 * 거기 말고 다른 곳에 없기 때문에, 이 한 줄이 파기의 전부다.
 *
 * 🔴 이 스크립트는 **수집 코드보다 먼저** 만들었다. 파기를 나중에 붙이면 그건 약속이지
 *    구조가 아니다 — 약속은 잊히고, 구조는 잊혀도 남는다.
 *
 * 저장소 안 `findings/` 는 **지우지 않는다.** 거기에는 발견(판별력·표본 통계)만 있고
 * 가게별 원본이 없다. 그것까지 지우려면 손으로 지운다.
 */
import fs from "node:fs";
import { RAW_DIR } from "./paths.mjs";

function walkCount(dir) {
  let files = 0;
  let bytes = 0;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = `${dir}/${e.name}`;
    if (e.isDirectory()) {
      const sub = walkCount(p);
      files += sub.files;
      bytes += sub.bytes;
    } else {
      files += 1;
      bytes += fs.statSync(p).size;
    }
  }
  return { files, bytes };
}

if (!fs.existsSync(RAW_DIR)) {
  console.log(`이미 없다: ${RAW_DIR}`);
  process.exit(0);
}

const { files, bytes } = walkCount(RAW_DIR);
fs.rmSync(RAW_DIR, { recursive: true, force: true });

if (fs.existsSync(RAW_DIR)) {
  console.error(`🔴 지우지 못했다: ${RAW_DIR}`);
  process.exit(1);
}

console.log(`지웠다: ${RAW_DIR}`);
console.log(`  파일 ${files}개 · ${(bytes / 1024).toFixed(1)} KB`);
