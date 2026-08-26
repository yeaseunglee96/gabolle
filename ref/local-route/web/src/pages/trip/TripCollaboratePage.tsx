import { RoutePlaceholder } from "../../components/RoutePlaceholder";

/**
 * /trips/:tripId/collaborate
 * 페이지 트리(routeTree.ts)의 "trip.collaborate" 노드에 해당하는 화면.
 * 구현 시 RoutePlaceholder 를 실제 UI 로 교체하고, routeTree 의 status 를 DONE 으로 바꾼다.
 */
export function TripCollaboratePage() {
  return <RoutePlaceholder routeId="trip.collaborate" />;
}
