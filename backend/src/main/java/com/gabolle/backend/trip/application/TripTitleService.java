package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행에 이름을 붙이거나 지운다 — S15P21E201-1023.
 *
 * <h2>🔴 권한은 여기서 다시 만들지 않는다</h2>
 *
 * 첫 줄이 {@link TripQueryService#get(String, String)} 이다. 그것이 <b>참여자가 아니면
 * 404</b>(존재를 감춘다)를 던지는 유일한 자리라, 여기서 소유자 ID 를 직접 비교하면 같은
 * 판정이 두 곳에 생기고 언젠가 한쪽만 바뀐다. {@code TripItineraryService} 가 같은
 * 이유로 같은 모양이다.
 *
 * <h2>🔴 이름은 OWNER·EDITOR 만 바꾼다</h2>
 *
 * 보기만 하는 동행자(VIEWER)가 남의 여행 이름을 바꾸면, 만든 사람의 목록에서 자기 여행이
 * <b>다른 이름으로 보인다.</b> 초대 발급({@code TripCollaborationController})이 이미 같은
 * 선을 긋고 있어 같은 선을 쓴다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripTitleService {

	private final TripQueryService tripQueryService;

	private final TripRepository repository;

	private final Clock clock;

	public TripTitleService(TripQueryService tripQueryService, TripRepository repository, Clock clock) {
		this.tripQueryService = tripQueryService;
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * @param title 비었거나 공백뿐이면 이름을 <b>지운다</b>. 지우기 전용 경로를 따로 두지
	 *     않는 이유는 {@link Trip#rename(String, Instant)} 의 javadoc 에 있다
	 * @return 이름이 반영된 여행
	 */
	@Transactional
	public Trip rename(String tripId, String requesterUserId, String title) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterUserId);
		if (view.role() == TripMember.Role.VIEWER) {
			throw new TitleChangeForbiddenException(tripId);
		}

		Trip trip = view.trip();
		// 🔴 길이·제어문자 규칙은 도메인이 본다. 여기서 다시 보면 규칙이 두 곳에 생긴다.
		trip.rename(title, Instant.now(this.clock));
		this.repository.updateTitle(trip);
		return trip;
	}

	/** 보기 전용 동행자가 이름을 바꾸려 했다. 🔴 404 가 아니라 403 이다 — 여행의 존재는 이미 안다. */
	public static class TitleChangeForbiddenException extends RuntimeException {

		public TitleChangeForbiddenException(String tripId) {
			super("이 여행의 이름을 바꿀 권한이 없습니다: tripId=" + tripId);
		}
	}
}
