package com.gabolle.backend.trip.application;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.application.port.TripCoverPort;

/**
 * 여행 조회. {@link TripCreationService} 와 나눠 둔 것은 생성에만 있는 멱등 저장 규칙이
 * 조회에 딸려오지 않게 하기 위해서다.
 */
@Service
public class TripQueryService {

	private final TripRepository repository;

	private final TripCoverPort coverPort;

	/** 표지 없이. 표지를 안 보는 부름과 시험이 쓰는 짧은 길이다. */
	public TripQueryService(TripRepository repository) {
		this(repository, TripCoverPort.NONE);
	}

	/**
	 * 🔴 표지 포트를 {@link ObjectProvider} 로 받는다 — 구현이 {@code db}·{@code dev}
	 * 프로파일에만 있어서, 직접 받으면 그 밖의 프로파일에서 문맥이 아예 안 뜬다.
	 * {@code ItineraryDraftService} 가 {@code RouteOrderPort} 를 같은 이유로 같게 받는다.
	 */
	@Autowired
	public TripQueryService(TripRepository repository, ObjectProvider<TripCoverPort> coverPort) {
		this(repository, coverPort.getIfAvailable(() -> TripCoverPort.NONE));
	}

	private TripQueryService(TripRepository repository, TripCoverPort coverPort) {
		this.repository = repository;
		this.coverPort = coverPort;
	}

	/**
	 * 여행 한 건과 그에 딸린 것을 함께 읽는다.
	 *
	 * <p>회원이 아니면 403 이 아니라 404 다 — 403 은 그 식별자의 여행이 있다는 것을 확인해 준다.
	 * 지운 여행도 같은 404 다. 삭제가 {@code deleted_at} 만 찍는 방식이라 이 검사가 없으면
	 * 식별자를 아는 사람이 계속 열고 고칠 수 있다.
	 *
	 * <p>두 검사가 여기 있는 것은 이 메서드가 여행에 닿는 공통 관문이기 때문이다 — 일정
	 * 열람·편집, 초대와 역할 변경, 공유 링크 발급, 추천 요청이 전부 여기를 지난다.
	 */
	@Transactional(readOnly = true)
	public View get(String tripId, String requesterUserId) {
		Trip trip = this.repository.findById(tripId)
				.orElseThrow(() -> new TripNotFoundException(tripId));
		if (trip.isDeleted()) {
			throw new TripNotFoundException(tripId);
		}

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
	 * 내 여행 목록. {@code limit} 은 0 과 {@link #MAX_LIST_SIZE} 사이로 자른다.
	 *
	 * <p>여기서 권한을 다시 판정하지 않는다. 저장소가 참여 표를 기준으로 고르므로 이미 전부
	 * 요청자가 회원인 여행이고, 판정이 두 곳에 있으면 {@link #get} 과 갈라져 목록에는 보이는데
	 * 못 여는 여행이 생긴다.
	 */
	@Transactional(readOnly = true)
	public List<TripRepository.MemberTrip> list(String requesterUserId, int limit) {
		return this.repository.findTripsForMember(requesterUserId, Math.min(Math.max(limit, 0), MAX_LIST_SIZE));
	}

	/**
	 * 내 여행 목록에 <b>표지</b>(대표 사진·첫 방문지 이름)를 얹어서 돌려준다.
	 *
	 * <p>🔴 <b>표지는 여행 수와 무관하게 질의 한 번이다.</b> 줄마다 일정 → 장소 → 사진을
	 * 따로 부르면 여행 50개에 질의 150번이 나간다. {@link TripCoverPort} 가 목록을 통째로
	 * 받는 모양인 이유가 그것이다 (S15P21E201-1370).
	 *
	 * <p>표지를 못 구한 여행도 <b>빠지지 않고 그대로 나온다</b> — 표지는 장식이라, 사진이
	 * 없다고 여행이 목록에서 사라지면 그게 훨씬 큰 고장이다. 그런 줄의 {@code cover} 는
	 * {@code null} 이다. 실서버에서는 이쪽이 오히려 다수다(2026-09-21 기준 59건 중 41건만
	 * 첫 방문지가 정해져 있고, 사진까지 있는 것은 10건).
	 */
	@Transactional(readOnly = true)
	public List<Listing> listWithCovers(String requesterUserId, int limit) {
		List<TripRepository.MemberTrip> rows = list(requesterUserId, limit);
		if (rows.isEmpty()) {
			return List.of();
		}

		List<String> tripIds = rows.stream().map((row) -> row.trip().tripId()).toList();
		Map<String, TripCoverPort.Cover> covers = this.coverPort.coversOf(tripIds);
		Map<String, String> currents = this.coverPort.currentItinerariesOf(tripIds);

		return rows.stream()
				.map((row) -> new Listing(row, covers.get(row.trip().tripId()), currents.get(row.trip().tripId())))
				.toList();
	}

	/**
	 * 목록 한 줄과 그 줄의 표지·확정 일정. {@code cover} 는 표지를 못 구했을 때, {@code currentItineraryId} 는 일정이
	 * 없을 때 {@code null} 이다.
	 */
	public record Listing(TripRepository.MemberTrip row, TripCoverPort.Cover cover, String currentItineraryId) {
	}

	/**
	 * 목록 한 번에 돌려주는 최대 개수. 한 사람이 들어 있는 여행 수에는 상한이 없어서 필요한
	 * 값이다. 이 값을 넘겨야 하는 날이 오면 이어 보기(cursor)를 붙인다.
	 */
	public static final int MAX_LIST_SIZE = 50;

	/** {@code role} 은 요청자 자신의 역할이다. 일정 접근 판정({@code ItineraryAccess})이 이 값을 그대로 쓴다. */
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
