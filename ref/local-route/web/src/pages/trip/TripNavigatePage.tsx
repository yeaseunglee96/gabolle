import { useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { getEmbeddedRoute, getTaxiCard } from "../../api/client";
import type { EmbeddedRoute, TaxiCard } from "../../types";
import { useTrip } from "./TripContext";
import { NavigateMap } from "../../components/NavigateMap";
import { haversineDistanceM, nearestDistanceToPathM, nearestStepIndex, offRouteThresholdM } from "../../utils/geo";

const DIFFICULTY_LABEL: Record<string, { ko: string; en: string }> = {
  EASY: { ko: "환승 쉬움", en: "Easy transfers" },
  MODERATE: { ko: "환승 보통", en: "Moderate transfers" },
  HARD: { ko: "환승 어려움", en: "Difficult transfers" },
};

const ARRIVAL_RADIUS_M = 30;

type GeoState =
  | { status: "IDLE" }
  | { status: "REQUESTING" }
  | { status: "UNSUPPORTED" }
  | { status: "DENIED" }
  | { status: "TRACKING"; lat: number; lng: number; heading: number | null; accuracy: number }
  | { status: "ARRIVED"; lat: number; lng: number };

/**
 * /trips/:tripId/schedule/:dayIndex/navigate
 * F-NAV-01~03(v4 6.3장): 구글맵이 한국에서 지원하지 못하는 영어 턴바이턴 안내와
 * 택시 기사용 한글 목적지 카드, 그리고 실시간 GPS 위치 추적을 한 화면에서 제공한다.
 *
 * [정직성 원칙] 카카오 대중교통 API의 도보 구간은 "OO까지 도보로 이동" 수준의
 * 단일 안내만 준다 - 횡단보도·육교 같은 턴바이턴 보행 안내는 이 API에 없다.
 * 여기서 확인 못 하는 걸 지어내지 않는다(v2 §16.5 추정값 표시 원칙과 동일한 이유).
 */
export function TripNavigatePage() {
  const { dayIndex: dayIndexParam } = useParams();
  const { itinerary } = useTrip();
  const day = itinerary.days.find((d) => d.dayIndex === Number(dayIndexParam)) ?? itinerary.days[0];

  const legs = useMemo(() => {
    if (!day) return [];
    // seq: 여행 일정에서의 순서 번호. 출발지(0)부터 그날 방문지 순서(1, 2, 3…)를 그대로 쓴다
    // - 이미 일정·지도 화면에서 쓰는 번호와 같은 체계라 사용자가 보던 순서 그대로 알아볼 수 있다.
    const points = [
      { placeId: null as string | null, seq: 0, label: "출발지", labelEn: "Starting point", lat: itinerary.trip.originLat, lng: itinerary.trip.originLng },
      ...day.items.map((item, index) => ({ placeId: item.placeId, seq: index + 1, label: item.nameKo, labelEn: item.nameEn ?? item.nameKo, lat: item.lat, lng: item.lng })),
    ];
    return points.slice(1).map((to, index) => ({ from: points[index], to }));
  }, [day, itinerary.trip.originLat, itinerary.trip.originLng]);

  const [legIndex, setLegIndex] = useState(0);
  const [routeMode, setRouteMode] = useState<"TRANSIT" | "CAR">(itinerary.trip.hasCar ? "CAR" : "TRANSIT");
  const [lang, setLang] = useState<"KO" | "EN">(itinerary.trip.language);
  const [route, setRoute] = useState<EmbeddedRoute | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [taxiCard, setTaxiCard] = useState<TaxiCard | null>(null);
  const [geo, setGeo] = useState<GeoState>({ status: "IDLE" });
  const watchIdRef = useRef<number | null>(null);

  const en = lang === "EN";
  const leg = legs[Math.min(legIndex, legs.length - 1)] ?? null;

  useEffect(() => { setLegIndex(0); stopLiveNav(); }, [dayIndexParam]);
  useEffect(() => () => stopLiveNav(), []);

  useEffect(() => {
    if (!leg) return;
    let cancelled = false;
    setLoading(true); setError(""); setTaxiCard(null); stopLiveNav();
    getEmbeddedRoute({ startLat: leg.from.lat, startLng: leg.from.lng, endLat: leg.to.lat, endLng: leg.to.lng, mode: routeMode, lang })
      .then((result) => { if (!cancelled) setRoute(result); })
      .catch((err) => { if (!cancelled) { setRoute(null); setError(err instanceof Error ? err.message : "경로를 불러오지 못했습니다."); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [leg, routeMode, lang]);

  function stopLiveNav() {
    if (watchIdRef.current !== null) {
      navigator.geolocation?.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
    setGeo({ status: "IDLE" });
  }

  function startLiveNav() {
    if (!navigator.geolocation) { setGeo({ status: "UNSUPPORTED" }); return; }
    watchIdRef.current = navigator.geolocation.watchPosition(
      (position) => {
        const { latitude, longitude, heading, accuracy } = position.coords;
        if (leg && haversineDistanceM(latitude, longitude, leg.to.lat, leg.to.lng) <= ARRIVAL_RADIUS_M) {
          setGeo({ status: "ARRIVED", lat: latitude, lng: longitude });
          if (watchIdRef.current !== null) navigator.geolocation.clearWatch(watchIdRef.current);
          watchIdRef.current = null;
          return;
        }
        setGeo({ status: "TRACKING", lat: latitude, lng: longitude, heading: heading ?? null, accuracy });
      },
      (err) => { setGeo({ status: err.code === err.PERMISSION_DENIED ? "DENIED" : "UNSUPPORTED" }); },
      { enableHighAccuracy: true, maximumAge: 3000, timeout: 15000 }
    );
    setGeo({ status: "REQUESTING" });
  }

  const userPos = geo.status === "TRACKING" || geo.status === "ARRIVED" ? { lat: geo.lat, lng: geo.lng, heading: geo.status === "TRACKING" ? geo.heading : null } : null;

  const activeStepIndex = useMemo(() => {
    if (!route || !userPos) return 0;
    return nearestStepIndex(userPos.lat, userPos.lng, route.steps);
  }, [route, userPos]);

  const offRouteInfo = useMemo(() => {
    if (!route || !userPos || geo.status !== "TRACKING") return null;
    const step = route.steps[activeStepIndex];
    if (!step) return null;
    const distanceM = nearestDistanceToPathM(userPos.lat, userPos.lng, step.path);
    return distanceM > offRouteThresholdM(step.vehicle) ? Math.round(distanceM) : null;
  }, [route, userPos, activeStepIndex, geo.status]);

  function rerouteFromCurrentPosition() {
    if (!leg || geo.status !== "TRACKING") return;
    setLoading(true); setError("");
    getEmbeddedRoute({ startLat: geo.lat, startLng: geo.lng, endLat: leg.to.lat, endLng: leg.to.lng, mode: routeMode, lang })
      .then((result) => setRoute(result))
      .catch((err) => setError(err instanceof Error ? err.message : "경로를 다시 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }

  if (!day || !leg) {
    return <div className="route-placeholder"><h1>{en ? "No route to navigate" : "안내할 경로가 없습니다"}</h1></div>;
  }

  const loadTaxiCard = () => {
    if (!leg.to.placeId) return;
    getTaxiCard(leg.to.placeId).then(setTaxiCard).catch(() => setTaxiCard(null));
  };

  const hasWalkStep = !!route?.steps.some((s) => !s.vehicle || s.vehicle.includes("도보") || /walk/i.test(s.vehicle));

  return (
    <div className="service-view">
      <header className="service-heading">
        <div>
          <span className="section-eyebrow">{en ? "NAVIGATION" : "네비게이션"}</span>
          <h1>{en ? `Day ${day.dayIndex} · Getting around` : `${day.dayIndex}일차 · 경로 안내`}</h1>
          <p>{en
            ? "Google Maps can't give reliable walking or transit directions in Korea. This uses Kakao Mobility instead."
            : "구글맵은 한국에서 도보·대중교통 길찾기를 제대로 지원하지 않습니다. 카카오모빌리티 기반 경로를 대신 안내합니다."}</p>
        </div>
        <div className="service-heading-actions">
          <button type="button" className="secondary-btn" onClick={() => setLang(en ? "KO" : "EN")}>{en ? "한국어" : "English"}</button>
        </div>
      </header>

      <section className="nav-card">
        <div className="nav-toolbar">
          <label className="nav-leg-field">
            <span>{en ? "Leg" : "구간"}</span>
            <select aria-label={en ? "Select leg" : "구간 선택"} value={legIndex} onChange={(event) => setLegIndex(Number(event.target.value))}>
              {legs.map((candidate, index) => (
                <option key={index} value={index}>
                  {(en && candidate.from.labelEn !== candidate.from.label ? `${candidate.from.labelEn} (${candidate.from.label})` : candidate.from.label)} → {(en && candidate.to.labelEn !== candidate.to.label ? `${candidate.to.labelEn} (${candidate.to.label})` : candidate.to.label)}
                </option>
              ))}
            </select>
          </label>

          <div className="nav-mode-toggle" role="tablist" aria-label={en ? "Transport mode" : "이동 수단"}>
            <button type="button" role="tab" aria-selected={routeMode === "TRANSIT"} className={routeMode === "TRANSIT" ? "active" : ""} onClick={() => setRouteMode("TRANSIT")}>{en ? "Public transit" : "대중교통"}</button>
            <button type="button" role="tab" aria-selected={routeMode === "CAR"} className={routeMode === "CAR" ? "active" : ""} onClick={() => setRouteMode("CAR")}>{en ? "Car / Taxi" : "자차/택시"}</button>
          </div>
        </div>

        {loading && <p className="nav-status">{en ? "Looking up the route…" : "경로를 조회하고 있어요."}</p>}
        {error && <p className="nav-alert nav-alert-danger">{error}</p>}

        {route && (
          <>
            <p className={`nav-alert ${route.isEstimate ? "nav-alert-warning" : "nav-alert-success"}`} role="status">
              {route.isEstimate
                ? (en ? "⚠ Live route data was unavailable — times and steps below are estimates, not a confirmed route." : "⚠ 실시간 경로 데이터를 불러오지 못해 아래 시간·경로는 추정치입니다. 실제 노선과 다를 수 있어요.")
                : (en ? "✓ Live data from Kakao" : "✓ 카카오 실시간 데이터")}
            </p>

            <div className="nav-stats">
              <div className="nav-stats-headline">
                <strong>{route.durationMin}</strong>
                <span>{en ? "min" : "분"}</span>
              </div>
              <div className="nav-chips">
                <span className="nav-chip">{(route.distanceM / 1000).toFixed(1)}km</span>
                {route.transfers !== null && <span className="nav-chip">{en ? `${route.transfers} transfer(s)` : `환승 ${route.transfers}회`}</span>}
                {route.transferDifficulty && <span className={`nav-chip transfer-difficulty-${route.transferDifficulty.toLowerCase()}`}>{en ? DIFFICULTY_LABEL[route.transferDifficulty].en : DIFFICULTY_LABEL[route.transferDifficulty].ko}</span>}
                {route.fare !== null && <span className="nav-chip">{en ? `₩${route.fare.toLocaleString()}` : `${route.fare.toLocaleString()}원`}</span>}
              </div>
            </div>

            <NavigateMap
              fullPath={route.path}
              steps={route.steps}
              activeStepIndex={Math.min(activeStepIndex, route.steps.length - 1)}
              origin={{ lat: leg.from.lat, lng: leg.from.lng, seq: leg.from.seq }}
              destination={{ lat: leg.to.lat, lng: leg.to.lng, seq: leg.to.seq }}
              userPos={userPos}
              mode={routeMode}
              en={en}
            />

            {geo.status === "IDLE" && (
              <p className="nav-alert nav-alert-info">
                {en ? "Turn on live GPS tracking to follow this route as you walk or ride." : "GPS 실시간 추적을 켜면 이동하는 동안 경로를 따라갑니다."}
                <button type="button" className="nav-alert-btn" onClick={startLiveNav}>{en ? "Start live navigation" : "실시간 내비게이션 시작"}</button>
              </p>
            )}
            {geo.status === "REQUESTING" && <p className="nav-status">{en ? "Waiting for GPS signal…" : "GPS 신호를 기다리는 중…"}</p>}
            {geo.status === "UNSUPPORTED" && <p className="nav-alert nav-alert-danger">{en ? "This browser can't access GPS." : "이 브라우저에서는 GPS를 사용할 수 없어요."}</p>}
            {geo.status === "DENIED" && <p className="nav-alert nav-alert-danger">{en ? "Location permission was denied — enable it in your browser settings to use live navigation." : "위치 권한이 거부됐어요 — 브라우저 설정에서 허용하면 실시간 내비게이션을 쓸 수 있어요."}</p>}
            {geo.status === "TRACKING" && (
              <p className="nav-alert nav-alert-info">
                {en ? `Live tracking · step ${activeStepIndex + 1} of ${route.steps.length}` : `실시간 추적 중 · ${activeStepIndex + 1}/${route.steps.length}구간`}
                <button type="button" className="nav-alert-btn" onClick={stopLiveNav}>{en ? "Stop" : "중지"}</button>
              </p>
            )}
            {geo.status === "ARRIVED" && (
              <p className="nav-alert nav-alert-success">
                🎉 {en ? "You've arrived!" : "도착했습니다!"}
                <button type="button" className="nav-alert-btn" onClick={stopLiveNav}>{en ? "Close" : "닫기"}</button>
              </p>
            )}
            {offRouteInfo !== null && (
              <p className="nav-alert nav-alert-danger">
                {en ? `You seem to be off-route (about ${offRouteInfo}m away).` : `경로를 벗어난 것 같아요 (약 ${offRouteInfo}m).`}
                <button type="button" className="nav-alert-btn" onClick={rerouteFromCurrentPosition}>{en ? "Reroute from here" : "여기서 다시 경로 찾기"}</button>
              </p>
            )}

            <div className="nav-steps">
              <h2 className="nav-section-title">{en ? "Step-by-step directions" : "상세 이동 경로"}</h2>
              <ol className="nav-step-list">
                {route.steps.map((step, index) => (
                  <li key={`${step.guidance}-${index}`} className={`nav-step ${geo.status === "TRACKING" ? (index === activeStepIndex ? "active" : index < activeStepIndex ? "done" : "") : ""}`}>
                    <span className="nav-step-num">{index + 1}</span>
                    <div><b>{step.guidance}</b><small>{step.vehicle ? `${step.vehicle} · ` : ""}{step.durationMin}{en ? " min" : "분"}{step.distanceM > 0 ? ` · ${(step.distanceM / 1000).toFixed(1)}km` : ""}</small></div>
                  </li>
                ))}
              </ol>
              {hasWalkStep && (
                <p className="nav-caveat">
                  {en
                    ? "Walking steps show total distance to the next stop, not turn-by-turn (crosswalks, overpasses) — follow the map and street signs."
                    : "도보 구간은 다음 지점까지의 총 거리만 안내합니다(횡단보도·육교 같은 턴바이턴 안내는 제공되지 않아요) — 지도와 실제 표지판을 함께 확인하세요."}
                </p>
              )}
            </div>
          </>
        )}
      </section>

      {leg.to.placeId && (
        <section className="nav-taxi-card">
          <div className="nav-taxi-head"><span>🚕</span><span>{en ? "Show this to a taxi driver" : "기사님께 보여주세요"}</span></div>
          {taxiCard ? (
            <>
              <p className="nav-taxi-phrase">{taxiCard.phraseKo}</p>
              <button type="button" className="secondary-btn" onClick={() => navigator.clipboard.writeText(taxiCard.phraseKo)}>{en ? "Copy" : "복사"}</button>
            </>
          ) : (
            <button type="button" className="primary-btn" onClick={loadTaxiCard}>{en ? "Generate taxi card" : "택시 카드 만들기"}</button>
          )}
        </section>
      )}
    </div>
  );
}
