package com.gabolle.backend.place;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabolle.backend.place.loader.PlaceFeatureNdjsonReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 가격대 산출물을 사실로 옮기는 판독기를 DB 없이 잰다. 재는 것은 값의 모양이다 —
 * {@code place_feature.value} 는 JSONB 라 모양이 바뀌어도 오류 없이 채점기에서만 빠진다.
 */
class PlaceFeatureLoaderTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@TempDir
	Path tempDir;

	private Path file(String name, String... lines) {
		try {
			Path path = this.tempDir.resolve(name);
			Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
			return path;
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static List<PlaceFeatureNdjsonReader.Fact> priceBands(Path path) {
		List<PlaceFeatureNdjsonReader.Fact> facts = new ArrayList<>();
		PlaceFeatureNdjsonReader.readPriceBands(path, 500, facts::addAll);
		return facts;
	}

	private static JsonNode value(PlaceFeatureNdjsonReader.Fact fact) {
		try {
			return MAPPER.readTree(fact.value());
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	@Test
	@DisplayName("가격대는 등급 낱말과 원문 낱말을 그대로 싣는다 — 숫자로 바꾸지 않는다")
	void 가격대는_낱말_그대로다() {
		Path path = file("priceband.ndjson",
				"{\"placeId\":\"MA0101\",\"raw\":\"mid\",\"band\":\"MID\"}",
				"{\"placeId\":\"MA0102\",\"raw\":\"mid-high\",\"band\":\"MID_HIGH\"}");

		List<PlaceFeatureNdjsonReader.Fact> facts = priceBands(path);

		assertThat(facts).hasSize(2);
		assertThat(facts).allSatisfy(fact -> assertThat(fact.featureType()).isEqualTo("PRICE_LEVEL"));
		assertThat(facts.get(0).storeId()).isEqualTo("MA0101");
		assertThat(value(facts.get(0)).path("band").asText()).isEqualTo("MID");
		assertThat(value(facts.get(0)).path("raw").asText()).isEqualTo("mid");
		// 등급을 숫자로 환산하는 규칙이 아직 없으므로 level 칸을 만들지 않는다.
		assertThat(value(facts.get(1)).has("level")).isFalse();
		assertThat(value(facts.get(1)).path("band").asText()).isEqualTo("MID_HIGH");
	}

	@Test
	@DisplayName("🔴 모르는 등급이 오면 멈춘다 — 조용히 버리면 개수만 줄고 아무도 못 알아챈다")
	void 모르는_등급은_멈춘다() {
		Path path = file("priceband.ndjson", "{\"placeId\":\"MA0101\",\"raw\":\"?\",\"band\":\"LUXURY\"}");

		assertThatThrownBy(() -> PlaceFeatureNdjsonReader.readPriceBands(path, 500, chunk -> {
		})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("LUXURY");
	}

	@Test
	@DisplayName("열쇠나 값이 없는 줄은 버리고, 버린 수를 세어서 돌려준다")
	void 버린_줄을_센다() {
		Path path = file("priceband.ndjson",
				"{\"placeId\":\"MA0101\",\"raw\":\"mid\",\"band\":\"MID\"}",
				// 등급이 없다 — 값이 없으므로 사실이 안 된다
				"{\"placeId\":\"MA0102\",\"raw\":\"모름\"}",
				// 상가업소번호가 없다 — 어느 장소인지 알 수 없다
				"{\"placeId\":\"\",\"raw\":\"mid\",\"band\":\"MID\"}");

		PlaceFeatureNdjsonReader.Counts counts = PlaceFeatureNdjsonReader.readPriceBands(path, 500, chunk -> {
		});

		assertThat(counts.total()).isEqualTo(3);
		assertThat(counts.usable()).isEqualTo(1);
		assertThat(counts.skippedNoValue()).isEqualTo(2);
	}

	@Test
	@DisplayName("가격대 사실은 namespace 가 SBIZ 로 고정된다 — 앞으로도 상가업소번호 산출물이라서다")
	void 가격대는_SBIZ_네임스페이스다() {
		List<PlaceFeatureNdjsonReader.Fact> facts = priceBands(
				file("priceband.ndjson", "{\"placeId\":\"MA0101\",\"raw\":\"mid\",\"band\":\"MID\"}"));

		assertThat(facts.get(0).keySource()).isEqualTo("SBIZ");
	}

	// 혼밥 안심·브레이크타임·라스트오더

	private static List<PlaceFeatureNdjsonReader.Fact> visitorFacts(Path path) {
		List<PlaceFeatureNdjsonReader.Fact> facts = new ArrayList<>();
		PlaceFeatureNdjsonReader.readVisitorFacts(path, 500, facts::addAll);
		return facts;
	}

	@Test
	@DisplayName("TourAPI 출처 장소에 혼밥 안심·브레이크타임을 붙인다")
	void 방문객_사실을_읽는다() {
		Path path = file("visitor-facts.ndjson",
				"{\"namespace\":\"TOURAPI\",\"storeId\":\"129156\",\"featureType\":\"SOLO_FRIENDLY\",\"value\":true}",
				"{\"namespace\":\"TOURAPI\",\"storeId\":\"129156\",\"featureType\":\"BREAK_TIME\","
						+ "\"value\":{\"start\":\"15:00\",\"end\":\"17:00\"}}");

		List<PlaceFeatureNdjsonReader.Fact> facts = visitorFacts(path);

		assertThat(facts).hasSize(2);
		assertThat(facts.get(0).keySource()).isEqualTo("TOURAPI");
		assertThat(facts.get(0).storeId()).isEqualTo("129156");
		assertThat(facts.get(0).featureKey()).isNull();
		assertThat(value(facts.get(0)).asBoolean()).isTrue();
		assertThat(value(facts.get(1)).path("start").asText()).isEqualTo("15:00");
	}

	@Test
	@DisplayName("🔴 모르는 namespace 가 오면 멈춘다 — 오타를 조용히 두면 엉뚱한 장소 id 로 계산된다")
	void 모르는_namespace는_멈춘다() {
		Path path = file("visitor-facts.ndjson",
				"{\"namespace\":\"KAKAO\",\"storeId\":\"1\",\"featureType\":\"SOLO_FRIENDLY\",\"value\":true}");

		assertThatThrownBy(() -> PlaceFeatureNdjsonReader.readVisitorFacts(path, 500, chunk -> {
		})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("KAKAO");
	}

	@Test
	@DisplayName("🔴 모르는 featureType 이 오면 멈춘다")
	void 모르는_featureType은_멈춘다() {
		Path path = file("visitor-facts.ndjson",
				"{\"namespace\":\"TOURAPI\",\"storeId\":\"1\",\"featureType\":\"WHEELCHAIR_OK\",\"value\":true}");

		assertThatThrownBy(() -> PlaceFeatureNdjsonReader.readVisitorFacts(path, 500, chunk -> {
		})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("WHEELCHAIR_OK");
	}

	@Test
	@DisplayName("값이 없는 줄은 버리고 버린 수를 센다")
	void 방문객_사실도_버린_줄을_센다() {
		Path path = file("visitor-facts.ndjson",
				"{\"namespace\":\"TOURAPI\",\"storeId\":\"129156\",\"featureType\":\"SOLO_FRIENDLY\",\"value\":true}",
				// value 가 없다
				"{\"namespace\":\"TOURAPI\",\"storeId\":\"129157\",\"featureType\":\"SOLO_FRIENDLY\"}");

		PlaceFeatureNdjsonReader.Counts counts = PlaceFeatureNdjsonReader.readVisitorFacts(path, 500, chunk -> {
		});

		assertThat(counts.total()).isEqualTo(2);
		assertThat(counts.usable()).isEqualTo(1);
		assertThat(counts.skippedNoValue()).isEqualTo(1);
	}

}
