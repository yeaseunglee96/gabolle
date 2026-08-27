import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { GenerationProgress } from "../components/GenerationProgress";
import { generateItinerary } from "../api/client";
import type { ItineraryJob } from "../types";
import { paths } from "../routes/paths";

/**
 * /generating/:tripId — 일정 생성 진행 화면
 * 추천·최적화 job 의 진행률을 SSE 로 받아 표시하고, 완료되면 결과로 이동한다.
 */
export function GeneratingPage() {
  const { tripId = "" } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [job, setJob] = useState<ItineraryJob | undefined>();
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  const mode = (location.state as { mode?: string } | null)?.mode;

  useEffect(() => {
    if (!tripId || started.current) return;
    started.current = true;
    generateItinerary(tripId, setJob, mode)
      .then((itinerary) => {
        navigate(`${paths.tripOverview(tripId)}?mode=${itinerary.mode}`, { replace: true });
      })
      .catch((err) => setError(err instanceof Error ? err.message : "일정을 계산하지 못했습니다."));
  }, [tripId, mode, navigate]);

  if (error) {
    return (
      <div className="route-placeholder">
        <h1>일정을 계산하지 못했습니다</h1>
        <p>{error}</p>
        <button type="button" className="primary-btn" onClick={() => navigate(paths.plan("confirm"))}>조건 다시 확인하기</button>
      </div>
    );
  }

  return <GenerationProgress job={job} />;
}
