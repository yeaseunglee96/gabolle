import { useEffect, useState } from "react";
import { addFestival, getActivities, getEvents, getNatureSpots, getNightViews, getSouvenirShops, getWalkTrails } from "../api/client";
import type { ActivitySpot, Festival, ItineraryOutput, SouvenirShop } from "../types";

type SpotTab = "ACTIVITY" | "WALK" | "NATURE" | "NIGHT_VIEW";
type DiscoveryTab = "FESTIVAL" | "NIGHT_MARKET" | "TRADITIONAL_MARKET" | SpotTab | "SOUVENIR";
const labels: Record<DiscoveryTab, { title: string; description: string; index: string }> = {
  FESTIVAL: { title: "축제", description: "여행 기간에 열리는 지역 축제", index: "01" },
  NIGHT_MARKET: { title: "야시장", description: "부산의 밤과 로컬 먹거리를 만나는 시장", index: "02" },
  TRADITIONAL_MARKET: { title: "전통시장", description: "부산의 생활과 오래된 먹거리를 만나는 시장", index: "03" },
  ACTIVITY: { title: "액티비티", description: "요트·케이블카·서핑 등 예약해서 즐기는 체험", index: "04" },
  WALK: { title: "산책", description: "길·로드·코스·공원 등 걷기 좋은 곳", index: "05" },
  NATURE: { title: "자연", description: "해수욕장과 산 등 부산의 자연", index: "06" },
  NIGHT_VIEW: { title: "야경", description: "밤에 보면 더 아름다운 부산의 장소", index: "07" },
  SOUVENIR: { title: "기념품샵", description: "지역의 기억을 담아갈 가까운 상점", index: "08" },
};

const SPOT_TAB_LABEL: Record<SpotTab, string> = { ACTIVITY: "LOCAL ACTIVITY", WALK: "LOCAL WALK", NATURE: "LOCAL NATURE", NIGHT_VIEW: "LOCAL NIGHT VIEW" };
const SPOT_EMPTY_TEXT: Record<SpotTab, string> = {
  ACTIVITY: "출발지 주변에서 확인된 액티비티가 없습니다.",
  WALK: "출발지 주변에서 확인된 산책 코스가 없습니다.",
  NATURE: "출발지 주변에서 확인된 자연 명소가 없습니다.",
  NIGHT_VIEW: "출발지 주변에서 확인된 야경 명소가 없습니다.",
};

