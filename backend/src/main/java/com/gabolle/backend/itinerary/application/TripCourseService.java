package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

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
 * 일정 둘이 여행마다 쌓이고, 저장마다 「일정이 완성됐다」 알림이 나간다. 고를 때 목록이 보여 준 초안을
 * 그대로 저장하므로 미리 본 곳들이 그대로 들어간다({@link #alternativesMemo}).
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

	/**
	 * 2안·3안 초안을 들고 있는 시간. 초안의 입력(추천 판의 순위표·꼭 갈 곳·1안의 첫 판)은 판이 만들어진 뒤로 안
	 * 바뀌므로 오래 들고 있어도 틀리지 않는다. 그래도 끝을 두는 것은 이동 시간·영업시간 같은 바깥 자료가 새로
	 * 들어오면 언젠가는 반영되게 하려는 것이다.
	 */
	static final Duration ALTERNATIVES_TTL = Duration.ofMinutes(30);

	/** 초안을 들고 있는 (판, 요청자) 짝의 상한. 넘치면 지난 것부터 버리고, 그래도 넘치면 통째로 비운다. */
	static final int ALTERNATIVES_MAX_ENTRIES = 256;

	/**
	 * (추천 판, 요청자) → 2안·3안 초안 (S15P21E201-1598).
	 *
	 * <p>🔴 <b>왜.</b> 전에는 코스 목록을 부를 때마다 2안·3안을 처음부터 다시 짰다. 조립은 날마다 경로 최적화기(파이썬
	 * OR-Tools)를 프로세스로 띄우는데 운영에서 한 번에 약 0.45초(기동 0.13~0.16초 + 풀이 예산 0.3초)라, 3일 여행이면
	 * 6번 ≈ 2.7초였다. 운영에서 이 요청이 3.7초, 앱이 겹쳐 부르면 12.8초였다(2026-09-25).
	 *
	 * <p>판 번호가 열쇠라 새로 추천하면 새 판이 되어 옛 초안이 나올 수 없다. 요청자도 열쇠에 넣는다 — 초안에 실린
	 * 요청자가 고른 안의 {@code created_by} 가 된다. 동시에 온 첫 요청들은 먼저 꽂힌 약속(future)을 함께 기다려
	 * 한 번만 계산한다. 프로세스 메모리에만 둔다 — 서버가 한 대다({@code RouteCache} 와 같은 판단).
	 */
	private final ConcurrentHashMap<String, Memo> alternativesMemo = new ConcurrentHashMap<>();

	private record Memo(Instant createdAt, CompletableFuture<List<Alternative>> alternatives) {
	}

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
		for (Alternative alternative : alternativesOnce(job, view, requesterUserId)) {
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
		// 목록이 보여 준 안이면 그 초안을 그대로 저장한다 — 미리 본 것과 저장되는 것이 같다.
		List<Alternative> built = (ref.index() <= ALTERNATIVES) ? alternativesOnce(job, view, requesterUserId)
				: alternatives(job, view, requesterUserId, ref.index());
		Alternative alternative = built.stream()
				.filter(candidate -> candidate.index() == ref.index())
				.findFirst()
				.orElseThrow(() -> new CourseNotFoundException(courseId));
		return this.draftService.persistAlternative(alternative.draft(), label).itineraryId();
	}

	/**
	 * {@link #alternatives} 를 (추천 판, 요청자)마다 한 번만 부른다 — {@link #alternativesMemo} 참고. 계산이 실패하면
	 * 담아 두지 않는다(다음 요청이 다시 시도한다).
	 */
	private List<Alternative> alternativesOnce(RecommendationJob job, TripQueryService.View view,
			String requesterUserId) {
		String key = job.getRequestId() + "|" + requesterUserId;
		Instant now = this.clock.instant();
		Memo fresh = new Memo(now, new CompletableFuture<>());
		Memo memo = this.alternativesMemo.compute(key, (k, old) -> usable(old, now) ? old : fresh);
		if (memo == fresh) {
			evictIfFull(now);
			try {
				fresh.alternatives().complete(alternatives(job, view, requesterUserId, ALTERNATIVES));
			}
			catch (RuntimeException ex) {
				this.alternativesMemo.remove(key, fresh);
				fresh.alternatives().completeExceptionally(ex);
				throw ex;
			}
		}
		try {
			return memo.alternatives().join();
		}
		catch (CompletionException ex) {
			// 다른 요청이 하던 계산이 실패했다 — 그 요청이 받은 것과 같은 예외를 받는다.
			throw (ex.getCause() instanceof RuntimeException cause) ? cause : ex;
		}
	}

	private static boolean usable(Memo memo, Instant now) {
		return memo != null && !memo.alternatives().isCompletedExceptionally()
				&& now.isBefore(memo.createdAt().plus(ALTERNATIVES_TTL));
	}

	private void evictIfFull(Instant now) {
		if (this.alternativesMemo.size() <= ALTERNATIVES_MAX_ENTRIES) {
			return;
		}
		this.alternativesMemo.values().removeIf((memo) -> !usable(memo, now));
		if (this.alternativesMemo.size() > ALTERNATIVES_MAX_ENTRIES) {
			this.alternativesMemo.clear();
		}
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
