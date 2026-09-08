package com.gabolle.backend.place.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.gabolle.backend.place.api.PlacePageResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse.MatchedField;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 장소 이름 검색(-462)과 갈래 필터 목록 조회(-473 의 목록 절반).
 *
 * <h2>🔴 정렬을 먼저 자르지 않고 왜 <b>다 받은 뒤에</b> 하는가</h2>
 *
 * {@link PlaceRepository#searchByName} 은 정렬 없이 후보만 돌려준다 — 정확일치·접두일치·포함을
 * 가르는 규칙이 SQL 로 쓰기엔 길고, 이 규모(수백~수천 행)에서 자바 정렬 비용은 무의미하다. 그래서
 * 후보를 자바에서 등급을 매기고 정렬한 다음 커서 구간을 잘라 낸다.
 *
 * <p>🔴 예전에는 리포지토리에서 {@code offset + limit + 1} 개만 <b>먼저 잘라서</b> 받고 그 잘린
 * 집합만 정렬했다. {@code searchByName} 에 {@code ORDER BY} 가 없어서 DB 가 그 {@code offset +
 * limit + 1} 개를 어떤 순서로 고르는지는 정해져 있지 않다 — 그래서 정확일치가 그 안에 안 뽑히면
 * 첫 페이지에서 통째로 사라지고, 페이지를 넘길 때마다 그 무작위성 때문에 같은 항목이 두 페이지에
 * 걸쳐 나올 수 있었다. 지금은 조건(query·category)만으로 <b>offset 없이</b> {@link #MAX_RANKED}
 * 행까지 받아 <b>전부</b> 정렬한 뒤에야 자바에서 {@code offset}·{@code limit} 으로 자른다 — 정렬
 * 대상 자체가 매번 같으므로 등급이 같은 항목의 순서도 항상 같다.
 *
 * <h2>🔴 {@link #MAX_RANKED} 를 넘으면 왜 알리는가</h2>
 *
 * 그래도 상한은 있다 — 조건에 맞는 행이 500 을 넘으면 그 이상은 정렬 대상에도 못 들어간다(그
 * 500 개 자체도 DB 반환 순서가 정해져 있지 않아 무작위 표본이다). 조용히 자르면 "정확일치가 왜
 * 안 나오냐" 가 재현 불가능한 버그가 되므로, 그 사실을 {@link PlacePageResponse#rankTruncated()}
 * 로 응답에 싣는다.
 *
 * <h2>🔴 페이지 경계를 "가져온 뒤에" 자르는 이유</h2>
 *
 * 리포지토리의 {@link Limit} 은 최대 개수일 뿐 오프셋을 모른다. 그래서 매번 정렬까지 끝낸 뒤,
 * 그중 {@code [offset, offset+limit)} 구간을 잘라 낸다. 다음 페이지가 있는지는 정렬된 전체
 * 개수와 {@code offset+limit} 을 비교해서 안다 — 리포지토리에서 한 장 더 받아 보는 예전 방식과
 * 달리 이미 전체를 들고 있으므로 추가로 받을 필요가 없다.
 */
@Service
@Profile({"db", "dev"})
public class PlaceSearchService {

	private static final int DEFAULT_LIMIT = 20;

	private static final int MAX_LIMIT = 50;

	/**
	 * 이름 검색에서 자바 정렬 대상으로 삼는 최대 행 수. 이 이상은
	 * {@link PlacePageResponse#rankTruncated()} 로 알린다 — 근거는 클래스 javadoc.
	 */
	private static final int MAX_RANKED = 500;

	/**
	 * 커서 offset 상한. fingerprint 는 위조를 막지 않으므로({@link SearchCursor} 참고) 임의로 큰
	 * offset 을 넣으면 매번 {@link #MAX_RANKED} 행 전체를 정렬만 하고 버리는 요청을 무한히 반복시킬
	 * 수 있다. 정상적인 이어받기라면 최대 {@link #MAX_LIMIT} 씩 200번을 넘겨받을 일이 없으므로
	 * 10,000이면 충분히 넉넉하다.
	 */
	private static final int MAX_OFFSET = 10_000;

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public PlaceSearchService(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	/** 이름 검색 (-462). */
	public PlacePageResponse search(String query, String category, Integer limit, String cursor) {
		String trimmedQuery = requireQuery(query);
		int effectiveLimit = requireLimit(limit);
		String fingerprint = SearchCursor.fingerprint(trimmedQuery, category, effectiveLimit);
		int offset = resolveOffset(cursor, fingerprint);

		String pattern = ("%" + escapeLike(trimmedQuery) + "%").toLowerCase(Locale.ROOT);
		// 🔴 offset 을 더하지 않는다 — 그러면 매번 다른 (임의 순서의) 부분집합을 정렬하게 되어
		// 정확일치가 페이지마다 있다 없다 한다. 조건이 같으면 항상 같은 MAX_RANKED 행을 받아
		// 항상 같은 방식으로 정렬한다.
		Limit repoLimit = Limit.of(MAX_RANKED + 1);
		List<Place> candidates = (category == null || category.isBlank())
				? this.placeRepository.searchByName(pattern, repoLimit)
				: this.placeRepository.searchByNameAndCategory(pattern, category, repoLimit);

		boolean rankTruncated = candidates.size() > MAX_RANKED;
		List<Place> rankable = rankTruncated ? candidates.subList(0, MAX_RANKED) : candidates;

		List<RankedPlace> ranked = rank(rankable, trimmedQuery);
		ranked.sort(Comparator.comparingInt(RankedPlace::tier)
				.thenComparing(rankedPlace -> rankedPlace.place().getNameKo(),
						Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(rankedPlace -> rankedPlace.place().getPlaceId()));

		return toPage(ranked, offset, effectiveLimit, fingerprint, rankTruncated);
	}

	/**
	 * 갈래 필터 목록 (-473). {@link PlaceRepository#findHavingFeature} 가 이름·placeId 순으로
	 * 이미 정렬해 주므로 여기서는 다시 정렬하지 않는다.
	 *
	 * <p>🔴 커서를 받지 않는다. {@link PlacePageResponse#nextCursor} 를 보라 — 이 경로는
	 * limit+1 로 다음 페이지 유무만 본다.
	 *
	 * <p>🔴 {@code findHavingFeature} 는 {@code evidenceStatus <> UNKNOWN} 까지만 걸러서 "확인된
	 * 부재"(값이 {@code false} 인 행)를 가진 장소도 그대로 돌려준다. 그래서 그 결과를 바로 내보내지
	 * 않고, 후보 장소들의 피처를 {@link PlaceFeatureRepository#findByPlaceIdIn} 한 번으로 마저 읽어
	 * {@link PlaceFeature#indicatesPresence()} 가 참인 행이 실제로 있는 장소만 남긴다 — 질의는
	 * 여전히 둘이고 장소 수에 비례해 늘지 않는다.
	 */
	public PlacePageResponse searchByFacet(String featureType, String featureKey, Integer limit) {
		String normalizedFeatureType = requireFeatureType(featureType);
		String normalizedFeatureKey = (featureKey == null || featureKey.isBlank()) ? null : featureKey;
		int effectiveLimit = requireLimit(limit);

		List<Place> found = this.placeRepository.findHavingFeature(normalizedFeatureType, normalizedFeatureKey,
				Limit.of(effectiveLimit + 1));
		List<Place> confirmedPresent = filterConfirmedPresence(found, normalizedFeatureType, normalizedFeatureKey);

		boolean hasNext = confirmedPresent.size() > effectiveLimit;
		List<Place> page = hasNext ? confirmedPresent.subList(0, effectiveLimit) : confirmedPresent;
		List<PlaceSummaryResponse> items = page.stream().map(PlaceSummaryResponse::ofFacetMatch).toList();
		return new PlacePageResponse(items, effectiveLimit, null, hasNext, false);
	}

	/**
	 * {@code candidates} 중 지정한 표식이 <b>실제로 있는(indicatesPresence)</b> 장소만 남긴다.
	 *
	 * <p>🔴 {@code findByPlaceIdIn} 은 후보 장소의 피처를 <b>전부</b> 돌려준다 — 다른 종류의 피처가
	 * 섞여 있으므로 여기서 다시 featureType·featureKey 로 걸러야 한다.
	 */
	private List<Place> filterConfirmedPresence(List<Place> candidates, String featureType, String featureKey) {
		if (candidates.isEmpty()) {
			return candidates;
		}
		List<UUID> placeIds = candidates.stream().map(Place::getPlaceId).toList();
		List<PlaceFeature> features = this.placeFeatureRepository.findByPlaceIdIn(placeIds);
		Set<UUID> confirmedPresentIds = features.stream()
				.filter(feature -> feature.getFeatureType().equals(featureType))
				.filter(feature -> featureKey == null || featureKey.equals(feature.getFeatureKey()))
				.filter(PlaceFeature::indicatesPresence)
				.map(PlaceFeature::getPlaceId)
				.collect(Collectors.toSet());
		return candidates.stream().filter(place -> confirmedPresentIds.contains(place.getPlaceId())).toList();
	}

	private PlacePageResponse toPage(List<RankedPlace> ranked, int offset, int limit, String fingerprint,
			boolean rankTruncated) {
		int fromIndex = Math.min(offset, ranked.size());
		int toIndex = Math.min(offset + limit, ranked.size());
		List<RankedPlace> windowed = ranked.subList(fromIndex, toIndex);
		boolean hasNext = ranked.size() > offset + limit;

		List<PlaceSummaryResponse> items = windowed.stream()
				.map(rankedPlace -> PlaceSummaryResponse.of(rankedPlace.place(), rankedPlace.matchedField()))
				.toList();

		String nextCursor = hasNext ? SearchCursor.of(fingerprint, offset + limit).encode() : null;
		return new PlacePageResponse(items, limit, nextCursor, hasNext, rankTruncated);
	}

	/**
	 * 🔴 offset 상한을 fingerprint 검사보다 먼저도 나중도 아니라 <b>같이</b> 본다 — 형식이 맞고
	 * fingerprint 도 지금 조건과 같은 커서라도, offset 이 {@link #MAX_OFFSET} 을 넘으면 매번
	 * {@link #MAX_RANKED} 행을 통째로 정렬만 하고 버리는 요청이 된다. 상한을 여기서 먼저 걸러
	 * {@code offset + limit} 덧셈이 다루는 값의 범위를 좁혀 두면(둘 다 int 로도 넉넉한 범위)
	 * {@code Math.addExact} 없이도 오버플로를 걱정할 필요가 없다.
	 */
	private int resolveOffset(String cursor, String fingerprint) {
		if (cursor == null || cursor.isBlank()) {
			return 0;
		}
		SearchCursor decoded = SearchCursor.decode(cursor);
		if (!decoded.fingerprint().equals(fingerprint)) {
			// 🔴 조건이 바뀐 채로 이어받으면 offset 이 다른 질의의 결과를 가리킨다 — 조용히
			// 뒤섞이느니 거부한다.
			throw new PlaceRequestException("INVALID_CURSOR", "검색 조건이 바뀌어 이어받을 수 없습니다.", List.of("cursor"));
		}
		if (decoded.offset() > MAX_OFFSET) {
			// 🔴 fingerprint 는 위조를 막지 않는다(SearchCursor 참고) — 검색 조건만 알면 누구나
			// 유효한 fingerprint 로 임의의 offset 을 만들 수 있다. 상한이 없으면 그 offset 으로
			// 매번 표 전체에 가까운 범위를 훑게 된다.
			throw new PlaceRequestException("INVALID_CURSOR", "커서 위치가 너무 커서 이어받을 수 없습니다.", List.of("cursor"));
		}
		return decoded.offset();
	}

	private List<RankedPlace> rank(List<Place> candidates, String query) {
		String queryLower = query.toLowerCase(Locale.ROOT);
		List<RankedPlace> ranked = new ArrayList<>(candidates.size());
		for (Place place : candidates) {
			int koTier = tier(place.getNameKo(), queryLower);
			int enTier = place.getNameEn() == null ? Integer.MAX_VALUE : tier(place.getNameEn(), queryLower);
			// 한국어 이름이 걸리면 우선한다 — 같은 등급이면(<=) 한국어를 택한다.
			if (koTier <= enTier) {
				ranked.add(new RankedPlace(place, koTier, MatchedField.NAME_KO));
			}
			else {
				ranked.add(new RankedPlace(place, enTier, MatchedField.NAME_EN));
			}
		}
		return ranked;
	}

	/** 0=정확일치, 1=접두일치, 2=포함. 후보는 이미 LIKE 로 걸러졌으니 포함까지는 항상 걸린다. */
	private int tier(String name, String queryLower) {
		String nameLower = name.toLowerCase(Locale.ROOT);
		if (nameLower.equals(queryLower)) {
			return 0;
		}
		if (nameLower.startsWith(queryLower)) {
			return 1;
		}
		return 2;
	}

	/**
	 * 🔴 {@code %} 와 {@code _} 를 이스케이프하지 않으면 사용자가 {@code %} 하나로 표 전체를
	 * 스캔시킬 수 있다. {@code \} 를 가장 먼저 바꿔야 한다 — 나중에 붙이는 {@code \%}·{@code \_}
	 * 의 역슬래시까지 다시 이스케이프되는 것을 막기 위해서다. 리포지토리 쪽 JPQL 이
	 * {@code ESCAPE '\'} 로 이스케이프 문자를 고정해 뒀다.
	 */
	private String escapeLike(String raw) {
		return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/**
	 * 검색어를 다듬어 돌려준다.
	 *
	 * <p>🔴 공백을 반드시 떼야 한다. 안 떼면 {@code "  감천  "} 이 {@code %  감천  %} 패턴이 되어
	 * 빈 결과가 나오고, 등급 판정({@link #tier})도 공백 포함 문자열과 비교해서 정확일치가 영영 안
	 * 나온다. 출발지 검색({@code OriginSearchService})은 이미 다듬고 있어서, 안 다듬으면 두 API 가
	 * 같은 입력에 다르게 반응한다.
	 *
	 * <p>다듬은 값이 커서 fingerprint 에도 들어가므로, 공백만 다른 같은 검색어는 같은 커서를 쓴다.
	 */
	private String requireQuery(String query) {
		if (query == null || query.isBlank()) {
			throw new PlaceRequestException("INVALID_REQUEST", "검색어를 입력해 주세요.", List.of("query"));
		}
		return query.strip();
	}

	private String requireFeatureType(String featureType) {
		if (featureType == null || featureType.isBlank()) {
			throw new PlaceRequestException("INVALID_REQUEST", "검색어와 갈래 중 하나만 지정해 주세요.", List.of("facetType"));
		}
		return featureType;
	}

	private int requireLimit(Integer limit) {
		int effective = limit == null ? DEFAULT_LIMIT : limit;
		if (effective < 1 || effective > MAX_LIMIT) {
			throw new PlaceRequestException("INVALID_REQUEST", "limit 은 1에서 50 사이여야 합니다.", List.of("limit"));
		}
		return effective;
	}

	/** 후보 하나에 매긴 등급과, 어느 이름 칸이 그 등급을 만들었는가. */
	private record RankedPlace(Place place, int tier, MatchedField matchedField) {
	}
}