export function ExperiencePanel({ itinerary, canEdit = true }: { itinerary: ItineraryOutput; canEdit?: boolean }) {
  const [events, setEvents] = useState<Festival[]>([]); const [shops, setShops] = useState<SouvenirShop[]>([]);
  const [spots, setSpots] = useState<Record<SpotTab, ActivitySpot[]>>({ ACTIVITY: [], WALK: [], NATURE: [], NIGHT_VIEW: [] });
  const [active, setActive] = useState<DiscoveryTab>("FESTIVAL"); const [notice, setNotice] = useState<string | null>(null);
  useEffect(() => {
    const { originLat: lat, originLng: lng } = itinerary.trip;
    getEvents(itinerary.trip.startDate, itinerary.trip.endDate).then(setEvents).catch(() => setEvents([]));
    getSouvenirShops(lat, lng).then(setShops).catch(() => setShops([]));
    getActivities(lat, lng).then((data) => setSpots((prev) => ({ ...prev, ACTIVITY: data }))).catch(() => undefined);
    getWalkTrails(lat, lng).then((data) => setSpots((prev) => ({ ...prev, WALK: data }))).catch(() => undefined);
    getNatureSpots(lat, lng).then((data) => setSpots((prev) => ({ ...prev, NATURE: data }))).catch(() => undefined);
    getNightViews(lat, lng).then((data) => setSpots((prev) => ({ ...prev, NIGHT_VIEW: data }))).catch(() => undefined);
  }, [itinerary.trip.startDate, itinerary.trip.endDate, itinerary.trip.originLat, itinerary.trip.originLng]);
  const counts = { FESTIVAL: events.filter((event) => (event.eventType ?? "FESTIVAL") === "FESTIVAL").length, NIGHT_MARKET: events.filter((event) => event.eventType === "NIGHT_MARKET").length, TRADITIONAL_MARKET: events.filter((event) => event.eventType === "TRADITIONAL_MARKET").length, ACTIVITY: spots.ACTIVITY.length, WALK: spots.WALK.length, NATURE: spots.NATURE.length, NIGHT_VIEW: spots.NIGHT_VIEW.length, SOUVENIR: shops.length };

  const renderEvents = (type: "FESTIVAL" | "NIGHT_MARKET" | "TRADITIONAL_MARKET") => {
    const visible = events.filter((event) => (event.eventType ?? "FESTIVAL") === type);
    return <div className="discovery-content" role="region" aria-label={`${labels[type].title} 목록`}>
      {visible.length === 0 && <p className="discovery-empty">{type === "FESTIVAL" ? "여행 기간에 확인된 축제가" : type === "NIGHT_MARKET" ? "확인된 야시장이" : "확인된 전통시장이"} 없습니다.</p>}
      {visible.map((event) => <article className="discovery-card" key={event.placeId}>
        {event.imageUrl ? <img src={event.imageUrl} alt={`${event.title} 이미지`} /> : <div className="discovery-art" aria-hidden="true"><span>{type === "NIGHT_MARKET" ? "NIGHT" : type === "TRADITIONAL_MARKET" ? "MARKET" : "LOCAL"}</span></div>}
        <div className="discovery-card-copy"><span className="discovery-type">{type === "NIGHT_MARKET" ? "LOCAL NIGHT MARKET" : type === "TRADITIONAL_MARKET" ? "LOCAL TRADITIONAL MARKET" : "LOCAL FESTIVAL"}</span><h3>{event.title}</h3><p>{type === "FESTIVAL" ? `${event.startDate} – ${event.endDate}${event.playTime ? ` · ${event.playTime}` : ""}` : event.playTime}</p><small>{event.address}</small>{type === "NIGHT_MARKET" && event.officialUrl && <a className="discovery-source-link" href={event.officialUrl} target="_blank" rel="noreferrer">공식 운영정보 확인</a>}{type === "FESTIVAL" && canEdit && <button type="button" onClick={async () => { try { await addFestival(itinerary.itineraryId, event.placeId); setNotice(`${event.title}을 일정에 추가했습니다.`); } catch (error) { setNotice(error instanceof Error ? error.message : "일정에 추가하지 못했습니다."); } }}>일정에 추가</button>}</div>
      </article>)}
    </div>;
  };

  const renderShops = () => <div className="discovery-content" role="region" aria-label="기념품샵 목록">
    {shops.length === 0 && <p className="discovery-empty">출발지 주변에서 확인된 기념품샵이 없습니다.</p>}
    {shops.map((shop) => <article className="discovery-card souvenir-card" key={shop.id}>{shop.imageUrl ? <img src={shop.imageUrl} alt={`${shop.nameKo} 이미지`} /> : <div className="discovery-art" aria-hidden="true"><span>SOUVENIR</span></div>}<div className="discovery-card-copy"><span className="discovery-type">LOCAL SOUVENIR</span><h3>{shop.nameKo}</h3><p>{shop.items.join(" · ")}</p><small>{shop.address}<br />운영 {shop.openTime}–{shop.closeTime}</small></div></article>)}
  </div>;

  const renderSpots = (type: SpotTab) => {
    const visible = spots[type];
    return <div className="discovery-content" role="region" aria-label={`${labels[type].title} 목록`}>
      {visible.length === 0 && <p className="discovery-empty">{SPOT_EMPTY_TEXT[type]}</p>}
      {visible.map((spot) => <article className="discovery-card" key={spot.id}>
        {spot.imageUrl ? <img src={spot.imageUrl} alt={`${spot.nameKo} 이미지`} /> : <div className="discovery-art" aria-hidden="true"><span>{type}</span></div>}
        <div className="discovery-card-copy">
          <span className="discovery-type">{SPOT_TAB_LABEL[type]}</span>
          <h3>{spot.nameKo}</h3>
          <p>운영 {spot.openTime}–{spot.closeTime} · 체류 약 {spot.recommendedStayMin}분</p>
          <small>{spot.address} · 출발지에서 {(spot.distanceM / 1000).toFixed(1)}km</small>
        </div>
      </article>)}
    </div>;
  };

  const isSpotTab = (type: DiscoveryTab): type is SpotTab => type === "ACTIVITY" || type === "WALK" || type === "NATURE" || type === "NIGHT_VIEW";

  return <section className="experience-panel discovery-panel" aria-label="축제 야시장 전통시장 액티비티 산책 자연 야경 기념품샵">
    {(Object.keys(labels) as DiscoveryTab[]).map((type) => <section className={`discovery-bar-section ${active === type ? "open" : ""}`} key={type}>
      <button type="button" className="discovery-bar" aria-expanded={active === type} aria-controls={`discovery-${type}`} onClick={() => setActive(type)}><span className="discovery-index">{labels[type].index}</span><span className="discovery-bar-copy"><strong>{labels[type].title}</strong><small>{labels[type].description}</small></span><span className="discovery-count">{counts[type]}곳</span><span className="discovery-arrow" aria-hidden="true">{active === type ? "접기" : "보기"}</span></button>
      {active === type && <div id={`discovery-${type}`}>{type === "SOUVENIR" ? renderShops() : isSpotTab(type) ? renderSpots(type) : renderEvents(type)}</div>}
    </section>)}
    {notice && <p className="feature-notice" role="status">{notice}</p>}
  </section>;
}
