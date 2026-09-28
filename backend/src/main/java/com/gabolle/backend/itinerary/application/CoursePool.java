package com.gabolle.backend.itinerary.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand.PlannedPlace;

/**
 * 추천 코스 2안·3안에 넣을 후보의 <b>차례</b> (S15P21E201-1454).
 *
 * <p>일정 조립({@link ItineraryDraftService#assemble})은 받은 목록을 <b>앞에서부터</b> 훑어 자리에
 * 앉힌다. 그래서 차례만 바꿔 넣으면 다른 코스가 나온다 — 앞 코스가 쓴 곳을 뒤로 보내면, 그 곳들은
 * 자리가 남을 때만 들어간다.
 *
 * <p>🔴 <b>사용자가 답한 값은 여기서 안 건드린다.</b> 들어오는 목록은 이미 지역·속도·예산·카테고리
 * 조건을 통과한 후보이고, 여기서 하는 일은 그중 <b>어느 곳을 먼저 볼지</b>뿐이다. 「여유롭게」라고
 * 답한 사람에게 빡빡한 안을 내미는 식으로 가르지 않는다.
 */
final class CoursePool {

	private CoursePool() {
	}

	/**
	 * @param ranked 조건을 통과한 후보, 순위 오름차순
	 * @param pinned 모든 코스에 들어가야 하는 곳 — 사용자가 적은 「꼭 가고 싶은 장소」. 앞 코스가
	 *     썼어도 뒤로 보내지 않는다. 뒤로 보내면 2안부터 그 장소가 빠진다
	 * @param used 앞 코스들이 이미 쓴 곳
	 * @return 꼭 갈 곳 → 아직 안 쓴 곳 → 이미 쓴 곳. 각 무리 안에서는 순위 차례 그대로다
	 */
	static List<PlannedPlace> order(List<PlannedPlace> ranked, Set<UUID> pinned, Set<UUID> used) {
		List<PlannedPlace> first = new ArrayList<>();
		List<PlannedPlace> fresh = new ArrayList<>();
		List<PlannedPlace> reused = new ArrayList<>();
		for (PlannedPlace place : ranked) {
			if (pinned.contains(place.placeId())) {
				first.add(place);
			}
			else if (used.contains(place.placeId())) {
				reused.add(place);
			}
			else {
				fresh.add(place);
			}
		}
		List<PlannedPlace> ordered = new ArrayList<>(ranked.size());
		ordered.addAll(first);
		ordered.addAll(fresh);
		ordered.addAll(reused);
		return ordered;
	}
}
