package com.gabolle.backend.recommendation.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.PlaceMenuPricePort;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.presentation.RecommendationResultController;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationResultResponse;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Job 하나가 만든 추천 결과를 읽는 자리. {@code RecommendationResultController} 가 부른다.
 *
 * 프로필과 {@code @ConditionalOnBean} 을 단 것은 추천 도메인만 스캔하는 슬라이스에는
 * {@link PlaceRepository} 빈이 없기 때문이다 — 조건 없이 두면 컨트롤러를 안 띄워도 이 빈이
 * 만들어지려 해 그 컨텍스트 로딩이 깨진다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(RecommendationJobRunner.class)
public class RecommendationResultQueryService {

	/** 혼잡도 구간화 경계. */
	private static final double CROWD_LEVEL_LOW_MAX = 0.34;
	private static final double CROWD_LEVEL_MEDIUM_MAX = 0.67;

	/** 이동 관련 경고만 고른다 — {@code STAIRS_PRESENT}·{@code WALKING_OVER_LIMIT} 은 정확히,
	 * {@code ACCESS*} 는 접두어로. */
	private static final Set<String> MOBILITY_EXACT_CODES = Set.of("STAIRS_PRESENT", "WALKING_OVER_LIMIT");

	private final RecommendationCandidateRepository candidateRepository;

	private final PlaceRepository placeRepository;

	private final ItineraryRepository itineraryRepository;

	private final ObjectMapper objectMapper;

	private final PlaceMenuPricePort menuPrice;

	public RecommendationResultQueryService(RecommendationCandidateRepository candidateRepository,
			PlaceRepository placeRepository, ItineraryRepository itineraryRepository, ObjectMapper objectMapper,
			PlaceMenuPricePort menuPrice) {
		this.candidateRepository = candidateRepository;
		this.placeRepository = placeRepository;
		this.itineraryRepository = itineraryRepository;
		this.objectMapper = objectMapper;
		this.menuPrice = menuPrice;
	}

	/**
	 * @throws RecommendationResultController.JobNotReadyException Job 이 아직 {@code PENDING}
	 *     ·{@code RUNNING} 이라 결과가 없다 — 프론트는 {@code GET /api/v1/jobs/{id}} 로
	 *     진행 상황을 본다
	 */
	@Transactional(readOnly = true)
	public RecommendationResultResponse buildResult(RecommendationJob job) {
		JobStatus jobStatus = job.getJobStatus();

		if (jobStatus == JobStatus.PENDING || jobStatus == JobStatus.RUNNING) {
			throw new RecommendationResultController.JobNotReadyException(job.getJobId().toString());
		}

		String itineraryId = (job.getItineraryId() != null) ? job.getItineraryId().toString() : null;

		if (jobStatus != JobStatus.SUCCEEDED) {
			// FAILED · EXPIRED · CANCELLED 는 셋 다 "결과가 없다" 는 같은 사실이라 응답에서는
			// FAILED 하나로 뭉뚱그린다. errorCode 는 markFailed 로만 채워져 EXPIRED·CANCELLED
			// 에서는 늘 null 이므로, 실제 JobStatus.FAILED 일 때만 errorMessage 를 채운다.
			String errorMessage = (jobStatus == JobStatus.FAILED) ? job.getErrorCode() : null;
			return new RecommendationResultResponse(
					"FAILED", List.of(), itineraryId, job.getFallbackMode(), List.of(), errorMessage,
					0, null, job.getRequestId().toString(), tripId(job));
		}

		List<RecommendationCandidate> returnedCandidates = this.candidateRepository
				.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(job.getRequestId());

		Map<UUID, Place> placesByPlaceId = lookupPlaces(returnedCandidates);

		// 후보 20곳의 가격을 한 번에 읽는다. 값이 있는 곳만 표에 있고, 없는 곳은 열쇠가 없다 —
		// 그 차이가 그대로 응답의 null(모름)과 숫자(앎)로 간다.
		Map<UUID, Integer> menuPriceByPlaceId = this.menuPrice.pricesOf(placesByPlaceId.keySet());

		List<RecommendationResultResponse.Item> items = new ArrayList<>(returnedCandidates.size());
		Set<String> conflicts = new TreeSet<>();
		boolean anyUnknownData = false;

		for (RecommendationCandidate candidate : returnedCandidates) {
			Place place = placesByPlaceId.get(candidate.getPlaceId());
			if (place == null) {
				// FK 가 있는 한 있을 수 없는 상태다 — 조용히 넘기면 화면에 빈 제목이 뜬다.
				throw new IllegalStateException(
						"추천 후보가 가리키는 place 를 찾을 수 없다: placeId=" + candidate.getPlaceId());
			}

			List<String> mobilityWarnings = mobilityWarnings(candidate);
			conflicts.addAll(mobilityWarnings);

			String dataStatus = mapDataStatus(candidate.getConstraintVerdict());
			if ("UNKNOWN".equals(dataStatus)) {
				anyUnknownData = true;
			}

			items.add(new RecommendationResultResponse.Item(
					candidate.getPlaceId().toString(),
					place.getNameKo(),
					null, // imageUrl — place 표에 이미지 칸이 없다
					List.of(candidate.getReasonCodes()),
					// 대표 메뉴 한 가지의 값. 조사가 안 된 곳은 null 그대로 둔다 — 0 을 넣으면
					// 화면이 「무료」로 그린다 (S15P21E201-1479).
					menuPriceByPlaceId.get(candidate.getPlaceId()),
					crowdLevel(candidate),
					mobilityWarnings.isEmpty() ? null : mobilityWarnings,
					dataStatus,
					candidate.getFallbackMode(),
					itineraryId));
		}

		String status = anyUnknownData ? "PARTIAL" : "COMPLETED";

		return new RecommendationResultResponse(
				status, items, itineraryId, job.getFallbackMode(), List.copyOf(conflicts), null,
				items.size(), estimatedTravelMinutes(itineraryId), job.getRequestId().toString(), tripId(job));
	}

