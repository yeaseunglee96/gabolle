package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 장소 상세 사실 줄 읽기 — DB 없이 돈다 (S15P21E201-1886). */
class PlaceDetailExtrasLoaderTest {

	private static final String PLACE = "3f2a1b9c-0000-4000-8000-000000000001";

	static String line(String featureType, String value) {
		return "{\"placeId\":\"" + PLACE + "\",\"featureType\":\"" + featureType + "\",\"value\":" + value
				+ ",\"evidenceStatus\":\"VERIFIED\",\"sourceType\":\"RESEARCH_DETAIL\",\"sourceId\":null,"
				+ "\"sourceVersion\":\"detail-202609\",\"observedAt\":\"2026-09-30T12:00:00+09:00\"}";
	}

	@Test
	@DisplayName("메뉴 줄을 읽으면 장소 번호·갈래·값·출처가 그대로 온다")
	void menuItemsLineIsRead() {
		PlaceDetailExtrasLoader.Row row = PlaceDetailExtrasLoader.parse(line("MENU_ITEMS",
				"{\"items\":[{\"nameKo\":\"밀면\",\"nameEn\":\"Milmyeon\",\"priceWon\":9000,"
						+ "\"ingredientsKo\":\"밀가루, 육수\",\"ingredientsEn\":null,\"signature\":true}]}"));

		assertThat(row.placeId()).isEqualTo(UUID.fromString(PLACE));
		assertThat(row.featureType()).isEqualTo("MENU_ITEMS");
		assertThat(row.value()).contains("\"nameKo\":\"밀면\"").contains("\"priceWon\":9000");
		assertThat(row.evidenceStatus()).isEqualTo("VERIFIED");
		assertThat(row.sourceId()).isNull();
		assertThat(row.sourceVersion()).isEqualTo("detail-202609");
		assertThat(row.observedAt()).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"FOREIGN_MENU|{\"available\":true}",
			"AMENITIES|{\"wifi\":true,\"parking\":null,\"restroom\":false,\"reservation\":\"전화\",\"homepage\":null}",
			"ADMISSION_FEE|{\"raw\":\"어른 3,000원\"}",
			"NEARBY_LANDMARK|{\"name\":\"해운대해수욕장\",\"distanceM\":350}",
			"BEST_TIME|{\"day\":3,\"night\":1,\"any\":0}",
			"OPENING_HOURS|{\"status\":\"PARSED\",\"byDay\":{\"MON\":[{\"open\":\"09:00\",\"close\":\"18:00\"}]},\"raw\":\"09~18\"}",
			"CHECK_IN_OUT|{\"status\":\"LODGING\",\"checkIn\":\"15:00\",\"checkOut\":null}" })
	@DisplayName("받는 갈래는 모두 모양대로 읽힌다")
	void everyAcceptedTypeParses(String spec) {
		String[] parts = spec.split("\\|", 2);
		assertThat(PlaceDetailExtrasLoader.parse(line(parts[0], parts[1])).featureType()).isEqualTo(parts[0]);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// 🔴 알레르기는 받지 않는다 — 추정 알레르기 행은 DB 도 막는다
			"ALLERGEN_TAG|{\"peanut\":false}",
			"MENU_ITEM|{\"items\":[]}",
			"SLOPE_PERCENT|{\"percent\":3}" })
	@DisplayName("🔴 모르는 갈래가 오면 멈춘다")
	void unknownTypeStops(String spec) {
		String[] parts = spec.split("\\|", 2);
		assertThatThrownBy(() -> PlaceDetailExtrasLoader.parse(line(parts[0], parts[1])))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("모르는 featureType");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"MENU_ITEMS|{\"items\":[]}",
			"MENU_ITEMS|{\"items\":[{\"nameKo\":\"밀면\"}]}",
			"MENU_ITEMS|{\"items\":[{\"nameKo\":\"밀면\",\"signature\":false,\"allergens\":\"밀\"}]}",
			"MENU_ITEMS|{\"items\":[{\"nameKo\":\"밀면\",\"signature\":false,\"priceWon\":-1}]}",
			"MENU_ITEMS|{\"items\":[{\"nameKo\":\"밀면\",\"signature\":false,\"priceWon\":\"9000\"}]}",
			"FOREIGN_MENU|{\"available\":\"yes\"}",
			"AMENITIES|{\"wifi\":null}",
			"AMENITIES|{\"wifi\":\"Y\"}",
			"ADMISSION_FEE|{\"raw\":\"\"}",
			"NEARBY_LANDMARK|{\"name\":\"해운대\"}",
			"BEST_TIME|{\"day\":0,\"night\":0,\"any\":0}",
			"BEST_TIME|{\"day\":1,\"night\":0}",
			"OPENING_HOURS|{\"status\":\"UNKNOWN\"}",
			"OPENING_HOURS|{\"mon\":\"09:00-18:00\"}",
			"CHECK_IN_OUT|{\"status\":\"LODGING\"}" })
	@DisplayName("🔴 값 모양이 틀리면 멈춘다")
	void malformedValueStops(String spec) {
		String[] parts = spec.split("\\|", 2);
		assertThatThrownBy(() -> PlaceDetailExtrasLoader.parse(line(parts[0], parts[1])))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("메뉴는 30개까지다")
	void menuIsCappedAtThirty() {
		String item = "{\"nameKo\":\"밀면\",\"signature\":false}";
		String items = String.join(",", java.util.Collections.nCopies(31, item));
		assertThatThrownBy(() -> PlaceDetailExtrasLoader.parse(line("MENU_ITEMS", "{\"items\":[" + items + "]}")))
				.hasMessageContaining("30");
	}

	@Test
	@DisplayName("증거 등급은 VERIFIED·ESTIMATED 만, 장소 번호는 UUID 만 받는다")
	void evidenceAndPlaceIdAreChecked() {
		String ok = line("FOREIGN_MENU", "{\"available\":true}");
		assertThatThrownBy(() -> PlaceDetailExtrasLoader.parse(ok.replace("\"VERIFIED\"", "\"UNKNOWN\"")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PlaceDetailExtrasLoader.parse(ok.replace(PLACE, "SBIZ-123")))
				.hasMessageContaining("UUID");
		assertThatThrownBy(() -> PlaceDetailExtrasLoader.parse(ok.replace("\"sourceId\"", "\"source_id\"")))
				.hasMessageContaining("모르는 칸");
	}

	@Test
	@DisplayName("🔴 파일에 틀린 줄이 하나라도 있으면 줄 번호와 함께 멈춘다 — 앞 줄만 들어가는 일이 없다")
	void fileStopsAtTheBrokenLine(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("extras.ndjson");
		Files.writeString(file, line("FOREIGN_MENU", "{\"available\":true}") + "\n\n"
				+ line("NOPE", "{}") + "\n", StandardCharsets.UTF_8);

		assertThatThrownBy(() -> PlaceDetailExtrasLoader.readAll(file)).hasMessageContaining("3번째 줄");
	}
}
