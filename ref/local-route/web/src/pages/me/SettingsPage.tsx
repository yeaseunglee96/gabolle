import { ChangeEvent, FormEvent, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getStoredAccount, updateAccountProfile } from "../../api/client";
import { setUiLanguage } from "../../i18n";
import { paths } from "../../routes/paths";

const AVATAR_COLORS = ["LAVENDER", "SKY", "MINT", "PEACH", "CHARCOAL"] as const;

async function resizeAvatar(file: File) {
  if (!file.type.startsWith("image/")) throw new Error("이미지 파일만 선택할 수 있어요.");
  if (file.size > 5 * 1024 * 1024) throw new Error("사진은 5MB 이하로 선택해주세요.");
  const source = await new Promise<string>((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = () => reject(new Error("사진을 읽지 못했습니다.")); reader.readAsDataURL(file); });
  const image = await new Promise<HTMLImageElement>((resolve, reject) => { const element = new Image(); element.onload = () => resolve(element); element.onerror = () => reject(new Error("사진을 읽지 못했습니다.")); element.src = source; });
  const size = Math.min(image.naturalWidth, image.naturalHeight);
  const canvas = document.createElement("canvas"); canvas.width = 256; canvas.height = 256;
  canvas.getContext("2d")?.drawImage(image, (image.naturalWidth - size) / 2, (image.naturalHeight - size) / 2, size, size, 0, 0, 256, 256);
  return canvas.toDataURL("image/jpeg", .78);
}

export function SettingsPage() {
  const navigate = useNavigate();
  const account = useMemo(getStoredAccount, []);
  const [name, setName] = useState(account?.name ?? "");
  const [nationality, setNationality] = useState(account?.nationality ?? "");
  const [locale, setLocale] = useState<"KO" | "EN">(account?.locale ?? "KO");
  const [dietType, setDietType] = useState(account?.dietType ?? "NONE");
  const [allergies, setAllergies] = useState((account?.allergies ?? []).join(", "));
  const [travelStyle, setTravelStyle] = useState(account?.travelStyle ?? "BALANCED");
  const [defaultTransport, setDefaultTransport] = useState(account?.defaultTransport ?? "TRANSIT");
  const [avatarImage, setAvatarImage] = useState<string | null>(account?.avatarImage ?? null);
  const [avatarColor, setAvatarColor] = useState(account?.avatarColor ?? "LAVENDER");
  const [status, setStatus] = useState<"idle" | "saving" | "saved">("idle");
  const [error, setError] = useState("");

  if (!account) return <main className="settings-page settings-guest"><h1>로그인이 필요해요</h1><p>프로필과 여행 기본값은 계정에 안전하게 저장됩니다.</p><button onClick={() => navigate(paths.login())}>로그인하기</button></main>;

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setStatus("saving"); setError("");
    try {
      await updateAccountProfile({ name, nationality: nationality || null, locale, dietType, allergies: allergies.split(",").map((value) => value.trim()).filter(Boolean), travelStyle, defaultTransport, avatarImage, avatarColor });
      setUiLanguage(locale); setStatus("saved");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "저장하지 못했습니다."); setStatus("idle"); }
  };

  const chooseAvatar = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]; if (!file) return;
    try { setError(""); setAvatarImage(await resizeAvatar(file)); setStatus("idle"); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "사진을 불러오지 못했습니다."); }
    event.target.value = "";
  };

  return (
    <main className="settings-page">
      <header><button type="button" onClick={() => navigate(-1)}>← 돌아가기</button><span>MY PROFILE</span><h1>프로필·여행 설정</h1><p>자주 쓰는 조건을 저장해 두면 다음 일정에서 다시 입력할 필요가 없어요.</p></header>
      <form onSubmit={submit}>
        <section><h2>기본 정보</h2>
          <div className="profile-avatar-editor">
            <div className={`profile-avatar-preview avatar-${avatarColor.toLowerCase()}`}>{avatarImage ? <img src={avatarImage} alt="현재 프로필" /> : name.slice(0, 1) || "?"}</div>
            <div><strong>프로필 이미지</strong><p>사진을 올리지 않으면 이름 첫 글자로 표시돼요.</p><div className="profile-avatar-actions"><label className="avatar-upload">사진 선택<input type="file" accept="image/jpeg,image/png,image/webp" onChange={chooseAvatar} /></label>{avatarImage && <button type="button" onClick={() => { setAvatarImage(null); setStatus("idle"); }}>기본 이미지로</button>}</div></div>
          </div>
          {!avatarImage && <fieldset className="avatar-color-picker"><legend>기본 이미지 색상</legend>{AVATAR_COLORS.map((color) => <button key={color} type="button" className={`avatar-${color.toLowerCase()} ${avatarColor === color ? "selected" : ""}`} onClick={() => { setAvatarColor(color); setStatus("idle"); }} aria-label={`${color} 색상`} aria-pressed={avatarColor === color} />)}</fieldset>}
          <div className="settings-grid">
          <label>이름<input value={name} onChange={(e) => setName(e.target.value)} maxLength={50} required /></label>
          <label>이메일<input value={account.email} disabled /><small>이메일 변경은 추후 지원할 예정입니다.</small></label>
          <label>국가·지역<input value={nationality} onChange={(e) => setNationality(e.target.value)} placeholder="예: 대한민국, Japan" maxLength={50} /></label>
          <label>화면 언어<select value={locale} onChange={(e) => setLocale(e.target.value as "KO" | "EN")}><option value="KO">한국어</option><option value="EN">English</option></select></label>
        </div></section>
        <section><h2>여행 기본값</h2><p>일정 만들기에서 먼저 제안할 값이에요. 매 여행마다 바꿀 수 있습니다.</p><div className="settings-grid">
          <label>여행 속도<select value={travelStyle} onChange={(e) => setTravelStyle(e.target.value as typeof travelStyle)}><option value="RELAXED">여유롭게</option><option value="BALANCED">균형 있게</option><option value="PACKED">알차게</option></select></label>
          <label>주요 이동 수단<select value={defaultTransport} onChange={(e) => setDefaultTransport(e.target.value as typeof defaultTransport)}><option value="TRANSIT">대중교통</option><option value="CAR">자차</option><option value="WALK">도보</option></select></label>
          <label>식단<select value={dietType} onChange={(e) => setDietType(e.target.value as typeof dietType)}><option value="NONE">제한 없음</option><option value="VEGETARIAN">채식</option><option value="VEGAN">비건</option><option value="HALAL">할랄</option></select></label>
          <label>알레르기<input value={allergies} onChange={(e) => setAllergies(e.target.value)} placeholder="예: 견과류, 갑각류 (쉼표로 구분)" /></label>
        </div></section>
        {error && <p className="settings-error" role="alert">{error}</p>}
        <div className="settings-actions"><span>{status === "saved" ? "✓ 저장되었습니다." : ""}</span><button type="submit" disabled={status === "saving"}>{status === "saving" ? "저장 중…" : "변경사항 저장"}</button></div>
      </form>
    </main>
  );
}
