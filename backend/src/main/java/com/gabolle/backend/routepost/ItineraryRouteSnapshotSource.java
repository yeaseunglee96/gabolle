package com.gabolle.backend.routepost;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.gabolle.backend.itinerary.application.ItineraryAccess;
import com.gabolle.backend.itinerary.application.ItineraryQueryService;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.place.domain.AccommodationCategories;

/**
 * 일정 상세 조회(멤버만 통과)로 사본을 뜬다. 숙소 판정은 {@code place.category} 로 한다.
 */
@Component
@Profile({ "db", "dev" })
public class ItineraryRouteSnapshotSource implements RouteSnapshotSource {

	/** 일정 조회가 없는 슬라이스(시험)에서도 이 빈은 뜬다 — 그때는 부르면 404 다. */
	private final ObjectProvider<ItineraryQueryService> itineraries;

	private final NamedParameterJdbcTemplate jdbc;

	public ItineraryRouteSnapshotSource(ObjectProvider<ItineraryQueryService> itineraries, NamedParameterJdbcTemplate jdbc) {
		this.itineraries = itineraries;
		this.jdbc = jdbc;
	}

	@Override
	public RouteSnapshot snapshot(UUID user, String itineraryId, Integer dayIndex) {
		ItineraryQueryService query = this.itineraries.getIfAvailable();
		if (query == null) {
			throw new RoutePostExceptions.SourceNotFoundException();
		}
		ItineraryDetailResponse detail;
		try {
			detail = query.getDetail(itineraryId, user.toString());
		}
		catch (ItineraryQueryController.ItineraryNotFoundException | ItineraryAccess.ItineraryForbiddenException e) {
			throw new RoutePostExceptions.SourceNotFoundException();
		}
		Set<UUID> placeIds = new HashSet<>();
		for (ItineraryDetailResponse.Day day : detail.days()) {
			for (ItineraryDetailResponse.Item item : day.items()) {
				if (item.placeId() != null) {
					try {
						placeIds.add(UUID.fromString(item.placeId()));
					}
					catch (IllegalArgumentException ignored) {
						// 장소가 아닌 항목 — 빌더가 숙소처럼 건너뛴다.
					}
				}
			}
		}
		Set<UUID> lodging = new HashSet<>();
		if (!placeIds.isEmpty()) {
			List<UUID> rows = this.jdbc.queryForList(
					"SELECT place_id FROM place WHERE place_id IN (:ids) AND category IN (:lodging)",
					new MapSqlParameterSource("ids", placeIds).addValue("lodging", AccommodationCategories.CODES),
					UUID.class);
			rows.stream().filter(Objects::nonNull).forEach(lodging::add);
		}
		return RouteSnapshotBuilder.build(detail, lodging, dayIndex);
	}
}
