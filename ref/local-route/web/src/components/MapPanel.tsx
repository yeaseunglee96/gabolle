import { useEffect, useMemo, useState } from "react";
import type { EmbeddedRoute, ItineraryDayOutput, SouvenirShop, TransitAlternative } from "../types";
import { KakaoMap } from "./KakaoMap";
import { getEmbeddedRoute, getSouvenirShops } from "../api/client";

interface Props { days: ItineraryDayOutput[]; originLat: number; originLng: number; activeDayIndex: number | null; onActiveDayChange: (dayIndex: number | null) => void; selectedPlaceId: string | null; showSouvenirControl?: boolean }
type RoutePoint = { id: string; label: string; lat: number; lng: number };

export function MapPanel({ days, originLat, originLng, activeDayIndex, onActiveDayChange, selectedPlaceId, showSouvenirControl = true }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [showSouvenirs, setShowSouvenirs] = useState(false); const [souvenirShops, setSouvenirShops] = useState<SouvenirShop[]>([]);
  const [routeMode, setRouteMode] = useState<"TRANSIT" | "CAR">("TRANSIT"); const [route, setRoute] = useState<EmbeddedRoute | null>(null);
  const [selectedAlternativeId, setSelectedAlternativeId] = useState<string | null>(null);
  const [routeLoading, setRouteLoading] = useState(false); const [routeNotice, setRouteNotice] = useState("");
  const visibleDays = activeDayIndex ? days.filter((day) => day.dayIndex === activeDayIndex) : days;
  const routePoints = useMemo<RoutePoint[]>(() => [{ id: "origin", label: "여행 출발지", lat: originLat, lng: originLng }, ...visibleDays.flatMap((day) => day.items.map((item) => ({ id: item.placeId, label: `DAY ${day.dayIndex} · ${item.nameKo}`, lat: item.lat, lng: item.lng })))], [visibleDays, originLat, originLng]);
  const [startId, setStartId] = useState("origin"); const [endId, setEndId] = useState(routePoints[1]?.id ?? "origin");

  useEffect(() => { if (!routePoints.some((point) => point.id === startId)) setStartId("origin"); if (!routePoints.some((point) => point.id === endId)) setEndId(routePoints[1]?.id ?? "origin"); }, [routePoints, startId, endId]);
  useEffect(() => { if (selectedPlaceId && routePoints.some((point) => point.id === selectedPlaceId)) setEndId(selectedPlaceId); }, [selectedPlaceId, routePoints]);
  useEffect(() => { if (showSouvenirs) getSouvenirShops(originLat, originLng).then(setSouvenirShops).catch(() => setSouvenirShops([])); else setSouvenirShops([]); }, [showSouvenirs, originLat, originLng]);
  useEffect(() => {
    const start = routePoints.find((point) => point.id === startId); const end = routePoints.find((point) => point.id === endId);
    if (!start || !end || start.id === end.id) { setRoute(null); setRouteNotice("출발지와 도착지를 다르게 선택해 주세요."); return; }
    let cancelled = false; setRouteLoading(true); setRouteNotice("");
    getEmbeddedRoute({ startLat: start.lat, startLng: start.lng, endLat: end.lat, endLng: end.lng, mode: routeMode }).then((result) => { if (!cancelled) { setRoute(result); setSelectedAlternativeId(result.alternatives?.[0]?.id ?? null); setRouteNotice(result.isEstimate ? "실시간 노선 데이터가 없어 이동수단별 예상 경로를 표시합니다." : ""); } }).catch((error) => { if (!cancelled) { setRoute(null); setSelectedAlternativeId(null); setRouteNotice(error instanceof Error ? error.message : "경로를 조회하지 못했습니다."); } }).finally(() => { if (!cancelled) setRouteLoading(false); });
    return () => { cancelled = true; };
  }, [routePoints, startId, endId, routeMode]);

  const activeAlternative: TransitAlternative | null = route?.alternatives?.find((alternative) => alternative.id === selectedAlternativeId) ?? route?.alternatives?.[0] ?? null;
  const activeRoute = activeAlternative ?? route;

  return <div className="map-panel">
    <div className="map-panel-header"><span className="map-panel-title">여행 지도</span><button type="button" className="map-expand-btn" aria-expanded={expanded} aria-controls="route-map" onClick={() => setExpanded((value) => !value)}>{expanded ? "지도 접기" : "전체 지도 보기"}</button></div>
    <div id="route-map" className={`map-panel-body ${expanded ? "expanded" : ""}`}><KakaoMap days={visibleDays} originLat={originLat} originLng={originLng} selectedPlaceId={selectedPlaceId} souvenirShops={souvenirShops} routePath={activeRoute?.path} routeMode={routeMode} /></div>
    <div className="map-day-controls" role="group" aria-label="지도 표시 날짜"><button type="button" aria-pressed={activeDayIndex === null} className={activeDayIndex === null ? "active" : ""} onClick={() => onActiveDayChange(null)}>전체</button>{days.map((day) => <button type="button" aria-pressed={activeDayIndex === day.dayIndex} key={day.dayIndex} className={activeDayIndex === day.dayIndex ? "active" : ""} onClick={() => onActiveDayChange(day.dayIndex)}>DAY {day.dayIndex}</button>)}</div>
    <div className="map-legend"><span><i className="legend-dot dot-visit" /> 방문지</span><span><i className="legend-line" /> 선택 경로</span>{showSouvenirControl && showSouvenirs && <span>기념품샵 (광고 아님)</span>}</div>
    <section className="inline-directions" aria-label="지도 아래 길찾기">
      <div className="directions-heading"><strong>이동 경로 확인</strong><span>지도에 표시할 출발지와 도착지를 선택하세요.</span></div>
      <div className="route-mode-tabs" role="tablist" aria-label="이동 수단"><button type="button" role="tab" aria-selected={routeMode === "TRANSIT"} className={routeMode === "TRANSIT" ? "active" : ""} onClick={() => setRouteMode("TRANSIT")}>대중교통</button><button type="button" role="tab" aria-selected={routeMode === "CAR"} className={routeMode === "CAR" ? "active" : ""} onClick={() => setRouteMode("CAR")}>자차</button></div>
      <div className="route-point-fields"><label><span>출발</span><select aria-label="길찾기 출발지" value={startId} onChange={(event) => setStartId(event.target.value)}>{routePoints.map((point) => <option key={`start-${point.id}`} value={point.id}>{point.label}</option>)}</select></label><button type="button" className="route-swap" aria-label="출발지와 도착지 바꾸기" onClick={() => { setStartId(endId); setEndId(startId); }}>바꾸기</button><label><span>도착</span><select aria-label="길찾기 도착지" value={endId} onChange={(event) => setEndId(event.target.value)}>{routePoints.map((point) => <option key={`end-${point.id}`} value={point.id}>{point.label}</option>)}</select></label></div>
      {routeMode === "TRANSIT" && route?.alternatives?.length ? <div className="transit-alternatives" role="tablist" aria-label="대중교통 경로 선택">{route.alternatives.map((alternative) => <button type="button" role="tab" aria-selected={activeAlternative?.id === alternative.id} className={activeAlternative?.id === alternative.id ? "active" : ""} key={alternative.id} onClick={() => setSelectedAlternativeId(alternative.id)}><strong>{alternative.label}</strong><span>{alternative.durationMin}분 · {alternative.transfers ?? 0}회 환승</span>{alternative.isEstimate && <small>예상</small>}</button>)}</div> : null}
      {routeLoading ? <p className="route-status">카카오 경로를 조회하고 있어요.</p> : activeRoute && <div className="route-summary"><strong>{activeRoute.durationMin}분</strong><span>{(activeRoute.distanceM / 1000).toFixed(1)}km</span>{activeRoute.transfers !== null && <span>환승 {activeRoute.transfers}회</span>}{route?.transferDifficulty && <span className={`transfer-difficulty-${route.transferDifficulty.toLowerCase()}`}>{{ EASY: "환승 쉬움", MODERATE: "환승 보통", HARD: "환승 어려움" }[route.transferDifficulty]}</span>}{activeRoute.fare !== null &&<span>{routeMode === "CAR" ? "예상 택시비" : "요금"} {activeRoute.fare.toLocaleString()}원</span>}<small>{route?.source === "KAKAO_MAP" ? "카카오맵 대중교통 경로" : route?.source === "KAKAO_MOBILITY" ? "카카오모빌리티 자동차 경로" : "거리 기반 예상"}</small></div>}
      {routeNotice && <p className="route-notice">{routeNotice}</p>}{activeRoute?.steps.length ? <div className="route-steps"><strong>상세 이동 경로</strong><ol>{activeRoute.steps.slice(0, 10).map((step, index) => <li key={`${step.guidance}-${index}`}><span>{index + 1}</span><div><b>{step.guidance}</b><small>{step.vehicle ? `${step.vehicle} · ` : ""}{step.durationMin}분{step.distanceM > 0 ? ` · ${(step.distanceM / 1000).toFixed(1)}km` : ""}</small></div></li>)}</ol></div> : null}
    </section>
    {showSouvenirControl && <button type="button" className={`souvenir-toggle ${showSouvenirs ? "active" : ""}`} aria-pressed={showSouvenirs} onClick={() => setShowSouvenirs((value) => !value)}>기념품·특산품 {showSouvenirs ? `${souvenirShops.length}곳 표시 중` : "지도에서 보기"}</button>}
    {showSouvenirControl && showSouvenirs && souvenirShops.slice(0, 3).map((shop) => <details className="souvenir-detail" key={shop.id}><summary>{shop.nameKo} · {(shop.distanceM / 1000).toFixed(1)}km</summary><p>{shop.items.join(" · ")}</p><small>{shop.openTime}~{shop.closeTime} · {shop.cardPayment ? "카드 결제" : "결제 확인 필요"} · 로컬 점수 순</small></details>)}
  </div>;
}
