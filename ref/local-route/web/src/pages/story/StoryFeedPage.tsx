import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getStories } from "../../api/client";
import { paths } from "../../routes/paths";
import type { StoryRecord } from "../../types";

export function StoryFeedPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<"all" | "mine">("all");
  const [stories, setStories] = useState<StoryRecord[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => { setLoading(true); getStories({ mine: tab === "mine" }).then(setStories).catch(() => setStories([])).finally(() => setLoading(false)); }, [tab]);
  return <main className="stories-page"><header><button onClick={() => navigate(paths.home())}>← 홈</button><span>TRAVEL STORIES</span><div><h1>여행 기록</h1><button onClick={() => navigate(paths.storyNew())}>＋ 기록 남기기</button></div><p>여행 중 남긴 순간을 장소와 일정별로 다시 만나보세요.</p></header><nav><button className={tab === "all" ? "active" : ""} onClick={() => setTab("all")}>모든 여행자</button><button className={tab === "mine" ? "active" : ""} onClick={() => setTab("mine")}>내 기록</button></nav>{loading ? <div className="stories-empty">기록을 불러오는 중이에요…</div> : stories.length === 0 ? <div className="stories-empty"><strong>{tab === "mine" ? "아직 내 여행 기록이 없어요" : "아직 공개된 기록이 없어요"}</strong><p>일정 속 장소에서 첫 순간을 남겨보세요.</p><button onClick={() => navigate(paths.storyNew())}>기록 시작하기</button></div> : <section className="stories-grid">{stories.map((story) => <article key={story.id}>{story.images[0] ? <img src={story.images[0]} alt={`${story.placeName} 여행 기록`} /> : <div className="story-card-placeholder">{story.placeName}</div>}<div><small>{story.areaLabel} · {story.placeName}</small><p>{story.content}</p><footer><span>{new Date(story.publishAt).toLocaleDateString("ko-KR")}</span>{story.mine && story.tripId ? <button onClick={() => navigate(paths.tripTogether(story.tripId!))}>수정하기</button> : <em>{story.visitVerified ? "방문 확인" : "여행 기록"}</em>}</footer></div></article>)}</section>}</main>;
}
