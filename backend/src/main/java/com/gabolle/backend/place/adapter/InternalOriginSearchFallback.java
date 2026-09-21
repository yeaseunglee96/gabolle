package com.gabolle.backend.place.adapter;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 카카오 로컬 검색이 안 될 때 {@code place} 표에서 이름으로 찾는 대체 목록. {@code place} 표가
 * 비어 있으면 빈 목록이 오는데 그것도 정상 동작이지 오류가 아니다.
 *
 * <p>{@code PlaceRepository#searchByName} 의 계약대로 {@code %}·{@code _}·{@code \} 를 이스케이프한
 * 뒤 {@code %질의어%} 패턴으로 넘긴다. 나온 순서 그대로 쓴다 — 대체 목록이라 정확도보다 "그래도
 * 뭔가는 나온다" 가 우선이다.
 */
@Component
@Profile({"db", "dev"})
public class InternalOriginSearchFallback implements OriginSearchPort {

	private final PlaceRepository placeRepository;

	public InternalOriginSearchFallback(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	@Override
	public List<OriginCandidate> search(String query, int limit) {
		String pattern = "%" + escape(query.toLowerCase(Locale.ROOT)) + "%";
		List<Place> places = this.placeRepository.searchByName(pattern, Limit.of(Math.max(1, limit)));
		return places.stream()
				.filter(Place::hasCoordinates)
				.map(this::toCandidate)
				.toList();
	}

	private OriginCandidate toCandidate(Place place) {
		return new OriginCandidate(place.getNameKo(), place.getAddress(), place.getLat(), place.getLng(),
				place.getPlaceId().toString(), OriginCandidate.Source.INTERNAL_FALLBACK);
	}

	/** {@code %} · {@code _} · {@code \} 를 이스케이프한다 — {@code PlaceRepository#searchByName} 계약. */
	private String escape(String raw) {
		return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
