package com.gabolle.backend.itinerary.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.ItineraryActualTimeService;
import com.gabolle.backend.itinerary.application.ItineraryQueryService;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.presentation.dto.RecordActualTimeRequest;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 방문지에 실제로 도착·출발한 시각을 적는다.
 * {@link ItineraryEditController} 에 넣지 않았다. 그 컨트롤러의 동작은 전부 새 판을 만드는
 * 것인데 실제 시각 기록은 판 체인 밖에 있어서 판을 만들지도, 낡은 판을 409 로 막지도 않는다.
 * 판을 만드는 것과 만들지 않는 것을 파일로 갈라 둔다.
 * {@code PUT} 인 이유는 같은 방문지에 다시 보내면 마지막 값이 남기 때문이다. 같은 요청을 두 번
 * 보내도 결과가 같아 통신이 끊겨 재전송하는 화면이 중복을 걱정하지 않아도 된다. 응답도 201 이
 * 아니라 200 이다 — 처음 적는 것과 고치는 것을 구분해 답해도 화면이 다르게 처리할 것이 없다.
 * 응답은 조회와 같은 모양이다. 앱은 편집 응답을 일정 전체로 받아 화면 상태에 그대로 넣게
 * 만들어져 있고, 그 습관을 여기서 깨면 화면이 방금 적은 시각을 보려고 조회를 한 번 더 불러야
 * 한다. 판 번호는 안 변한 값이 그대로 실린다 — 이 요청이 판을 만들지 않았다는 사실이 응답에서도
 * 보인다.
 */
@RestController
@RequestMapping("/api/v1/itineraries")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryActualTimeController {

	private final ItineraryActualTimeService actualTimeService;

	private final ItineraryQueryService queryService;

	public ItineraryActualTimeController(ItineraryActualTimeService actualTimeService,
			ItineraryQueryService queryService) {
		this.actualTimeService = actualTimeService;
		this.queryService = queryService;
	}

	/**
	 * 보낸 것이 그 방문지의 최종 상태다 — 도착만 보내면 출발은 {@code null} 이 된다(부분 갱신이
	 * 아니다).
	 * {@code itemKey} 는 항목의 PK 가 아니라 판을 건너 살아남는 이름이다 — 조회 응답의
	 * {@code items[].id} 로 내려가는 값 그대로다.
	 * 기록과 조회를 서로 다른 트랜잭션으로 둔다. 저장이 커밋된 뒤에 읽어야 화면이 받는 것이 실제로
	 * 저장된 값이다.
	 */
	@PutMapping("/{itineraryId}/items/{itemKey}/actual")
	public ApiResponse<ItineraryDetailResponse> recordActualTime(
			@PathVariable String itineraryId,
			@PathVariable String itemKey,
			@RequestBody RecordActualTimeRequest request,
			Authentication authentication) {

		// 요청자를 X-User-Id 헤더가 아니라 인증 주체에서 정한다 — 헤더는 부르는 쪽이 정하는
		// 값이라 그것을 믿으면 남의 이름으로 기록을 남길 수 있다.
		String recorder = AuthenticatedUsers.requireId(authentication).toString();

		this.actualTimeService.record(itineraryId, itemKey,
				request.arrivedAtInstant(), request.departedAtInstant(), recorder);

		ItineraryDetailResponse detail = this.queryService.getDetail(itineraryId, recorder);
		return ApiResponse.success(detail, "req_" + UUID.randomUUID());
	}
}
