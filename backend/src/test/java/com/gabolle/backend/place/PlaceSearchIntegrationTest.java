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
import com.gabolle.backend.place.service.SearchCursor;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이름 검색의 완료 기준을 하나씩 확인한다.
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
		// 표를 통째로 비우지 않는다 — 다른 통합 테스트가 같은 표에 행을 남긴다.
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
	@DisplayName("🔴 검색어 앞뒤 공백을 다듬는다 — 안 다듬으면 '  감천  ' 이 빈 결과가 된다")
	void queryIsTrimmedBeforeMatching() {
		UUID placeId = this.fixture.insertPlace("감천문화마을", null, "ATTRACTION", 35.0975, 129.0107);

		PlacePageResponse padded = this.placeSearchService.search(
				"  " + this.fixture.prefix() + "감천  ", null, null, null);

		assertThat(padded.items()).extracting(PlaceSummaryResponse::placeId).contains(placeId);
	}

	@Test
	@DisplayName("🔴 띄어 쓴 검색어도 붙여 쓴 이름을 찾는다 — 「해운대 해수욕장」이 0건이었다(S15P21E201-1745)")
	void spacedQueryFindsUnspacedName() {
		UUID placeId = this.fixture.insertPlace("해운대해수욕장", null, "SEA_BEACH", 35.1585, 129.1598);

		PlacePageResponse page = this.placeSearchService.search(this.fixture.prefix() + "해운대 해수욕장", null, null, null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId).contains(placeId);
	}

	@Test
	@DisplayName("🔴 붙여 쓴 검색어도 띄어 쓴 이름을 찾는다 — 반대 방향도 같다")
	void unspacedQueryFindsSpacedName() {
		UUID placeId = this.fixture.insertPlace("해운대 관광특구", null, "CITY", 35.1631, 129.1635);

		PlacePageResponse page = this.placeSearchService.search(this.fixture.prefix() + "해운대관광특구", null, null, null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId).contains(placeId);
	}

	@Test
	@DisplayName("🔴 띄어쓰기만 다르면 정확일치로 친다 — 정확일치가 「…주차장」보다 먼저다")
	void spacingDifferenceStillRanksAsExactMatch() {
		UUID parking = this.fixture.insertPlace("해운대해수욕장 주차장", null, "CITY", 35.1590, 129.1600);
		UUID beach = this.fixture.insertPlace("해운대해수욕장", null, "SEA_BEACH", 35.1585, 129.1598);

		PlacePageResponse page = this.placeSearchService.search(this.fixture.prefix() + "해운대 해수욕장", null, null, null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId).containsSubsequence(beach, parking);
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
	@DisplayName("🔴 완료 기준 — 한 페이지보다 많은 행에서도 정확일치가 첫 페이지에 나오고, "
			+ "어떤 항목도 두 페이지에 걸쳐 나오지 않으며, 전체 항목이 빠짐없이 나온다")
	void exactMatchSurvivesPagingAcrossManyRowsWithoutDuplicates() {
		// 행 수는 한 페이지(limit)보다 뚜렷이 많고 MAX_RANKED(500) 보다는 훨씬 적어야 한다.
		// 한 페이지 분량이면 잘림이 안 일어나 정렬 결함이 재현되지 않는다.
		String token = this.fixture.token();
		String exactName = "정렬완료" + token;
		UUID exactMatch = this.fixture.insertPlace(exactName, null, "ATTRACTION", 35.1, 129.0);
		List<UUID> prefixMatches = new ArrayList<>();
		for (int i = 0; i < 24; i++) {
			prefixMatches.add(this.fixture.insertPlace(exactName + "-" + i, null, "ATTRACTION", 35.1, 129.0));
		}
		String query = this.fixture.prefix() + exactName;
		int limit = 10;

		List<UUID> collected = new ArrayList<>();
		String cursor = null;
		boolean firstIteration = true;
		boolean exactMatchOnFirstPage = false;
		while (true) {
			PlacePageResponse page = this.placeSearchService.search(query, null, limit, cursor);
			List<UUID> pageIds = page.items().stream().map(PlaceSummaryResponse::placeId).toList();
			if (firstIteration) {
				exactMatchOnFirstPage = pageIds.contains(exactMatch);
				firstIteration = false;
			}
			collected.addAll(pageIds);
			if (!page.hasNext()) {
				break;
			}
			cursor = page.nextCursor();
		}

		assertThat(exactMatchOnFirstPage).isTrue();
		assertThat(collected).doesNotHaveDuplicates();
		List<UUID> expectedAll = new ArrayList<>(prefixMatches);
		expectedAll.add(exactMatch);
		assertThat(collected).containsExactlyInAnyOrderElementsOf(expectedAll);
	}

	@Test
	@DisplayName("🔴 offset 이 상한(10,000)을 넘는 커서는 INVALID_CURSOR 로 거부된다 — "
			+ "위조된 offset 으로 표 전체를 훑는 것을 막는다")
	void offsetBeyondMaxIsRejected() {
		// fingerprint 는 위조 방지가 아니라 조건 일치 확인용이라, 검색 조건만 알면 유효한
		// fingerprint 로 임의의 offset 을 만들 수 있다 — 그래서 서비스가 별도로 상한을 둔다.
		String query = this.fixture.prefix() + "오프셋상한" + this.fixture.token();
		int limit = 20;
		String fingerprint = SearchCursor.fingerprint(query, null, limit);
		String forgedCursor = SearchCursor.of(fingerprint, 10_001).encode();

		assertThatThrownBy(() -> this.placeSearchService.search(query, null, limit, forgedCursor))
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
