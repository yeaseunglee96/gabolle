package com.gabolle.backend.share.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.recommendation.application.RecommendationJobRunner;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.share.domain.TripShareLink;
import com.gabolle.backend.share.repository.TripShareLinkRepository;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.domain.TripSeedPlace;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;

/**
 * 공유 일정 복제. 복사가 아니다 — 원본에서 장소 구성만 가져와 씨앗(trip_seed_place)으로
 * 남기고, 요청자의 인원·예산·기간으로 여행을 새로 만들어 일정 생성 Job 을 접수한다.
 *
 * 지키는 것 셋:
 * 원본은 읽기만 한다.
 * 새 여행 + 씨앗은 한 트랜잭션이다 — 씨앗 없이 여행만 남으면 복제가 아니라 보통 여행이 된다.
 * Job 접수는 그 트랜잭션이 커밋된 뒤에 한다(RecommendationJobRunner 의 요구).
 * 만료된 공유 주소로는 복제하지 않는다(410). 원본이 지워졌으면 404.
 *
 * 제약을 하나도 답하지 않은 요청은 여행과 씨앗만 만들고 Job 은 접수하지 못한다 —
 * 그때 WARNING_NO_CONSTRAINTS 를 응답에 남긴다. 여행까지 안 만들면 사용자가 이유를 모른다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ShareCloneService {

	public static final String WARNING_NO_CONSTRAINTS = "RECOMMENDATION_NOT_REQUESTED_NO_CONSTRAINTS";

	public static final String WARNING_IDEMPOTENT_RETRY = "IDEMPOTENT_RETRY_JOB_NOT_REQUESTED";

	private final TripShareLinkRepository shareLinkRepository;

	private final TripRepository tripRepository;

	private final ItineraryRepository itineraryRepository;

	private final TripCreationService tripCreationService;

	private final TripSeedPlaceRepository seedPlaceRepository;

	private final RecommendationJobRunner jobRunner;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public ShareCloneService(TripShareLinkRepository shareLinkRepository, TripRepository tripRepository,
			ItineraryRepository itineraryRepository, TripCreationService tripCreationService,
			TripSeedPlaceRepository seedPlaceRepository, RecommendationJobRunner jobRunner,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.shareLinkRepository = shareLinkRepository;
		this.tripRepository = tripRepository;
		this.itineraryRepository = itineraryRepository;
		this.tripCreationService = tripCreationService;
		this.seedPlaceRepository = seedPlaceRepository;
		this.jobRunner = jobRunner;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * command.userId() 가 새 여행의 소유자다. idempotencyKey 재시도면 기존 여행을 돌려주고
	 * Job 은 다시 접수하지 않는다.
	 */
	public Result clone(String token, TripCreationService.Command command, String idempotencyKey) {
		Instant now = this.clock.instant();

		TripShareLink link = this.shareLinkRepository.findByToken(token)
				.orElseThrow(() -> new ShareLinkNotFoundException(token));
		if (link.isExpiredAt(now)) {
			throw new ShareLinkExpiredException(token);
		}
		String sourceTripId = link.getTripId().toString();
		Trip source = this.tripRepository.findById(sourceTripId)
				.filter(t -> t.deletedAt() == null)
				.orElseThrow(() -> new SharedTripNotFoundException(sourceTripId));

		List<String> placeIds = sourcePlaceIds(source.tripId());
		if (placeIds.isEmpty()) {
			throw new SharedItineraryEmptyException(sourceTripId);
		}

		// 새 여행 + 씨앗을 한 트랜잭션으로. 멱등 재시도(created=false)면 씨앗은 이미 있다.
		TripCreationService.Result result = this.transaction.execute(status -> {
			TripCreationService.Result r = this.tripCreationService.create(command, idempotencyKey);
			if (r.created()) {
				List<TripSeedPlace> seeds = new ArrayList<>(placeIds.size());
				for (int i = 0; i < placeIds.size(); i++) {
					seeds.add(new TripSeedPlace(r.trip().tripId(), placeIds.get(i), i + 1, source.tripId(),
							link.getTripShareLinkId().toString(), now));
				}
				this.seedPlaceRepository.saveAll(seeds);
			}
			return r;
		});

		List<String> warnings = new ArrayList<>();
		RecommendationJob job = null;
		if (!result.created()) {
			warnings.add(WARNING_IDEMPOTENT_RETRY);
		}
		else {
			try {
				job = this.jobRunner.enqueue(result.trip().tripId(), command.userId(), null, null);
			}
			catch (IllegalStateException e) {
				// 제약을 하나도 답하지 않은 요청이다. 여행은 이미 커밋됐다.
				warnings.add(WARNING_NO_CONSTRAINTS);
			}
		}

		return new Result(result.trip(), source.tripId(), link.getTripShareLinkId().toString(), placeIds.size(), job,
				result.created(), List.copyOf(warnings));
	}

	/** 원본의 최신 판에서 장소를 방문 순서대로, 중복 없이. 일정이 없거나 항목이 없으면 비어 있다. */
	private List<String> sourcePlaceIds(String sourceTripId) {
		List<Itinerary> itineraries = this.itineraryRepository.findByTripId(sourceTripId);
		if (itineraries.isEmpty()) {
			return List.of();
		}
		Itinerary itinerary = itineraries.get(0);
		Optional<ItineraryContent> content = this.itineraryRepository.findContent(itinerary.itineraryId(),
				itinerary.latestVersion());
		if (content.isEmpty()) {
			return List.of();
		}
		Set<String> ordered = new LinkedHashSet<>();
		content.get().items().stream()
				.sorted((a, b) -> a.dayIndex() != b.dayIndex() ? Integer.compare(a.dayIndex(), b.dayIndex())
						: Integer.compare(a.sequence(), b.sequence()))
				.map(ItineraryItem::placeId)
				.forEach(ordered::add);
		return List.copyOf(ordered);
	}

	public record Result(Trip trip, String sourceTripId, String shareLinkId, int seedPlaceCount, RecommendationJob job,
			boolean created, List<String> warningCodes) {
	}

	public static class ShareLinkNotFoundException extends RuntimeException {
		public ShareLinkNotFoundException(String token) {
			super("공유 주소를 찾을 수 없습니다.");
		}
	}

	public static class ShareLinkExpiredException extends RuntimeException {
		public ShareLinkExpiredException(String token) {
			super("공유 기간이 끝났어요.");
		}
	}

	public static class SharedTripNotFoundException extends RuntimeException {
		public SharedTripNotFoundException(String tripId) {
			super("원본 여행이 삭제됐어요.");
		}
	}

	/** 원본에 장소가 하나도 없다 — 가져올 구성이 없어 복제할 것이 없다. */
	public static class SharedItineraryEmptyException extends RuntimeException {
		public SharedItineraryEmptyException(String tripId) {
			super("공유된 일정에 아직 장소가 없어 복제할 수 없어요.");
		}
	}
}