	/**
	 * 여행에 매이지 않은 작업이면 {@code null} 이다. 빈 문자열이나 지어낸 값을 넣지 않는다 —
	 * 앱이 이 값으로 주소를 만들기 때문에 없는 여행을 부르게 된다.
	 */
	private String tripId(RecommendationJob job) {
		return (job.getTripId() != null) ? job.getTripId().toString() : null;
	}

	/**
	 * 최신 판의 구간(leg) {@code duration_min} 합. 일정이 없거나 구간이 하나도 값을 갖지
	 * 않으면 {@code null} 이고, 일부만 값이 있으면 그 값들의 합이라 "적어도 이만큼" 이다.
	 */
	private Integer estimatedTravelMinutes(String itineraryId) {
		if (itineraryId == null) {
			return null;
		}
		return this.itineraryRepository.findById(itineraryId)
				.flatMap(itinerary -> this.itineraryRepository.findContent(itineraryId, itinerary.latestVersion()))
				.map(ItineraryContent::legs)
				.flatMap(legs -> {
					boolean anyKnown = legs.stream().anyMatch(leg -> leg.durationMin() != null);
					if (!anyKnown) {
						return Optional.<Integer>empty();
					}
					int total = legs.stream().map(ItineraryLeg::durationMin)
							.filter(Objects::nonNull)
							.mapToInt(Integer::intValue)
							.sum();
					return Optional.of(total);
				})
				.orElse(null);
	}

	/**
	 * 데이터 신뢰도를 직접 나타내는 칸이 없어 {@link ConstraintVerdict} 를 그대로 옮긴다 —
	 * {@code PASS} 는 판정에 필요한 사실을 전부 확인했다는 뜻이고 {@code UNKNOWN} 은 일부를
	 * 확인하지 못했다는 뜻이다. {@code FAIL} 은 반환되는 후보에 나타나지 않지만 방어적으로
	 * {@code UNKNOWN} 을 준다. {@code ESTIMATED} 로 갈 수 있는 값은 없어 만들지 않는다.
	 */
	private static String mapDataStatus(ConstraintVerdict verdict) {
		return switch (verdict) {
			case PASS -> "VERIFIED";
			case UNKNOWN, FAIL -> "UNKNOWN";
		};
	}

	/** {@code feature_values} 의 {@code CROWDING_SCORE} 를 구간화한다. 없거나 숫자가 아니면 {@code null}. */
	private String crowdLevel(RecommendationCandidate candidate) {
		JsonNode features = readJson(candidate.getFeatureValues());
		if (features == null) {
			return null;
		}
		JsonNode crowdingScore = features.path("CROWDING_SCORE");
		if (!crowdingScore.isNumber()) {
			return null;
		}
		double score = crowdingScore.doubleValue();
		if (score < CROWD_LEVEL_LOW_MAX) {
			return "LOW";
		}
		if (score < CROWD_LEVEL_MEDIUM_MAX) {
			return "MEDIUM";
		}
		return "HIGH";
	}

	private List<String> mobilityWarnings(RecommendationCandidate candidate) {
		List<String> result = new ArrayList<>();
		for (String code : candidate.getWarningCodes()) {
			if (MOBILITY_EXACT_CODES.contains(code) || code.startsWith("ACCESS")) {
				result.add(code);
			}
		}
		return result;
	}

	private Map<UUID, Place> lookupPlaces(List<RecommendationCandidate> candidates) {
		if (candidates.isEmpty()) {
			return Map.of();
		}
		List<UUID> placeIds = candidates.stream().map(RecommendationCandidate::getPlaceId).distinct().toList();
		return this.placeRepository.findByPlaceIdIn(placeIds).stream()
				.collect(Collectors.toMap(Place::getPlaceId, place -> place));
	}

	/**
	 * 값이 깨져 있으면 이 항목 하나만 정보 없이 두고 전체 응답을 실패시키지 않는다 —
	 * {@code PlaceDetailService.readValue} 와 같은 판단이다.
	 */
	private JsonNode readJson(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return this.objectMapper.readTree(raw);
		}
		catch (JacksonException e) {
			return null;
		}
	}
}
