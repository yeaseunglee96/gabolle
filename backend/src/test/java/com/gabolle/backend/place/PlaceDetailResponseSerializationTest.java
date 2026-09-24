package com.gabolle.backend.place;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceDetailResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import com.gabolle.backend.place.domain.Place;

/**
 * {@code PlaceDetailResponse} 의 직렬화 계약. "값이 null 이다" 와 "JSON 에 그 키가 없다" 는
 * record 필드만 봐서는 구분되지 않으므로 Jackson 으로 직접 찍어 본다.
 */
class PlaceDetailResponseSerializationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("🔴 addressEn·photoUrl·photoSource·openingHours·priceLevel 이 null 이면 키 자체가 빠진다")
	void nullNewFieldsAreOmittedFromJson() throws Exception {
		PlaceDetailResponse response = sample(null, null, null, null, null, "ko");

		JsonNode json = writeAndRead(response);

		assertThat(json.has("addressEn")).isFalse();
		assertThat(json.has("photoUrl")).isFalse();
		assertThat(json.has("photoSource")).isFalse();
		assertThat(json.has("photoSubject")).isFalse();
		assertThat(json.has("openingHours")).isFalse();
		assertThat(json.has("priceLevel")).isFalse();
		assertThat(json.has("photoLicense")).isFalse();
	}

	@Test
	@DisplayName("값이 있으면 키가 값과 함께 온다")
	void presentNewFieldsAreSerialized() throws Exception {
		PlaceDetailResponse.FeatureSlot openingHours = new PlaceDetailResponse.FeatureSlot(
				this.objectMapper.readTree("{\"mon\": \"09:00-18:00\"}"), "VERIFIED");
		PlaceDetailResponse response = sample("1-2-3 Test-dong", "https://example.com/p.jpg", "출처: 위키",
				openingHours, null, "en");

		JsonNode json = writeAndRead(response);

		assertThat(json.get("addressEn").asString()).isEqualTo("1-2-3 Test-dong");
		assertThat(json.get("photoUrl").asString()).isEqualTo("https://example.com/p.jpg");
		assertThat(json.get("photoSource").asString()).isEqualTo("출처: 위키");
		assertThat(json.get("openingHours").get("evidenceStatus").asString()).isEqualTo("VERIFIED");
		assertThat(json.get("openingHours").get("value").get("mon").asString()).isEqualTo("09:00-18:00");
		assertThat(json.has("priceLevel")).isFalse();
	}

	/**
	 * 이 칸이 없으면 화면은 "이 장소를 찍은 사진" 과 "이 장소가 들어 있는 곳을 찍은 사진" 을
	 * 구분할 방법이 없어 주변 시설 사진을 이 장소 사진처럼 그린다.
	 */
	@Test
	@DisplayName("사진이 무엇을 찍은 것인지가 상세 응답에 실린다 — 축제에만 있던 칸")
	void thePhotoSubjectIsSerializedForPlacesToo() throws Exception {
		PlaceDetailResponse response = sample("1-2-3 Test-dong", "https://example.com/p.jpg",
				"한국관광공사 관광사진갤러리", Place.PhotoSubject.VENUE, null, null, "ko");

		JsonNode json = writeAndRead(response);

		assertThat(json.get("photoSubject").asString()).isEqualTo("VENUE");
		assertThat(json.get("photoSource").asString())
				.as("출처와 주제는 다른 질문이라 칸이 갈려 있다 — 둘 다 와야 한다")
				.isEqualTo("한국관광공사 관광사진갤러리");
	}

	/**
	 * 위키미디어 사진(CC BY-SA 등)은 라이선스 이름과 링크를 같이 보여야 쓸 수 있다 (S15P21E201-1606).
	 * 실제 수집본의 한 줄(부산근대역사관)에서 가져온 값이다.
	 */
	@Test
	@DisplayName("🔴 라이선스가 있으면 이름·주소·원본 파일 페이지가 한 칸에 묶여 온다")
	void thePhotoLicenseIsSerialized() throws Exception {
		PlaceDetailResponse response = sample("1-2-3 Test-dong", "https://upload.wikimedia.org/a.jpg",
				"Wikimedia Commons · 촬영 Katsura Roen", Place.PhotoSubject.SELF, null, null, "ko",
				new Place.PhotoLicense("CC BY-SA 3.0", "https://creativecommons.org/licenses/by-sa/3.0",
						"https://commons.wikimedia.org/wiki/File:Busan_Modern_History_Museum-01.jpg"));

		JsonNode license = writeAndRead(response).get("photoLicense");

		assertThat(license.get("name").asString()).isEqualTo("CC BY-SA 3.0");
		assertThat(license.get("url").asString()).isEqualTo("https://creativecommons.org/licenses/by-sa/3.0");
		assertThat(license.get("filePage").asString())
				.isEqualTo("https://commons.wikimedia.org/wiki/File:Busan_Modern_History_Museum-01.jpg");
	}

	@Test
	@DisplayName("🔴 기존 필드는 null 이어도 키가 남는다 — 전역이 아니라 새 필드에만 NON_NULL 을 붙였다")
	void existingNullableFieldsAreNotOmitted() throws Exception {
		PlaceDetailResponse response = sample(null, null, null, null, null, "ko");

		JsonNode json = writeAndRead(response);

		// category·address 는 record 에 NON_NULL 이 없다 — null 이어도 키는 남아야 한다.
		assertThat(json.has("category")).isTrue();
		assertThat(json.get("category").isNull()).isTrue();
		assertThat(json.has("address")).isTrue();
		assertThat(json.get("address").isNull()).isTrue();
	}

	@Test
	@DisplayName("resolvedLanguage 는 늘 채워진다")
	void resolvedLanguageAlwaysPresent() throws Exception {
		PlaceDetailResponse response = sample(null, null, null, null, null, "en");

		JsonNode json = writeAndRead(response);

		assertThat(json.get("resolvedLanguage").asString()).isEqualTo("en");
	}

	private JsonNode writeAndRead(PlaceDetailResponse response) {
		String json = this.objectMapper.writeValueAsString(response);
		return this.objectMapper.readTree(json);
	}

	private PlaceDetailResponse sample(String addressEn, String photoUrl, String photoSource,
			PlaceDetailResponse.FeatureSlot openingHours, PlaceDetailResponse.FeatureSlot priceLevel,
			String resolvedLanguage) {
		return sample(addressEn, photoUrl, photoSource, null, openingHours, priceLevel, resolvedLanguage);
	}

	private PlaceDetailResponse sample(String addressEn, String photoUrl, String photoSource,
			Place.PhotoSubject photoSubject, PlaceDetailResponse.FeatureSlot openingHours,
			PlaceDetailResponse.FeatureSlot priceLevel, String resolvedLanguage) {
		return sample(addressEn, photoUrl, photoSource, photoSubject, openingHours, priceLevel, resolvedLanguage,
				null);
	}

	private PlaceDetailResponse sample(String addressEn, String photoUrl, String photoSource,
			Place.PhotoSubject photoSubject, PlaceDetailResponse.FeatureSlot openingHours,
			PlaceDetailResponse.FeatureSlot priceLevel, String resolvedLanguage, Place.PhotoLicense photoLicense) {
		return new PlaceDetailResponse(
				UUID.randomUUID(), "샘플장소", null, null, null, null, null,
				new PlaceDetailResponse.Provenance(null, null, null, null, null),
				List.of(),
				new PlaceDetailResponse.ItineraryInclusion("UNAVAILABLE", "ITINERARY_NOT_SPECIFIED"),
				addressEn, photoUrl, photoSource, photoSubject, openingHours, priceLevel, resolvedLanguage,
				photoLicense);
	}
}
