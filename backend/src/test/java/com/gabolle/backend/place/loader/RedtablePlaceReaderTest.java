package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 레드테이블 NDJSON 읽기와 갈래 옮김표 (S15P21E201-1897). */
class RedtablePlaceReaderTest {

	@TempDir
	Path dir;

	private static final String LINE = "{\"rstrId\":\"39929\",\"name\":\"밀양가산돼지국밥\",\"lat\":35.1405587,"
			+ "\"lng\":129.0625474,\"roadAddress\":\"부산광역시 동구 자성로133번길 45\","
			+ "\"jibunAddress\":\"부산광역시 동구 범일동 830-173\",\"businessType\":\"한식\",\"licenseType\":\"일반음식점\","
			+ "\"tel\":\"051-632-0409\",\"intro\":null,\"imageUrl\":null,\"surveyRecommenders\":1}";

	@Test
	@DisplayName("한 줄을 칸 그대로 읽는다 — 주소는 도로명이 먼저다")
	void readsLine() throws IOException {
		List<RedtablePlaceRow> rows = RedtablePlaceReader.read(write(LINE));

		assertThat(rows).hasSize(1);
		RedtablePlaceRow row = rows.get(0);
		assertThat(row.rstrId()).isEqualTo("39929");
		assertThat(row.name()).isEqualTo("밀양가산돼지국밥");
		assertThat(row.lat()).isEqualTo(35.1405587);
		assertThat(row.address()).isEqualTo("부산광역시 동구 자성로133번길 45");
		assertThat(row.surveyRecommenders()).isEqualTo(1);
		assertThat(RedtablePlaceLoader.categoryOf(row)).isEqualTo("FOOD");
	}

	@Test
	@DisplayName("도로명이 없으면 지번 주소를 쓴다")
	void fallsBackToJibun() throws IOException {
		RedtablePlaceRow row = RedtablePlaceReader.read(write(LINE.replace("\"부산광역시 동구 자성로133번길 45\"", "null")))
				.get(0);
		assertThat(row.address()).isEqualTo("부산광역시 동구 범일동 830-173");
	}

	@Test
	@DisplayName("🔴 모르는 칸이 있으면 줄 번호와 함께 멈춘다 — 오타 난 칸이 조용히 버려지지 않게")
	void unknownFieldStops() throws IOException {
		Path file = write(LINE, LINE.replace("39929", "1").replace("\"tel\"", "\"phone\""));
		assertThatThrownBy(() -> RedtablePlaceReader.read(file))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("2번째 줄")
				.hasMessageContaining("phone");
	}

	@Test
	@DisplayName("🔴 위경도를 뒤바꾼 줄·같은 번호 두 줄·이름 없는 줄은 멈춘다")
	void badLinesStop() throws IOException {
		Path swapped = write(LINE.replace("35.1405587", "129.0").replace("129.0625474", "35.14"));
		assertThatThrownBy(() -> RedtablePlaceReader.read(swapped)).hasMessageContaining("부산 밖");

		Path duplicate = write(LINE, LINE);
		assertThatThrownBy(() -> RedtablePlaceReader.read(duplicate)).hasMessageContaining("같은 rstrId");

		Path noName = write(LINE.replace("\"밀양가산돼지국밥\"", "\"  \""));
		assertThatThrownBy(() -> RedtablePlaceReader.read(noName)).hasMessageContaining("name");

		Path stringCoordinate = write(LINE.replace("35.1405587", "\"35.1405587\""));
		assertThatThrownBy(() -> RedtablePlaceReader.read(stringCoordinate)).hasMessageContaining("lat");
	}

	@Test
	@DisplayName("🔴 갈래 옮김표 — 카페 업태·빈 업태의 휴게음식점·카페 이름은 CAFE_HEALING, 업태가 있으면 이름보다 업태")
	void categoryMapping() {
		assertThat(RedtablePlaceLoader.categoryOf(row("에타리", "커피숍", "휴게음식점"))).isEqualTo("CAFE_HEALING");
		assertThat(RedtablePlaceLoader.categoryOf(row("차마당", "전통찻집", "휴게음식점"))).isEqualTo("CAFE_HEALING");
		assertThat(RedtablePlaceLoader.categoryOf(row("희와제과", "제과점영업", "제과점영업"))).isEqualTo("CAFE_HEALING");
		assertThat(RedtablePlaceLoader.categoryOf(row("히타로", null, "휴게음식점"))).isEqualTo("CAFE_HEALING");
		assertThat(RedtablePlaceLoader.categoryOf(row("사랑방다실", null, "일반음식점"))).isEqualTo("CAFE_HEALING");
		assertThat(RedtablePlaceLoader.categoryOf(row("썰스데이파티", "호프/통닭", "일반음식점"))).isEqualTo("FOOD");
		assertThat(RedtablePlaceLoader.categoryOf(row("툼브로이", null, "일반음식점"))).isEqualTo("FOOD");
		assertThat(RedtablePlaceLoader.categoryOf(row("치킨버거클럽", "일반조리판매", "휴게음식점"))).isEqualTo("FOOD");
		// 설문은 카페라 했지만 업태가 한식이다.
		assertThat(RedtablePlaceLoader.categoryOf(row("진수밥상", "한식", "일반음식점"))).isEqualTo("FOOD");
	}

	@Test
	@DisplayName("장소 id 는 번호에서 언제나 같고, 상가·관광공사 id 와 겹치지 않는다")
	void placeIdIsDeterministic() {
		assertThat(RedtablePlaceLoader.placeIdOf("39929")).isEqualTo(RedtablePlaceLoader.placeIdOf("39929"));
		assertThat(RedtablePlaceLoader.placeIdOf("39929")).isNotEqualTo(SbizPlaceLoader.placeIdOf("39929"));
		assertThat(RedtablePlaceLoader.placeIdOf("39929")).isNotEqualTo(TourApiPlaceLoader.placeIdOf("39929"));
	}

	private static RedtablePlaceRow row(String name, String businessType, String licenseType) {
		return new RedtablePlaceRow("1", name, 35.1, 129.0, "부산", null, businessType, licenseType, null, null, null,
				null);
	}

	private Path write(String... lines) throws IOException {
		Path file = this.dir.resolve("redtable.ndjson");
		Files.write(file, List.of(lines), StandardCharsets.UTF_8);
		return file;
	}
}
