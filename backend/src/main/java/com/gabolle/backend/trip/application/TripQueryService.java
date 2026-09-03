package com.gabolle.backend.trip.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행 조회 — S15P21E201-461 완료 기준
 * <b>"그 식별자로 조회하면 보낸 조건이 그대로 나온다"</b>.
 *
 * <p>{@link TripCreationService} 와 분리한 이유 — 생성은 멱등 저장이라는 별도 규칙이
 * 있고, 조회는 그 규칙과 무관하다. 하나로 합치면 조회를 부를 때도 멱등 로직이 딸려온다.
 */
@Service
public class TripQueryService {

	private final TripRepository repository;

	public TripQueryService(TripRepository repository) {
		this.repository = repository;
	}

	/**
	 * 여행 한 건과 그에 딸린 것을 함께 읽는다.
	 *
	 * <p>🔴 <b>비회원에게는 존재 자체를 확인해 주지 않는다.</b> {@code TripMember} 의
	 * 문서가 말하는 대로 조회 권한 판정은 그 표를 본다 — 회원이 아니면 "권한 없음(403)"
	 * 이 아니라 "없음(404)" 으로 답한다. 403 은 "이 여행은 있는데 너는 못 본다" 를
	 * 확인해 주는 것과 같아서, 존재 여부 자체가 새는 것을 막지 못한다.
	 */
	@Transactional(readOnly = true)
	public View get(String tripId, String requesterUserId) {
		Trip trip = this.repository.findById(tripId)
				.orElseThrow(() -> new TripNotFoundException(tripId));

		List<TripMember> members = this.repository.findMembers(tripId);
		boolean isMember = members.stream().anyMatch(m -> m.userId().equals(requesterUserId));
		if (!isMember) {
			throw new TripNotFoundException(tripId);
		}

		List<TripConstraint> constraints = this.repository.findConstraints(tripId);
		PreferenceSnapshot snapshot = this.repository.findLatestSnapshot(tripId).orElse(null);

		return new View(trip, constraints, snapshot);
	}

	public record View(Trip trip, List<TripConstraint> constraints, PreferenceSnapshot snapshot) {
	}

	/** 없는 여행이거나, 있어도 요청자가 그 여행의 회원이 아니다. 둘을 구분해 응답하지 않는다. */
	public static class TripNotFoundException extends RuntimeException {
		public TripNotFoundException(String tripId) {
			super("여행을 찾을 수 없습니다: " + tripId);
		}
	}
}
