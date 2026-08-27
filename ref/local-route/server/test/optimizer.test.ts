import assert from "node:assert/strict";
import test from "node:test";
import { optimizeDayWithOrTools, resolveOrToolsRuntime } from "../src/services/optimizer.js";
import { scored } from "./fixtures.js";

const DAY_OPTIONS = {
  startLat: 35.115,
  startLng: 129.04,
  dayStart: "09:00",
  dayEnd: "20:00",
  dayBudget: 100_000,
  partySize: 1,
  hasCar: false,
  maxWalkingKm: 8,
  maxItems: 4,
  mustVisitPlaceIds: [] as string[],
};

const runtime = resolveOrToolsRuntime();

/**
 * OR-Tools 는 선택 의존성이다. optimizer.ts 는 실행할 수 없으면 null 을 돌려주고
 * schedule.ts 가 휴리스틱으로 폴백하도록 설계돼 있다.
 * 따라서 "환경이 없으면 실패"가 아니라 "환경이 있으면 제대로 푼다"를 검증해야 한다.
 * CI 는 requirements-optimizer.txt 를 설치하고 PYTHON_EXECUTABLE 을 넘기므로 여기서 실제로 실행된다.
 */
test(
  "OR-Tools 환경이 있으면 제약조건을 만족하는 경로 해를 반환한다",
  { skip: runtime.available ? false : `OR-Tools 실행 환경이 없어 건너뜁니다 (server/.venv 또는 ORTOOLS_PYTHON/PYTHON_EXECUTABLE 필요, 현재 source=${runtime.source})` },
  async () => {
    const result = await optimizeDayWithOrTools([
      scored({ id: "near", lat: 35.116, lng: 129.04 }),
      scored({ id: "restaurant", category: "RESTAURANT", lat: 35.117, lng: 129.04 }),
    ], DAY_OPTIONS);

    assert.ok(result, "OR-Tools 프로세스를 실행할 수 있어야 합니다.");
    assert.ok(["FEASIBLE", "OPTIMAL"].includes(result.status));
    assert.ok(result.orderedPlaceIds.length > 0);
  }
);

test("실행 환경이 없으면 예외 대신 null 을 돌려주어 휴리스틱 폴백을 허용한다", async () => {
  if (runtime.available) {
    // 환경이 있는 머신에서는 존재하지 않는 인터프리터를 지정해 폴백 경로만 확인한다.
    const previous = process.env.ORTOOLS_PYTHON;
    process.env.ORTOOLS_PYTHON = "/nonexistent/python-for-test";
    try {
      const result = await optimizeDayWithOrTools([scored({ id: "near" })], DAY_OPTIONS);
      assert.equal(result, null);
    } finally {
      if (previous === undefined) delete process.env.ORTOOLS_PYTHON;
      else process.env.ORTOOLS_PYTHON = previous;
    }
    return;
  }
  const result = await optimizeDayWithOrTools([scored({ id: "near" })], DAY_OPTIONS);
  assert.equal(result, null);
});

test("실행 환경 판별은 명시 지정을 프로젝트 venv보다 우선한다", () => {
  const previous = process.env.ORTOOLS_PYTHON;
  process.env.ORTOOLS_PYTHON = "/custom/python";
  try {
    const resolved = resolveOrToolsRuntime();
    assert.equal(resolved.python, "/custom/python");
    assert.equal(resolved.source, "ORTOOLS_PYTHON");
  } finally {
    if (previous === undefined) delete process.env.ORTOOLS_PYTHON;
    else process.env.ORTOOLS_PYTHON = previous;
  }
});
