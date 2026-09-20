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
 * 여행에 이름을 붙이거나 지운다.
 *
 * <p>권한 판정은 {@link TripQueryService#get(String, String)} 에 맡긴다 — 참여자가 아니면
 * 404 로 존재를 감추는 유일한 자리다. 여기서 소유자 ID 를 직접 비교하면 같은 판정이 두 곳에
 * 생긴다.
 *
 * <p>이름은 OWNER·EDITOR 만 바꾼다. VIEWER 까지 열면 만든 사람의 목록에서 자기 여행이 다른
 * 이름으로 보인다.
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
	 * @param title 비었거나 공백뿐이면 이름을 지운다. 지우기 전용 경로를 따로 두지 않는 이유는
	 *     {@link Trip#rename(String, Instant)} 에 있다
	 */
	@Transactional
	public Trip rename(String tripId, String requesterUserId, String title) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterUserId);
		if (view.role() == TripMember.Role.VIEWER) {
			throw new TitleChangeForbiddenException(tripId);
		}

		Trip trip = view.trip();
		// 길이·제어문자 규칙은 도메인이 본다. 여기서 다시 보면 규칙이 두 곳에 생긴다.
		trip.rename(title, Instant.now(this.clock));
		this.repository.updateTitle(trip);
		return trip;
	}

	/** VIEWER 가 이름을 바꾸려 했다. 404 가 아니라 403 이다 — 여행의 존재는 이미 안다. */
	public static class TitleChangeForbiddenException extends RuntimeException {

		public TitleChangeForbiddenException(String tripId) {
			super("이 여행의 이름을 바꿀 권한이 없습니다: tripId=" + tripId);
		}
	}
}
