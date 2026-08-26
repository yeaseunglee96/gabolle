import type { ItineraryJob } from "../types";

const STEPS = [
  { stage: "COLLECTING", label: "여행 조건과 장소 후보 수집" },
  { stage: "SCORING", label: "조건 필터링과 추천 점수 계산" },
  { stage: "OPTIMIZING", label: "날짜별 이동 순서 최적화" },
  { stage: "VALIDATING", label: "예산·운영시간 최종 검증" },
] as const;

export function GenerationProgress({ job }: { job?: ItineraryJob }) {
  const active = Math.max(0, STEPS.findIndex((step) => step.stage === job?.stage));
  const progress = job?.progress ?? 5;
  return <main className="generation-screen" aria-live="polite" aria-busy="true">
    <div className="generation-card"><div className="route-loader" aria-hidden="true"><span /><span /><span /></div><span className="eyebrow">일정 생성 · {progress}%</span><h1>실행 가능한 순서를 찾고 있어요</h1><p>서버의 실제 생성 단계와 진행 상태를 표시하고 있습니다.</p><progress max={100} value={progress} aria-label={`일정 생성 ${progress}%`} /><ol>{STEPS.map((step, index) => <li key={step.stage} className={index < active ? "done" : index === active ? "active" : ""}><span>{index < active ? "✓" : index + 1}</span><b>{step.label}</b>{index === active && <small>확인 중…</small>}</li>)}</ol></div>
  </main>;
}
