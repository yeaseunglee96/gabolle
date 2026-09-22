// 코스 한 안을 지도에 그릴 모양으로 —-1333.
//
// 🔴 **좌표가 없는 정차지는 안 그린다.** 좌표 없이 선을 그으면 실제로 안 가는 길을 그리게
//    되고, 그건 빈 지도보다 나쁘다. 하나도 없으면 빈 것을 돌려주고, 화면은 그때 지도 대신
//    동선을 글로 세운다.
import { color } from '@/design/tokens';
import type { MapRouteLayer } from '@/map/RouteMap';
import type { MapStop } from '@/map/types';
import { legKey, type LegPath } from '@/map/courseRoutePaths';
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

export function courseMapLayers(course: TripCourse | null | undefined, legs?: Record<string, LegPath>): CourseMap {
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

    // 🔴 **하루를 선 하나로 긋지 않고 구간마다 나눈다.** 어떤 구간은 실제 길을 받고 어떤
    //    구간은 못 받는데(대중교통은 경로를 주는 API 가 아직 없다), 하나로 이으면 그 둘을
    //    같은 선으로 그리게 된다. 실제로 안 가는 길을 실선으로 그리는 것이 제일 나쁘다.
    //
    //    경로를 하나도 안 받았을 때도 결과는 예전과 같다 — 같은 색 점선이 이어질 뿐이다.
    for (let i = 0; i + 1 < dayStops.length; i += 1) {
      const leg = legs?.[legKey(day.day, i)];
      routes.push({
        id: `day-${day.day}-leg-${i}`,
        color: dayColor(dayIndex),
        stops: [dayStops[i], dayStops[i + 1]],
        path: leg?.path,
        // 🔴 모르면 추정 쪽으로 기운다. 실제 길인지 아닌지는 서버가 말해 준다.
        estimated: leg ? leg.estimated : true,
      });
    }

    // 정차가 하나뿐인 날은 그릴 구간이 없다. 점만 남는다.
  });

  // 🔴 **점이 있으면 지도를 그린다.** 선 개수로 판정하면 정차가 하루 한 곳뿐인 코스가
  //    지도를 통째로 잃는다 — 구간이 없을 뿐 그릴 점은 있다. 원래 지키려던 것은
  //    「좌표가 하나도 없으면 빈 것」이고, 그건 점으로 재는 것이 맞다.
  return stops.length ? { stops, routes } : EMPTY;
}
