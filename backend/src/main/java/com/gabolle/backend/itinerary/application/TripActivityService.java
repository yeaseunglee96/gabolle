package com.gabolle.backend.itinerary.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.dto.TripActivityResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 여행의 최근 변경 — 판을 이력으로 읽는다.
 * 권한은 여행 조회와 같다 — 회원이면(VIEWER 도) 볼 수 있고, 아니면 여행이 없는 것처럼 404 다.
 * 열람자도 "누가 바꿨나" 는 봐야 하니 편집 권한을 요구하지 않는다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripActivityService {

	public static final int DEFAULT_LIMIT = 20;

	public static final int MAX_LIMIT = 100;

	private final TripQueryService tripQueryService;

	private final ItineraryRepository itineraryRepository;

	private final ActorNames actorNames;

	public TripActivityService(TripQueryService tripQueryService, ItineraryRepository itineraryRepository,
			ActorNames actorNames) {
		this.tripQueryService = tripQueryService;
		this.itineraryRepository = itineraryRepository;
		this.actorNames = actorNames;
	}

	/**
	 * @param limit 1~{@value #MAX_LIMIT}. 밖이면 {@link IllegalArgumentException}(400) — 조용히
	 *     잘라 주지 않는다. 화면이 상한을 넘겨 부르면 그건 화면 버그고, 서버가 감추면 아무도 못 찾는다
	 * @throws TripQueryService.TripNotFoundException 여행이 없거나 요청자가 회원이 아니다
	 */
	@Transactional(readOnly = true)
	public TripActivityResponse list(String tripId, String requesterUserId, int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException("limit 은 1 이상 " + MAX_LIMIT + " 이하여야 한다: " + limit);
		}
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterUserId);

		List<String> itineraryIds = this.itineraryRepository.findByTripId(tripId).stream()
				.map(Itinerary::itineraryId)
				.toList();
		List<ItineraryVersion> versions = this.itineraryRepository.findRecentVersions(itineraryIds, limit);
		Map<String, String> names = this.actorNames.resolve(versions.stream().map(ItineraryVersion::createdBy).toList());

		List<TripActivityResponse.Entry> entries = versions.stream()
				.map(v -> new TripActivityResponse.Entry(
						v.itineraryId(),
						v.version(),
						v.operation().name(),
						v.createdBy(),
						names.get(v.createdBy()),
						// 작성자가 비어 있을 수 있다(탈퇴). v.createdBy().equals(…) 로 두면 그 판 하나 때문에
						//    활동 기록 전체가 터지고, 피해는 탈퇴한 본인이 아니라 같은 여행을 쓰던 동행자에게 간다.
						//    비어 있으면 "내가 한 것" 이 아니다.
						Objects.equals(v.createdBy(), requesterUserId),
						v.createdAt().toString(),
						v.baseVersion(),
						v.revertedFromVersion(),
						v.warningCodes()))
				.toList();

		return new TripActivityResponse(tripId, entries, limit, view.role().name());
	}
}
