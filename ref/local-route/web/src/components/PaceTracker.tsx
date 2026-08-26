import type { ItineraryDayOutput, PaceForecast } from "../types";

/**
 * 실측 기록 컨트롤 - "지금 도착했어요 / 이제 이동할게요".
 *
 * 페이스 러닝의 모든 계산은 이 두 번의 탭에서 나온다. 다른 입력은 요구하지 않는다.
 */

interface Props {
  day: ItineraryDayOutput;
  forecast: PaceForecast;
  language?: "KO" | "EN";
  busy?: boolean;
  onArrive: (itemId: string) => void;
  onDepart: (itemId: string) => void;
}

export function PaceTracker({ day, forecast, language = "KO", busy, onArrive, onDepart }: Props) {
  const isEn = language === "EN";
  const ordered = [...day.items].sort((a, b) => a.seqOrder - b.seqOrder);
  const staying = ordered.find((item) => item.actualArrival && !item.actualDeparture) ?? null;
  const next = ordered.find((item) => !item.actualArrival) ?? null;
  const target = staying ?? next;
  if (!target?.itemId) return null;

  const projected = forecast.projectedArrivals.find((entry) => entry.seqOrder === target.seqOrder);
  const drift = staying ? null : projected && projected.projectedArrival !== target.plannedArrival ? projected.projectedArrival : null;

  return (
    <div className="pace-tracker">
      <div className="pace-tracker-info">
        <span className="pace-tracker-label">
          {staying ? (isEn ? "Currently at" : "지금 있는 곳") : (isEn ? "Up next" : "다음 목적지")}
        </span>
        <strong>{isEn ? (target.nameEn ?? target.nameKo) : target.nameKo}</strong>
        <small>
          {staying
            ? (isEn
                ? `Arrived ${staying.actualArrival} · planned ${staying.plannedArrival}`
                : `${staying.actualArrival} 도착 · 계획 ${staying.plannedArrival}`)
            : (isEn
                ? `Planned ${target.plannedArrival}${drift ? ` · now expected ${drift}` : ""}`
                : `계획 ${target.plannedArrival}${drift ? ` · 현재 예상 ${drift}` : ""}`)}
        </small>
      </div>
      {staying
        ? <button type="button" className="secondary-btn" disabled={busy} onClick={() => onDepart(staying.itemId!)}>{isEn ? "Leaving now" : "이제 이동할게요"}</button>
        : <button type="button" className="primary-btn" disabled={busy} onClick={() => onArrive(target.itemId!)}>{isEn ? "I'm here" : "지금 도착했어요"}</button>}
    </div>
  );
}
