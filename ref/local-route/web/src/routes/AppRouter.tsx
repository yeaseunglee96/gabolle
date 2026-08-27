import { Navigate, Route, Routes, useParams, useSearchParams } from "react-router-dom";
import { AppShell } from "./AppShell";

import { HomePage } from "../pages/HomePage";
import { OnboardingPage } from "../pages/OnboardingPage";
import { WelcomePage } from "../pages/WelcomePage";
import { AuthPage } from "../pages/auth/AuthPage";
import { PlanWizardPage } from "../pages/PlanWizardPage";
import { GeneratingPage } from "../pages/GeneratingPage";
import { InviteAcceptPage } from "../pages/InviteAcceptPage";
import { NotFoundPage } from "../pages/NotFoundPage";
import { SharedItineraryPage } from "../pages/SharedItineraryPage";

import { TripLayout } from "../pages/trip/TripLayout";
import { TripOverviewPage } from "../pages/trip/TripOverviewPage";
import { TripSchedulePage } from "../pages/trip/TripSchedulePage";
import { TripMapPage } from "../pages/trip/TripMapPage";
import { TripDiscoverPage } from "../pages/trip/TripDiscoverPage";
import { TripFestivalsPage } from "../pages/trip/TripFestivalsPage";
import { TripSouvenirsPage } from "../pages/trip/TripSouvenirsPage";
import { TripTogetherPage } from "../pages/trip/TripTogetherPage";
import { TripPrepPage } from "../pages/trip/TripPrepPage";
import { TripCollaboratePage } from "../pages/trip/TripCollaboratePage";
import { TripNavigatePage } from "../pages/trip/TripNavigatePage";

import { PlaceDetailPage } from "../pages/place/PlaceDetailPage";
import { PlaceReviewsPage } from "../pages/place/PlaceReviewsPage";

import { StoryFeedPage } from "../pages/story/StoryFeedPage";
import { StoryComposePage } from "../pages/story/StoryComposePage";
import { StoryDetailPage } from "../pages/story/StoryDetailPage";
import { UserProfilePage } from "../pages/user/UserProfilePage";

import { MyTripsPage } from "../pages/me/MyTripsPage";
import { MyLocalPage } from "../pages/me/MyLocalPage";
import { SettingsPage } from "../pages/me/SettingsPage";

import { ModerationPage } from "../pages/admin/ModerationPage";
import { KpiPage } from "../pages/admin/KpiPage";
import { AdCampaignPage } from "../pages/admin/AdCampaignPage";

import { TermsPage } from "../pages/legal/TermsPage";
import { PrivacyPage } from "../pages/legal/PrivacyPage";
import { OpenSourcePage } from "../pages/legal/OpenSourcePage";

import { paths } from "./paths";
import { getLastVisitedTripId, hasSeenWelcome } from "../utils/visitor";

/** /s/:slug 라우트에서 slug 를 읽어 기존 화면에 넘긴다. */
function SharedItineraryRoute() {
  const { slug = "" } = useParams();
  return <SharedItineraryPage slug={slug} />;
}

/**
 * 구버전 쿼리스트링 링크 호환.
 * 라우터 도입 이전에 공유된 ?trip= / ?share= / ?invite= 링크가 계속 동작해야 한다.
 * 새 경로로 영구 이동시키므로 앞으로는 이 분기를 타지 않는다.
 */
