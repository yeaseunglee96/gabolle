package com.gabolle.backend.place;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceDetailResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code PlaceDetailResponse} 의 직렬화 계약 — S15P21E201-476.
 *
 * <p>DB 도 MockMvc 도 없이 record 를 직접 만들어 Jackson 으로 찍어 본다. "값이 null 이다" 와
 * "JSON 에 그 키가 없다" 는 record 필드만 봐서는 구분되지 않는다 — 여기서 그 간극을 직접 메운다.
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
		assertThat(json.has("openingHours")).isFalse();
		assertThat(json.has("priceLevel")).isFalse();
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
		return new PlaceDetailResponse(
				UUID.randomUUID(), "샘플장소", null, null, null, null, null,
				new PlaceDetailResponse.Provenance(null, null, null, null, null),
				List.of(),
				new PlaceDetailResponse.ItineraryInclusion("UNAVAILABLE", "ITINERARY_NOT_SPECIFIED"),
				addressEn, photoUrl, photoSource, openingHours, priceLevel, resolvedLanguage);
	}
}
