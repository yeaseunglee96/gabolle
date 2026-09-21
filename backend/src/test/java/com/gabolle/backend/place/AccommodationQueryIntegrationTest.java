package com.gabolle.backend.place;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlacePageResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.domain.AccommodationCategories;
import com.gabolle.backend.place.service.PlaceSearchService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

class AccommodationQueryIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceSearchService placeSearchService;

	private PlaceFixture fixture;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
	}

	@AfterEach
	void tearDown() {
		this.fixture.cleanUp();
	}

	@Test
	@DisplayName("완료 기준 — 숙소 조회에 숙소 종류만 나온다")
	void accommodationQueryReturnsOnlyLodgingCategory() {
		String token = this.fixture.token();
		UUID lodging = this.fixture.insertPlace("숙소" + token, null, "LODGING", 35.1, 129.0);
		UUID food = this.fixture.insertPlace("음식점" + token, null, "FOOD", 35.1, 129.0);

		PlacePageResponse page = this.placeSearchService.listByCategories(AccommodationCategories.CODES, null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId)
				.contains(lodging)
				.doesNotContain(food);
		assertThat(page.items()).allSatisfy(item -> assertThat(item.category()).isEqualToIgnoringCase("LODGING"));
	}

	@Test
	@DisplayName("category 비교는 대소문자를 가리지 않는다")
	void categoryComparisonIsCaseInsensitive() {
		String token = this.fixture.token();
		UUID lodging = this.fixture.insertPlace("소문자숙소" + token, null, "lodging", 35.1, 129.0);

		PlacePageResponse page = this.placeSearchService.listByCategories(AccommodationCategories.CODES, null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId).contains(lodging);
	}

	@Test
	@DisplayName("숙소를 안 넣었으면 빈 목록이 나온다 — 지어내서 채우지 않는다")
	void emptyWhenNoAccommodationLoaded() {
		// 🔴 2026-09-21 정정. 여기 「지금 적재 자료에는 숙소가 없는 것이 사실이다」라고 적혀
		// 있었다. 그때는 맞았다 — 숙박 65곳의 갈래가 비어 있어 이 조회가 운영에서도 0건이었다.
		// 지금은 적재가 LODGING 을 채우므로 그 문장은 더 이상 사실이 아니다. 이 시험이 보는 것은
		// 「자료가 없다」가 아니라 「없으면 없다고 답한다」다.
		PlacePageResponse page = this.placeSearchService.listByCategories(AccommodationCategories.CODES, 1);
		assertThat(page.items()).noneMatch(item -> "LODGING".equalsIgnoreCase(item.category()));
	}
}
