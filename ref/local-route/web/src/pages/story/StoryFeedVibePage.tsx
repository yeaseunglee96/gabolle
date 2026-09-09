import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getStories } from "../../api/client";
import { BrandLogo } from "../../components/BrandLogo";
import { paths } from "../../routes/paths";
import type { StoryRecord } from "../../types";

export function StoryFeedVibePage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<"all" | "following" | "mine">("following");
  const [stories, setStories] = useState<StoryRecord[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true);
    getStories({ mine: tab === "mine" }).then((items) => setStories(tab === "following" ? items.filter((story) => story.isFollowing || story.mine) : items)).catch(() => setStories([])).finally(() => setLoading(false));
  }, [tab]);
  const storyRing = stories.slice(0, 8);
  return <main className="stories-page">
    <header><div className="stories-brand-row"><button aria-label="홈으로" onClick={() => navigate(paths.home())}>←</button><BrandLogo /><span>TRAVEL STORIES</span></div><div><h1>함께 발견한 부산</h1><button onClick={() => navigate(paths.storyNew())}>스토리 올리기</button></div><p>팔로우한 여행자의 장면을 먼저 보고, 장소 후기와 일정으로 이어가세요.</p></header>
    {storyRing.length > 0 && <section className="story-ring" aria-label="팔로우한 여행자의 새 스토리">{storyRing.map((story) => <button key={story.id} type="button" onClick={() => navigate(paths.story(story.id))}><span>{story.images[0] ? <img src={story.images[0]} alt="" /> : story.authorLabel.slice(0, 1)}</span><small>{story.authorLabel}</small></button>)}</section>}
    <nav><button className={tab === "following" ? "active" : ""} onClick={() => setTab("following")}>팔로잉</button><button className={tab === "all" ? "active" : ""} onClick={() => setTab("all")}>모든 여행자</button><button className={tab === "mine" ? "active" : ""} onClick={() => setTab("mine")}>내 기록</button></nav>
    {loading ? <div className="stories-empty">기록을 불러오는 중이에요…</div> : stories.length === 0 ? <div className="stories-empty"><strong>{tab === "mine" ? "아직 내 여행 기록이 없어요" : tab === "following" ? "팔로우한 여행자의 새 기록이 없어요" : "아직 공개된 기록이 없어요"}</strong><p>다른 여행자의 로컬 기록을 둘러보거나 첫 장면을 남겨보세요.</p><button onClick={() => tab === "following" ? setTab("all") : navigate(paths.storyNew())}>{tab === "following" ? "모든 여행자 보기" : "기록 시작하기"}</button></div> : <section className="stories-grid">{stories.map((story) => <article key={story.id}>{story.images[0] ? <img src={story.images[0]} alt={`${story.placeName} 여행 기록`} /> : <div className="story-card-placeholder">{story.placeName}</div>}<div><small>{story.areaLabel} · {story.placeName}</small><p>{story.content}</p><footer><span>{new Date(story.publishAt).toLocaleDateString("ko-KR")}</span>{story.mine && story.tripId ? <button onClick={() => navigate(paths.tripTogether(story.tripId!))}>수정하기</button> : <em>{story.visitVerified ? "방문 확인" : "여행 기록"}</em>}</footer></div></article>)}</section>}
  </main>;
}
