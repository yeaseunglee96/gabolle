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
 * Job 하나가 만든 추천 결과를 읽는 자리 — S15P21E201-604.
 * {@code RecommendationResultController} 가 부른다.
 *
 * <p>🔴 {@code @Profile({"db","dev"})} · {@code @ConditionalOnBean(RecommendationJobRunner.class)} —
 * {@code RecommendationJobController} 와 같은 이유다. {@code RecommendationSliceApplication}
 * (추천 도메인만 스캔)은 {@code place} 패키지를 스캔하지 않아 {@link PlaceRepository} 빈이
 * 없다. 조건 없이 이 빈을 만들면 그 슬라이스의 컨텍스트 로딩 자체가 깨진다 — 컨트롤러가
 * 없어도 서비스 빈은 컴포넌트 스캔에 걸리는 즉시 만들어지려 하기 때문이다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(RecommendationJobRunner.class)
public class RecommendationResultQueryService {

	/** 구간화 경계 — 티켓 본문이 준 값 그대로다. */
	private static final double CROWD_LEVEL_LOW_MAX = 0.34;
	private static final double CROWD_LEVEL_MEDIUM_MAX = 0.67;

	/** 이동 관련 경고만 고른다 — {@code STAIRS_PRESENT}·{@code WALKING_OVER_LIMIT} 은 정확히,
	 * {@code ACCESS*} 는 접두어로. */
	private static final Set<String> MOBILITY_EXACT_CODES = Set.of("STAIRS_PRESENT", "WALKING_OVER_LIMIT");

	private final RecommendationCandidateRepository candidateRepository;

	private final PlaceRepository placeRepository;

	private final ItineraryRepository itineraryRepository;

	private final ObjectMapper objectMapper;

	public RecommendationResultQueryService(RecommendationCandidateRepository candidateRepository,
			PlaceRepository placeRepository, ItineraryRepository itineraryRepository, ObjectMapper objectMapper) {
		this.candidateRepository = candidateRepository;
		this.placeRepository = placeRepository;
		this.itineraryRepository = itineraryRepository;
		this.objectMapper = objectMapper;
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
			// FAILED · EXPIRED · CANCELLED — 셋 다 "결과가 없다"는 같은 사실을 나타내므로
			// 응답에서는 FAILED 하나로 뭉뚱그린다(GB-API-001 명세가 이 응답에 대해 EXPIRED·
			// CANCELLED 를 따로 두지 않는다).
			//
			// 🔴 errorCode 는 markFailed 로만 채워진다 — EXPIRED·CANCELLED 는 그 경로를 타지
			// 않아 늘 null 이다. 그래서 실제 JobStatus.FAILED 일 때만 errorMessage 를 채운다.
			String errorMessage = (jobStatus == JobStatus.FAILED) ? job.getErrorCode() : null;
			return new RecommendationResultResponse(
					"FAILED", List.of(), itineraryId, job.getFallbackMode(), List.of(), errorMessage,
					0, null, job.getRequestId().toString(), tripId(job));
		}

		List<RecommendationCandidate> returnedCandidates = this.candidateRepository
				.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(job.getRequestId());

		Map<UUID, Place> placesByPlaceId = lookupPlaces(returnedCandidates);

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
					null, // estimatedCostKrw — 비용 데이터가 없다
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
	 * 이 작업이 어느 여행의 것인가 — S15P21E201-1084.
	 *
	 * <p>여행에 매이지 않은 작업이면 {@code null} 이다. 빈 문자열이나 지어낸 값을 넣지 않는다 —
	 * 앱이 그 값으로 주소를 만들기 때문에, 없는 것을 있는 것처럼 주면 앱이 없는 여행을 부른다.
	 */
	private String tripId(RecommendationJob job) {
		return (job.getTripId() != null) ? job.getTripId().toString() : null;
	}

	/**
	 * 최신 판의 구간(leg) {@code duration_min} 합. 일정이 없거나(itineraryId == null) 구간이
	 * 하나도 값을 갖지 않으면 {@code null} — 클래스 상단 참고("적어도 이만큼").
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
	 * 🔴 {@code recommendation_candidate} 표에는 "이 항목의 데이터를 얼마나 믿을 수 있는가"를
	 * 직접 나타내는 칸이 없다. 가장 가까운 기존 값은 {@link ConstraintVerdict} 다 — {@code PASS}
	 * 는 하드 제약 판정에 필요한 사실을 전부 확인했다는 뜻이고, {@code UNKNOWN} 은 그 사실 중
	 * 일부를 확인하지 못했다는 뜻이라 이 필드가 뜻하는 방향과 같다. 그래서 다시 계산하지 않고
	 * 이미 있는 이 값을 그대로 옮긴다. {@code FAIL} 은 반환되는 후보에는 나타나지 않는다
	 * ({@code RecommendationCandidate.validateInvariants} 가 보장) — 그래도 방어적으로
	 * {@code UNKNOWN} 을 준다. {@code ESTIMATED} 로 갈 수 있는 기존 값은 없어서 이 메서드는
	 * 그 값을 만들어내지 않는다.
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
