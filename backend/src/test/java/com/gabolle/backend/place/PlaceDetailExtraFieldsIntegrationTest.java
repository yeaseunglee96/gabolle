package com.gabolle.backend.place;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.service.PlaceDetailService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장소 상세에 더한 칸 — 영문 주소·사진·영업시간·예상비용 전용 칸과 언어 선택. 기존 계약은
 * {@code PlaceDetailIntegrationTest} 가 본다.
 */
class PlaceDetailExtraFieldsIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceDetailService placeDetailService;

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
	@DisplayName("영문 주소가 있으면 addressEn 이 실린다")
	void addressEnPresentWhenSet() {
		UUID placeId = this.fixture.insertPlace("칸시험", "KanTest", "ATTRACTION", 35.1, 129.0);
		setAddressEn(placeId, "1-2-3 Test-dong, Busan");

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		assertThat(detail.addressEn()).isEqualTo("1-2-3 Test-dong, Busan");
	}

	@Test
	@DisplayName("🔴 영문 주소가 없으면 addressEn 은 null 이다 — 직렬화에서는 키가 빠진다")
	void addressEnNullWhenAbsent() {
		UUID placeId = this.fixture.insertPlace("칸없음", null, "ATTRACTION", 35.1, 129.0);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		assertThat(detail.addressEn()).isNull();
	}

	@Test
	@DisplayName("사진 칸은 지금 항상 비어 있다 — 채우는 경로가 아직 없다")
	void photoFieldsAreAlwaysNullForNow() {
		UUID placeId = this.fixture.insertPlace("사진없음", null, "ATTRACTION", 35.1, 129.0);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		assertThat(detail.photoUrl()).isNull();
		assertThat(detail.photoSource()).isNull();
	}

	@Test
	@DisplayName("🔴 OPENING_HOURS 표식이 있으면 전용 칸과 evidenceStatus 가 함께 온다")
	void openingHoursSlotComesWithEvidenceStatus() {
		UUID placeId = this.fixture.insertPlace("영업시간있음", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertValueFeature(placeId, "OPENING_HOURS", "VERIFIED", "{\"mon\": \"09:00-18:00\"}");

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		assertThat(detail.openingHours()).isNotNull();
		assertThat(detail.openingHours().evidenceStatus()).isEqualTo("VERIFIED");
		assertThat(detail.openingHours().value().get("mon").asString()).isEqualTo("09:00-18:00");
	}

	@Test
	@DisplayName("🔴 같은 사실이 features 목록에도 그대로 남아 있다 — 편의 필드로 더한 것이지 뺀 것이 아니다")
	void openingHoursStaysInFeaturesListToo() {
		UUID placeId = this.fixture.insertPlace("중복확인", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertValueFeature(placeId, "OPENING_HOURS", "VERIFIED", "{\"mon\": \"09:00-18:00\"}");

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		assertThat(detail.features())
				.filteredOn(view -> "OPENING_HOURS".equals(view.featureType()))
				.hasSize(1)
				.allSatisfy(view -> assertThat(view.evidenceStatus()).isEqualTo("VERIFIED"));
	}

	@Test
	@DisplayName("🔴 PRICE_LEVEL 표식이 추정값이면 evidenceStatus 가 ESTIMATED 로 온다")
	void priceLevelSlotCarriesEstimated() {
		UUID placeId = this.fixture.insertPlace("예상비용있음", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertValueFeature(placeId, "PRICE_LEVEL", "ESTIMATED", "{\"level\": 2}");

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		assertThat(detail.priceLevel()).isNotNull();
		assertThat(detail.priceLevel().evidenceStatus()).isEqualTo("ESTIMATED");
	}

	@Test
	@DisplayName("🔴 표식이 없으면 전용 칸이 null 이다 — 행이 아예 없는 것과 UNKNOWN 은 다르게 다룬다")
	void featureSlotAbsentWhenNoRow() {
		UUID placeId = this.fixture.insertPlace("표식없음", null, "ATTRACTION", 35.1, 129.0);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		assertThat(detail.openingHours()).isNull();
		assertThat(detail.priceLevel()).isNull();
	}

	@Test
	@DisplayName("표식이 UNKNOWN 이어도 행이 있으면 전용 칸은 채워진다 — 값만 없다")
	void featureSlotPresentEvenWhenUnknown() {
		UUID placeId = this.fixture.insertPlace("영업시간모름", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertValueFeature(placeId, "OPENING_HOURS", "UNKNOWN", null);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		assertThat(detail.openingHours()).isNotNull();
		assertThat(detail.openingHours().evidenceStatus()).isEqualTo("UNKNOWN");
		assertThat(detail.openingHours().value()).isNull();
	}

	@Test
	@DisplayName("Accept-Language 가 en 이고 영문 이름이 없으면 한국어로 되돌리고 resolvedLanguage 가 ko 다")
	void languageFallsBackToKoreanWithoutEnglishName() {
		UUID placeId = this.fixture.insertPlace("영문없음", null, "ATTRACTION", 35.1, 129.0);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null, "en-US,en;q=0.9");

		assertThat(detail.resolvedLanguage()).isEqualTo("ko");
		assertThat(detail.nameKo()).isNotNull();
		assertThat(detail.nameEn()).isNull();
	}

	@Test
	@DisplayName("Accept-Language 가 en 이고 영문 이름이 있으면 resolvedLanguage 가 en 이다")
	void languageResolvesToEnglishWhenAvailable() {
		UUID placeId = this.fixture.insertPlace("영문있음", "EnglishName", "ATTRACTION", 35.1, 129.0);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null, "en");

		assertThat(detail.resolvedLanguage()).isEqualTo("en");
		// 언어 선택은 더하는 것이지 바꾸는 것이 아니라 기존 칸은 둘 다 그대로 나간다.
		assertThat(detail.nameKo()).isNotNull();
		assertThat(detail.nameEn()).isNotNull();
	}

	@Test
	@DisplayName("Accept-Language 헤더가 없으면(2-인자 get) 한국어를 우선한다")
	void defaultsToKoreanWithoutHeader() {
		UUID placeId = this.fixture.insertPlace("헤더없음", "EnglishName", "ATTRACTION", 35.1, 129.0);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		assertThat(detail.resolvedLanguage()).isEqualTo("ko");
	}

	@Test
	@DisplayName("한국어를 요청하면(또는 그 밖의 언어) resolvedLanguage 는 ko 다")
	void nonEnglishHeaderResolvesToKorean() {
		UUID placeId = this.fixture.insertPlace("한국어요청", "EnglishName", "ATTRACTION", 35.1, 129.0);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null, "ko-KR,ko;q=0.9");

		assertThat(detail.resolvedLanguage()).isEqualTo("ko");
	}

	private void setAddressEn(UUID placeId, String addressEn) {
		this.jdbcTemplate.update("UPDATE place SET address_en = ? WHERE place_id = ?", addressEn, placeId);
	}
}
