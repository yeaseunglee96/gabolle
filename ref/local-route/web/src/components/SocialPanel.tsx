import { useEffect, useMemo, useState } from "react";
import { createStory, deleteStory, followUser, getStories, reportStory, updateStory } from "../api/client";
import type { ItineraryOutput, StoryRecord } from "../types";

const shortAuthor = (label: string) => label.replace(/^여행자\s*/, "");
const storyInitial = (story: StoryRecord) => shortAuthor(story.authorLabel).slice(0, 1) || "L";

async function prepareStoryImage(file: File) {
  if (!file.type.startsWith("image/")) throw new Error("이미지 파일만 선택할 수 있어요.");
  if (file.size > 8 * 1024 * 1024) throw new Error("사진은 장당 8MB 이하로 선택해주세요.");
  const source = await new Promise<string>((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = () => reject(new Error("사진을 읽지 못했습니다.")); reader.readAsDataURL(file); });
  const image = await new Promise<HTMLImageElement>((resolve, reject) => { const element = new Image(); element.onload = () => resolve(element); element.onerror = () => reject(new Error("사진을 읽지 못했습니다.")); element.src = source; });
  const scale = Math.min(1, 1280 / Math.max(image.naturalWidth, image.naturalHeight));
  const canvas = document.createElement("canvas"); canvas.width = Math.round(image.naturalWidth * scale); canvas.height = Math.round(image.naturalHeight * scale);
  canvas.getContext("2d")?.drawImage(image, 0, 0, canvas.width, canvas.height);
  const output = canvas.toDataURL("image/jpeg", .76);
  if (output.length > 1_000_000) throw new Error("사진을 압축할 수 없어요. 더 작은 사진을 선택해주세요.");
  return output;
}

