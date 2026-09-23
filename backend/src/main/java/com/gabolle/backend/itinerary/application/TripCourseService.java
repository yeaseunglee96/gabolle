package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.presentation.dto.TripCoursesResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand.PlannedPlace;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;

/**
 * 추천 코스 3안 (S15P21E201-1454) — 여행 전체를 통째로 견주는 안들.
 *
 * <ul>
 *   <li><b>1안</b>은 추천 작업이 이미 만들어 둔 일정이다</li>
 *   <li><b>2안·3안</b>은 같은 추천 결과(조건을 통과한 후보와 그 순위)로 일정 조립을 다시 돌린 것이다.
 *       앞 코스가 쓴 곳을 뒤로 보내 넣는다({@link CoursePool}). 추천 엔진은 다시 부르지 않는다</li>
 * </ul>
 *
 * <p>2안·3안은 <b>고른 순간에 저장한다</b>({@link #choose}). 추천이 끝날 때 셋 다 저장하면 고르지도 않은
 * 일정 둘이 여행마다 쌓이고, 저장마다 「일정이 완성됐다」 알림이 나간다. 고를 때 같은 입력으로 다시
 * 조립하므로 미리 본 곳들이 그대로 들어간다.
 *
 * <p>권한은 추천 작업과 같다 — 여행 회원이면 된다({@link TripQueryService#get}). 없는 여행과 남의 여행은
 * 같은 404 다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripCourseService {

	private static final Logger log = LoggerFactory.getLogger(TripCourseService.class);

	/** 1안 말고 더 만드는 안의 수. 화면이 세 안을 견준다. */
	static final int ALTERNATIVES = 2;

	/**
	 * 최근 추천 작업을 몇 개까지 훑나. 맨 앞 작업이 실패했거나 아직 도는 중일 수 있어서 하나만 보면
	 * 그 앞의 성공한 추천을 못 찾는다 — {@code RecommendationJobRunner.findJobsByTrip} 과 같은 이유다.
	 */
	private static final int JOB_LOOKBACK = 20;

	/**
	 * 고른 2안·3안 일정의 판 {@code request_id} 앞머리. 같은 안을 두 번 골라도 새로 만들지 않도록
	 * 이미 만든 일정을 이것으로 찾는다({@link ItineraryDraftService#persistAlternative}).
	 */
	private static final String LABEL_PREFIX = "course:";

	private final TripQueryService tripQueryService;

	private final RecommendationJobRepository jobRepository;

	private final RecommendationCandidateRepository candidateRepository;

	private final ItineraryRepository itineraryRepository;

	private final ItineraryDraftService draftService;

	private final ItineraryQueryService queryService;

	private final PlaceRepository placeRepository;

	private final TripSeedPlaceRepository seedPlaceRepository;

	private final Clock clock;

	public TripCourseService(TripQueryService tripQueryService, RecommendationJobRepository jobRepository,
			RecommendationCandidateRepository candidateRepository, ItineraryRepository itineraryRepository,
			ItineraryDraftService draftService, ItineraryQueryService queryService, PlaceRepository placeRepository,
			TripSeedPlaceRepository seedPlaceRepository, Clock clock) {
		this.tripQueryService = tripQueryService;
		this.jobRepository = jobRepository;
		this.candidateRepository = candidateRepository;
		this.itineraryRepository = itineraryRepository;
		this.draftService = draftService;
		this.queryService = queryService;
		this.placeRepository = placeRepository;
		this.seedPlaceRepository = seedPlaceRepository;
		this.clock = clock;
	}

	/**
	 * 이 여행의 코스 안들. 추천이 아직 없으면 빈 목록이다.
	 *
	 * @throws TripQueryService.TripNotFoundException 여행이 없거나 요청자가 회원이 아니다
	 */
	public TripCoursesResponse list(String tripId, String requesterUserId) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterUserId);
		String trip = view.trip().tripId();

		Optional<RecommendationJob> found = latestGeneration(trip);
		if (found.isEmpty()) {
			return new TripCoursesResponse(List.of());
		}
		RecommendationJob job = found.get();
		String baseId = job.getItineraryId().toString();

		List<TripCoursesResponse.Course> courses = new ArrayList<>(1 + ALTERNATIVES);
		courses.add(toCourse(courseId(job, 0), this.queryService.getDetail(baseId, requesterUserId), baseId, null));

		Map<String, String> chosen = chosenByLabel(trip);
		for (Alternative alternative : alternatives(job, view, requesterUserId, ALTERNATIVES)) {
			String id = courseId(job, alternative.index());
			String itineraryId = chosen.get(label(job, alternative.index()));
			if (itineraryId != null) {
				// 이미 고른 안은 만들어진 일정으로 그린다. 고른 뒤에 고친 것까지 보여야 한다.
				courses.add(toCourse(id, this.queryService.getDetail(itineraryId, requesterUserId), itineraryId, null));
				continue;
			}
			ItineraryDetailResponse preview = preview(id, view, alternative.draft());
			courses.add(toCourse(id, preview, null, preview));
		}
		return new TripCoursesResponse(courses);
	}

	/**
	 * 고른 안을 일정으로 만들고 그 번호를 돌려준다. 1안이나 이미 고른 안이면 있는 일정을 돌려준다.
	 *
	 * @throws TripQueryService.TripNotFoundException 여행이 없거나 요청자가 회원이 아니다
	 * @throws CourseNotFoundException 이 여행의 코스 번호가 아니거나, 그 안을 더는 만들 수 없다
	 */
	public String choose(String tripId, String courseId, String requesterUserId) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterUserId);
		String trip = view.trip().tripId();

		CourseRef ref = CourseRef.parse(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
		RecommendationJob job = this.jobRepository.findByRequestId(ref.requestId())
				.filter(TripCourseService::isFinishedGeneration)
				// 남의 여행의 코스 번호를 내 여행 주소로 부르는 것을 막는다.
				.filter(candidate -> candidate.getTripId().toString().equals(trip))
				.orElseThrow(() -> new CourseNotFoundException(courseId));

		if (ref.index() == 0) {
			return job.getItineraryId().toString();
		}
		String label = label(job, ref.index());
		String existing = chosenByLabel(trip).get(label);
		if (existing != null) {
			return existing;
		}
		Alternative alternative = alternatives(job, view, requesterUserId, ref.index()).stream()
				.filter(candidate -> candidate.index() == ref.index())
				.findFirst()
				.orElseThrow(() -> new CourseNotFoundException(courseId));
		return this.draftService.persistAlternative(alternative.draft(), label).itineraryId();
	}

	/**
	 * 1안 다음의 안들. 앞 안과 겹치지 않는 곳을 먼저 넣어 조립하고, <b>새로 들어간 곳이 하나도 없으면
	 * 거기서 멈춘다</b> — 같은 코스를 이름만 바꿔 한 번 더 보여 주지 않는다. 후보가 모자란 여행은
	 * 그래서 안이 셋보다 적다.
	 *
	 * <p>🔴 <b>1안이 쓴 곳은 1안의 첫 판에서 읽는다</b>(최신 판이 아니라). 사용자가 1안을 고친 뒤에 2안을
	 * 고르면, 최신 판을 기준으로 했을 때 2안이 미리 본 것과 다르게 만들어진다.
	 */
	private List<Alternative> alternatives(RecommendationJob job, TripQueryService.View view,
			String requesterUserId, int upTo) {
		List<PlannedPlace> ranked = rankedPool(job.getRequestId());
		if (ranked.isEmpty()) {
			return List.of();
		}
		String trip = view.trip().tripId();
		Set<UUID> pinned = new HashSet<>();
		this.seedPlaceRepository.findByTripId(trip).forEach(seed -> pinned.add(UUID.fromString(seed.placeId())));

		Set<UUID> used = new HashSet<>();
		this.itineraryRepository.findContent(job.getItineraryId().toString(), 1)
				.ifPresent(content -> content.items().forEach(item -> used.add(UUID.fromString(item.placeId()))));

		List<Alternative> result = new ArrayList<>(upTo);
		for (int index = 1; index <= upTo; index++) {
			ItineraryDraft draft;
			try {
				draft = this.draftService.assemble(new ItineraryDraftCommand(job.getRequestId(), trip,
						requesterUserId, CoursePool.order(ranked, pinned, used), job.getModelVersion(),
						job.getFeatureVersion(), job.getOntologyVersion(), job.getPolicyVersion(),
						job.getDatasetVersion()));
			}
			catch (RuntimeException ex) {
				// 1안은 이미 있다. 뒤의 안 하나를 못 만들었다고 화면 전체를 오류로 만들지 않는다 —
				// 만든 데까지만 보여 준다. 이 경로가 없던 때 화면은 1안만 그렸고, 그보다 나빠지면 안 된다.
				log.warn("추천 코스 {}안을 조립하지 못해 앞의 안만 보여 준다: requestId={}", index + 1,
						job.getRequestId(), ex);
				break;
			}
			Set<UUID> places = new HashSet<>();
			draft.items().forEach(item -> places.add(item.placeId()));
			if (used.containsAll(places)) {
				break;
			}
			result.add(new Alternative(index, draft));
			used.addAll(places);
		}
		return result;
	}

	/**
	 * 추천이 조건을 통과시킨 후보 전부, 순위 차례로. 1안에 들어간 윗부분({@code RETURNED})만이 아니라
	 * 그 아래({@code RANKED})까지다 — 2안·3안은 그 아래에서 채워진다. 조건에 걸린 후보(순위가 없다)는
	 * 뺀다. 그것이 사용자가 답한 조건이다.
	 */
	private List<PlannedPlace> rankedPool(UUID requestId) {
		List<RecommendationCandidate> rows = this.candidateRepository
				.findByRequestIdOrderByFinalRankAscPlaceIdAsc(requestId).stream()
				.filter(row -> row.isEligible() && row.getFinalRank() != null)
				.toList();
		if (rows.isEmpty()) {
			return List.of();
		}
		// 갈래는 후보 행에 없다. 1안도 같은 칸(place.category)을 쓴다 — 하루 밥집 상한이 그것을 본다.
		Map<UUID, String> categoryByPlace = new HashMap<>();
		for (Place place : this.placeRepository.findByPlaceIdIn(rows.stream().map(RecommendationCandidate::getPlaceId).toList())) {
			categoryByPlace.put(place.getPlaceId(), place.getCategory());
		}
		return rows.stream()
				.map(row -> new PlannedPlace(row.getPlaceId(), row.getFinalRank(), List.of(row.getReasonCodes()),
						List.of(row.getWarningCodes()), categoryByPlace.get(row.getPlaceId())))
				.toList();
	}

	/** 저장하지 않은 초안을 일정과 같은 모양으로. 옮기는 코드는 저장과 한 벌이다({@link ItineraryDraftService#contentOf}). */
	private ItineraryDetailResponse preview(String id, TripQueryService.View view, ItineraryDraft draft) {
		ItineraryDraftService.DraftContent content = this.draftService.contentOf(draft, id, this.clock.instant());
		return this.queryService.preview(id, view.trip(), view.role(), content.items(), content.legs(),
				draft.warningCodes());
	}

	/** 이 여행에서 이미 고른 2안·3안 — 판 {@code request_id} 꼬리표 → 일정 번호. */
	private Map<String, String> chosenByLabel(String tripId) {
		Map<String, String> chosen = new HashMap<>();
		for (Itinerary itinerary : this.itineraryRepository.findByTripId(tripId)) {
			this.itineraryRepository.findVersion(itinerary.itineraryId(), 1)
					.map(ItineraryVersion::requestId)
					.filter(requestId -> requestId != null && requestId.startsWith(LABEL_PREFIX))
					.ifPresent(requestId -> chosen.putIfAbsent(requestId, itinerary.itineraryId()));
		}
		return chosen;
	}

	private Optional<RecommendationJob> latestGeneration(String tripId) {
		return this.jobRepository
				.findByTripIdOrderByCreatedAtDesc(UUID.fromString(tripId), PageRequest.of(0, JOB_LOOKBACK)).stream()
				.filter(TripCourseService::isFinishedGeneration)
				.findFirst();
	}

	private static boolean isFinishedGeneration(RecommendationJob job) {
		return job.getJobType() == JobType.ITINERARY_GENERATION && job.getJobStatus() == JobStatus.SUCCEEDED
				&& job.getItineraryId() != null;
	}

	/**
	 * 일정 모양을 코스 한 안으로. 요약 숫자는 화면이 일정에서 세던 방식({@code courseFromItinerary})과
	 * 같게 센다 — 1안의 숫자가 이 경로가 생기기 전과 달라지면 안 된다.
	 */
	private static TripCoursesResponse.Course toCourse(String id, ItineraryDetailResponse detail, String itineraryId,
			ItineraryDetailResponse preview) {
		List<TripCoursesResponse.Day> days = new ArrayList<>(detail.days().size());
		int places = 0;
		int moveMin = 0;
		boolean anyMove = false;
		for (int index = 0; index < detail.days().size(); index++) {
			List<TripCoursesResponse.Stop> stops = new ArrayList<>();
			for (ItineraryDetailResponse.Item item : detail.days().get(index).items()) {
				stops.add(new TripCoursesResponse.Stop(item.placeId(), item.title(), clockTime(item.startsAt()), null,
						null, null, item.lat(), item.lng()));
				if (item.travelDurationMin() != null) {
					moveMin += item.travelDurationMin();
					anyMove = true;
				}
			}
			places += stops.size();
			days.add(new TripCoursesResponse.Day(index + 1, stops));
		}
		Integer walked = detail.totalWalkingMeters();
		Double walkKm = (walked == null || walked <= 0) ? null : Math.round(walked / 100.0) / 10.0;
		TripCoursesResponse.Summary summary = new TripCoursesResponse.Summary(places, anyMove ? moveMin : null,
				walkKm, detail.totalEstimatedCostKrw());
		return new TripCoursesResponse.Course(id, "", "", days, summary, "ESTIMATED",
				null, itineraryId, preview);
	}

	/** 「2026-10-04T09:46:00+09:00」 → 「09:46」. */
	private static String clockTime(String startsAt) {
		return (startsAt == null || startsAt.length() < 16) ? null : startsAt.substring(11, 16);
	}

	private static String courseId(RecommendationJob job, int index) {
		return job.getRequestId() + ":" + index;
	}

	private static String label(RecommendationJob job, int index) {
		return LABEL_PREFIX + courseId(job, index);
	}

	private record Alternative(int index, ItineraryDraft draft) {
	}

	/** 코스 번호 「추천 요청 번호:안 번호」. 1안이 0 이다. */
	private record CourseRef(UUID requestId, int index) {

		static Optional<CourseRef> parse(String courseId) {
			if (courseId == null) {
				return Optional.empty();
			}
			String[] parts = courseId.split(":");
			if (parts.length != 2) {
				return Optional.empty();
			}
			try {
				int index = Integer.parseInt(parts[1]);
				if (index < 0 || index > ALTERNATIVES) {
					return Optional.empty();
				}
				return Optional.of(new CourseRef(UUID.fromString(parts[0]), index));
			}
			catch (IllegalArgumentException malformed) {
				return Optional.empty();
			}
		}
	}

	/** 이 여행의 코스 번호가 아니다. 남의 여행 것과 없는 것을 가르지 않는다 — 둘 다 404 다. */
	public static class CourseNotFoundException extends RuntimeException {

		public CourseNotFoundException(String courseId) {
			super("코스를 찾을 수 없다: " + courseId);
		}
	}
}
