import { useEffect, useState } from "react";
import { createShare, getCollaboration, inviteCompanion } from "../api/client";

type LinkResult = { url: string; label: string; expiresAt?: string };
type LinkMode = "PUBLIC" | "EDITOR";

export function ShareBar({ itineraryId, tripId, language = "KO" }: { itineraryId: string; tripId: string; language?: "KO" | "EN" }) {
  const en = language === "EN";
  const [notice, setNotice] = useState<string | null>(null);
  const [members, setMembers] = useState(1);
  const [role, setRole] = useState("VIEWER");
  const [busyMode, setBusyMode] = useState<LinkMode | null>(null);
  const [generated, setGenerated] = useState<LinkResult | null>(null);

  useEffect(() => {
    const poll = () => getCollaboration(itineraryId).then((data) => { setMembers(data.members.length); setRole(data.myRole); }).catch(() => undefined);
    void poll();
    const timer = window.setInterval(poll, 5000);
    return () => window.clearInterval(timer);
  }, [itineraryId]);

  const localUrl = (remoteUrl: string) => {
    const url = new URL(remoteUrl);
    url.port = window.location.port;
    url.protocol = window.location.protocol;
    url.hostname = window.location.hostname;
    return url.toString();
  };

  const copyLink = async (url: string) => {
    try {
      if (navigator.clipboard?.writeText) { await navigator.clipboard.writeText(url); return true; }
    } catch { /* 보안이 제한된 개발 주소에서는 아래 방식으로 다시 시도한다. */ }
    const field = document.createElement("textarea");
    field.value = url;
    field.setAttribute("readonly", "");
    field.style.position = "fixed";
    field.style.opacity = "0";
    document.body.appendChild(field);
    field.select();
    const copied = document.execCommand("copy");
    field.remove();
    return copied;
  };

  const finishLink = async (result: LinkResult) => {
    setGenerated(result);
    const copied = await copyLink(result.url);
    setNotice(copied ? (en ? `${result.label} link copied.` : `${result.label} 링크를 복사했습니다.`) : (en ? "Link created. Copy it below." : "링크를 만들었습니다. 아래 주소를 눌러 복사해주세요."));
  };

  const createPublicLink = async () => {
    setBusyMode("PUBLIC"); setNotice(null);
    try { const result = await createShare(itineraryId); await finishLink({ url: localUrl(result.url), label: en ? "View-only" : "일정 보기" }); }
    catch (error) { setNotice(error instanceof Error ? error.message : "공유 링크를 만들지 못했습니다."); }
    finally { setBusyMode(null); }
  };

  const createInvite = async () => {
    setBusyMode("EDITOR"); setNotice(null);
    try {
      const result = await inviteCompanion(tripId);
      await finishLink({ url: localUrl(result.inviteUrl), label: en ? "Companion invitation" : "동행자 초대", expiresAt: result.expiresAt });
    } catch (error) { setNotice(error instanceof Error ? error.message : "초대 링크를 만들지 못했습니다."); }
    finally { setBusyMode(null); }
  };

  const shareGenerated = async () => {
    if (!generated) return;
    try {
      if (navigator.share) await navigator.share({ title: generated.label, text: en ? "Join my Busan trip plan." : "부산 여행 일정을 함께 확인해요.", url: generated.url });
      else setNotice(await copyLink(generated.url) ? (en ? "Link copied." : "링크를 복사했습니다.") : (en ? "Select and copy the link above." : "위 링크를 선택해 복사해주세요."));
    } catch (error) { if (!(error instanceof DOMException && error.name === "AbortError")) setNotice(en ? "Could not share the link." : "링크를 공유하지 못했습니다."); }
  };

  return <section className="share-bar collab-card" aria-label="일정 공유와 공동 편집">
    <header className="collab-card-heading"><div><span className="share-label">{en ? "Plan together" : "동행자와 함께 계획하기"}</span><p>{en ? "Everyone invited can edit the same itinerary. View-only sharing stays separate." : "초대받은 동행자는 모두 같은 일정을 편집합니다. 단순 열람 공유는 별도로 보낼 수 있어요."}</p></div><span className="collab-member-count"><i aria-hidden="true" />{en ? `${members} joined` : `${members}명 참여`}</span></header>

    {role === "OWNER" ? <div className="collab-options" aria-label="동행자 초대와 일정 공유">
      <button type="button" className="collab-option primary" disabled={busyMode !== null} onClick={() => void createInvite()}><span aria-hidden="true">01</span><strong>{busyMode === "EDITOR" ? (en ? "Creating…" : "만드는 중…") : (en ? "Invite a companion" : "동행자 초대하기")}</strong><small>{en ? "Invitees can reorder places and edit times." : "초대받은 사람은 장소 순서와 방문 시간을 함께 바꿀 수 있어요."}</small></button>
      <button type="button" className="collab-option" disabled={busyMode !== null} onClick={() => void createPublicLink()}><span aria-hidden="true">02</span><strong>{busyMode === "PUBLIC" ? (en ? "Creating…" : "만드는 중…") : (en ? "Share view-only itinerary" : "보기 전용으로 공유")}</strong><small>{en ? "A sanitized copy anyone can view without joining." : "가입하지 않아도 보는 개인정보 제거 일정이에요."}</small></button>
    </div> : <div className="collab-role-summary"><strong>{en ? "You can edit this trip" : "이 일정을 함께 수정할 수 있어요"}</strong><span>{en ? "Changes are checked every 5 seconds." : "다른 동행자의 변경 사항을 5초마다 확인합니다."}</span></div>}

    {generated && <div className="collab-link-panel"><div><span>{generated.label}</span><small>{generated.expiresAt ? (en ? `Valid until ${new Date(generated.expiresAt).toLocaleDateString()}` : `${new Date(generated.expiresAt).toLocaleDateString("ko-KR")}까지 유효`) : (en ? "No personal origin information" : "출발지 등 개인정보 제외")}</small></div><input value={generated.url} readOnly aria-label={`${generated.label} 링크`} onFocus={(event) => event.currentTarget.select()} /><div><button type="button" onClick={async () => setNotice(await copyLink(generated.url) ? (en ? "Link copied." : "링크를 복사했습니다.") : (en ? "Select and copy the link above." : "위 링크를 선택해 복사해주세요."))}>{en ? "Copy" : "복사"}</button><button type="button" onClick={() => void shareGenerated()}>{en ? "Share" : "보내기"}</button></div></div>}
    {notice && <div className="share-notice" role="status">{notice}</div>}
  </section>;
}
