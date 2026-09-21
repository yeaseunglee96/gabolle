// 코스 한 안을 지도에 그릴 모양으로 —-1333.
//
// 🔴 **좌표가 없는 정차지는 안 그린다.** 좌표 없이 선을 그으면 실제로 안 가는 길을 그리게
//    되고, 그건 빈 지도보다 나쁘다. 하나도 없으면 빈 것을 돌려주고, 화면은 그때 지도 대신
//    동선을 글로 세운다.
import { color } from '@/design/tokens';
import type { MapRouteLayer } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import type { TripCourse } from '@/plan/tripCourses';

/**
 * 날짜마다 다른 색.
 *
 * 🔴 여행 지도 화면(`app/(trip)/[id]/map.tsx`)이 쓰는 것과 **같은 팔레트**다 — 두 화면의
 * 「1일차」가 다른 색이면, 같은 여행을 두 곳에서 보는 사람이 다른 것으로 읽는다.
 */
const DAY_PALETTE = [color.action.primary, color.state.success, color.brand.navy, color.text.eyebrow] as const;

export const dayColor = (index: number) => DAY_PALETTE[index % DAY_PALETTE.length];

export type CourseMap = { stops: MapStop[]; routes: MapRouteLayer[] };

const EMPTY: CourseMap = { stops: [], routes: [] };

export function courseMapLayers(course: TripCourse | null | undefined): CourseMap {
  if (!course) return EMPTY;
  const stops: MapStop[] = [];
  const routes: MapRouteLayer[] = [];

  course.days.forEach((day, dayIndex) => {
    const dayStops: MapStop[] = [];
    for (const stop of day.stops) {
      if (stop.lat === null || stop.lng === null) continue;
      dayStops.push({
        // 🔴 하루 안에서 다시 1부터 센다. 지도의 번호는 「그날 몇 번째」이지 여행 전체의
        //    몇 번째가 아니다 — 목록의 「1일차 1·2·3」과 같은 숫자를 봐야 한다.
        //
        // 🔴 빠진 곳이 있어도 1,2,4 처럼 건너뛰지 않는다. 지도에 찍힌 순서를 센다.
        id: `${day.day}-${dayStops.length + 1}`,
        number: dayStops.length + 1,
        name: stop.name,
        latitude: stop.lat,
        longitude: stop.lng,
      });
    }
    if (dayStops.length === 0) return;
    stops.push(...dayStops);
    routes.push({
      id: `day-${day.day}`,
      color: dayColor(dayIndex),
      stops: dayStops,
      // 🔴 **실제 길이 아니라 직선이다.** 서버가 구간 좌표를 안 주므로 점을 곧게 잇는다.
      //    그 사실을 지도에 적게 한다 — 안 적으면 저 선을 걸어갈 수 있는 길로 읽는다.
      estimated: true,
    });
  });

  return routes.length ? { stops, routes } : EMPTY;
}
