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
		TripMember requesterMembership = members.stream()
				.filter(m -> m.userId().equals(requesterUserId))
				.findFirst()
				.orElseThrow(() -> new TripNotFoundException(tripId));

		List<TripConstraint> constraints = this.repository.findConstraints(tripId);
		PreferenceSnapshot snapshot = this.repository.findLatestSnapshot(tripId).orElse(null);

		return new View(trip, constraints, snapshot, requesterMembership.role());
	}

	/**
	 * 내 여행 목록 — S15P21E201-738.
	 *
	 * <p>사용자 제보로 시작한 자리다. "여행 만들기는 되는데 내 여행으로 안 들어가진다" 의
	 * 원인이 화면이 아니라 <b>서버에 목록 기능이 없는 것</b>이었다. 그래서 앱이 목록을
	 * 기기에 따로 적어 두고 있었고, 앱을 지우거나 기기를 바꾸면 여행이 사라졌다.
	 *
	 * <p>🔴 <b>여기서 권한을 다시 판정하지 않는다.</b> 저장소가 참여 표를 기준으로 고르므로
	 * 돌아온 여행은 이미 전부 요청자가 회원인 것이다. 판정을 한 번 더 넣으면 두 곳이
	 * 같은 규칙을 각자 들고 있게 되고, 나중에 한쪽만 바뀐다 — {@link #get} 이 회원 여부로
	 * 404 를 내는 규칙과 여기가 갈라지면 목록에 보이는데 못 여는 여행이 생긴다.
	 */
	@Transactional(readOnly = true)
	public List<TripRepository.MemberTrip> list(String requesterUserId, int limit) {
		return this.repository.findTripsForMember(requesterUserId, Math.min(Math.max(limit, 0), MAX_LIST_SIZE));
	}

	/**
	 * 목록 한 번에 돌려주는 최대 개수 — S15P21E201-738.
	 *
	 * <p>상한을 두는 이유는 한 사람이 들어 있는 여행 수에 상한이 없기 때문이다. 이 값을
	 * 넘겨야 하는 날이 오면 이어 보기(cursor)를 붙인다. 지금 붙이지 않는 이유는 이 목록이
	 * 사람이 만든 여행이라 수백 개가 되는 경로가 없고, 안 쓰는 이어 보기를 먼저 만들면
	 * 그 코드가 검증되지 않은 채로 남기 때문이다.
	 */
	public static final int MAX_LIST_SIZE = 50;

	/**
	 * 🔴 {@code role} — S15P21E201-224. 요청자가 이미 읽어 둔 {@code members} 목록의
	 * 어느 자리에 있는지로 정한다. 추가 질의는 없다 — 그 목록을 다시 훑을 뿐이다.
	 * 이 role 을 일정 접근 판정({@code itinerary.application.ItineraryAccess})이 그대로 쓴다.
	 */
	public record View(Trip trip, List<TripConstraint> constraints, PreferenceSnapshot snapshot,
			TripMember.Role role) {
	}

	/** 없는 여행이거나, 있어도 요청자가 그 여행의 회원이 아니다. 둘을 구분해 응답하지 않는다. */
	public static class TripNotFoundException extends RuntimeException {
		public TripNotFoundException(String tripId) {
			super("여행을 찾을 수 없습니다: " + tripId);
		}
	}
}
