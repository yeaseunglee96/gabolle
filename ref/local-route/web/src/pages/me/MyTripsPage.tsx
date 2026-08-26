import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiResponseError, getMyTrips, getStoredAccount, type MyTripSummary } from "../../api/client";
import { paths } from "../../routes/paths";

const paceLabel = { RELAXED: "여유롭게", NORMAL: "균형 있게", PACKED: "알차게" } as const;

export function MyTripsPage() {
  const navigate = useNavigate();
  const [trips, setTrips] = useState<MyTripSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [sessionExpired, setSessionExpired] = useState(false);
  const [filter, setFilter] = useState<"all" | "upcoming" | "past">("all");
  const account = getStoredAccount();
  const today = new Date().toISOString().slice(0, 10);
  const visibleTrips = trips.filter((trip) => filter === "all" || (filter === "past" ? trip.endDate < today : trip.endDate >= today));
  const pastCount = trips.filter((trip) => trip.endDate < today).length;

  useEffect(() => { getMyTrips().then(setTrips).catch((reason) => { if (reason instanceof ApiResponseError && reason.status === 401) setSessionExpired(true); else setError(reason instanceof Error ? reason.message : "여행을 불러오지 못했습니다."); }).finally(() => setLoading(false)); }, []);
  if (!account) return <main className="my-trips-page"><div className="my-trips-empty"><h1>내 여행을 이어가려면 로그인해주세요</h1><p>비회원으로 만든 일정도 가입하면 내 계정으로 연결할 수 있어요.</p><button onClick={() => navigate(paths.login())}>로그인하기</button></div></main>;
  if (sessionExpired) return <main className="my-trips-page"><div className="my-trips-empty"><div>🔐</div><h1>로그인이 만료되었어요</h1><p>여행 기록을 안전하게 불러오려면 다시 로그인해주세요.</p><button onClick={() => navigate(paths.login())}>다시 로그인하기</button></div></main>;

  return <main className="my-trips-page">
    <header><button type="button" onClick={() => navigate(paths.home())}>← 홈</button><span>MY TRIPS</span><div><h1>{account.name}님의 여행</h1><button type="button" onClick={() => navigate(paths.plan())}>＋ 새 여행 만들기</button></div><p>만든 일정과 지난 여행을 날짜순으로 모아봤어요.</p></header>
    {loading && <div className="my-trips-loading">여행을 불러오는 중이에요…</div>}
    {error && <div className="my-trips-error" role="alert"><strong>여행을 불러오지 못했어요.</strong><span>{error}</span><button onClick={() => location.reload()}>다시 시도</button></div>}
    {!loading && !error && trips.length === 0 && <div className="my-trips-empty"><div>🧳</div><h2>아직 저장된 여행이 없어요</h2><p>첫 일정을 만들면 이곳에서 언제든 다시 열 수 있어요.</p><button onClick={() => navigate(paths.plan())}>첫 여행 일정 만들기</button></div>}
    {!loading && trips.length > 0 && <nav className="my-trips-filters" aria-label="여행 기간 필터"><button className={filter === "all" ? "active" : ""} onClick={() => setFilter("all")}>전체 {trips.length}</button><button className={filter === "upcoming" ? "active" : ""} onClick={() => setFilter("upcoming")}>예정·진행 중 {trips.length - pastCount}</button><button className={filter === "past" ? "active" : ""} onClick={() => setFilter("past")}>지난 여행 {pastCount}</button></nav>}
    {!loading && trips.length > 0 && visibleTrips.length === 0 && <div className="my-trips-filter-empty">해당하는 여행이 없어요.</div>}
    {!loading && visibleTrips.length > 0 && <section className="my-trips-grid" aria-label="내 여행 목록">{visibleTrips.map((trip) => <article className="my-trip-card" key={trip.id}>
      <div className="my-trip-cover" style={trip.coverImage ? { backgroundImage: `url(${trip.coverImage})` } : undefined}>{!trip.coverImage && <span>LOCAL<br />ROUTE</span>}<em>{trip.endDate < today ? (trip.storyCount > 0 ? `여행책 · ${trip.storyCount}장면` : "지난 여행") : trip.itineraryId ? "예정된 여행" : "작성 중"}</em></div>
      <div className="my-trip-body"><small>{trip.startDate} — {trip.endDate}</small><h2>부산 여행</h2><p>출발 {trip.origin} · {trip.partySize}명 · {paceLabel[trip.pace]}</p>{trip.highlights.length > 0 && <div className="my-trip-highlights">{trip.highlights.map((place) => <span key={place}>{place}</span>)}</div>}<footer><span>{trip.storyCount > 0 ? `사진과 이야기 ${trip.storyCount}개` : trip.placeCount > 0 ? `${trip.placeCount}곳의 일정` : "아직 일정 없음"}</span><div className="my-trip-actions">{trip.storyCount > 0 && <button onClick={() => navigate(paths.tripTogether(trip.id))}>여행책 보기</button>}<button onClick={() => navigate(trip.itineraryId ? paths.tripOverview(trip.id) : paths.plan())}>{trip.itineraryId ? "여행 열기 →" : "계속 만들기 →"}</button></div></footer></div>
    </article>)}</section>}
  </main>;
}
