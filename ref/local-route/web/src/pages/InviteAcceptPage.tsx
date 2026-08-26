import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { acceptInvite } from "../api/client";
import { paths } from "../routes/paths";

/**
 * /invite/:inviteToken — 동행 초대 수락
 * 7일 유효한 초대 토큰을 받아 편집자·열람자로 합류시킨 뒤 여행 대시보드로 보낸다.
 */
export function InviteAcceptPage() {
  const { inviteToken = "" } = useParams();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  useEffect(() => {
    if (!inviteToken || started.current) return;
    started.current = true;
    acceptInvite(inviteToken)
      .then(({ tripId }) => navigate(paths.tripOverview(tripId), { replace: true }))
      .catch((err) => setError(err instanceof Error ? err.message : "초대를 수락하지 못했습니다."));
  }, [inviteToken, navigate]);

  return (
    <div className="route-placeholder">
      <h1>{error ? "초대를 수락하지 못했습니다" : "초대를 확인하는 중입니다…"}</h1>
      {error && <p>{error}</p>}
      {error && <button type="button" className="primary-btn" onClick={() => navigate(paths.home())}>홈으로</button>}
    </div>
  );
}
