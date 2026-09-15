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
 * 여행 이름 후보를 만든다 — S15P21E201-1025.
 *
 * <h2>🔴 이름은 <b>언제나</b> 나온다. 다만 어디서 나왔는지를 숨기지 않는다</h2>
 *
 * 모델이 죽었든, 키가 없든, 지어낸 이름이 전부 버려졌든 사용자는 이름을 받는다 —
 * 일정에서 그대로 만든 템플릿 이름이다. 그리고 응답의 {@code source} 가 그것이
 * <b>모델이 지은 것이 아님</b>을 말한다.
 *
 * <p>🔴 메뉴판 읽기({@code MenuScanService})는 정반대로 <b>실패를 실패로</b> 답한다.
 * 규칙이 다른 이유는 <b>실패가 뜻하는 것이 다르기 때문</b>이다. 거기서 빈 결과는
 * 「알레르기가 없다」로 읽혀 사람이 다칠 수 있고, 여기서 템플릿 이름은 그냥
 * <b>덜 멋진 이름</b>이다. 같은 규칙을 기계적으로 복사하지 않는다.
 *
 * <h2>순서</h2>
 * <ol>
 *   <li>참여자인지 본다 — {@code TripQueryService} 가 아니면 존재를 감춘 404</li>
 *   <li>이 여행의 일정에 있는 장소 이름을 모은다. <b>없으면 여기서 템플릿</b></li>
 *   <li>모델을 부른다 (키가 없거나 한도를 넘었으면 건너뛴다)</li>
 *   <li>🔴 받은 이름을 <b>검사한다</b> — 일정에 없는 장소가 들어 있으면 버린다</li>
 *   <li>남은 것이 없으면 템플릿</li>
 * </ol>
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
	 * 사용자마다 최근 호출 시각.
	 *
	 * <p>🔴 {@code MenuScanRateLimiter} 와 같은 일을 하는 <b>두 번째 사본</b>이다. 지금
	 * 합치지 않은 이유는 그쪽이 방금 머지됐고, 두 기능이 넘칠 때 하는 일이 서로 다르기
	 * 때문이다 — 메뉴판은 <b>거절</b>하고 이쪽은 <b>템플릿으로 물러선다.</b> 세 번째가
	 * 생기면 그때 합치는 것이 맞다.
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
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterUserId);
		Trip trip = view.trip();

		List<String> placeNames = this.vocabulary.placeNamesOf(tripId);
		if (placeNames.isEmpty() || !this.namer.isConfigured() || !withinLimit(requesterUserId)) {
			// 🔴 기댈 곳이 없는데 지어내게 두면 그게 전부 거짓이 된다.
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
	 * 저장할 수 있는 이름인가. 🔴 {@link Trip#rename} 이 거부할 이름을 <b>후보로 내놓지
	 * 않는다</b> — 고른 순간 400 이 나면 그건 우리가 만든 막다른 길이다.
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
	 * 일정에서 그대로 만든 이름. <b>지어낸 것이 하나도 없다.</b>
	 *
	 * <p>장소가 하나도 없으면 날짜로만 만든다 — 지금 화면이 쓰는 것과 같은 모양이라
	 * 사용자에게 새로울 것이 없지만, <b>거짓은 아니다.</b>
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

	/** 넘으면 <b>거절하지 않고</b> 템플릿으로 간다 — 이름을 못 받는 것보다 낫다. */
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
