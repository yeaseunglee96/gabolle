package com.gabolle.backend.place;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlacePageResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.service.PlaceRequestException;
import com.gabolle.backend.place.service.PlaceSearchService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이름 검색(S15P21E201-462) 완료 기준을 하나씩 확인한다.
 */
class PlaceSearchIntegrationTest extends PlacePostgresIntegrationTest {

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
		// 🔴 표를 비우지 않는다 — PlaceFeatureCodeMapTest 등 다른 통합 테스트가 같은 표에 행을 남긴다.
		this.fixture.cleanUp();
	}

	@Test
	@DisplayName("완료 기준 — '감천' 으로 검색하면 감천문화마을이 나온다")
	void searchByKoreanNameFindsThePlace() {
		UUID placeId = this.fixture.insertPlace("감천문화마을", "Gamcheon Culture Village",
				"ATTRACTION", 35.0975, 129.0107);

		PlacePageResponse page = this.placeSearchService.search(this.fixture.prefix() + "감천", null, null, null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId).contains(placeId);
	}

	@Test
	@DisplayName("완료 기준 — 영문 이름이 있는 장소는 영어로 검색해도 같은 장소가 나온다")
	void searchByEnglishNameFindsTheSamePlace() {
		UUID placeId = this.fixture.insertPlace("감천문화마을", "Gamcheon Culture Village",
				"ATTRACTION", 35.0975, 129.0107);

		PlacePageResponse page = this.placeSearchService.search(this.fixture.prefix() + "Gamcheon", null, null, null);

		PlaceSummaryResponse matched = page.items().stream()
				.filter(item -> item.placeId().equals(placeId)).findFirst().orElseThrow();
		assertThat(matched.matchedField()).isEqualTo(PlaceSummaryResponse.MatchedField.NAME_EN);
	}

	@Test
	@DisplayName("완료 기준 — 종류 필터가 동작한다")
	void categoryFilterWorks() {
		String token = this.fixture.token();
		UUID attraction = this.fixture.insertPlace("종류필터" + token, null, "ATTRACTION", 35.1, 129.0);
		UUID cafe = this.fixture.insertPlace("종류필터" + token, null, "CAFE", 35.1, 129.0);

		PlacePageResponse page = this.placeSearchService.search(
				this.fixture.prefix() + "종류필터" + token, "ATTRACTION", null, null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId).contains(attraction).doesNotContain(cafe);
	}

	@Test
	@DisplayName("완료 기준 — 결과 개수가 limit 으로 제한되고 커서로 이어받을 수 있다")
	void limitAndCursorPaginateResults() {
		String token = this.fixture.token();
		UUID first = this.fixture.insertPlace("이어받기가" + token, null, "ATTRACTION", 35.1, 129.0);
		UUID second = this.fixture.insertPlace("이어받기나" + token, null, "ATTRACTION", 35.1, 129.0);
		UUID third = this.fixture.insertPlace("이어받기다" + token, null, "ATTRACTION", 35.1, 129.0);
		String query = this.fixture.prefix() + "이어받기";

		PlacePageResponse firstPage = this.placeSearchService.search(query, null, 2, null);
		assertThat(firstPage.items()).hasSize(2);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(firstPage.nextCursor()).isNotBlank();

		PlacePageResponse secondPage = this.placeSearchService.search(query, null, 2, firstPage.nextCursor());
		assertThat(secondPage.items()).hasSize(1);
		assertThat(secondPage.hasNext()).isFalse();

		List<UUID> allIds = new ArrayList<>();
		firstPage.items().forEach(item -> allIds.add(item.placeId()));
		secondPage.items().forEach(item -> allIds.add(item.placeId()));
		assertThat(allIds).containsExactlyInAnyOrder(first, second, third);
	}

	@Test
	@DisplayName("검색어가 비어 있으면 INVALID_REQUEST 로 거부된다")
	void blankQueryIsRejected() {
		assertThatThrownBy(() -> this.placeSearchService.search("  ", null, null, null))
				.isInstanceOf(PlaceRequestException.class)
				.satisfies(ex -> assertThat(((PlaceRequestException) ex).getCode()).isEqualTo("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("limit 이 1~50 범위를 벗어나면 INVALID_REQUEST 로 거부된다")
	void outOfRangeLimitIsRejected() {
		assertThatThrownBy(() -> this.placeSearchService.search("아무거나", null, 51, null))
				.isInstanceOf(PlaceRequestException.class);
		assertThatThrownBy(() -> this.placeSearchService.search("아무거나", null, 0, null))
				.isInstanceOf(PlaceRequestException.class);
	}

	@Test
	@DisplayName("🔴 검색 조건이 바뀐 채로 커서를 보내면 INVALID_CURSOR 로 거부된다 — 결과가 조용히 뒤섞이는 것을 막는다")
	void cursorFingerprintMismatchIsRejected() {
		String token = this.fixture.token();
		this.fixture.insertPlace("지문검사가" + token, null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertPlace("지문검사나" + token, null, "ATTRACTION", 35.1, 129.0);
		String query = this.fixture.prefix() + "지문검사";

		PlacePageResponse firstPage = this.placeSearchService.search(query, null, 1, null);
		assertThat(firstPage.hasNext()).isTrue();

		// 같은 커서인데 limit 을 바꿔서 이어받으려 한다 — fingerprint 가 안 맞아야 한다.
		assertThatThrownBy(() -> this.placeSearchService.search(query, null, 2, firstPage.nextCursor()))
				.isInstanceOf(PlaceRequestException.class)
				.satisfies(ex -> assertThat(((PlaceRequestException) ex).getCode()).isEqualTo("INVALID_CURSOR"));
	}

	@Test
	@DisplayName("🔴 '_' 는 아무 한 글자가 아니라 글자 그대로 취급된다 — 이스케이프가 빠지면 다른 글자도 걸린다")
	void underscoreIsTreatedLiterallyNotAsWildcard() {
		String token = this.fixture.token();
		UUID withUnderscore = this.fixture.insertPlace("언더바_있음" + token, null, "ATTRACTION", 35.1, 129.0);
		UUID withOtherChar = this.fixture.insertPlace("언더바X있음" + token, null, "ATTRACTION", 35.1, 129.0);

		PlacePageResponse page = this.placeSearchService.search(
				this.fixture.prefix() + "언더바_있음" + token, null, null, null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId)
				.contains(withUnderscore)
				.doesNotContain(withOtherChar);
	}
}
