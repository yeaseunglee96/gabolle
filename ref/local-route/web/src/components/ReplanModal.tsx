import { useState } from "react";
import type { PaceForecast } from "../types";

/**
 * 남은 일정 다시 짜기 - 사용자에게 선택지를 준다.
 *
 * 임의로 일정을 바꿔버리지 않는 것이 핵심이다(v2 §16.5 정직성 원칙).
 * 무엇이 왜 문제인지 보여주고, 어떻게 할지는 사용자가 고른다.
 */

export type ReplanStrategy = "KEEP_ALL" | "DROP_ONE" | "DEFER_LAST";

interface Props {
  forecast: PaceForecast;
  language?: "KO" | "EN";
  busy?: boolean;
  errorMessage?: string | null;
  onConfirm: (strategy: ReplanStrategy) => void;
  onClose: () => void;
}

export function ReplanModal({ forecast, language = "KO", busy, errorMessage, onConfirm, onClose }: Props) {
  const isEn = language === "EN";
  const [strategy, setStrategy] = useState<ReplanStrategy>(forecast.status === "AT_RISK" ? "DROP_ONE" : "KEEP_ALL");
  const remaining = forecast.totalCount - forecast.completedCount;

  const options: { value: ReplanStrategy; title: string; desc: string }[] = [
    {
      value: "KEEP_ALL",
      title: isEn ? "Keep every stop" : "모든 장소 유지",
      desc: isEn
        ? "Re-route from where you are now, keeping all remaining stops."
        : "지금 위치에서 다시 계산하되 남은 장소는 그대로 둡니다.",
    },
    {
      value: "DROP_ONE",
      title: isEn ? "Drop one stop" : "한 곳 덜어내기",
      desc: isEn
        ? "Remove the least essential stop so the rest fit comfortably."
        : "가장 덜 중요한 한 곳을 빼서 나머지에 여유를 만듭니다.",
    },
    {
      value: "DEFER_LAST",
      title: isEn ? "Move one to another day" : "한 곳 다음 기회로",
      desc: isEn
        ? "Shorten today and keep the pace relaxed."
        : "오늘을 짧게 마무리하고 여유롭게 갑니다.",
    },
  ];

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="replan-title" onClick={onClose}>
      <div className="modal-card replan-modal" onClick={(event) => event.stopPropagation()}>
        <header className="replan-head">
          <h3 id="replan-title">{isEn ? "Let's fix the rest of today" : "남은 일정을 다시 짤까요?"}</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label={isEn ? "Close" : "닫기"}>×</button>
        </header>

        <div className="replan-diagnosis">
          {forecast.atRisk.length > 0 ? (
            <ul>
              {forecast.atRisk.map((risk) => (
                <li key={risk.placeId}>
                  <strong>{risk.nameKo}</strong>
                  <span>
                    {isEn
                      ? `arrives ${risk.projectedArrival}, closes ${risk.closeTime}`
                      : `${risk.projectedArrival} 도착 예정 · ${risk.closeTime} 마감`}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p>
              {isEn
                ? `You're ${forecast.delayMinutes} minutes behind with ${remaining} stops left.`
                : `${forecast.delayMinutes}분 밀린 상태이고 남은 일정은 ${remaining}곳이에요.`}
            </p>
          )}
        </div>

        <div className="replan-options" role="radiogroup" aria-label={isEn ? "Replan option" : "재계산 방식"}>
          {options.map((option) => (
            <button
              type="button" key={option.value} role="radio" aria-checked={strategy === option.value}
              className={strategy === option.value ? "selected" : ""}
              onClick={() => setStrategy(option.value)}
            >
              <strong>{option.title}</strong>
              <small>{option.desc}</small>
            </button>
          ))}
        </div>

        {errorMessage && <div className="error-box" role="alert"><span>{errorMessage}</span></div>}

        <p className="replan-note">
          {isEn
            ? "Already-visited stops stay untouched. You can undo this afterwards."
            : "이미 다녀온 곳은 그대로 두고, 나중에 되돌릴 수 있어요."}
        </p>

        <div className="replan-actions">
          <button type="button" className="secondary-btn" onClick={onClose} disabled={busy}>
            {isEn ? "Not now" : "그냥 둘게요"}
          </button>
          <button type="button" className="primary-btn" onClick={() => onConfirm(strategy)} disabled={busy}>
            {busy ? (isEn ? "Recalculating…" : "계산 중…") : (isEn ? "Apply" : "이렇게 바꾸기")}
          </button>
        </div>
      </div>
    </div>
  );
}
