/**
 * @deprecated 라우터 도입 이후 이 화면은 5개 라우트로 분리되었다.
 *
 *   /trips/:tripId/overview  → pages/trip/TripOverviewPage
 *   /trips/:tripId/schedule  → pages/trip/TripSchedulePage
 *   /trips/:tripId/discover  → pages/trip/TripDiscoverPage
 *   /trips/:tripId/together  → pages/trip/TripTogetherPage
 *   /trips/:tripId/prep      → pages/trip/TripPrepPage
 *
 * 공통 레이아웃·상태·모달은 pages/trip/TripLayout 이 소유한다.
 */
export { TripLayout as ResultDashboard } from "./trip/TripLayout";
