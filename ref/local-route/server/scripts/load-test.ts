const baseUrl = process.env.LOAD_TEST_URL ?? "http://localhost:4000";
const total = Math.max(10, Number(process.env.LOAD_TEST_REQUESTS ?? 250));
const concurrency = Math.max(1, Number(process.env.LOAD_TEST_CONCURRENCY ?? 20));
const paths = ["/health", "/api/events/catalog", "/api/analytics/kpis?hours=24", "/api/places?category=CAFE"];
const latencies: number[] = [];
let failures = 0;
let cursor = 0;

async function worker() {
  while (true) {
    const index = cursor++;
    if (index >= total) return;
    const started = performance.now();
    try {
      const response = await fetch(`${baseUrl}${paths[index % paths.length]}`);
      if (!response.ok) failures++;
      await response.arrayBuffer();
    } catch { failures++; }
    latencies.push(performance.now() - started);
  }
}

const wallStarted = performance.now();
await Promise.all(Array.from({ length: concurrency }, worker));
const wallSeconds = (performance.now() - wallStarted) / 1000;
latencies.sort((a, b) => a - b);
const percentile = (p: number) => latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * p))] ?? 0;
const result = { baseUrl, total, concurrency, requestsPerSecond: Math.round(total / wallSeconds), errorRate: failures / total, p50Ms: Math.round(percentile(0.5)), p95Ms: Math.round(percentile(0.95)), p99Ms: Math.round(percentile(0.99)) };
console.log(JSON.stringify(result));
if (result.errorRate > 0.01 || result.p95Ms > 500) process.exitCode = 1;
