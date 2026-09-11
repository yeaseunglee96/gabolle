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
 * 가격대 산출물을 사실로 옮기는 판독기를 잰다 — DB 없이 도는 쪽이다.
 *
 * <p>여기서 재는 것은 <b>값의 모양</b>이다. {@code place_feature.value} 는 JSONB 라 아무거나
 * 들어가므로, 모양이 조용히 바뀌면 채점기가 그 항을 통째로 못 읽고 <b>아무 오류 없이</b> 점수가
 * 빠진다. 그 빠짐은 순위가 이상해진 뒤에야 보인다.
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
		// 🔴 「중상」을 1~4 중 어디에 둘지 아무도 안 정했다. 여기서 숫자를 붙이면 그 결정을
		//    대신 내리는 셈이고, 한 번 들어간 숫자는 계약처럼 굳는다.
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
		// 🔴 몇이 빠졌는지 모르면 나중에 "왜 그 가게에 값이 없나" 에 답할 수 없다.
		assertThat(counts.skippedNoValue()).isEqualTo(2);
	}

}
