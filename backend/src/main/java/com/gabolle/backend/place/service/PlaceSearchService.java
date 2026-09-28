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
		ranked.sort(Comparator.comparingInt(RankedPlace::tier)
				.thenComparing(rankedPlace -> rankedPlace.place().getNameKo(),
						Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(rankedPlace -> rankedPlace.place().getPlaceId()));

		return toPage(ranked, offset, effectiveLimit, fingerprint, rankTruncated);
	}

	/**
	 * 갈래 필터 목록. {@link PlaceRepository#findHavingFeature} 가 이름·placeId 순으로 이미
	 * 정렬해 주므로 다시 정렬하지 않는다. 커서를 받지 않고 limit+1 로 다음 페이지 유무만 본다.
	 *
	 * <p>{@code findHavingFeature} 는 {@code evidenceStatus} 까지만 걸러서 확인된 부재(값이
	 * {@code false} 인 행)를 가진 장소도 돌려준다. 그래서 피처를
	 * {@link PlaceFeatureRepository#findByPlaceIdIn} 한 번으로 마저 읽어
	 * {@link PlaceFeature#indicatesPresence()} 가 참인 장소만 남긴다 — 질의는 둘이고 장소 수에
	 * 비례해 늘지 않는다.
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

	/**
	 * fingerprint 가 맞아도 offset 이 {@link #MAX_OFFSET} 을 넘으면 거부한다. 여기서 범위를 좁혀
	 * 두므로 뒤의 {@code offset + limit} 덧셈에 {@code Math.addExact} 가 필요 없다.
	 */
	private int resolveOffset(String cursor, String fingerprint) {
		if (cursor == null || cursor.isBlank()) {
			return 0;
		}
		SearchCursor decoded = SearchCursor.decode(cursor);
		if (!decoded.fingerprint().equals(fingerprint)) {
			// 조건이 바뀐 채로 이어받으면 offset 이 다른 질의의 결과를 가리킨다.
			throw new PlaceRequestException("INVALID_CURSOR", "검색 조건이 바뀌어 이어받을 수 없습니다.", List.of("cursor"));
		}
		if (decoded.offset() > MAX_OFFSET) {
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
		String nameLower = compact(name).toLowerCase(Locale.ROOT);
		if (nameLower.equals(queryLower)) {
			return 0;
		}
		if (nameLower.startsWith(queryLower)) {
			return 1;
		}
		return 2;
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
	private record RankedPlace(Place place, int tier, MatchedField matchedField) {
	}
}
