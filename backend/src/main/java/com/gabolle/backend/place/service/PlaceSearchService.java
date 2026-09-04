package com.gabolle.backend.place.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.gabolle.backend.place.api.PlacePageResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse.MatchedField;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 장소 이름 검색(-462)과 갈래 필터 목록 조회(-473 의 목록 절반).
 *
 * <h2>🔴 정렬을 왜 여기서 하는가</h2>
 *
 * {@link PlaceRepository#searchByName} 은 정렬 없이 후보만 돌려준다 — 정확일치·접두일치·포함을
 * 가르는 규칙이 SQL 로 쓰기엔 길고, 이 규모(수백~수천 행)에서 자바 정렬 비용은 무의미하다.
 * 그래서 후보를 다 받아 여기서 등급을 매기고 정렬한 다음 커서 구간을 잘라 낸다.
 *
 * <h2>🔴 페이지 경계를 "가져온 뒤에" 자르는 이유</h2>
 *
 * 리포지토리의 {@link Limit} 은 최대 개수일 뿐 오프셋을 모른다. 그래서 매번
 * {@code offset + limit + 1} 개를 받아 전부 정렬한 뒤, 그중 {@code [offset, offset+limit)} 구간을
 * 잘라 낸다. {@code +1} 은 그 다음 페이지가 있는지 보려고 한 장 더 받는 것이다 — 정확히 그만큼만
 * 받으면 지금 페이지가 마지막인지 알 방법이 없다.
 */
@Service
@Profile({"db", "dev"})
public class PlaceSearchService {

	private static final int DEFAULT_LIMIT = 20;

	private static final int MAX_LIMIT = 50;

	private final PlaceRepository placeRepository;

	public PlaceSearchService(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/** 이름 검색 (-462). */
	public PlacePageResponse search(String query, String category, Integer limit, String cursor) {
		String trimmedQuery = requireQuery(query);
		int effectiveLimit = requireLimit(limit);
		String fingerprint = SearchCursor.fingerprint(trimmedQuery, category, effectiveLimit);
		int offset = resolveOffset(cursor, fingerprint);

		String pattern = ("%" + escapeLike(trimmedQuery) + "%").toLowerCase(Locale.ROOT);
		Limit repoLimit = Limit.of(offset + effectiveLimit + 1);
		List<Place> candidates = (category == null || category.isBlank())
				? this.placeRepository.searchByName(pattern, repoLimit)
				: this.placeRepository.searchByNameAndCategory(pattern, category, repoLimit);

		List<RankedPlace> ranked = rank(candidates, trimmedQuery);
		ranked.sort(Comparator.comparingInt(RankedPlace::tier)
				.thenComparing(rankedPlace -> rankedPlace.place().getNameKo(),
						Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(rankedPlace -> rankedPlace.place().getPlaceId()));

		return toPage(ranked, offset, effectiveLimit, fingerprint);
	}

	/**
	 * 갈래 필터 목록 (-473). {@link PlaceRepository#findHavingFeature} 가 이름·placeId 순으로
	 * 이미 정렬해 주므로 여기서는 다시 정렬하지 않는다.
	 *
	 * <p>🔴 커서를 받지 않는다. {@link PlacePageResponse#nextCursor} 를 보라 — 이 경로는
	 * limit+1 로 다음 페이지 유무만 본다.
	 */
	public PlacePageResponse searchByFacet(String featureType, String featureKey, Integer limit) {
		String normalizedFeatureType = requireFeatureType(featureType);
		String normalizedFeatureKey = (featureKey == null || featureKey.isBlank()) ? null : featureKey;
		int effectiveLimit = requireLimit(limit);

		List<Place> found = this.placeRepository.findHavingFeature(normalizedFeatureType, normalizedFeatureKey,
				Limit.of(effectiveLimit + 1));

		boolean hasNext = found.size() > effectiveLimit;
		List<Place> page = hasNext ? found.subList(0, effectiveLimit) : found;
		List<PlaceSummaryResponse> items = page.stream().map(PlaceSummaryResponse::ofFacetMatch).toList();
		return new PlacePageResponse(items, effectiveLimit, null, hasNext);
	}

	private PlacePageResponse toPage(List<RankedPlace> ranked, int offset, int limit, String fingerprint) {
		int fromIndex = Math.min(offset, ranked.size());
		int toIndex = Math.min(offset + limit, ranked.size());
		List<RankedPlace> windowed = ranked.subList(fromIndex, toIndex);
		boolean hasNext = ranked.size() > offset + limit;

		List<PlaceSummaryResponse> items = windowed.stream()
				.map(rankedPlace -> PlaceSummaryResponse.of(rankedPlace.place(), rankedPlace.matchedField()))
				.toList();

		String nextCursor = hasNext ? SearchCursor.of(fingerprint, offset + limit).encode() : null;
		return new PlacePageResponse(items, limit, nextCursor, hasNext);
	}

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

	private String requireQuery(String query) {
		if (query == null || query.isBlank()) {
			throw new PlaceRequestException("INVALID_REQUEST", "검색어를 입력해 주세요.", List.of("query"));
		}
		return query;
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
