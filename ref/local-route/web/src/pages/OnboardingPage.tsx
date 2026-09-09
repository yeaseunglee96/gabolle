import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { BrandLogo } from "../components/BrandLogo";
import { saveAccountConsents } from "../api/client";
import { paths } from "../routes/paths";
import { getConsentSettings, saveConsentSettings } from "../utils/visitor";

/**
 * /onboarding
 * 페이지 트리(routeTree.ts)의 "onboarding" 노드에 해당하는 화면.
 * 앱(Expo) 최초 실행 시 위치·알림 권한을 설명·요청하는 화면 전용이며, 웹의 첫 방문 소개는
 * WelcomePage(/welcome)가 담당한다 — 혼동하지 말 것.
 * 구현 시 RoutePlaceholder 를 실제 UI 로 교체하고, routeTree 의 status 를 DONE 으로 바꾼다.
 */
export function OnboardingPage() {
  const navigate = useNavigate();
  const saved = getConsentSettings();
  const [behavior, setBehavior] = useState(saved?.behavior ?? true);
  const [sensitive, setSensitive] = useState(saved?.sensitive ?? false);
  const [preciseLocation, setPreciseLocation] = useState(saved?.preciseLocation ?? false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const complete = async () => {
    const settings = {
      personalizationMode: behavior ? "BEHAVIOR_ENABLED" : "EXPLICIT_ONLY",
      behavior,
      sensitive,
      preciseLocation,
      policyVersion: "2026-09",
    } as const;
    setSaving(true); setError("");
    try {
      saveConsentSettings(settings);
      await saveAccountConsents(settings);
      navigate(paths.home(), { replace: true });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "설정을 저장하지 못했습니다.");
    } finally { setSaving(false); }
  };

  return <main className="consent-page">
    <header><BrandLogo /><span>나에게 맞는 추천 설정</span><h1>필요한 정보만, 원하는 만큼 사용해요</h1><p>여행 계획에 필요한 기본 정보와 선택 동의를 분리했습니다. 선택 동의를 거부해도 주소를 직접 입력해 일정을 만들 수 있어요.</p></header>
    <section className="consent-list" aria-label="개인정보 및 개인화 설정">
      <article className="consent-item required"><div><b>서비스 필수 정보</b><span>계정, 여행 조건, 저장한 일정과 명시적으로 고른 취향</span></div><em>필수</em></article>
      <label className="consent-item"><div><b>행동 기반 개인화</b><span>조회·저장·교체·방문을 분석해 다음 추천을 개선합니다.</span></div><input type="checkbox" checked={behavior} onChange={(event) => setBehavior(event.target.checked)} /></label>
      <label className="consent-item"><div><b>민감정보 처리</b><span>알레르기 등 건강 관련 제약을 안전 필터에 사용합니다. 별도 동의 전에는 입력받지 않습니다.</span></div><input type="checkbox" checked={sensitive} onChange={(event) => setSensitive(event.target.checked)} /></label>
      <label className="consent-item"><div><b>여행 중 정밀 위치</b><span>앱을 사용하는 동안 도착과 이동을 돕습니다. 원시 위치 이동 경로는 장기 보관하지 않습니다.</span></div><input type="checkbox" checked={preciseLocation} onChange={(event) => setPreciseLocation(event.target.checked)} /></label>
    </section>
    <aside className="consent-summary"><b>현재 추천 방식</b><span>{behavior ? "명시 취향 + 행동 개인화" : "명시 취향만 사용"}</span><small>설정은 계정에 저장되며 언제든 끄거나 초기화할 수 있습니다.</small></aside>
    {error && <p className="auth-error" role="alert">{error}</p>}
    <button type="button" className="primary-btn" disabled={saving} onClick={() => void complete()}>{saving ? "저장 중…" : "설정 완료"}</button>
  </main>;
}