function LegacyQueryRedirect() {
  const [searchParams] = useSearchParams();

  const share = searchParams.get("share");
  if (share) return <Navigate to={paths.share(share)} replace />;

  const invite = searchParams.get("invite");
  if (invite) return <Navigate to={paths.invite(invite)} replace />;

  const trip = searchParams.get("trip");
  if (trip) {
    const view = searchParams.get("view");
    const mode = searchParams.get("mode");
    const target = view === "schedule" ? paths.tripSchedule(trip)
      : view === "discover" ? paths.tripDiscover(trip)
      : view === "together" ? paths.tripTogether(trip)
      : view === "prep" ? paths.tripPrep(trip)
      : paths.tripOverview(trip);
    return <Navigate to={mode ? `${target}?mode=${mode}` : target} replace />;
  }

  // 재방문 사용자(진행 중인 일정이 있음)는 홈/웰컴 화면을 건너뛰고 자신의 일정으로 바로 들어간다.
  const lastTripId = getLastVisitedTripId();
  if (lastTripId) return <Navigate to={paths.tripOverview(lastTripId)} replace />;

  // 첫 방문 사용자는 언어 선택과 서비스 소개를 먼저 본다.
  if (!hasSeenWelcome()) return <Navigate to={paths.welcome()} replace />;

  return <HomePage />;
}

/**
 * 전체 라우팅 정의.
 * 여기 있는 경로 목록은 web/src/routes/routeTree.ts 와 1:1로 일치해야 한다.
 * 새 화면을 만들 때는 routeTree.ts → 페이지 파일 → 이 파일 순서로 추가한다.
 */
export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<LegacyQueryRedirect />} />
        <Route path="/welcome" element={<WelcomePage />} />
        <Route path="/onboarding" element={<OnboardingPage />} />
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/signup" element={<AuthPage mode="signup" />} />

        <Route path="/plan" element={<Navigate to={paths.plan("basic")} replace />} />
        <Route path="/plan/:step" element={<PlanWizardPage />} />
        <Route path="/generating/:tripId" element={<GeneratingPage />} />

        <Route path="/trips/:tripId" element={<TripLayout />}>
          <Route index element={<Navigate to="overview" replace />} />
          <Route path="overview" element={<TripOverviewPage />} />
          <Route path="schedule" element={<TripSchedulePage />} />
          <Route path="schedule/:dayIndex" element={<TripSchedulePage />} />
          <Route path="schedule/:dayIndex/navigate" element={<TripNavigatePage />} />
          <Route path="map" element={<TripMapPage />} />
          <Route path="discover" element={<TripDiscoverPage />} />
          <Route path="discover/festivals" element={<TripFestivalsPage />} />
          <Route path="discover/souvenirs" element={<TripSouvenirsPage />} />
          <Route path="together" element={<TripTogetherPage />} />
          <Route path="prep" element={<TripPrepPage />} />
          <Route path="collaborate" element={<TripCollaboratePage />} />
        </Route>

        <Route path="/places/:placeId" element={<PlaceDetailPage />} />
        <Route path="/places/:placeId/reviews" element={<PlaceReviewsPage />} />

        <Route path="/stories" element={<StoryFeedPage />} />
        <Route path="/stories/new" element={<StoryComposePage />} />
        <Route path="/stories/:storyId" element={<StoryDetailPage />} />
        <Route path="/users/:userId" element={<UserProfilePage />} />

        <Route path="/me" element={<Navigate to={paths.myTrips()} replace />} />
        <Route path="/me/trips" element={<MyTripsPage />} />
        <Route path="/me/local" element={<MyLocalPage />} />
        <Route path="/me/settings" element={<SettingsPage />} />

        <Route path="/s/:slug" element={<SharedItineraryRoute />} />
        <Route path="/invite/:inviteToken" element={<InviteAcceptPage />} />

        <Route path="/admin" element={<Navigate to={paths.adminModeration()} replace />} />
        <Route path="/admin/moderation" element={<ModerationPage />} />
        <Route path="/admin/kpis" element={<KpiPage />} />
        <Route path="/admin/ads" element={<AdCampaignPage />} />

        <Route path="/legal" element={<Navigate to={paths.terms()} replace />} />
        <Route path="/legal/terms" element={<TermsPage />} />
        <Route path="/legal/privacy" element={<PrivacyPage />} />
        <Route path="/legal/open-source" element={<OpenSourcePage />} />

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
