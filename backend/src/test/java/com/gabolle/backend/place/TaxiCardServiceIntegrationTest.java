package com.gabolle.backend.place;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.TaxiCardResponse;
import com.gabolle.backend.place.service.PlaceNotFoundException;
import com.gabolle.backend.place.service.TaxiCardService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL 위에서 {@link TaxiCardService} 를 직접 부른다. JSON 직렬화는
 * {@code TaxiCardControllerTest} 가 보고, 여기서는 문장 조립과 언어 판정을 확인한다.
 */
class TaxiCardServiceIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TaxiCardService taxiCardService;

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
	@DisplayName("없는 장소는 PlaceNotFoundException 이다 — 이것이 404 로 이어진다")
	void unknownPlaceIsNotFound() {
		UUID missing = UUID.randomUUID();

		assertThatThrownBy(() -> this.taxiCardService.get(missing, null))
				.isInstanceOf(PlaceNotFoundException.class);
	}

	@Test
	@DisplayName("한국어 주소가 있으면 driverSentence 가 그 주소를 담는다")
	void driverSentenceCarriesKoreanAddress() {
		UUID placeId = this.fixture.insertPlace("남포동", null, "ATTRACTION", 35.0, 129.0);
		String expectedAddress = this.fixture.prefix() + "주소";

		TaxiCardResponse card = this.taxiCardService.get(placeId, null);

		assertThat(card.addressKo()).isEqualTo(expectedAddress);
		assertThat(card.driverSentence()).isEqualTo("이 주소로 가주세요, " + expectedAddress);
	}

	@Test
	@DisplayName("🔴 한국어 주소가 없으면 driverSentence 가 장소 이름으로 대신한다")
	void driverSentenceFallsBackToNameWhenAddressMissing() {
		UUID placeId = this.fixture.insertPlace("주소없는장소", null, "ATTRACTION", 35.0, 129.0);
		clearAddress(placeId);
		String expectedName = this.fixture.prefix() + "주소없는장소";

		TaxiCardResponse card = this.taxiCardService.get(placeId, null);

		assertThat(card.addressKo()).isNull();
		assertThat(card.driverSentence()).isEqualTo("이 주소로 가주세요, " + expectedName);
	}

	@Test
	@DisplayName("영문 주소가 없으면 카드의 addressEn 은 null 이다")
	void addressEnNullWhenAbsent() {
		UUID placeId = this.fixture.insertPlace("영문주소없음", null, "ATTRACTION", 35.0, 129.0);

		TaxiCardResponse card = this.taxiCardService.get(placeId, null);

		assertThat(card.addressEn()).isNull();
	}

	@Test
	@DisplayName("영문 주소가 있으면 카드의 addressEn 에 실린다")
	void addressEnPresentWhenSet() {
		UUID placeId = this.fixture.insertPlace("영문주소있음", null, "ATTRACTION", 35.0, 129.0);
		setAddressEn(placeId, "12-3 Nampo-dong, Busan");

		TaxiCardResponse card = this.taxiCardService.get(placeId, null);

		assertThat(card.addressEn()).isEqualTo("12-3 Nampo-dong, Busan");
	}

	@Test
	@DisplayName("Accept-Language 가 en 이어도 driverSentence 는 항상 한국어다")
	void driverSentenceIsAlwaysKoreanRegardlessOfLanguage() {
		UUID placeId = this.fixture.insertPlace("언어무관", "EnglishName", "ATTRACTION", 35.0, 129.0);
		String expectedAddress = this.fixture.prefix() + "주소";

		TaxiCardResponse card = this.taxiCardService.get(placeId, "en-US,en;q=0.9");

		assertThat(card.driverSentence()).isEqualTo("이 주소로 가주세요, " + expectedAddress);
	}

	@Test
	@DisplayName("Accept-Language 가 en 이고 영문 이름이 없으면 resolvedLanguage 가 ko 로 되돌아간다")
	void resolvedLanguageFallsBackWithoutEnglishName() {
		UUID placeId = this.fixture.insertPlace("영문이름없음", null, "ATTRACTION", 35.0, 129.0);

		TaxiCardResponse card = this.taxiCardService.get(placeId, "en");

		assertThat(card.resolvedLanguage()).isEqualTo("ko");
	}

	@Test
	@DisplayName("🔴 영문 주소가 있어야 en 이다 — 영문 이름만 있으면 ko 로 되돌린다")
	void resolvedLanguageFollowsAddressNotName() {
		// 이 카드가 기사에게 보여주는 것은 주소라서 언어 판정도 주소를 기준으로 한다.
		// 장소 상세는 반대로 이름을 기준으로 삼는다 — 응답마다 주된 값이 다르기 때문이다.
		UUID nameOnly = this.fixture.insertPlace("영문이름만", "EnglishName", "ATTRACTION", 35.0, 129.0);
		assertThat(this.taxiCardService.get(nameOnly, "en").resolvedLanguage()).isEqualTo("ko");

		// 영문 주소가 있으면 en 이다. 영문 이름이 없어도 상관없다
		UUID addressOnly = this.fixture.insertPlace("영문주소만", null, "ATTRACTION", 35.0, 129.0);
		setAddressEn(addressOnly, "12-3 Nampo-dong, Jung-gu, Busan");
		assertThat(this.taxiCardService.get(addressOnly, "en").resolvedLanguage()).isEqualTo("en");
	}

	@Test
	@DisplayName("Accept-Language 가 없으면 영문 주소가 있어도 ko 다 — 기본값을 영어로 두지 않는다")
	void resolvedLanguageDefaultsToKorean() {
		UUID placeId = this.fixture.insertPlace("헤더없음", "EnglishName", "ATTRACTION", 35.0, 129.0);
		setAddressEn(placeId, "12-3 Nampo-dong, Jung-gu, Busan");

		TaxiCardResponse card = this.taxiCardService.get(placeId, null);

		assertThat(card.resolvedLanguage()).isEqualTo("ko");
	}

	private void setAddressEn(UUID placeId, String addressEn) {
		this.jdbcTemplate.update("UPDATE place SET address_en = ? WHERE place_id = ?", addressEn, placeId);
	}

	private void clearAddress(UUID placeId) {
		this.jdbcTemplate.update("UPDATE place SET address = NULL WHERE place_id = ?", placeId);
	}
}
