import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getMyTrips, type MyTripSummary } from "../../api/client";
import { paths } from "../../routes/paths";

export function StoryComposePage() {
  const navigate = useNavigate(); const [trips, setTrips] = useState<MyTripSummary[]>([]); const [loading, setLoading] = useState(true);
  useEffect(() => { getMyTrips().then((items) => setTrips(items.filter((trip) => trip.itineraryId))).finally(() => setLoading(false)); }, []);
  return <main className="story-start-page"><header><button onClick={() => navigate(paths.stories())}>← 여행 기록</button><span>NEW TRAVEL NOTE</span><h1>어느 여행의 기록인가요?</h1><p>여행을 먼저 선택하면 날짜와 장소에 기록이 정확히 연결돼요.</p></header>{loading ? <div className="stories-empty">여행을 불러오는 중이에요…</div> : trips.length === 0 ? <div className="stories-empty"><strong>기록할 완성 일정이 없어요</strong><p>일정을 만든 뒤 장소별로 사진과 글을 남길 수 있어요.</p><button onClick={() => navigate(paths.plan())}>여행 일정 만들기</button></div> : <section className="story-trip-picker">{trips.map((trip) => <button key={trip.id} onClick={() => navigate(paths.tripTogether(trip.id))}><span>{trip.startDate} — {trip.endDate}</span><strong>부산 여행</strong><small>{trip.origin} 출발 · {trip.placeCount}곳</small><em>이 여행에 기록 남기기 →</em></button>)}</section>}</main>;
}
