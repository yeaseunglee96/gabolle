import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { BrandLogo } from "../components/BrandLogo";
import { PlacePhoto } from "../components/PlacePhoto";
import { getNowRecommendations } from "../api/client";
import { paths } from "../routes/paths";
import type { LocationSearchResult, NowRecommendation } from "../types";

export function NowPage() {
  const navigate = useNavigate();
  const [origin, setOrigin] = useState("부산역");
  const [hours, setHours] = useState(2);
  const [indoor, setIndoor] = useState(false);
  const [resolvedOrigin, setResolvedOrigin] = useState<LocationSearchResult | null>(null);
  const [results, setResults] = useState<NowRecommendation[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const search = async () => {
    if (origin.trim().length < 2) { setError("출발지를 2자 이상 입력해 주세요."); return; }
    setLoading(true); setError(null);
    try {
      const response = await getNowRecommendations(origin.trim(), hours, indoor);
      setResolvedOrigin(response.origin); setResults(response.recommendations);
    } catch (cause) {
      setResolvedOrigin(null); setResults([]); setError(cause instanceof Error ? cause.message : "지금 갈 장소를 찾지 못했습니다.");
    } finally { setLoading(false); }
  };

  return <main className="now-page">
    <header className="now-header"><Link to={paths.home()}><BrandLogo /></Link><Link to={paths.home()}>홈으로</Link></header>
    <section className="now-hero"><span className="section-eyebrow">RIGHT NOW IN BUSAN</span><h1>지금, 어디 가볼래?</h1><p>현재 위치를 허용하지 않아도 주소나 역 이름을 직접 입력할 수 있어요.</p></section>
    <section className="now-search" aria-label="지금 갈 곳 조건">
      <label><span>현재 위치 또는 출발지</span><input value={origin} onChange={(event) => setOrigin(event.target.value)} placeholder="주소, 역, 장소 이름" /></label>
      <label><span>남은 시간</span><select value={hours} onChange={(event) => setHours(Number(event.target.value))}><option value={1}>1시간</option><option value={2}>2시간</option><option value={3}>3시간</option></select></label>
      <label className="now-toggle"><input type="checkbox" checked={indoor} onChange={(event) => setIndoor(event.target.checked)} /><span>실내 장소 우선</span></label>
      <button type="button" className="primary-btn" disabled={loading} onClick={search}>{loading ? "카카오 경로 확인 중…" : "지금 갈 곳 찾기"}</button>
    </section>
    {error && <p className="now-error" role="alert">{error}</p>}
    {resolvedOrigin && <section className="now-results" aria-live="polite"><div className="now-result-heading"><div><span>{resolvedOrigin.name} · {resolvedOrigin.address}</span><h2>{hours}시간 안에 다녀올 수 있어요</h2></div><small>카카오 경로 · TourAPI 장소·사진 · 운영시간 확인</small></div>{results.length === 0 ? <div className="now-empty">현재 영업시간과 이동 가능 시간을 모두 만족하는 장소가 없습니다. 남은 시간을 늘려보세요.</div> : results.map((place) => <article key={place.placeId}><PlacePhoto placeId={place.placeId} imageUrl={place.imageUrl} alt={`${place.nameKo} 전경`} category={place.category} /><div><span>{place.category === "TOURIST" ? "관광·체험" : place.category === "RESTAURANT" ? "식당" : "카페"} · {(place.distanceM / 1000).toFixed(1)}km</span><h3>{place.nameKo}</h3><p>{place.reason}</p><small>{place.isOpenNow ? `현재 영업 중 · ${place.closeTime} 종료` : `운영 ${place.openTime}–${place.closeTime}`} · 체류 약 {place.recommendedStayMin}분</small></div><aside><b>약 {place.durationMin}분</b><span>{place.isEstimate ? "예상 경로" : "카카오 경로"}</span><button type="button" onClick={() => navigate(paths.place(place.placeId))}>상세 보기</button></aside></article>)}</section>}
  </main>;
}
