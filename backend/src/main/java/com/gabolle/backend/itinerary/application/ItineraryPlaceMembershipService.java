package com.gabolle.backend.itinerary.application;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.place.service.ItineraryMembershipPort;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 장소 상세가 묻는 "이 장소가 그 일정에 들어 있나" 에 답한다.
 * {@link ItineraryMembershipPort} 는 {@code place} 쪽에 선언된 인터페이스다. 쓰는 쪽이 계약을
 * 적고 제공하는 쪽이 구현하는 배치라, {@code place} 패키지는 일정의 판·항목 구조를 몰라도 되고
 * 이 클래스만 일정 도메인을 안다.
 * 권한 확인이 이 클래스의 존재 이유다. 확인을 빼면 남의 일정 식별자를 넣어
 * {@code INCLUDED}·{@code NOT_INCLUDED} 가 갈리는 것만으로 남의 일정 내용을 하나씩 알아낼 수
 * 있고, 장소 목록을 훑으며 반복하면 일정 전체가 드러난다.
 * 판정은 새로 쓰지 않고 {@link ItineraryAccess#requireMember} 를 그대로 쓴다. 일정 조회에 쓰는
 * 것과 같은 판정이어야 일정 화면에서 안 보이는 것이 이 칸으로도 안 보인다.
 * {@code requireMember} 가 던지는 404 예외는 밖으로 내보내지 않고 여기서 잡아
 * {@code UNAVAILABLE} 로 바꾼다 — 응답 코드가 존재 여부를 알려주는 신호가 되지 않게 한다.
 * {@code @Profile({"db","dev"})} 는 실제 저장소를 물어야 해서이고,
 * {@code @ConditionalOnBean(TripQueryService.class)} 는 {@link ItineraryAccess} 자체가 같은 조건을
 * 달고 있어서다 — 그것이 빠지는 컨텍스트에서는 이 빈도 함께 빠져야 기동이 실패하지 않는다.
 * {@code itineraryId} 를 준 요청만 이 경로를 지나므로 포함 여부를 물은 요청만 그 대가를 낸다.
 * 이 경로는 일정·여행·판과 {@code findContent} 를 읽는다. 항목만 읽는 좁은 조회가 더 싸지만
 * 그러려면 {@code ItineraryRepository} 계약과 두 구현을 함께 고쳐야 해서 있는 것을 쓴다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryPlaceMembershipService implements ItineraryMembershipPort {

	private final ItineraryAccess itineraryAccess;

	private final ItineraryRepository itineraryRepository;

	public ItineraryPlaceMembershipService(ItineraryAccess itineraryAccess,
			ItineraryRepository itineraryRepository) {
		this.itineraryAccess = itineraryAccess;
		this.itineraryRepository = itineraryRepository;
	}

	/**
	 * 판정 기준은 그 일정의 최신 판이다.
	 * 판은 덮어쓰지 않는 스냅샷이라 한 일정에 여러 판이 남아 있고, 낡은 판을 보면 이미 뺀 장소가
	 * 계속 "들어 있다" 로 나온다. 뺀 장소는 새 판의 항목 목록에서 사라지므로 항목만 보면 되고
	 * 제외 목록을 따로 볼 필요가 없다.
	 * {@code NOT_SUPPORTED}(부르는 쪽 트랜잭션을 잠시 멈춰 두고 트랜잭션 없이 실행한다)인 것이
	 * 정확성의 문제다. 못 보는 일정을 물으면 권한 판정이 예외를 던지는데, 그 예외가 스프링
	 * 트랜잭션 프록시를 지나는 순간 지금 참여 중인 트랜잭션에 "되돌림 예정" 표시가 찍힌다.
	 * 그 뒤에 예외를 잡아 {@code UNAVAILABLE} 로 바꿔도 표시는 지워지지 않아, 장소 상세 조회가
	 * 커밋을 시도하는 순간 {@code UnexpectedRollbackException} 으로 실패한다.
	 * 표시를 찍는 것은 이 클래스가 아니라서 여기서 예외를 잡는 것만으로는 막을 수 없다. 그래서
	 * 판정 전체를 부르는 쪽 트랜잭션 밖에서 돌린다 — 판정이 쓰는 트랜잭션은 새로 열린 것이라
	 * 실패해도 그것만 되돌려진다.
	 * 대가는 짧은 읽기 트랜잭션 몇 개가 따로 열리는 것이다. 두 트랜잭션이 서로 다른 시점을 볼
	 * 수는 있지만, 이 응답은 장소 정보와 포함 여부가 같은 순간의 것이라고 약속하지 않는다.
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public Inclusion inclusionOf(UUID userId, UUID itineraryId, UUID placeId) {
		if (itineraryId == null) {
			return Inclusion.unavailable(REASON_NOT_SPECIFIED);
		}
		if (userId == null) {
			// 누구인지 모르면 권한을 판정할 수 없다. 못 보는 일정과 같은 값으로 답한다 —
			// 로그인 여부가 일정의 존재 여부를 알려주는 통로가 되지 않게 한다.
			return Inclusion.unavailable(REASON_NOT_VISIBLE);
		}

		ItineraryAccess.Access access;
		try {
			access = this.itineraryAccess.requireMember(itineraryId.toString(), userId.toString());
		}
		catch (ItineraryQueryController.ItineraryNotFoundException notVisible) {
			// 없는 일정과 남의 일정이 여기 함께 온다. requireMember 가 이미 둘을 같은 예외로
			// 합쳐 두었고, 그 합침을 여기서 다시 나누지 않는다.
			return Inclusion.unavailable(REASON_NOT_VISIBLE);
		}

		return this.itineraryRepository
				.findContent(access.itinerary().itineraryId(), access.itinerary().latestVersion())
				.map((content) -> contains(content, placeId) ? Inclusion.included() : Inclusion.notIncluded())
				// 최신 판 포인터가 가리키는 판이 없다 — 데이터가 깨진 것이고 요청자와 무관하다.
				// 없다고 단정하는 대신 모른다고 답한다.
				.orElseGet(() -> Inclusion.unavailable(REASON_LOOKUP_UNAVAILABLE));
	}

	private boolean contains(ItineraryContent content, UUID placeId) {
		String target = placeId == null ? null : placeId.toString();
		return content.items().stream().anyMatch((item) -> target != null && target.equals(item.placeId()));
	}
}
