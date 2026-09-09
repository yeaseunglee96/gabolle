package com.gabolle.backend.share.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.common.security.OpaqueTokens;
import com.gabolle.backend.share.domain.TripShareLink;
import com.gabolle.backend.share.presentation.dto.SharedItineraryResponse;
import com.gabolle.backend.share.repository.TripShareLinkRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 읽기 전용 공유 주소 발급·비로그인 조회 — S15P21E201-330 · -332 (F-COL-03).
 *
 * <p>🔴 <b>왜 소유자만 발급할 수 있는가.</b> 공유 주소는 로그인 없이 아무나 연다. 만드는 것은
 * 곧 "이 여행을 공개한다" 는 결정이고, 그 결정은 여행 주인의 몫이다. 편집자(EDITOR)는 일정을
 * 고칠 수 있어도 공개 여부까지 정할 권한은 없다.
 */
@Service
@Profile({ "db", "dev" })
public class ShareLinkService {

	private final TripRepository tripRepository;

	private final TripQueryService tripQueryService;

	private final TripShareLinkRepository shareLinkRepository;

	private final SharedItineraryAssembler assembler;

	private final Clock clock;

	public ShareLinkService(TripRepository tripRepository, TripQueryService tripQueryService,
			TripShareLinkRepository shareLinkRepository, SharedItineraryAssembler assembler, Clock clock) {
		this.tripRepository = tripRepository;
		this.tripQueryService = tripQueryService;
		this.shareLinkRepository = shareLinkRepository;
		this.assembler = assembler;
		this.clock = clock;
	}

	/**
	 * 공유 주소를 발급한다.
	 *
	 * @throws TripQueryService.TripNotFoundException 없는 여행이거나 요청자가 그 여행의 회원이
	 *     아니다 — 존재 자체를 감춘다({@code TripQueryService.get} 이 그대로 던진다)
	 * @throws ShareForbiddenException 회원이지만 OWNER 가 아니다
	 */
	@Transactional
	public TripShareLink issue(String tripId, String requesterUserId) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterUserId);
		if (view.role() != TripMember.Role.OWNER) {
			throw new ShareForbiddenException();
		}
		TripShareLink link = TripShareLink.issue(UUID.fromString(tripId), OpaqueTokens.generate(),
				UUID.fromString(requesterUserId), this.clock.instant());
		return this.shareLinkRepository.save(link);
	}

	/**
	 * 표로 공유 일정을 연다. 인증 없이 부른다.
	 *
	 * <p>🔴 조회와 열람 수 기록을 같은 트랜잭션에서 한다({@link TripShareLink#recordView} 의
	 * javadoc 이 그 이유를 설명한다) — 완료 기준 "두 번 열면 조회 수가 2".
	 *
	 * <p>🔴 여행 회원 판정은 하지 않는다 — 링크를 아는 사람이 보는 것이 이 기능의 목적이다.
	 *
	 * @throws ShareLinkNotFoundException 없는 표
	 * @throws ShareLinkExpiredException 표는 있으나 만료됐다 — 열람 수를 올리지 않는다
	 * @throws SharedTripNotFoundException 원본 여행이 없거나 지워졌다
	 */
	@Transactional
	public SharedItineraryResponse open(String token) {
		Instant now = this.clock.instant();

		TripShareLink link = this.shareLinkRepository.findByToken(token)
				.orElseThrow(ShareLinkNotFoundException::new);

		if (link.isExpiredAt(now)) {
			throw new ShareLinkExpiredException();
		}

		Trip trip = this.tripRepository.findById(link.getTripId().toString())
				.filter(t -> t.deletedAt() == null)
				.orElseThrow(SharedTripNotFoundException::new);

		link.recordView(now);

		return this.assembler.assemble(trip, link);
	}

	/** 회원이지만 OWNER 가 아니라 공유 주소를 만들 수 없다. */
	public static class ShareForbiddenException extends RuntimeException {
		public ShareForbiddenException() {
			super("여행 소유자만 공유 주소를 만들 수 있어요.");
		}
	}

	/** 없는 표(token). */
	public static class ShareLinkNotFoundException extends RuntimeException {
		public ShareLinkNotFoundException() {
			super("공유 주소를 찾을 수 없어요.");
		}
	}

	/** 표는 있으나 만료됐다 — 404 가 아니라 410 이어야 "있었는데 끝났다" 를 화면이 구분한다. */
	public static class ShareLinkExpiredException extends RuntimeException {
		public ShareLinkExpiredException() {
			super("공유 기간이 끝났어요.");
		}
	}

	/** 표는 유효하나 가리키는 여행이 없거나(soft) 지워졌다. */
	public static class SharedTripNotFoundException extends RuntimeException {
		public SharedTripNotFoundException() {
			super("원본 여행이 삭제됐어요.");
		}
	}
}
