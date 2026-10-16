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
 * 장소 이름 검색과 갈래 필터 목록 조회.
 *
 * <p>{@link PlaceRepository#searchByName} 은 정렬 없이 후보만 돌려준다 — 정확일치·접두일치·포함을
 * 가르는 규칙이 SQL 로 쓰기엔 길고, 이 규모에서 자바 정렬 비용은 무의미하다.
 *
 * <p>DB 에서 {@code offset} 을 빼고 조건만으로 {@link #MAX_RANKED} 행까지 받아 전부 정렬한 뒤에야
 * 자바에서 자른다. {@code searchByName} 에 {@code ORDER BY} 가 없어 DB 가 고르는 순서가 정해져
 * 있지 않으므로, 먼저 자르면 정확일치가 페이지마다 있다 없다 하고 같은 항목이 두 페이지에 걸쳐
 * 나온다. 정렬 대상이 매번 같아야 같은 등급의 순서도 같다.
 *
 * <p>조건에 맞는 행이 {@link #MAX_RANKED} 를 넘으면 그 이상은 정렬 대상에도 못 들어가고, 받아 온
 * 500 개 자체도 무작위 표본이다. 조용히 자르면 "정확일치가 왜 안 나오냐" 가 재현 불가능한 버그가
 * 되므로 {@link PlacePageResponse#rankTruncated()} 로 알린다.
 *
 * <p>리포지토리의 {@link Limit} 은 최대 개수일 뿐 오프셋을 모르므로 페이지 경계는 정렬을 끝낸 뒤
 * {@code [offset, offset+limit)} 로 자른다. 다음 페이지 유무는 정렬된 전체 개수로 안다.
 */
@Service
@Profile({"db", "dev"})
public class PlaceSearchService {

	private static final int DEFAULT_LIMIT = 20;

	private static final int MAX_LIMIT = 50;

	/**
	 * 이름 검색에서 자바 정렬 대상으로 삼는 최대 행 수. 이 이상은
	 * {@link PlacePageResponse#rankTruncated()} 로 알린다.
	 */
	private static final int MAX_RANKED = 500;

	/**
	 * 커서 offset 상한. fingerprint 는 위조를 막지 않으므로({@link SearchCursor} 참고) 임의로 큰
	 * offset 을 넣으면 매번 {@link #MAX_RANKED} 행 전체를 정렬만 하고 버리는 요청을 무한히 반복시킬
	 * 수 있다. 정상적인 이어받기라면 최대 {@link #MAX_LIMIT} 씩 200번을 넘겨받을 일이 없으므로
	 * 10,000이면 충분히 넉넉하다.
	 */
	private static final int MAX_OFFSET = 10_000;

	/**
	 * 갈래 목록의 커서 offset 상한. 갈래 목록은 이름 검색과 달리 정렬을 DB 가 하므로 «앞에서 offset+limit+1 행»을
	 * 읽어 자른다 — offset 이 커질수록 읽는 행이 늘어난다. 그래서 이름 검색보다 훨씬 낮게 잡는다. 지금 탐색 갈래 중
	 * 가장 큰 것이 76곳이라 1,000이면 넉넉하다.
	 */
	private static final int MAX_FACET_OFFSET = 1_000;

	/**
	 * 갈래 목록에서 한 요청이 DB 에서 읽는 행의 상한. {@link #MAX_FACET_OFFSET} 에 한 쪽({@link #MAX_LIMIT})과
	 * 「다음 쪽이 있나」 한 행을 더하고도 남는다. 이 안에서 확인된 장소를 못 채우면
	 * {@link PlacePageResponse#rankTruncated()} 로 알린다.
	 */
	private static final int MAX_FACET_SCAN = 1_200;

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public PlaceSearchService(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	/** 이름 검색. */
	public PlacePageResponse search(String query, String category, Integer limit, String cursor) {
		String trimmedQuery = requireQuery(query);
		int effectiveLimit = requireLimit(limit);
		String fingerprint = SearchCursor.fingerprint(trimmedQuery, category, effectiveLimit);
		int offset = resolveOffset(cursor, fingerprint);

		// 공백을 뗀다 — 리포지토리가 이름에서도 공백을 떼고 비교한다(S15P21E201-1745).
		String pattern = ("%" + escapeLike(compact(trimmedQuery)) + "%").toLowerCase(Locale.ROOT);
		// offset 을 더하지 않는다 — 조건이 같으면 항상 같은 MAX_RANKED 행을 받아야 한다.
		Limit repoLimit = Limit.of(MAX_RANKED + 1);
		List<Place> candidates = (category == null || category.isBlank())
				? this.placeRepository.searchByName(pattern, repoLimit)
				: this.placeRepository.searchByNameAndCategory(pattern, category, repoLimit);

		boolean rankTruncated = candidates.size() > MAX_RANKED;
		List<Place> rankable = rankTruncated ? candidates.subList(0, MAX_RANKED) : candidates;

		List<RankedPlace> ranked = rank(rankable, trimmedQuery);
		String landmarkQuery = compact(trimmedQuery).toLowerCase(Locale.ROOT);
		ranked.sort(Comparator.comparingInt(RankedPlace::tier)
				// S15P21E201-1995 — 같은 등급(예: 「광안리」 접두일치 38곳) 안에서는 대표 관광지를 먼저.
				// 전에는 이름 가나다순이라 「광안리해수욕장」이 16번째였다(택시 카드 첫 화면에 없음).
				.thenComparingInt(rankedPlace -> landmarkRank(rankedPlace.place(), landmarkQuery))
				.thenComparing(rankedPlace -> rankedPlace.place().getNameKo(),
						Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(rankedPlace -> rankedPlace.place().getPlaceId()));

		return toPage(ranked, offset, effectiveLimit, fingerprint, rankTruncated);
	}

	/** 갈래 필터 목록의 첫 쪽 — 커서 없이 부르는 곳을 위한 짧은 형태. */
	public PlacePageResponse searchByFacet(String featureType, String featureKey, Integer limit) {
		return searchByFacet(featureType, featureKey, limit, null);
	}

	/**
	 * 갈래 필터 목록. {@link PlaceRepository#findHavingFeature} 가 이름·placeId 순으로 이미
	 * 정렬해 주므로 다시 정렬하지 않는다.
	 *
	 * <p>🔴 이어받기 커서를 지원한다(S15P21E201-2060). 전에는 커서를 안 받아서 {@code hasNext} 가 참인데
	 * {@code nextCursor} 는 {@code null} 이었고, 한 쪽이 최대 50개라 축제처럼 76곳인 갈래는 앞 20곳 뒤로 아무도 못
	 * 넘어갔다. 커서는 이름 검색과 같은 모양({@link SearchCursor})이고 지문에 갈래 종류·열쇠·한 쪽 크기가 들어
	 * 있다. {@link PlaceRepository#findHavingFeature} 는 오프셋을 모르는 {@link Limit} 뿐이라 «앞에서
	 * offset+limit+1 행»을 읽어 {@code [offset, offset+limit)} 를 자른다 — 첫 쪽은 전과 같은 질의다.
	 *
	 * <p>{@code findHavingFeature} 는 {@code evidenceStatus} 까지만 걸러서 확인된 부재(값이
	 * {@code false} 인 행)를 가진 장소도 돌려준다. 그래서 피처를
	 * {@link PlaceFeatureRepository#findByPlaceIdIn} 한 번으로 마저 읽어
	 * {@link PlaceFeature#indicatesPresence()} 가 참인 장소만 남긴다. 걸러져 쪽을 못 채우면 읽는 행을 두 배로
	 * 늘려 다시 읽는다(상한 {@link #MAX_FACET_SCAN}) — 걸러진 줄이 쪽 경계를 밀지 않게 하려는 것이다.
	 * 상한까지 읽고도 모자라면 {@code rankTruncated} 가 참이다.
	 */
	public PlacePageResponse searchByFacet(String featureType, String featureKey, Integer limit, String cursor) {
		String normalizedFeatureType = requireFeatureType(featureType);
		String normalizedFeatureKey = (featureKey == null || featureKey.isBlank()) ? null : featureKey;
		int effectiveLimit = requireLimit(limit);
		// 다른 갈래·다른 한 쪽 크기로 이어받는 실수를 거른다 — 이름 검색의 커서도 여기서는 지문이 달라 거절된다.
		String fingerprint = SearchCursor.fingerprint(
				"facet|" + normalizedFeatureType + "|" + (normalizedFeatureKey == null ? "" : normalizedFeatureKey),
				null, effectiveLimit);
		int offset = resolveOffset(cursor, fingerprint, MAX_FACET_OFFSET);

		// 다음 쪽이 있는지 보려고 한 행을 더 읽는다.
		int wanted = offset + effectiveLimit + 1;
		int fetch = wanted;
		List<Place> confirmedPresent;
		boolean exhausted;
		while (true) {
			List<Place> found = this.placeRepository.findHavingFeature(normalizedFeatureType, normalizedFeatureKey,
					Limit.of(fetch));
			confirmedPresent = filterConfirmedPresence(found, normalizedFeatureType, normalizedFeatureKey);
			exhausted = found.size() < fetch;
			if (confirmedPresent.size() >= wanted || exhausted || fetch >= MAX_FACET_SCAN) {
				break;
			}
			fetch = Math.min(fetch * 2, MAX_FACET_SCAN);
		}
		// 상한까지 읽었는데 DB 에 더 있고 확인된 장소가 모자라다 — 그 뒤는 보이지 않는다.
		boolean scanCapped = !exhausted && confirmedPresent.size() < wanted;

		int fromIndex = Math.min(offset, confirmedPresent.size());
		int toIndex = Math.min(offset + effectiveLimit, confirmedPresent.size());
		boolean hasNext = confirmedPresent.size() > offset + effectiveLimit;
		List<PlaceSummaryResponse> items = confirmedPresent.subList(fromIndex, toIndex).stream()
				.map(PlaceSummaryResponse::ofFacetMatch).toList();
		String nextCursor = hasNext ? SearchCursor.of(fingerprint, offset + effectiveLimit).encode() : null;
		return new PlacePageResponse(items, effectiveLimit, nextCursor, hasNext, scanCapped);
	}

	/**
	 * 종류(category)만으로 거른 목록 — 숙소 후보 조회가 쓴다. {@link #searchByFacet} 과 같이
	 * 커서를 받지 않는다.
	 */
	public PlacePageResponse listByCategories(List<String> categories, Integer limit) {
		int effectiveLimit = requireLimit(limit);
		List<String> lowerCategories = categories.stream().map(c -> c.toLowerCase(Locale.ROOT)).toList();

		List<Place> found = this.placeRepository.findByCategoryIn(lowerCategories, Limit.of(effectiveLimit + 1));
		boolean hasNext = found.size() > effectiveLimit;
		List<Place> page = hasNext ? found.subList(0, effectiveLimit) : found;
		List<PlaceSummaryResponse> items = page.stream().map(PlaceSummaryResponse::ofFacetMatch).toList();
		return new PlacePageResponse(items, effectiveLimit, null, hasNext, false);
	}

	/**
	 * 지정한 표식이 실제로 있는 장소만 남긴다. {@code findByPlaceIdIn} 은 후보 장소의 피처를 전부
	 * 돌려주므로 여기서 featureType·featureKey 로 다시 걸러야 한다.
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

	/** 이름 검색의 이어받기 — offset 상한은 {@link #MAX_OFFSET}. */
	private int resolveOffset(String cursor, String fingerprint) {
		return resolveOffset(cursor, fingerprint, MAX_OFFSET);
	}

	/**
	 * fingerprint 가 맞아도 offset 이 {@code maxOffset} 을 넘으면 거부한다. 여기서 범위를 좁혀
	 * 두므로 뒤의 {@code offset + limit} 덧셈에 {@code Math.addExact} 가 필요 없다.
	 */
	private int resolveOffset(String cursor, String fingerprint, int maxOffset) {
		if (cursor == null || cursor.isBlank()) {
			return 0;
		}
		SearchCursor decoded = SearchCursor.decode(cursor);
		if (!decoded.fingerprint().equals(fingerprint)) {
			// 조건이 바뀐 채로 이어받으면 offset 이 다른 질의의 결과를 가리킨다.
			throw new PlaceRequestException("INVALID_CURSOR", "검색 조건이 바뀌어 이어받을 수 없습니다.", List.of("cursor"));
		}
		if (decoded.offset() > maxOffset) {
			// fingerprint 는 위조를 막지 않는다 — 검색 조건만 알면 누구나 유효한 fingerprint 로
			// 임의의 offset 을 만들 수 있다.
			throw new PlaceRequestException("INVALID_CURSOR", "커서 위치가 너무 커서 이어받을 수 없습니다.", List.of("cursor"));
		}
		return decoded.offset();
	}

	private List<RankedPlace> rank(List<Place> candidates, String query) {
		String queryLower = compact(query).toLowerCase(Locale.ROOT);
		List<RankedPlace> ranked = new ArrayList<>(candidates.size());
		for (Place place : candidates) {
			int koTier = tier(place.getNameKo(), queryLower);
			int enTier = place.getNameEn() == null ? Integer.MAX_VALUE : tier(place.getNameEn(), queryLower);
			int localTier = place.localNames().values().stream()
					.mapToInt(name -> tier(name, queryLower)).min().orElse(Integer.MAX_VALUE);
			// 한국어 → 영어 → 현지 이름 순으로 우선한다 — 같은 등급이면(<=) 앞의 것을 택한다.
			if (koTier <= enTier && koTier <= localTier) {
				ranked.add(new RankedPlace(place, koTier, MatchedField.NAME_KO));
			}
			else if (enTier <= localTier) {
				ranked.add(new RankedPlace(place, enTier, MatchedField.NAME_EN));
			}
			else {
				ranked.add(new RankedPlace(place, localTier, MatchedField.NAME_LOCAL));
			}
		}
		return ranked;
	}

	/**
	 * 0=정확일치, 1=접두일치, 2=포함, 안 걸림={@link Integer#MAX_VALUE}. 후보는 LIKE 로 걸러졌지만 그건 이름 칸
	 * 중 <b>하나</b>가 걸렸다는 뜻이라, 칸마다 정말 들어 있는지를 여기서 다시 본다 — 안 보면 안 걸린 칸이
	 * 「포함」으로 쳐져 어느 칸이 걸렸는지({@code matchedField})가 틀린다(S15P21E201-1875).
	 */
	private int tier(String name, String queryLower) {
		String nameLower = compact(name).toLowerCase(Locale.ROOT);
		if (nameLower.equals(queryLower)) {
			return 0;
		}
		if (nameLower.startsWith(queryLower)) {
			return 1;
		}
		return nameLower.contains(queryLower) ? 2 : Integer.MAX_VALUE;
	}

	/** 공백을 전부 뗀다. 「해운대 해수욕장」과 「해운대해수욕장」을 같은 이름으로 본다. */
	private static String compact(String raw) {
		return raw.replaceAll("\s+", "");
	}

	/**
	 * {@code %}·{@code _} 를 이스케이프하지 않으면 {@code %} 하나로 표 전체를 스캔시킬 수 있다.
	 * {@code \} 를 가장 먼저 바꿔야 나중에 붙이는 역슬래시가 다시 이스케이프되지 않는다.
	 * 리포지토리 쪽 JPQL 이 {@code ESCAPE '\'} 로 이스케이프 문자를 고정해 뒀다.
	 */
	private String escapeLike(String raw) {
		return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/**
	 * 공백을 반드시 떼야 한다. 안 떼면 패턴에 공백이 섞여 빈 결과가 나오고 {@link #tier} 의
	 * 정확일치도 영영 안 나온다. 다듬은 값이 커서 fingerprint 에도 들어가므로 공백만 다른 같은
	 * 검색어는 같은 커서를 쓴다.
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
	/** 검색어 뒤에 이것이 붙은 이름은 그 지명의 대표 장소다(「광안리」+「해수욕장」). */
	private static final List<String> LANDMARK_SUFFIXES = List.of(
			"해수욕장", "해변", "문화마을", "전통시장", "시장", "공원", "해상케이블카", "케이블카", "역", "beach");

	/** 사람들이 「거기 가자」고 할 때 떠올리는 갈래가 앞이다. 식당·숙소·갈래 없음은 뒤. */
	private static final List<String> LANDMARK_CATEGORIES = List.of(
			"SEA_BEACH", "NATURE_WALK", "CULTURE_TEMPLE", "CITY", "MARKET", "FESTIVAL_EVENT");

	/**
	 * 같은 등급 안의 순서. 작을수록 앞이다.
	 * 0~ : 이름이 「검색어+대표 꼬리말」이면 꼬리말 순번(가장 앞)
	 * 20~: 그 밖은 갈래 순번(관광 갈래가 앞), 영어 이름이 있으면(공공 관광 자료로 정리된 곳) 한 칸 더 앞
	 */
	private static int landmarkRank(Place place, String queryLower) {
		for (String name : new String[] { place.getNameKo(), place.getNameEn() }) {
			if (name == null) {
				continue;
			}
			String n = compact(name).toLowerCase(Locale.ROOT);
			for (int i = 0; i < LANDMARK_SUFFIXES.size(); i++) {
				if (n.equals(queryLower + LANDMARK_SUFFIXES.get(i))) {
					return i;
				}
			}
		}
		int category = place.getCategory() == null ? -1 : LANDMARK_CATEGORIES.indexOf(place.getCategory());
		int base = 20 + (category < 0 ? LANDMARK_CATEGORIES.size() : category) * 2;
		boolean curated = place.getNameEn() != null && !place.getNameEn().isBlank();
		return curated ? base : base + 1;
	}

	private record RankedPlace(Place place, int tier, MatchedField matchedField) {
	}
}
