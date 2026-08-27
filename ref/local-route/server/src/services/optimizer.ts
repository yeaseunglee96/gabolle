import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { ScoredPlace } from "../types.js";
import { estimateTravelSync } from "./kakao.js";
import { estimatePlaceCost } from "./recommend.js";

export interface OptimizerResult {
  status: "FEASIBLE" | "OPTIMAL" | "INFEASIBLE";
  orderedPlaceIds: string[];
  arrivals: number[];
}

function parseTime(value: string) {
  const [hour, minute] = value.split(":").map(Number);
  return hour * 60 + minute;
}

export interface OrToolsRuntime {
  python: string;
  script: string;
  /** 실행을 시도할 만한 환경인지. false 면 호출부는 곧바로 휴리스틱으로 폴백한다. */
  available: boolean;
  /** 어떤 경로로 파이썬을 정했는지(진단·테스트 메시지용). */
  source: "ORTOOLS_PYTHON" | "PYTHON_EXECUTABLE" | "PROJECT_VENV" | "NONE";
}

/**
 * OR-Tools 실행 환경을 한 곳에서 판별한다.
 *
 * 우선순위: 명시 지정(ORTOOLS_PYTHON → PYTHON_EXECUTABLE) → 프로젝트 venv.
 * venv 경로는 플랫폼마다 다르다(Windows: .venv/Scripts/python.exe, 그 외: .venv/bin/python).
 * OR-Tools 는 선택 의존성이므로 없으면 available:false 를 돌려주고 호출부가 휴리스틱을 쓴다.
 */
export function resolveOrToolsRuntime(): OrToolsRuntime {
  const moduleRoot = fileURLToPath(new URL("../../", import.meta.url));
  const serverRoot = [moduleRoot, process.cwd(), path.resolve(process.cwd(), "server")]
    .find((candidate) => existsSync(path.resolve(candidate, "solver", "route_optimizer.py"))) ?? moduleRoot;
  const script = path.resolve(serverRoot, "solver", "route_optimizer.py");
  const hasScript = existsSync(script);

  const explicit = process.env.ORTOOLS_PYTHON || process.env.PYTHON_EXECUTABLE || null;
  if (explicit) {
    const source = process.env.ORTOOLS_PYTHON ? "ORTOOLS_PYTHON" : "PYTHON_EXECUTABLE";
    return { python: explicit, script, available: hasScript, source };
  }

  const venvPython = process.platform === "win32"
    ? path.resolve(serverRoot, ".venv", "Scripts", "python.exe")
    : path.resolve(serverRoot, ".venv", "bin", "python");
  if (existsSync(venvPython)) return { python: venvPython, script, available: hasScript, source: "PROJECT_VENV" };

  return { python: venvPython, script, available: false, source: "NONE" };
}

export async function optimizeDayWithOrTools(
  places: ScoredPlace[],
  options: {
    startLat: number;
    startLng: number;
    dayStart: string;
    dayEnd: string;
    dayBudget: number;
    partySize: number;
    hasCar: boolean;
    maxWalkingKm: number;
    maxItems: number;
    maxFoodStops: number;
    maxRestaurantStops: number;
    travelMinutes?: number[][];
    distanceMeters?: number[][];
    mustVisitPlaceIds: string[];
  }
): Promise<OptimizerResult | null> {
  const maxFoodStops = options.maxFoodStops ?? Math.max(1, Math.floor(options.maxItems / 2));
  const maxRestaurantStops = options.maxRestaurantStops ?? (options.maxItems >= 5 ? 2 : 1);
  const nodes = [{ lat: options.startLat, lng: options.startLng }, ...places];
  const travelMinutes = options.travelMinutes ?? nodes.map((from) => nodes.map((to) => estimateTravelSync(from.lat, from.lng, to.lat, to.lng, options.hasCar).durationMin));
  const distanceMeters = options.distanceMeters ?? nodes.map((from) => nodes.map((to) => estimateTravelSync(from.lat, from.lng, to.lat, to.lng, options.hasCar).distanceM));
  const payload = {
    places: places.map((place) => ({
      id: place.id,
      category: place.category,
      score: place.score,
      stayMinutes: place.recommendedStayMin,
      openMin: parseTime(place.openTime),
      closeMin: parseTime(place.closeTime),
      cost: estimatePlaceCost(place.priceTier, options.partySize),
      mustVisit: options.mustVisitPlaceIds.includes(place.id),
    })),
    travelMinutes,
    distanceMeters,
    dayStartMin: parseTime(options.dayStart),
    dayEndMin: parseTime(options.dayEnd),
    dayBudget: Math.floor(options.dayBudget),
    hasCar: options.hasCar,
    maxWalkingM: Math.floor(options.maxWalkingKm * 1000),
    maxItems: options.maxItems,
    maxFoodStops: Math.max(
      maxFoodStops,
      places.filter((place) => options.mustVisitPlaceIds.includes(place.id) && ["RESTAURANT", "CAFE"].includes(place.category)).length,
    ),
    maxRestaurantStops: Math.max(
      maxRestaurantStops,
      places.filter((place) => options.mustVisitPlaceIds.includes(place.id) && place.category === "RESTAURANT").length,
    ),
    timeoutMs: 1500,
  };

  const runtime = resolveOrToolsRuntime();
  // 실행 환경이 아예 없으면 프로세스를 띄워 ENOENT 를 기다릴 이유가 없다. 바로 폴백시킨다.
  if (!runtime.available) return null;
  const { python, script } = runtime;
  return new Promise((resolve) => {
    const child = spawn(python, [script], { stdio: ["pipe", "pipe", "pipe"], windowsHide: true });
    let stdout = "";
    let stderr = "";
    const timeout = setTimeout(() => child.kill(), 3_000);
    child.stdout.on("data", (chunk) => { stdout += chunk.toString(); });
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", (error) => {
      clearTimeout(timeout);
      console.warn(`[optimizer] OR-Tools process failed to start (${python}): ${error.message}`);
      resolve(null);
    });
    child.on("close", (code) => {
      clearTimeout(timeout);
      if (code !== 0 || !stdout) {
        console.warn(`[optimizer] OR-Tools process exited with code ${code}: ${stderr.trim() || stdout.trim() || "no output"}`);
        return resolve(null);
      }
      try {
        const result = JSON.parse(stdout) as OptimizerResult;
        if (result.status !== "INFEASIBLE" && result.orderedPlaceIds.length === 0 && places.length > 0) {
          console.warn(`[optimizer] empty solution for ${places.length} candidates`, payload.places.map((place, index) => ({ id: place.id, open: place.openMin, close: place.closeMin, cost: place.cost, distanceFromOrigin: distanceMeters[0][index + 1] })));
        }
        resolve(["FEASIBLE", "OPTIMAL", "INFEASIBLE"].includes(result.status) ? result : null);
      } catch {
        console.warn(`[optimizer] invalid OR-Tools output: ${stdout.slice(0, 200)}`);
        resolve(null);
      }
    });
    child.stdin.end(JSON.stringify(payload));
  });
}
