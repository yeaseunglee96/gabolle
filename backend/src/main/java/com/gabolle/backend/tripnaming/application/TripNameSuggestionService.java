package com.gabolle.backend.tripnaming.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.tripnaming.adapter.GmsTripNamer;
import com.gabolle.backend.tripnaming.config.TripNamingProperties;
import com.gabolle.backend.tripnaming.presentation.dto.TripNameSuggestionsResponse;

/**
 * 여행 이름 후보를 만든다. 모델이 죽었든 키가 없든 지어낸 이름이 전부 버려졌든 사용자는
 * 이름을 받고, 응답의 {@code source} 가 그것이 모델이 지은 것인지를 말한다.
 *
 * <p>메뉴판 읽기({@code MenuScanService})는 반대로 실패를 실패로 답한다. 거기서 빈 결과는
 * 「알레르기가 없다」로 읽히지만, 여기서 템플릿 이름은 덜 멋진 이름일 뿐이다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripNameSuggestionService {

	private final TripQueryService tripQueryService;

	private final TripPlaceVocabulary vocabulary;

	private final GmsTripNamer namer;

	private final TripNamingProperties properties;

	private final Clock clock;

	/**
	 * 사용자마다 최근 호출 시각. {@code MenuScanRateLimiter} 와 같은 일을 하지만 한도를 넘을
	 * 때의 행동이 다르다 — 메뉴판은 거절하고 이쪽은 템플릿으로 물러선다.
	 */
	private final Map<String, Deque<Instant>> recentCalls = new ConcurrentHashMap<>();

	public TripNameSuggestionService(TripQueryService tripQueryService, TripPlaceVocabulary vocabulary,
			GmsTripNamer namer, TripNamingProperties properties, Clock clock) {
		this.tripQueryService = tripQueryService;
		this.vocabulary = vocabulary;
		this.namer = namer;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public TripNameSuggestionsResponse suggest(String tripId, String requesterUserId) {
		return suggest(tripId, requesterUserId, false);
	}

	/**
	 * @param english 영어 화면이 부르는가({@code Accept-Language} 첫 언어가 영어). S15P21E201-1780(고지혁 QA) — 전에는
	 *     언어를 안 보고 한국어 이름만 냈다.
	 *
	 *     <p>🔴 영어면 모델을 부르지 않고 영어 템플릿으로 답한다. 지어낸 장소를 거르는 {@link PlaceWordGuard} 는 한글
	 *     조각만 본다 — 영어 이름은 검사 없이 통과하므로, 모델이 지어낸 영어 장소 이름이 그대로 제목에 박힐 수 있다.
	 *     거를 수 없는 것을 내보내지 않는다.
	 */
	@Transactional(readOnly = true)
	public TripNameSuggestionsResponse suggest(String tripId, String requesterUserId, boolean english) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterUserId);
		Trip trip = view.trip();

		if (english) {
			return englishTemplate(trip, this.vocabulary.placeNamesOf(tripId, true));
		}

		List<String> placeNames = this.vocabulary.placeNamesOf(tripId);
		if (placeNames.isEmpty() || !this.namer.isConfigured() || !withinLimit(requesterUserId)) {
			// 기댈 곳이 없는데 지어내게 두면 그게 전부 거짓이 된다.
			return template(trip, placeNames);
		}

		List<String> raw = this.namer.suggest(placeNames, trip.days(), trip.partySize());

		List<String> kept = new ArrayList<>();
		int discarded = 0;
		for (String candidate : raw) {
			if (isUsable(candidate) && PlaceWordGuard.isTruthful(candidate, placeNames)) {
				kept.add(candidate);
			}
			else {
				discarded++;
			}
		}

		if (kept.isEmpty()) {
			TripNameSuggestionsResponse fallback = template(trip, placeNames);
			return new TripNameSuggestionsResponse(fallback.suggestions(),
					TripNameSuggestionsResponse.TEMPLATE, discarded);
		}
		return new TripNameSuggestionsResponse(List.copyOf(kept), TripNameSuggestionsResponse.MODEL, discarded);
	}

	/**
	 * 저장할 수 있는 이름인가. {@link Trip#rename} 이 거부할 이름은 후보로 내놓지 않는다 —
	 * 고른 순간 400 이 나면 막다른 길이다.
	 */
	private boolean isUsable(String candidate) {
		if (candidate == null || candidate.isBlank()) {
			return false;
		}
		if (candidate.codePointCount(0, candidate.length()) > Trip.TITLE_MAX_LENGTH) {
			return false;
		}
		return candidate.codePoints().noneMatch(Character::isISOControl);
	}

	/**
	 * 일정에서 그대로 만든 이름. 지어낸 것이 하나도 없다. 장소가 없으면 날짜로만 만든다.
	 */
	private TripNameSuggestionsResponse template(Trip trip, List<String> placeNames) {
		List<String> names = new ArrayList<>();
		if (!placeNames.isEmpty()) {
			String first = placeNames.get(0);
			int others = placeNames.size() - 1;
			names.add(others > 0 ? first + " 외 " + others + "곳 · " + trip.days() + "일"
					: first + " · " + trip.days() + "일");
			names.add(first + " " + trip.days() + "일");
		}
		names.add(trip.startDate() + " ~ " + trip.finishDate());
		return new TripNameSuggestionsResponse(List.copyOf(names), TripNameSuggestionsResponse.TEMPLATE, 0);
	}

	/**
	 * {@link #template} 의 영어판. 장소 이름은 영어 이름이 있으면 그것, 없으면 한국어 그대로다.
	 */
	private TripNameSuggestionsResponse englishTemplate(Trip trip, List<String> placeNames) {
		List<String> names = new ArrayList<>();
		String days = trip.days() + (trip.days() == 1 ? " day" : " days");
		if (!placeNames.isEmpty()) {
			String first = placeNames.get(0);
			int others = placeNames.size() - 1;
			names.add(others > 0 ? first + " + " + others + " more · " + days : first + " · " + days);
			names.add(days + " at " + first);
		}
		names.add(trip.startDate() + " ~ " + trip.finishDate());
		return new TripNameSuggestionsResponse(List.copyOf(names), TripNameSuggestionsResponse.TEMPLATE, 0);
	}

	/** 넘으면 거절하지 않고 템플릿으로 간다. */
	private boolean withinLimit(String userId) {
		Instant now = Instant.now(this.clock);
		Deque<Instant> calls = this.recentCalls.computeIfAbsent(userId, (key) -> new ArrayDeque<>());
		synchronized (calls) {
			calls.removeIf((at) -> at.isBefore(now.minusSeconds(60)));
			if (calls.size() >= this.properties.getPerMinuteLimit()) {
				return false;
			}
			calls.addLast(now);
			return true;
		}
	}
}
