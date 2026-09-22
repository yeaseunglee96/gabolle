package com.gabolle.backend.itinerary.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;

/**
 * 일정 접근 권한 판정을 한 곳에 모은다.
 * 편집 경로가 고정·해제·제외·재계산·되돌리기로 늘어나는데, "이 일정이 있는가"·"요청자가 그
 * 일정이 속한 여행의 회원인가"·"그 역할로 고칠 수 있는가" 세 판정을 서비스마다 복사하면 새
 * 경로를 추가하는 사람이 언젠가 한 곳을 빠뜨린다. 그 빠뜨림은 버그가 아니라 인가 우회다.
 * {@link ItineraryQueryController.ItineraryNotFoundException}(404)과
 * {@link ItineraryForbiddenException}(403)을 구분해 던지는 것도 이 클래스의 책임이다 —
 * 조회(회원이면 누구나)와 편집(OWNER·EDITOR 만)이 서로 다른 문턱이다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryAccess {

	private final ItineraryRepository itineraryRepository;

	private final TripQueryService tripQueryService;

	public ItineraryAccess(ItineraryRepository itineraryRepository, TripQueryService tripQueryService) {
		this.itineraryRepository = itineraryRepository;
		this.tripQueryService = tripQueryService;
	}

	/** 일정 · 그 일정이 속한 여행 · 요청자의 역할을 함께 담는다. */
	public record Access(Itinerary itinerary, Trip trip, TripMember.Role role) {
	}

	/**
	 * 그 일정을 볼 수 있는가. 여행의 회원이면(OWNER·EDITOR·VIEWER 무엇이든) 통과한다.
	 *
	 * @throws ItineraryQueryController.ItineraryNotFoundException 일정이 없거나, 있어도 요청자가
	 *     그 일정이 속한 여행의 회원이 아니다. 둘을 같은 404 로 답한다 — 비회원에게는 그 일정이
	 *     있다는 사실 자체를 알려주지 않는다
	 */
	@Transactional(readOnly = true)
	public Access requireMember(String itineraryId, String userId) {
		Itinerary itinerary = this.itineraryRepository.findById(itineraryId)
				.orElseThrow(() -> new ItineraryQueryController.ItineraryNotFoundException(itineraryId));

		TripQueryService.View view;
		try {
			view = this.tripQueryService.get(itinerary.tripId(), userId);
		}
		catch (TripQueryService.TripNotFoundException e) {
			// 있는데 너는 못 본다(403)를 알려주지 않는다 — 존재 자체를 감춘다.
			throw new ItineraryQueryController.ItineraryNotFoundException(itineraryId);
		}

		return new Access(itinerary, view.trip(), view.role());
	}

	/**
	 * 그 일정을 고칠 수 있는가. 회원이라도 VIEWER 면 막는다.
	 *
	 * @throws ItineraryQueryController.ItineraryNotFoundException {@link #requireMember} 와 같다
	 * @throws ItineraryForbiddenException 회원이지만 역할이 VIEWER 라 편집 권한이 없다 — 403.
	 *     이때는 404 로 감추지 않는다. VIEWER 는 이 일정을 볼 수 있는 사람이라 존재를 감출 이유가
	 *     없고, 조회가 이미 성공했던 화면에 404 를 주면 화면이 상태를 잃는다
	 */
	@Transactional(readOnly = true)
	public Access requireEditor(String itineraryId, String userId) {
		Access access = requireMember(itineraryId, userId);
		if (!access.role().canEdit()) {
			throw new ItineraryForbiddenException(itineraryId, access.role());
		}
		return access;
	}

	/** 회원이지만 VIEWER 라 이 일정을 고칠 수 없다. */
	public static class ItineraryForbiddenException extends RuntimeException {

		private final String itineraryId;

		private final TripMember.Role role;

		public ItineraryForbiddenException(String itineraryId, TripMember.Role role) {
			super("보기 전용 권한이라 일정을 고칠 수 없어요.");
			this.itineraryId = itineraryId;
			this.role = role;
		}

		public String itineraryId() {
			return this.itineraryId;
		}

		public TripMember.Role role() {
			return this.role;
		}
	}
}
