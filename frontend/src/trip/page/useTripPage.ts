// 여행 페이지의 데이터 — 넓은 화면(1단계)과 폰(2단계)이 같은 것을 쓴다 (S15P21E201-1535).
//
// 🔴 둘이 따로 부르면 두 화면이 서로 다른 숫자를 말하는 날이 온다(한쪽만 고치고 끝나기 때문이다).
//    그래서 «무엇을 불러와서 어떻게 세는가» 는 여기 한 벌만 두고, 화면은 그리기만 한다.
//    1단계 때 TripPageDesktop 안에 있던 것을 그대로 옮겼다 — 옮기면서 바꾼 것은 아래 🔴 둘뿐이다.
import { useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'expo-router';

import { useAuth } from '@/auth/AuthProvider';
import { color } from '@/design/tokens';
import { useI18n } from '@/i18n';
import { formatDayHeading } from '@/i18n/datetime';
import { txf } from '@/i18n/format';
import { useCourseRoutePaths } from '@/map/courseRoutePaths';
import { loadItinerary, loadItineraryPace, type ItineraryDto, type ItineraryItemDto, type ItineraryPaceDto } from '@/plan/itinerary';
import { summarizeItineraryBudget } from '@/plan/itineraryBudget';
import { totalTravelMinutes } from '@/plan/itinerarySummary';
import { loadPlacePhotos, type PlacePhoto } from '@/plan/placePhotos';
import { canConfirmCourse, ensureCourseItinerary, type TripCourse } from '@/plan/tripCourses';
import { loadTripBudget } from '@/trip/tripBudget';
import { humanTripTitle, shouldAskTripName, wasTripNameAsked } from '@/trip/tripNaming';
import { loadTrips } from '@/trip/trips';

import { loadTripPageCourses, type TripPageCourses, type TripPageSource } from './tripPageData';
import { dayMap, dayRoutes, formatManwon, returnRoute, returnTrip } from './tripPageModel';

export type TripItinerary = { id: string; value: ItineraryDto | null; message: string | null };

export function useTripPage(source: TripPageSource) {
  const router = useRouter();
  const { accessToken } = useAuth();
  const { tx, locale } = useI18n();

  const [page, setPage] = useState<TripPageCourses | null>(null);
  const [courseIndex, setCourseIndex] = useState(0);
  const [confirmed, setConfirmed] = useState(false);
  const [itinerary, setItinerary] = useState<TripItinerary | null>(null);
  /** 늘리면 같은 일정을 다시 받는다 — 폰의 제외처럼 서버가 새 일정을 «안 돌려주는» 편집 뒤에 쓴다. */
  const [itineraryNonce, setItineraryNonce] = useState(0);
  const [dayIndex, setDayIndex] = useState(0);
  const [selectedId, setSelectedId] = useState('');
  const [photos, setPhotos] = useState<Record<string, PlacePhoto>>({});
  const [pace, setPace] = useState<ItineraryPaceDto | null>(null);
  const [paceNonce, setPaceNonce] = useState(0);
  const [budgetKrw, setBudgetKrw] = useState<number | null>(null);
  const [confirming, setConfirming] = useState(false);
  const confirmingNow = useRef(false);

  const sourceKey = source.kind === 'trip' ? `trip:${source.tripId}:${source.jobId ?? ''}` : `itinerary:${source.itineraryId}`;
  const load = useMemo(() => async () => {
    setPage(null);
    const next = await loadTripPageCourses(source, accessToken);
    setPage(next);
    if (next.state === 'ready') {
      const own = next.courses.findIndex((course) => course.id === next.confirmedCourseId);
      // 확정한 코스가 있으면 그것을, 없으면 첫 안을 켠다 — 시안 3a 는 코스 A 가 켜진 채로 열린다.
      setCourseIndex(own >= 0 ? own : 0);
      setConfirmed(own >= 0);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceKey, accessToken]);
  useEffect(() => { void load(); }, [load]);

  const courses = useMemo(() => (page?.state === 'ready' ? page.courses : []), [page]);
  const course: TripCourse | null = courses[courseIndex] ?? null;
  const tripId = page?.state === 'ready' ? page.tripId : '';

  // 고른 코스의 일정을 받는다 — 카드의 비용·추정·구간 시간은 코스 요약이 아니라 일정 항목에 있다.
  useEffect(() => {
    const id = course?.itineraryId;
    // 🔴 아직 일정이 없는 안(2안·3안)은 서버가 같은 모양으로 실어 보낸 미리보기를 그린다 (S15P21E201-1454).
    //    받을 일정이 없다고 비워 두면 그 안을 눌렀을 때 카드와 지도가 통째로 빈다.
    if (!id) { setItinerary(course?.preview ? { id: course.id, value: course.preview, message: null } : null); return; }
    let alive = true;
    // 🔴 (옮기며 바꾼 것 ①) 같은 일정을 다시 받을 때는 지금 것을 비우지 않는다. 비우면 편집 한 번에
    //    화면 전체가 뼈대(Skeleton)로 깜박인다 — 「지워졌나」로 읽힌다.
    setItinerary((prev) => (prev?.id === id ? prev : { id, value: null, message: null }));
    void loadItinerary(id, accessToken).then((next) => {
      if (!alive) return;
      setItinerary({ id, value: next.state === 'success' ? next.itinerary : null, message: next.state === 'success' ? null : next.message });
    });
    return () => { alive = false; };
  }, [course?.itineraryId, course?.id, course?.preview, accessToken, itineraryNonce]);

  const loaded = itinerary?.value ?? null;
  // 코스·일정이 바뀌면 1일차 첫 곳으로 돌아간다 — 열린 채로 내용만 갈리면 무엇을 보고 있는지 모른다.
  useEffect(() => { setDayIndex(0); }, [loaded?.id]);
  const items: ItineraryItemDto[] = useMemo(() => loaded?.days[dayIndex]?.items ?? [], [loaded, dayIndex]);
  // 🔴 (옮기며 바꾼 것 ②) 고른 곳이 새 목록에도 있으면 그대로 둔다. 고정 하나 눌렀다고 지도가
  //    첫 장소로 튀어 가면, 방금 누른 장소가 화면에서 사라진다.
  useEffect(() => { setSelectedId((prev) => (items.some((item) => item.id === prev) ? prev : items[0]?.id ?? '')); }, [items]);

  useEffect(() => {
    if (!loaded) return;
    let alive = true;
    void loadPlacePhotos(loaded.days.flatMap((day) => day.items.map((item) => item.placeId))).then((next) => { if (alive) setPhotos(next); });
    return () => { alive = false; };
  }, [loaded]);

  // 🔴 도착 기록은 일정 판(version)을 안 올린다(itinerary.tsx 주석). 그래서 판만 보고 다시 받으면
  //    「도착 찍기」 뒤에 예상 도착이 영영 안 바뀐다 — paceNonce 로 따로 깨운다.
  useEffect(() => {
    // 미리보기(2안·3안)는 아직 일정이 아니라 물어볼 일정 번호가 없다 — 부르면 404 만 돌아온다.
    if (!loaded || !course?.itineraryId) { setPace(null); return; }
    let alive = true;
    void loadItineraryPace(loaded.id, dayIndex, accessToken).then((next) => { if (alive) setPace(next.state === 'success' ? next.pace : null); });
    return () => { alive = false; };
  }, [loaded?.id, loaded?.version, course?.itineraryId, dayIndex, accessToken, paceNonce]);

  useEffect(() => {
    if (!tripId) return;
    let alive = true;
    void loadTripBudget(tripId, accessToken).then((next) => { if (alive) setBudgetKrw(next.state === 'success' ? next.budgetKrw : null); });
    return () => { alive = false; };
  }, [tripId, accessToken]);

  // ── 지도 ────────────────────────────────────────────────────────────────
  const map = useMemo(() => dayMap(items, dayIndex + 1), [items, dayIndex]);
  // 하루 끝 — 숙소(마지막 날은 출발지)로 돌아가는 선과 표식(S15P21E201-1567). 그 구간의 길도 같이 받아 온다.
  const returnLeg = loaded?.days[dayIndex]?.returnLeg ?? null;
  const back = useMemo(() => returnTrip(map, dayIndex + 1, returnLeg), [map, dayIndex, returnLeg]);
  const legDays = useMemo(() => (back ? [...map.days, back.day] : map.days), [map, back]);
  const legs = useCourseRoutePaths(legDays, accessToken);
  const routes = useMemo(
    () => [...dayRoutes(map, dayIndex + 1, color.brand.navy, legs), ...(back ? [returnRoute(back, color.brand.navy, legs)] : [])],
    [map, dayIndex, legs, back],
  );
  const points = useMemo(
    () => (back ? [{ id: 'return', label: back.kind === 'LODGING' ? tx('숙소', 'Stay') : tx('출발지', 'Start'), color: color.brand.navy, stops: [back.anchor] }] : []),
    [back, tx],
  );
  const anyEstimatedLine = routes.some((route) => route.estimated !== false);

  // ── 요약 숫자 ──────────────────────────────────────────────────────────
  const allItems = useMemo(() => loaded?.days.flatMap((day) => day.items) ?? [], [loaded]);
  const travelTotal = totalTravelMinutes(allItems);
  const categoryByPlaceId = useMemo(() => Object.fromEntries(Object.entries(photos).map(([placeId, photo]) => [placeId, photo.category])), [photos]);
  const budget = useMemo(() => (loaded ? summarizeItineraryBudget(loaded, categoryByPlaceId, budgetKrw) : null), [loaded, categoryByPlaceId, budgetKrw]);
  const atRisk = useMemo(() => items.filter((item) => pace?.atRiskItemIds.includes(item.id)), [items, pace]);
  const allEstimated = items.length > 0 && items.every((item) => item.dataStatus !== 'VERIFIED');

  const title = humanTripTitle(loaded?.title) ?? tx('부산 여행', 'Busan trip');
  const firstDate = loaded?.days[0]?.date;
  const headSub = loaded ? [
    firstDate ? (formatDayHeading(firstDate, locale) ?? firstDate) : null,
    loaded.days.length > 1 ? txf(tx, '%s일', '%s days', loaded.days.length) : null,
    txf(tx, '%s곳', '%s places', allItems.length),
    budget && budget.known > 0 ? txf(tx, '약 %s', 'about %s', formatManwon(budget.krw, tx)) : null,
    travelTotal > 0 ? txf(tx, '이동 %s분', '%s min travel', travelTotal) : null,
  ].filter(Boolean).join(' · ') : '';

  // ── 확정 ────────────────────────────────────────────────────────────────
  const confirm = async (target: TripCourse) => {
    // `confirming` 은 다음 그리기까지 안 바뀐다 — 그 사이 두 번째 누름이 들어오면 서버가 같은 안을 둘
    // 만들 수 있어서, 곧바로 바뀌는 잠금을 따로 둔다.
    if (!canConfirmCourse(target) || confirmingNow.current) return;
    confirmingNow.current = true;
    setConfirming(true);
    // 🔴 2안·3안은 아직 일정이 없다 — 고른 지금 서버가 만든다 (S15P21E201-1454). 못 만들면 카드 자리에
    //    오류와 「다시 시도」를 띄운다. 눌렀는데 아무 일도 안 나는 버튼은 없는 버튼보다 나쁘다.
    const made = await ensureCourseItinerary(tripId, target, accessToken);
    confirmingNow.current = false;
    if (made.state !== 'success') {
      setConfirming(false);
      setItinerary({ id: target.id, value: null, message: made.message });
      return;
    }
    const id = made.itineraryId;
    const path = `/trips/${id}/itinerary`;
    try {
      const [trips, alreadyAsked] = await Promise.all([loadTrips(accessToken), wasTripNameAsked(tripId)]);
      const currentTitle = trips.state === 'success' ? trips.trips.find((trip) => trip.tripId === tripId)?.title : null;
      // 코스를 고른 직후에만 이름 묻기가 열린 채로 들어간다 — recommendations.tsx 와 같은 규칙.
      if (shouldAskTripName({ title: currentTitle, alreadyAsked })) { router.push(`${path}?name=1`); return; }
    } catch {
      // 물어볼지 정하다 실패하면 묻지 않고 지나간다. 일정을 여는 길을 막지 않는다.
    } finally {
      setConfirming(false);
    }
    // 같은 일정이면 주소가 같다 — 옮기지 않고 확정 표시만 켠다.
    if (source.kind === 'itinerary' && source.itineraryId === id) { setConfirmed(true); return; }
    router.push(path);
  };

  return {
    page, load, courses, course, courseIndex, setCourseIndex, confirmed, setConfirmed, tripId,
    itinerary, setItinerary, loaded, reloadItinerary: () => setItineraryNonce((n) => n + 1),
    dayIndex, setDayIndex, items, selectedId, setSelectedId, photos, pace, reloadPace: () => setPaceNonce((n) => n + 1),
    map, routes, points, anyEstimatedLine, allItems, travelTotal, budget, atRisk, allEstimated,
    title, headSub, confirm, confirming,
  };
}
