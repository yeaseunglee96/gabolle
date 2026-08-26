#!/usr/bin/env node
/**
 * 실행 환경 점검. 화면이 안 뜰 때 가장 먼저 실행한다.
 *
 *   npm run doctor
 *
 * 원인을 추측하지 않고 하나씩 확인해 어디가 막혔는지 짚어준다.
 */
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { createConnection } from "node:net";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const RESET = "[0m", OK = "[32m", BAD = "[31m", WARN = "[33m", DIM = "[90m";

const results = [];
const pass = (name, detail = "") => results.push({ level: "ok", name, detail });
const warn = (name, detail = "") => results.push({ level: "warn", name, detail });
const fail = (name, detail = "") => results.push({ level: "fail", name, detail });

// 1. Node 버전
const major = Number(process.versions.node.split(".")[0]);
major >= 20
  ? pass("Node 버전", `v${process.versions.node}`)
  : fail("Node 버전", `v${process.versions.node} · 20 이상이 필요합니다`);

// 2. 의존성 설치 여부
existsSync(join(ROOT, "node_modules"))
  ? pass("node_modules", "설치됨")
  : fail("node_modules", "없음 · 프로젝트 루트에서 npm install 을 실행하세요");

// 3. 현재 플랫폼용 네이티브 바이너리
const platformKey = process.platform === "win32" ? "win32" : process.platform === "darwin" ? "darwin" : "linux";
const checkPlatformPkg = (label, dir, needle) => {
  const base = join(ROOT, "node_modules", dir);
  if (!existsSync(base)) return warn(label, `${dir} 없음 (건너뜀)`);
  const found = readdirSync(base).filter((entry) => entry.includes(needle));
  found.length
    ? pass(label, found.join(", "))
    : fail(label, `${platformKey} 용 바이너리가 없습니다 · npm install 을 이 컴퓨터에서 다시 실행하세요`);
};
checkPlatformPkg("esbuild 바이너리", "@esbuild", platformKey);
checkPlatformPkg("rolldown 바이너리", "@rolldown", platformKey);

// 4. Prisma 클라이언트 · 엔진
const prismaDir = join(ROOT, "node_modules", ".prisma", "client");
if (!existsSync(prismaDir)) {
  fail("Prisma 클라이언트", "생성되지 않음 · npm run prisma:generate --workspace server");
} else {
  const engines = readdirSync(prismaDir).filter((f) => f.endsWith(".node"));
  const wantsWindows = process.platform === "win32";
  const matched = engines.filter((f) => (wantsWindows ? f.includes("windows") : !f.includes("windows")));
  matched.length
    ? pass("Prisma 엔진", matched.join(", "))
    : fail("Prisma 엔진", `현재 OS(${process.platform})용 엔진 없음 · 발견: ${engines.join(", ") || "없음"} · npm run prisma:generate --workspace server`);
}

// 5. react-router-dom 해석 가능 여부
try {
  createRequire(join(ROOT, "web", "package.json")).resolve("react-router-dom");
  pass("react-router-dom", "해석 가능");
} catch {
  fail("react-router-dom", "web 에서 찾을 수 없음 · npm install 을 실행하세요");
}

// 6. 환경변수 파일
for (const [label, file, required] of [
  ["server/.env", join(ROOT, "server", ".env"), ["DATABASE_URL"]],
  ["web/.env", join(ROOT, "web", ".env"), []],
]) {
  if (!existsSync(file)) { warn(label, "없음 · .env.example 을 복사해 만드세요"); continue; }
  const text = readFileSync(file, "utf8");
  const missing = required.filter((key) => !new RegExp(`^\\s*${key}\\s*=\\s*\\S`, "m").test(text));
  missing.length ? fail(label, `필수 값 없음: ${missing.join(", ")}`) : pass(label, "존재");
}

// 7. 개발 DB 파일 (SQLite 사용 중일 때)
const dbUrl = existsSync(join(ROOT, "server", ".env"))
  ? (readFileSync(join(ROOT, "server", ".env"), "utf8").match(/^\s*DATABASE_URL\s*=\s*"?([^"\n\r]+)"?/m)?.[1] ?? "")
  : "";
if (dbUrl.startsWith("file:")) {
  const dbPath = join(ROOT, "server", "prisma", dbUrl.replace("file:", "").replace(/^\.\//, ""));
  existsSync(dbPath)
    ? pass("개발 DB", dbUrl)
    : fail("개발 DB", `${dbUrl} 파일이 없습니다 · npx prisma migrate deploy 후 npm run seed --workspace server`);
} else if (dbUrl) {
  pass("DATABASE_URL", dbUrl.replace(/:[^:@/]+@/, ":***@"));
}

// 8. 포트 점유 상태
const portInUse = (port) => new Promise((resolve) => {
  const socket = createConnection({ host: "127.0.0.1", port });
  const done = (r) => { socket.destroy(); resolve(r); };
  socket.setTimeout(700);
  socket.on("connect", () => done(true));
  socket.on("timeout", () => done(false));
  socket.on("error", () => done(false));
});
for (const [port, who] of [[4000, "API 서버"], [5173, "웹 개발 서버"]]) {
  (await portInUse(port))
    ? warn(`포트 ${port}`, `이미 사용 중 (${who}) · 이전 프로세스가 남아 있다면 종료하세요`)
    : pass(`포트 ${port}`, "비어 있음");
}

// ── 출력 ────────────────────────────────────────────────
console.log("\nLOCAL ROUTE 환경 점검\n" + "─".repeat(60));
for (const r of results) {
  const mark = r.level === "ok" ? `${OK}  OK ${RESET}` : r.level === "warn" ? `${WARN} 주의${RESET}` : `${BAD} 실패${RESET}`;
  console.log(`${mark}  ${r.name.padEnd(22)} ${DIM}${r.detail}${RESET}`);
}
const failed = results.filter((r) => r.level === "fail");
console.log("─".repeat(60));
if (failed.length) {
  console.log(`${BAD}${failed.length}건이 실패했습니다. 위 안내를 먼저 처리하세요.${RESET}\n`);
  process.exitCode = 1;
} else {
  console.log(`${OK}이상 없습니다. npm run dev 로 실행하세요.${RESET}`);
  console.log(`${DIM}   웹  http://localhost:5173${RESET}\n`);
}
