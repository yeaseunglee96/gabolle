package com.gabolle.backend.assistant.application;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.itinerary.application.ItineraryQueryService;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;

/**
 * 챗봇에 얹을 "하루치 일정" 요약 — S15P21E201-987.
 *
 * <p>{@link ItineraryQueryService#getDetail} 이 이미 회원 여부를 확인한다({@code
 * ItineraryQueryController.ItineraryNotFoundException} 으로 막는다) — 여기서 다시 확인하지
 * 않는다.
 *
 * <p>🔴 장소명·시각·도보거리만 담는다. {@code TripConstraint}(알레르기 등 건강·식이 제약)는
 * 이 클래스가 아예 참조하지 않는다 — 건강 정보를 여기 섞으려면 {@code HEALTH_CONSTRAINTS}
 * 동의를 별도로 확인해야 하는데, 지금 범위에서는 그 필요 자체가 없도록 데이터를 좁혔다.
 */
@Component
@Profile({ "db", "dev" })
public class AssistantTripContextBuilder {

	private final ItineraryQueryService itineraryQueryService;

	public AssistantTripContextBuilder(ItineraryQueryService itineraryQueryService) {
		this.itineraryQueryService = itineraryQueryService;
	}

	/**
	 * @throws IllegalArgumentException {@code dayIndex} 가 그 일정의 일수 범위 밖이다 — 400
	 * @throws com.gabolle.backend.itinerary.presentation.ItineraryQueryController.ItineraryNotFoundException
	 *     그 일정이 없거나 요청자가 회원이 아니다 — 404
	 */
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
