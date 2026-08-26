import type { RhythmProfile } from "../types";

/**
 * 개인 체류 리듬 브리핑 - "어제 보니 카페에서 84분 계셨어요".
 *
 * 페이스 러닝에서 사용자가 가장 또렷하게 기억하는 지점이다.
 * 앱이 나를 알아본다는 감각은 추천 정확도보다 강한 인상을 남긴다.
 */

const CATEGORY_LABEL_KO: Record<string, string> = {
  TOURIST: "관광지", RESTAURANT: "식당", CAFE: "카페", LODGING: "숙소", FESTIVAL: "축제", SOUVENIR: "기념품샵",
};
const CATEGORY_LABEL_EN: Record<string, string> = {
  TOURIST: "attractions", RESTAURANT: "restaurants", CAFE: "cafes", LODGING: "lodging", FESTIVAL: "festivals", SOUVENIR: "souvenir shops",
};

function categoryLabel(code: string, isEn: boolean) {
  return (isEn ? CATEGORY_LABEL_EN[code] : CATEGORY_LABEL_KO[code]) ?? code;
}

interface Props {
  profile: RhythmProfile;
  language?: "KO" | "EN";
  /** 학습된 리듬으로 남은 일정을 다시 짜기. */
  onApply?: () => void;
  applying?: boolean;
  onDismiss?: () => void;
}

export function RhythmBriefing({ profile, language = "KO", onApply, applying, onDismiss }: Props) {
  const isEn = language === "EN";
  if (!profile.hasProfile || profile.observations.length === 0) return null;

  const top = profile.observations[0];
  const longer = top.deltaMinutes > 0;

  return (
    <section className="rhythm-briefing" aria-labelledby="rhythm-title">
      <div className="rhythm-head">
        <span className="rhythm-eyebrow">{isEn ? "YOUR RHYTHM" : "당신의 여행 리듬"}</span>
        <h3 id="rhythm-title">
          {isEn
            ? `You spend about ${top.averageActualMinutes} min at ${categoryLabel(top.category, true)}`
            : `${categoryLabel(top.category, false)}에서 평균 ${top.averageActualMinutes}분 머무시네요`}
        </h3>
        <p>
          {isEn
            ? `We had planned ${top.averagePlannedMinutes} min — ${longer ? "you stay longer" : "you move faster"} than we assumed. Measured from ${profile.totalSamples} verified visits.`
            : `저희 예상은 ${top.averagePlannedMinutes}분이었어요. 생각보다 ${longer ? "오래 머무시는" : "빠르게 움직이시는"} 편이에요. 실제 방문 ${profile.totalSamples}건으로 계산했습니다.`}
        </p>
      </div>

      <ul className="rhythm-list">
        {profile.observations.slice(0, 3).map((observation) => (
          <li key={observation.category}>
            <span className="rhythm-cat">{categoryLabel(observation.category, isEn)}</span>
            <span className="rhythm-bar-track" aria-hidden="true">
              <i style={{ width: `${Math.min(100, (observation.scale / 2) * 100)}%` }} />
            </span>
            <span className={`rhythm-delta ${observation.deltaMinutes > 0 ? "up" : "down"}`}>
              {observation.deltaMinutes > 0 ? "+" : ""}{observation.deltaMinutes}
              {isEn ? " min" : "분"}
            </span>
          </li>
        ))}
      </ul>

      {onApply && (
        <div className="rhythm-actions">
          <button type="button" className="primary-btn" onClick={onApply} disabled={applying}>
            {applying
              ? (isEn ? "Rebuilding…" : "다시 짜는 중…")
              : (isEn ? "Rebuild today with my rhythm" : "이 리듬으로 오늘 일정 다시 짜기")}
          </button>
          {onDismiss && (
            <button type="button" className="secondary-btn" onClick={onDismiss} disabled={applying}>
              {isEn ? "Keep as is" : "원래대로 할래요"}
            </button>
          )}
        </div>
      )}
    </section>
  );
}