export function SocialPanel({ itinerary }: { itinerary: ItineraryOutput }) {
  const items = itinerary.days.flatMap((day) => day.items);
  const [publicStories, setPublicStories] = useState<StoryRecord[]>([]);
  const [followingPosts, setFollowingPosts] = useState<StoryRecord[]>([]);
  const [myStories, setMyStories] = useState<StoryRecord[]>([]);
  const [selectedStory, setSelectedStory] = useState<StoryRecord | null>(null);
  const [composerOpen, setComposerOpen] = useState(false);
  const [placeId, setPlaceId] = useState(items[0]?.placeId ?? "");
  const [content, setContent] = useState("");
  const [images, setImages] = useState<string[]>([]);
  const [visibility, setVisibility] = useState("PUBLIC");
  const [publishNow, setPublishNow] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [editingStory, setEditingStory] = useState<StoryRecord | null>(null);
  const [editContent, setEditContent] = useState("");
  const [editImages, setEditImages] = useState<string[]>([]);
  const [editVisibility, setEditVisibility] = useState("PRIVATE");
  const [savingEdit, setSavingEdit] = useState(false);
  const [section, setSection] = useState<"book" | "community">("book");

  const load = async () => {
    try {
      const [all, followed, mine] = await Promise.all([getStories(false), getStories({ following: true }), getStories({ mine: true })]);
      setPublicStories(all); setFollowingPosts(followed); setMyStories(mine);
      setSelectedStory((current) => current ? all.find((story) => story.id === current.id) ?? null : null);
    } catch { setPublicStories([]); setFollowingPosts([]); }
  };
  useEffect(() => { void load(); }, []);

  const storyTray = useMemo(() => {
    const latestByAuthor = new Map<string, StoryRecord>();
    publicStories.forEach((story) => { if (!latestByAuthor.has(story.authorId)) latestByAuthor.set(story.authorId, story); });
    return [...latestByAuthor.values()].slice(0, 14);
  }, [publicStories]);
  const tripStories = useMemo(() => myStories.filter((story) => story.tripId === itinerary.tripId).sort((a, b) => (a.dayIndex ?? 999) - (b.dayIndex ?? 999) || String(a.plannedArrival ?? "").localeCompare(String(b.plannedArrival ?? ""))), [myStories, itinerary.tripId]);

  const selected = items.find((item) => item.placeId === placeId);
  const chooseImages = async (files: FileList | null, target: "create" | "edit") => {
    if (!files?.length) return;
    const current = target === "create" ? images : editImages;
    if (current.length + files.length > 3) { setNotice("사진은 최대 3장까지 올릴 수 있어요."); return; }
    try { const prepared = await Promise.all([...files].map(prepareStoryImage)); target === "create" ? setImages([...current, ...prepared]) : setEditImages([...current, ...prepared]); }
    catch (error) { setNotice(error instanceof Error ? error.message : "사진을 불러오지 못했습니다."); }
  };
  const submit = async () => {
    if (!selected || !content.trim()) return;
    try {
      const result = await createStory({ placeId, itineraryItemId: selected.itemId, content: content.trim(), images, visibility, publishMode: publishNow ? "NOW" : "AFTER_TRIP" });
      setContent(""); setImages([]); setComposerOpen(false);
      setNotice(result.delayed ? "여행 종료 후 공개되도록 저장했습니다. 사진의 위치정보는 제거됩니다." : "여행 기록을 공개했습니다.");
      await load();
    } catch (error) { setNotice(error instanceof Error ? error.message : "여행 기록을 저장하지 못했습니다."); }
  };
  const toggleFollow = async (story: StoryRecord) => { await followUser(story.authorId, story.isFollowing); await load(); };
  const startEdit = (story: StoryRecord) => { setEditingStory(story); setEditContent(story.content); setEditImages(story.images); setEditVisibility(story.visibility); setSelectedStory(null); };
  const saveEdit = async (publishNow = false) => { if (!editingStory || !editContent.trim()) return; setSavingEdit(true); try { await updateStory(editingStory.id, { content: editContent.trim(), images: editImages, visibility: editVisibility, publishNow }); setEditingStory(null); setNotice(publishNow ? "여행 기록을 지금 공개했습니다." : "여행 기록을 수정했습니다."); await load(); } catch (error) { setNotice(error instanceof Error ? error.message : "수정하지 못했습니다."); } finally { setSavingEdit(false); } };
  const removeStory = async () => { if (!editingStory || !window.confirm("이 여행 기록을 삭제할까요? 삭제하면 되돌릴 수 없어요.")) return; setSavingEdit(true); try { await deleteStory(editingStory.id); setEditingStory(null); setNotice("여행 기록을 삭제했습니다."); await load(); } catch (error) { setNotice(error instanceof Error ? error.message : "삭제하지 못했습니다."); } finally { setSavingEdit(false); } };

  return <section className="social-panel social-home" aria-label="함께 여행 피드">
    <section className="story-quick-start" aria-label="내 여행 기록 작성">
      <div><span>MY TRAVEL NOTE</span><strong>이 여행의 사진과 이야기를 남겨보세요</strong><small>일정 속 장소를 선택해 기록하면 나중에 여행별로 다시 볼 수 있어요.</small></div>
      <button type="button" onClick={() => { setComposerOpen(true); window.setTimeout(() => document.getElementById("my-travel-note")?.scrollIntoView({ behavior: "smooth", block: "center" }), 0); }}>＋ 사진·기록 남기기</button>
    </section>

    <nav className="story-section-tabs" aria-label="여행 기록 메뉴">
      <button type="button" className={section === "book" ? "active" : ""} onClick={() => setSection("book")}>내 여행책</button>
      <button type="button" className={section === "community" ? "active" : ""} onClick={() => setSection("community")}>여행자 피드</button>
    </nav>

    {section === "book" && <section className="my-story-manager" aria-labelledby="my-story-manager-title">
      <div className="travel-book-cover" style={tripStories[0]?.images[0] ? { backgroundImage: `linear-gradient(90deg, rgba(16,16,22,.8), rgba(16,16,22,.2)), url(${tripStories[0].images[0]})` } : undefined}><span>LOCAL ROUTE · TRAVEL BOOK</span><h2>부산에서의 {itinerary.days.length}일</h2><p>{itinerary.trip.startDate} — {itinerary.trip.endDate} · {new Set(tripStories.map((story) => story.placeId)).size}곳의 기록</p></div>
      <div className="social-section-heading"><div><span>MY TRAVEL BOOK</span><h2 id="my-story-manager-title">이 여행의 기록</h2></div><small>{tripStories.length}개의 장면</small></div>
      {tripStories.length === 0 ? <div className="my-story-manager-empty">이 여행에는 아직 기록이 없어요. 위 버튼으로 첫 장면을 남겨보세요.</div> : <div className="travel-book-strip">
        {tripStories.map((story) => <button type="button" key={story.id} onClick={() => startEdit(story)}>
          {story.images[0] ? <img src={story.images[0]} alt="" /> : <span className="my-story-record-placeholder">{story.placeName.slice(0, 1)}</span>}
          <span><small>{story.dayIndex ? `DAY ${story.dayIndex}` : "TRAVEL NOTE"}{story.plannedArrival ? ` · ${story.plannedArrival}` : ""}</small><strong>{story.placeName}</strong><em>{story.content}</em><b>{new Date(story.publishAt) > new Date() ? "공개 예정 · 수정 →" : "기록 수정 →"}</b></span>
        </button>)}
      </div>}
    </section>}
    {section === "community" && <><section className="people-stories" aria-labelledby="story-tray-title">
      <div className="social-section-heading"><div><span>지금 여행 중</span><h2 id="story-tray-title">여행자 스토리</h2></div><small>최근 공유된 순간</small></div>
      <div className="story-rail" role="list">
        {storyTray.length === 0 && <div className="story-rail-empty"><strong>첫 번째 여행 순간을 기다리고 있어요</strong><span>공개된 스토리가 생기면 여기에 먼저 보여드릴게요.</span></div>}
        {storyTray.map((story) => <button type="button" className="story-bubble" role="listitem" key={story.id} onClick={() => setSelectedStory(story)} aria-label={`${story.authorLabel}의 ${story.placeName} 스토리 보기`}>
          <span className="story-ring"><span className="story-thumb">{story.images[0] ? <img src={story.images[0]} alt="" /> : <b>{storyInitial(story)}</b>}</span></span><strong>{shortAuthor(story.authorLabel)}</strong><small>{story.areaLabel || story.placeName}</small>
        </button>)}
      </div>
    </section>

    <section className="following-feed-section" aria-labelledby="following-feed-title">
      <div className="social-section-heading"><div><span>내가 고른 여행자</span><h2 id="following-feed-title">팔로잉 피드</h2></div><small>{followingPosts.length}개의 새 기록</small></div>
      {followingPosts.length === 0 ? <div className="following-empty"><strong>아직 팔로우한 여행자의 게시물이 없어요</strong><p>위 스토리를 열어 마음에 드는 여행자를 팔로우하면, 새 여행 기록이 이곳에 차분히 쌓입니다.</p></div> : <div className="following-posts">
        {followingPosts.map((story) => <article className="following-post" key={story.id}>
          <header><div className="post-author"><span>{storyInitial(story)}</span><div><strong>{story.authorLabel}</strong><small>{story.areaLabel} · {story.placeName}</small></div></div><button type="button" className="following-button" onClick={() => void toggleFollow(story)}>팔로잉</button></header>
          {story.images[0] ? <img className="post-photo" src={story.images[0]} alt={`${story.placeName}에서 공유한 여행 사진`} /> : <button type="button" className="post-text-cover" onClick={() => setSelectedStory(story)}><span>{story.placeName}</span><strong>{story.content}</strong></button>}
          <div className="post-body">{story.images[0] && <p><strong>{story.authorLabel}</strong> {story.content}</p>}<div className="post-meta"><span>{story.visitVerified ? "방문 확인" : "여행 기록"} · {new Date(story.publishAt).toLocaleDateString("ko-KR")}</span>{story.moderationStatus === "REVIEW" ? <em>검토 중</em> : <button type="button" onClick={async () => { await reportStory(story.id); await load(); }}>신고</button>}</div></div>
        </article>)}
      </div>}
    </section></>}

    {section === "book" && <section id="my-travel-note" className="my-story-area" aria-labelledby="my-story-title">
      <div><span>MY TRAVEL NOTE</span><h2 id="my-story-title">내 여행 기록 남기기</h2><p>피드를 둘러본 뒤, 오늘의 기억을 한 장면으로 남겨보세요.</p></div>
      <button type="button" className="composer-toggle" aria-expanded={composerOpen} onClick={() => setComposerOpen((open) => !open)}>{composerOpen ? "작성 닫기" : "기록 작성"}</button>
      {composerOpen && <div className="story-composer">
        <label><span>장소</span><select aria-label="스토리 장소" value={placeId} onChange={(event) => setPlaceId(event.target.value)}>{items.map((item) => <option key={item.itemId} value={item.placeId}>{item.nameKo}</option>)}</select></label>
        <label><span>기록</span><textarea value={content} maxLength={500} placeholder="이 장소에서 오래 기억하고 싶은 순간은 무엇인가요?" onChange={(event) => setContent(event.target.value)} /></label>
        <div className="story-options"><label className="image-picker">사진 선택 ({images.length}/3)<input type="file" multiple accept="image/jpeg,image/png,image/webp" onChange={(event) => void chooseImages(event.target.files, "create")} /></label><select aria-label="공개 범위" value={visibility} onChange={(event) => setVisibility(event.target.value)}><option value="PUBLIC">전체 공개</option><option value="FOLLOWERS">팔로워 공개</option><option value="PRIVATE">나만 보기</option></select><label className="publish-check"><input type="checkbox" checked={publishNow} onChange={(event) => setPublishNow(event.target.checked)} /> 지금 바로 공개</label><button type="button" disabled={!content.trim()} onClick={() => void submit()}>저장하기</button></div>
        {!publishNow && visibility !== "PRIVATE" && <p className="publish-schedule-note">체크하지 않으면 여행 종료 후 자동으로 공개돼요.</p>}
        {publishNow && <p className="privacy-warning">여행 중 공개할 때는 현재 위치가 드러나지 않도록 지역 단위로만 표시합니다.</p>}{images.length > 0 && <div className="story-preview-grid">{images.map((image, index) => <figure key={`${image.slice(-20)}-${index}`}><img src={image} alt={`업로드할 사진 ${index + 1}`} /><button type="button" onClick={() => setImages(images.filter((_, itemIndex) => itemIndex !== index))} aria-label={`사진 ${index + 1} 삭제`}>×</button></figure>)}</div>}
      </div>}
    </section>}
    {notice && <p className="feature-notice" role="status">{notice}</p>}

    {selectedStory && <div className="story-viewer-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setSelectedStory(null); }}>
      <article className="story-viewer" role="dialog" aria-modal="true" aria-labelledby="story-viewer-title">
        <header><div className="post-author"><span>{storyInitial(selectedStory)}</span><div><strong id="story-viewer-title">{selectedStory.authorLabel}</strong><small>{selectedStory.areaLabel} · {selectedStory.placeName}</small></div></div><button type="button" className="viewer-close" onClick={() => setSelectedStory(null)} aria-label="스토리 닫기">닫기</button></header>
        {selectedStory.images.length > 0 ? <div className="viewer-gallery">{selectedStory.images.map((image, index) => <img key={`${image.slice(-20)}-${index}`} src={image} alt={`${selectedStory.placeName} 여행 사진 ${index + 1}`} />)}</div> : <div className="viewer-text"><span>{selectedStory.placeName}</span><p>{selectedStory.content}</p></div>}
        <div className="viewer-copy">{selectedStory.images[0] && <p>{selectedStory.content}</p>}<div><span>{new Date(selectedStory.publishAt).toLocaleDateString("ko-KR")}</span>{selectedStory.mine ? <button type="button" onClick={() => startEdit(selectedStory)}>수정</button> : <button type="button" onClick={() => void toggleFollow(selectedStory)}>{selectedStory.isFollowing ? "팔로잉 취소" : "팔로우"}</button>}</div></div>
      </article>
    </div>}
    {editingStory && <div className="story-viewer-backdrop"><section className="story-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="story-edit-title"><header><div><span>MY TRAVEL NOTE</span><h2 id="story-edit-title">여행 기록 수정</h2><p>{editingStory.placeName} · {new Date(editingStory.publishAt) > new Date() ? `${new Date(editingStory.publishAt).toLocaleDateString("ko-KR")} 공개 예정` : "공개됨"}</p></div><button type="button" onClick={() => setEditingStory(null)} aria-label="수정 닫기">×</button></header><label>기록<textarea value={editContent} maxLength={500} onChange={(event) => setEditContent(event.target.value)} /></label><div className="story-preview-grid">{editImages.map((image, index) => <figure key={`${image.slice(-20)}-${index}`}><img src={image} alt={`사진 ${index + 1}`} /><button type="button" onClick={() => setEditImages(editImages.filter((_, itemIndex) => itemIndex !== index))} aria-label={`사진 ${index + 1} 삭제`}>×</button></figure>)}</div><div className="story-edit-options"><label className="image-picker">사진 추가 ({editImages.length}/3)<input type="file" multiple accept="image/jpeg,image/png,image/webp" onChange={(event) => void chooseImages(event.target.files, "edit")} /></label><select aria-label="수정 공개 범위" value={editVisibility} onChange={(event) => setEditVisibility(event.target.value)}><option value="PUBLIC">전체 공개</option><option value="FOLLOWERS">팔로워 공개</option><option value="PRIVATE">나만 보기</option></select></div><footer><button type="button" className="story-delete-button" onClick={() => void removeStory()}>기록 삭제</button><div>{new Date(editingStory.publishAt) > new Date() && editVisibility !== "PRIVATE" && <button type="button" className="publish-now-button" disabled={savingEdit} onClick={() => void saveEdit(true)}>지금 공개하기</button>}<button type="button" onClick={() => setEditingStory(null)}>취소</button><button type="button" disabled={savingEdit || !editContent.trim()} onClick={() => void saveEdit()}>{savingEdit ? "저장 중…" : "수정 저장"}</button></div></footer></section></div>}
  </section>;
}
