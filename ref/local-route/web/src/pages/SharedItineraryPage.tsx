import { useEffect, useState } from "react";
import { getSharedItinerary } from "../api/client";
import type { SharedItinerary } from "../types";

export function SharedItineraryPage({ slug }: { slug: string }) {
  const [data, setData] = useState<SharedItinerary | null>(null); const [error, setError] = useState<string | null>(null);
  useEffect(() => { getSharedItinerary(slug).then(setData).catch((reason) => setError(reason instanceof Error ? reason.message : "공유 일정을 열 수 없습니다.")); }, [slug]);
  if (error) return <main className="shared-page"><div className="shared-hero"><h1>공유 링크를 확인해주세요</h1><p>{error}</p><a href="/">새 여행 만들기</a></div></main>;
  if (!data) return <main className="shared-page"><div className="spinner" /><p>개인정보를 제외한 일정을 불러오는 중…</p></main>;
  return <main className="shared-page"><header className="shared-hero"><span>LOCAL ROUTE · 읽기 전용 공유</span><h1>{data.trip.startDate}부터 {data.itinerary.days.length}일 부산 여행</h1><p>{data.authorId}의 일정 · 조회 {data.viewCount}회 · {new Date(data.expiresAt).toLocaleDateString()}까지</p><div><a href="/">내 조건으로 새 여행 만들기</a><button onClick={() => navigator.clipboard.writeText(window.location.href)}>링크 복사</button></div><small>안전을 위해 출발지·정확한 개인 위치·연락처는 공유하지 않습니다.</small></header><section className="shared-days">{data.itinerary.days.map((day) => <article key={day.dayIndex}><h2>DAY {day.dayIndex} <span>{day.visitDate}</span></h2><ol>{day.items.map((item) => <li key={item.itemId}><time>{item.plannedArrival}</time><div><b>{item.nameKo}</b><span>{item.address}</span><small>{item.category} · {item.stayMinutes}분</small></div></li>)}</ol></article>)}</section></main>;
}
