import assert from "node:assert/strict";
import test from "node:test";
import { getTravelCacheStats, getTravelEstimate, getTravelMatrix } from "../src/services/kakao.js";

test("동일 이동 구간을 TTL 캐시에 저장하고 행렬을 생성한다", async () => {
  const before = getTravelCacheStats().entries;
  const first = await getTravelEstimate(35.1152, 129.0403, 35.116, 129.041, false);
  const second = await getTravelEstimate(35.1152, 129.0403, 35.116, 129.041, false);
  assert.deepEqual(second, first);
  assert.ok(getTravelCacheStats().entries >= before + 1);
  const matrix = await getTravelMatrix([
    { lat: 35.1152, lng: 129.0403 },
    { lat: 35.116, lng: 129.041 },
    { lat: 35.117, lng: 129.042 },
  ], false);
  assert.equal(matrix.travelMinutes.length, 3);
  assert.equal(matrix.travelMinutes[0][0], 0);
  assert.ok(matrix.travelMinutes[0][1] > 0);
});
