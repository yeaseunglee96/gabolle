import type { PaceForecast } from "../types";

/**
 * 지연 예보 - 일기예보처럼 항상 떠 있는 한 줄.
 *
 * "지금 밀리고 있다"는 사실을 사용자가 스스로 알아차리기를 기다리지 않는다.
 * 마지막 가게가 닫힌 뒤에 아는 것과 두 시간 전에 아는 것은 완전히 다른 여행이 된다.
 */

const TONE: Record<PaceForecast["status"], { icon: string; className: string }> = {
  NOT_STARTED: { icon: "🕘", className: "idle" },
  ON_TIME: { icon: "🟢", className: "ok" },
  SLIGHTLY_BEHIND: { icon: "🟡", className: "warn" },
  BEHIND: { icon: "🟠", className: "late" },
  AT_RISK: { icon: "🔴", className: "risk" },
  DONE: { icon: "🎉", className: "done" },
};

function headline(forecast: PaceForecast, isEn: boolean): string {
  const delay = Math.abs(forecast.delayMinutes);
  switch (forecast.status) {
    case "NOT_STARTED":
      return isEn ? "Today's plan hasn't started yet" : "오늘 일정이 아직 시작 전이에요";
    case "DONE":
      return isEn ? "All done for today" : "오늘 일정을 모두 마쳤어요";
    case "AT_RISK": {
      const first = forecast.atRisk[0];
      if (!first) return isEn ? "Some stops are at risk" : "일정에 무리가 있어요";
      return isEn
        ? `${first.nameKo} closes at ${first.closeTime} — you'd arrive ${first.projectedArrival}`
        : `${first.nameKo}는 ${first.closeTime}에 닫는데 ${first.projectedArrival} 도착 예정이에요`;
    }
    case "BEHIND":
      return isEn ? `Running ${delay} min behind` : `${delay}분 밀리고 있어요`;
    case "SLIGHTLY_BEHIND":
      return isEn ? `A little behind (${delay} min)` : `조금 밀리고 있어요 (${delay}분)`;
    case "ON_TIME":
    default:
      return forecast.delayMinutes < -9
        ? (isEn ? `Ahead of plan by ${delay} min` : `계획보다 ${delay}분 빨라요`)
        : (isEn ? "On schedule" : "계획대로 가고 있어요");
  }
}

interface Props {
  forecast: PaceForecast;
  language?: "KO" | "EN";
  /** 남은 일정 다시 짜기. 제공하지 않으면 버튼을 감춘다(열람자 등). */
  onReplan?: () => void;
  replanning?: boolean;
}

export function PaceForecastBar({ forecast, language = "KO", onReplan, replanning }: Props) {
  const isEn = language === "EN";
  const tone = TONE[forecast.status];
  const needsAction = forecast.status === "AT_RISK" || forecast.status === "BEHIND";

  return (
    <div className={`pace-bar ${tone.className}`} role="status" aria-live="polite">
      <span className="pace-icon" aria-hidden="true">{tone.icon}</span>
      <div className="pace-copy">
        <strong>{headline(forecast, isEn)}</strong>
        {forecast.projectedEndTime && forecast.status !== "DONE" && (
          <small>
            {isEn
              ? `Projected finish ${forecast.projectedEndTime} · planned ${forecast.plannedEndTime} · estimate`
              : `이대로면 ${forecast.projectedEndTime} 종료 예상 · 계획 ${forecast.plannedEndTime} · 추정`}
          </small>
        )}
      </div>
      <span className="pace-progress">{forecast.completedCount}/{forecast.totalCount}</span>
      {needsAction && onReplan && (
        <button type="button" className="pace-replan-btn" onClick={onReplan} disabled={replanning}>
          {replanning ? (isEn ? "Recalculating…" : "다시 짜는 중…") : (isEn ? "Fix my plan" : "남은 일정 다시 짜기")}
        </button>
      )}
    </div>
  );
}
