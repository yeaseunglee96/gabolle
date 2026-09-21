package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행 삭제. 소유자만 지운다 — 동행자가 목록에서 치우는 것은
 * {@link TripMemberService#remove} 가 맡는다.
 *
 * <p>없는 여행과 회원이 아닌 여행은 같은 404 다. 존재 여부가 새지 않게 하기 위해서이고,
 * 403 은 회원이지만 소유자가 아닌 경우뿐이다.
 *
 * <p>회원 판정을 {@link TripQueryService#get} 에 맡기지 않고 여기서 직접 읽는다. 그 메서드는
 * 지운 여행도 없는 것으로 답하는데, 삭제는 반대로 이미 지워진 여행을 볼 수 있어야 재시도를
 * 성공으로 답할 수 있다.
 */
@Service
public class TripDeletionService {

	private final TripRepository repository;

	private final Clock clock;

	public TripDeletionService(TripRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * 여행을 지운다. 행은 남고 지운 시각만 찍힌다. 일정·초대·공유 링크가 함께 닫히는 것은
	 * 그 경로들이 전부 {@link TripQueryService#get} 을 지나기 때문이다.
	 *
	 * <p>두 번 보내도 두 번째가 오류가 아니다. 이미 지워져 있으면 아무것도 하지 않고, 지운
	 * 시각도 덮어쓰지 않는다 — 처음 지운 때가 사실이다.
	 *
	 * @throws TripQueryService.TripNotFoundException 없는 여행이거나 요청자가 회원이 아니다 — 둘 다 404
	 * @throws TripDeleteForbiddenException 회원이지만 소유자가 아니다 — 403
	 */
	@Transactional
	public void delete(String tripId, String requesterUserId) {
		Trip trip = this.repository.findById(tripId)
				.orElseThrow(() -> new TripQueryService.TripNotFoundException(tripId));

		// 회원이 아니면 없는 여행과 같은 답이다.
		TripMember membership = this.repository.findMembers(tripId).stream()
				.filter(member -> member.userId().equals(requesterUserId))
				.findFirst()
				.orElseThrow(() -> new TripQueryService.TripNotFoundException(tripId));

		// 역할 판정이 먼저다. 이미 지워진 여행이라고 동행자에게 성공을 돌려주면 화면이
		// 삭제 버튼을 계속 띄운다.
		if (membership.role() != TripMember.Role.OWNER) {
			throw new TripDeleteForbiddenException();
		}
		if (trip.isDeleted()) {
			return;
		}

		trip.markDeleted(Instant.now(this.clock));
		this.repository.softDelete(trip);
	}

	/** 회원이지만 그 여행의 소유자가 아니다. */
	public static class TripDeleteForbiddenException extends RuntimeException {
		public TripDeleteForbiddenException() {
			super("여행 소유자만 여행을 지울 수 있어요. 동행자는 참여자에서 빠지면 목록에서 사라져요.");
		}
	}
}
