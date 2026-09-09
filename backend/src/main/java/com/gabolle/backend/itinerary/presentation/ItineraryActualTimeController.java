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
 * 방문지에 실제로 도착·출발한 시각을 적는다 — S15P21E201-293.
 *
 * <h2>🔴 {@link ItineraryEditController} 에 넣지 않았다</h2>
 * 그 컨트롤러의 동작은 <b>전부 새 판을 만드는 것</b>이다(고정·장소 더하기·되돌리기). 실제
 * 시각 기록은 판 체인 밖에 있어서 판을 만들지도, 낡은 판을 409 로 막지도 않는다. 같은
 * 클래스에 두면 "이것도 판을 만드나" 를 읽는 사람이 메서드마다 확인해야 한다 — 판을 만드는
 * 것과 만들지 않는 것을 파일로 갈라 둔다.
 *
 * <h2>{@code PUT} 인 이유</h2>
 * 같은 방문지에 다시 보내면 마지막 값이 남는다({@code -293} 완료 기준). 같은 요청을 두 번
 * 보내도 결과가 같으므로 {@code POST} 가 아니라 {@code PUT} 이다 — 통신이 끊겨 재전송하는
 * 화면이 중복을 걱정하지 않아도 된다. 그래서 응답도 201 이 아니라 200 이다: 처음 적는 것과
 * 고치는 것을 구분해 답하면 화면이 그 차이를 다뤄야 하는데, 실제로 다르게 처리할 것이 없다.
 *
 * <p>응답은 조회({@link ItineraryQueryController#get})와 <b>같은 모양</b>이다 — 앱은 편집
 * 응답을 일정 전체로 받아 화면 상태에 그대로 넣는 방식으로 이미 만들어져 있고
 * ({@link ItineraryEditController} 주석의 실측), 그 습관을 여기서 깨면 화면이 방금 적은
 * 시각을 보려고 조회를 한 번 더 불러야 한다. 판 번호({@code version})는 안 변한 값이 그대로
 * 실린다 — 이 요청이 판을 만들지 않았다는 사실이 응답에서도 보인다.
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
	 * 보낸 것이 그 방문지의 최종 상태다 — 도착만 보내면 출발은 {@code null} 이 된다(부분
	 * 갱신이 아니다).
	 *
	 * <p>{@code itemKey} 는 항목의 PK 가 아니라 판을 건너 살아남는 이름이다 — 조회 응답의
	 * {@code items[].id} 로 내려가는 값 그대로다.
	 *
	 * <p>기록과 조회를 <b>서로 다른 트랜잭션</b>으로 둔다. 저장이 커밋된 뒤에 읽어야 화면이
	 * 받는 것이 실제로 저장된 값이다({@link ItineraryEditController} 가 편집에서 하는 것과
	 * 같은 판단).
	 */
	@PutMapping("/{itineraryId}/items/{itemKey}/actual")
	public ApiResponse<ItineraryDetailResponse> recordActualTime(
			@PathVariable String itineraryId,
			@PathVariable String itemKey,
			@RequestBody RecordActualTimeRequest request,
			Authentication authentication) {

		// 요청자를 X-User-Id 헤더가 아니라 인증 주체에서 정한다 — 헤더는 부르는 쪽이 정하는
		// 값이라 그것을 믿으면 남의 이름으로 기록을 남길 수 있다(S15P21E201-610).
		String recorder = AuthenticatedUsers.requireId(authentication).toString();

		this.actualTimeService.record(itineraryId, itemKey,
				request.arrivedAtInstant(), request.departedAtInstant(), recorder);

		ItineraryDetailResponse detail = this.queryService.getDetail(itineraryId, recorder);
		return ApiResponse.success(detail, "req_" + UUID.randomUUID());
	}
}
