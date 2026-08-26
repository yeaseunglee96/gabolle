#!/usr/bin/env node
/**
 * 백엔드(:4000)와 프런트엔드(:5173)를 한 번에 실행한다.
 *
 * 설계 원칙
 *  1) 한쪽이 죽어도 나머지는 살린다.
 *     이전 버전은 서버가 죽으면 웹까지 같이 종료시켰다. 그 결과 서버 기동에
 *     실패하면 브라우저에는 "연결할 수 없음"만 뜨고 원인이 화면에 남지 않았다.
 *     이제 서버가 죽어도 웹은 계속 살아 있고, 웹 화면의 경고 배너가 상황을 알린다.
 *  2) 실패를 눈에 띄게 만든다. 종료 코드와 마지막 출력 몇 줄을 다시 보여준다.
 *  3) 접속 주소를 마지막에 한 번 더 크게 찍는다. Vite 출력이 로그에 묻히지 않도록.
 */
import { spawn } from "node:child_process";
import { createConnection } from "node:net";

const RESET = "[0m";
const C = { server: "[36m", web: "[35m", ok: "[32m", warn: "[33m", err: "[31m", dim: "[90m" };

const TARGETS = [
  { name: "server", color: C.server, workspace: "server", port: 4000 },
  { name: "web", color: C.web, workspace: "web", port: 5173 },
];

/** 포트가 이미 사용 중인지 확인한다. 사용 중이면 true. */
function portInUse(port) {
  return new Promise((resolve) => {
    const socket = createConnection({ host: "127.0.0.1", port });
    const done = (result) => { socket.destroy(); resolve(result); };
    socket.setTimeout(700);
    socket.on("connect", () => done(true));
    socket.on("timeout", () => done(false));
    socket.on("error", () => done(false));
  });
}

const children = new Map();
const lastLines = new Map();   // name -> 최근 출력 12줄
let shuttingDown = false;

function shutdown(code) {
  if (shuttingDown) return;
  shuttingDown = true;
  for (const child of children.values()) {
    try { child.kill(); } catch { /* 이미 종료됨 */ }
  }
  process.exit(code);
}

function start(target) {
  const child = spawn("npm", ["run", "dev", "--workspace", target.workspace], {
    shell: process.platform === "win32",
    stdio: ["ignore", "pipe", "pipe"],
  });

  const prefix = `${target.color}[${target.name}]${RESET} `;
  const recent = [];
  lastLines.set(target.name, recent);

  const pipe = (stream, out) => {
    let buffered = "";
    stream.setEncoding("utf8");
    stream.on("data", (chunk) => {
      buffered += chunk;
      const lines = buffered.split("\n");
      buffered = lines.pop() ?? "";
      for (const line of lines) {
        recent.push(line);
        if (recent.length > 12) recent.shift();
        out.write(prefix + line + "\n");
      }
    });
  };
  pipe(child.stdout, process.stdout);
  pipe(child.stderr, process.stderr);

  child.on("error", (error) => {
    process.stderr.write(`${prefix}${C.err}실행할 수 없습니다: ${error.message}${RESET}\n`);
  });

  child.on("exit", (code) => {
    if (shuttingDown) return;
    children.delete(target.name);

    const survivors = [...children.keys()];
    process.stderr.write(
      `\n${C.err}${"─".repeat(64)}\n` +
      ` ${target.name} 프로세스가 종료되었습니다 (exit ${code}).\n` +
      `${"─".repeat(64)}${RESET}\n`
    );
    for (const line of recent) process.stderr.write(`${C.dim}  | ${line}${RESET}\n`);

    if (target.name === "server") {
      process.stderr.write(
        `\n${C.warn} 웹(:5173)은 계속 실행합니다. 브라우저 화면은 뜨지만\n` +
        ` 장소 조회·일정 생성 등 API 기능은 동작하지 않습니다.\n` +
        ` 위 로그에서 서버가 죽은 이유를 확인한 뒤 다시 실행하세요.\n` +
        ` 환경 점검:  npm run doctor${RESET}\n\n`
      );
    }

    if (survivors.length === 0) shutdown(code ?? 1);
  });

  children.set(target.name, child);
}

// ── 사전 점검 ────────────────────────────────────────────────
for (const target of TARGETS) {
  if (await portInUse(target.port)) {
    process.stderr.write(
      `${C.warn}[사전 점검] 포트 ${target.port} 가 이미 사용 중입니다 (${target.name}).\n` +
      `           이전에 띄운 프로세스가 남아 있을 수 있습니다.\n` +
      `           Windows:  netstat -ano | findstr :${target.port}   →  taskkill /PID <PID> /F${RESET}\n`
    );
  }
}

for (const target of TARGETS) start(target);

process.on("SIGINT", () => shutdown(0));
process.on("SIGTERM", () => shutdown(0));

process.stdout.write(
  `\n${C.ok}▶ server(:4000) + web(:5173) 실행 중 · 중지하려면 Ctrl+C${RESET}\n` +
  `${C.dim}   웹  http://localhost:5173\n` +
  `   API http://localhost:4000/health${RESET}\n\n`
);
