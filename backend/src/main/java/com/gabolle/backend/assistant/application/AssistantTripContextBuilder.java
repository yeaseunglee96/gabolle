package com.gabolle.backend.assistant.application;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.itinerary.application.ItineraryQueryService;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;

/**
 * 챗봇에 얹을 하루치 일정 요약. 회원 여부는 ItineraryQueryService.getDetail 이 이미
 * 확인하므로 여기서 다시 보지 않는다.
 *
 * 장소명·시각·도보거리만 담는다. TripConstraint(건강·식이 제약)는 아예 참조하지 않는다 —
 * 섞으려면 HEALTH_CONSTRAINTS 동의를 따로 확인해야 하므로 그 필요 자체를 없앴다.
 */
@Component
@Profile({ "db", "dev" })
public class AssistantTripContextBuilder {

	private final ItineraryQueryService itineraryQueryService;

	public AssistantTripContextBuilder(ItineraryQueryService itineraryQueryService) {
		this.itineraryQueryService = itineraryQueryService;
	}

	/** dayIndex 가 일수 범위 밖이면 400, 일정이 없거나 회원이 아니면 404 다. */
	public String build(String itineraryId, int dayIndex, UUID requesterUserId) {
		ItineraryDetailResponse detail = this.itineraryQueryService.getDetail(itineraryId,
				requesterUserId.toString());

		List<ItineraryDetailResponse.Day> days = detail.days();
		if (dayIndex < 0 || dayIndex >= days.size()) {
			throw new IllegalArgumentException(
					"dayIndex 는 0 이상 " + days.size() + " 미만이어야 합니다.");
		}

		ItineraryDetailResponse.Day day = days.get(dayIndex);
		StringBuilder summary = new StringBuilder(day.date()).append(" 일정:\n");
		for (ItineraryDetailResponse.Item item : day.items()) {
			summary.append("- ");
			if (item.startsAt() != null) {
				summary.append(item.startsAt()).append(" ");
			}
			summary.append(item.title());
			if (item.walkingMeters() != null) {
				summary.append(" (도보 ").append(item.walkingMeters()).append("m)");
			}
			summary.append("\n");
		}
		return summary.toString();
	}
}
