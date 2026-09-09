package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행 삭제 — S15P21E201-746.
 *
 * <h2>왜 이 자리가 비어 있었나</h2>
 *
 * 표에는 삭제 칸({@code trip.deleted_at})이 처음부터 있었고 목록 조회도 그 칸이 채워진
 * 여행을 이미 빼고 있었다. 그런데 <b>그 칸을 채우는 경로가 아무 데도 없었다.</b> 그래서
 * 앱은 "내 여행에서 삭제" 버튼을 아예 그리지 않았고, 잘못 만든 여행이 목록에 영영 남았다.
 *
 * <h2>🔴 소유자만 지운다</h2>
 *
 * 동행자가 지우면 <b>남의 여행이 사라진다.</b> 초대받아 들어온 사람이 목록에서 치우고
 * 싶은 것은 "내 화면에서 안 보이게" 이지 "이 여행을 없애기" 가 아니다. 그 요구는
 * {@link TripMemberService#remove} 가 이미 맡는다(참여자에서 빠지면 목록에서도 사라진다).
 * 둘을 한 버튼으로 합치면 누른 사람의 역할에 따라 결과가 달라지는 버튼이 된다.
 *
 * <h2>🔴 없는 여행과 남의 여행을 같은 404 로 답한다</h2>
 *
 * 비회원에게 403 을 주면 "이 식별자의 여행은 있다" 를 확인해 주는 셈이고, 그것만으로 남의
 * 여행 존재 여부를 훑을 수 있다. 회원이지만 소유자가 아닌 경우만 403 이다 — 그 사람은 이미
 * 여행이 있다는 것을 알고 있으므로 감출 것이 없고, 오히려 404 로 답하면 "방금 본 여행이 왜
 * 없다는 거지" 가 된다.
 *
 * <h2>🔴 회원 판정을 {@link TripQueryService#get} 에 맡기지 않는다</h2>
 *
 * 규칙은 그 메서드와 <b>같다</b>(회원이 아니면 없는 것처럼 404). 그런데 그 메서드는 지운
 * 여행도 없는 것으로 답한다 — 조회를 막는 것이 그 자리의 일이기 때문이다. 삭제는 그
 * 반대여야 한다. <b>이미 지워진 여행을 다시 지우는 요청은 성공으로 답해야</b> 앱이 마음
 * 놓고 재시도할 수 있다. 지하철에서 응답이 끊겨 다시 누르거나, 오래된 목록에서 누르는 것은
 * 오류가 아니라 정상적으로 일어나는 일이다.
 *
 * <p>그래서 이 메서드만 여행을 직접 읽는다. 같은 규칙을 두 번 쓰는 것이 아니라, 지운 여행을
 * <b>볼 수 있어야 하는 유일한 자리</b>라서다.
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
	 * 여행을 지운다. 행은 남고 지운 시각만 찍힌다.
	 *
	 * <p>지우고 나면 그 여행의 일정·초대·공유 링크도 함께 닫힌다. 그 경로들이 전부
	 * {@link TripQueryService#get} 을 지나고, 그 메서드가 지운 여행을 없는 것으로 답하기
	 * 때문이다. 그것이 이 삭제를 실제 삭제로 만든다.
	 *
	 * <p>🔴 <b>같은 요청을 두 번 보내도 두 번째가 오류가 아니다.</b> 이미 지워져 있으면
	 * 아무것도 하지 않고 성공으로 답한다. 지운 시각도 덮어쓰지 않는다 — 처음 지운 때가
	 * 사실이고, 재시도한 때가 아니다.
	 *
	 * @throws TripQueryService.TripNotFoundException 없는 여행이거나 요청자가 그 여행의
	 *     회원이 아니다 — 둘 다 404
	 * @throws TripDeleteForbiddenException 요청자가 회원이지만 소유자가 아니다 — 403
	 */
	@Transactional
	public void delete(String tripId, String requesterUserId) {
		Trip trip = this.repository.findById(tripId)
				.orElseThrow(() -> new TripQueryService.TripNotFoundException(tripId));

		// 회원이 아니면 없는 여행과 같은 답이다 — TripQueryService.get 과 같은 규칙이다.
		TripMember membership = this.repository.findMembers(tripId).stream()
				.filter(member -> member.userId().equals(requesterUserId))
				.findFirst()
				.orElseThrow(() -> new TripQueryService.TripNotFoundException(tripId));

		// 🔴 역할 판정이 먼저다. 이미 지워진 여행이라고 동행자에게 성공을 돌려주면
		//    "지울 수 있었다" 는 잘못된 신호가 되고, 화면은 그 버튼을 계속 띄운다.
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
