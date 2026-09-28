package com.gabolle.backend.place.loader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 여기 쓰는 줄은 실제 산출물에서 그대로 가져온 것이다. 지어낸 예로 검사하면 정리 프로그램이
 * 실제로 내는 모양과 어긋나도 초록이 된다.
 *
 * <p>산복도로와 해안 매립지는 대조군이다 — 16.4% 대 2.7% 로 갈리는 것이 이 값이 쓸모 있다는
 * 근거다.
 */
class PlaceSlopeReaderTest {

	/** 산복도로 쪽. */
	private static final String GAMCHEON = """
			{"contentid":"1997221","title":"부산 감천문화마을","featureType":"SLOPE_PERCENT",\
			"evidenceStatus":"ESTIMATED","slopePercent":16.4,"segments":93,\
			"walkLengthM":21842,"radiusM":200}""";

	/** 해안 매립지 쪽. */
	private static final String MARINE_CITY = """
			{"contentid":"2617724","title":"마린시티","featureType":"SLOPE_PERCENT",\
			"evidenceStatus":"ESTIMATED","slopePercent":2.7,"segments":24,\
			"walkLengthM":11012,"radiusM":200}""";

	@TempDir
	Path dir;

	private List<PlaceFeatureNdjsonReader.Fact> readAll(String... lines) throws Exception {
		Path file = this.dir.resolve("place-slope.ndjson");
		Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		List<PlaceFeatureNdjsonReader.Fact> facts = new ArrayList<>();
		PlaceFeatureNdjsonReader.readPlaceSlopes(file, 500, facts::addAll);
		return facts;
	}

	@Test
	@DisplayName("열쇠는 관광공사 contentid 이고 값은 채점기가 읽는 score 에 담긴다")
	void mapsContentIdAndScore() throws Exception {
		List<PlaceFeatureNdjsonReader.Fact> facts = readAll(GAMCHEON, MARINE_CITY);

		assertThat(facts).hasSize(2);
		assertThat(facts.get(0).storeId()).isEqualTo("1997221");
		assertThat(facts.get(0).featureType()).isEqualTo("SLOPE_PERCENT");
		// 채점기가 value.score 를 읽는다. 다른 이름에 담으면 값이 있는데도 "없음" 으로 읽히고
		// 아무 오류도 안 난다.
		assertThat(facts.get(0).value()).contains("\"score\":16.4");
		assertThat(facts.get(1).value()).contains("\"score\":2.7");
	}

	@Test
	@DisplayName("이 값이 어떻게 나왔는지를 행 안에 같이 남긴다")
	void keepsProvenance() throws Exception {
		List<PlaceFeatureNdjsonReader.Fact> facts = readAll(GAMCHEON);

		// 반경을 바꾸면 값이 달라진다. 어느 반경으로 만든 행인지 모르면 옛 행과 새 행을
		// 구분할 수 없다.
		assertThat(facts.get(0).value()).contains("\"radiusM\":200.0");
		assertThat(facts.get(0).value()).contains("\"segments\":93.0");
	}

	/** 장소 번호판(S15P21E201-1625) — {@code place-slope-by-id.mjs} 가 낸 줄 그대로. */
	private static final String BONGNAESAN_BY_ID = """
			{"placeId":"0a9c1f64-5f8e-3a86-9f53-3a5c5d2f7a11","title":"봉래산(부산)","featureType":"SLOPE_PERCENT",\
			"evidenceStatus":"ESTIMATED","slopePercent":16.0,"segments":40,"walkLengthM":5777,"radiusM":200,\
			"stat":"p50"}""";

	private List<PlaceFeatureNdjsonReader.Fact> readAllById(String... lines) throws Exception {
		Path file = this.dir.resolve("place-slope-by-id.ndjson");
		Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		List<PlaceFeatureNdjsonReader.Fact> facts = new ArrayList<>();
		PlaceFeatureNdjsonReader.readPlaceSlopesById(file, 500, facts::addAll);
		return facts;
	}

	@Test
	@DisplayName("🔴 장소 번호판은 열쇠가 장소 번호 그대로이고, 어떻게 셌는지(가운데 값)를 같이 남긴다")
	void byIdKeepsThePlaceIdAndTheStat() throws Exception {
		List<PlaceFeatureNdjsonReader.Fact> facts = readAllById(BONGNAESAN_BY_ID);

		assertThat(facts).hasSize(1);
		assertThat(facts.get(0).storeId()).isEqualTo("0a9c1f64-5f8e-3a86-9f53-3a5c5d2f7a11");
		assertThat(facts.get(0).keySource()).isEqualTo(PlaceFeatureLoader.PLACE_ID_KEY);
		assertThat(facts.get(0).value()).contains("\"score\":16.0").contains("\"stat\":\"p50\"");
	}

	@Test
	@DisplayName("장소 번호가 아닌 열쇠·범위 밖 값은 버리지 않고 멈춘다")
	void byIdStopsOnBrokenLines() {
		assertThatThrownBy(() -> readAllById("{\"placeId\":\"1997221\",\"slopePercent\":5.0}"))
				.hasMessageContaining("장소 번호가 아닌 열쇠");
		assertThatThrownBy(() -> readAllById(
				"{\"placeId\":\"0a9c1f64-5f8e-3a86-9f53-3a5c5d2f7a11\",\"slopePercent\":120.0}"))
				.hasMessageContaining("범위를 벗어난 값");
	}

	@Test
	@DisplayName("열쇠나 값이 없는 줄은 버린다")
	void skipsIncompleteLines() throws Exception {
		List<PlaceFeatureNdjsonReader.Fact> facts = readAll(
				"{\"title\":\"열쇠 없음\",\"slopePercent\":5.0}",
				"{\"contentid\":\"126508\"}",
				GAMCHEON);

		assertThat(facts).hasSize(1);
		assertThat(facts.get(0).storeId()).isEqualTo("1997221");
	}

	@Test
	@DisplayName("표본이 모자라 값이 안 난 장소는 애초에 줄이 없다 — 0% 로 오지 않는다")
	void noRowMeansNoFact() throws Exception {
		// 정리 프로그램이 「모른다」를 「평지」로 바꿔 말하지 않는다. 표본이 모자라면 그 장소는
		// 파일에 아예 없다 — 값이 0 인 줄이 오는 것과 줄이 없는 것은 다르다.
		List<PlaceFeatureNdjsonReader.Fact> facts = readAll(GAMCHEON);

		assertThat(facts).extracting(PlaceFeatureNdjsonReader.Fact::storeId).containsExactly("1997221");
	}

	@Test
	@DisplayName("범위를 벗어난 경사는 조용히 버리지 않고 멈춘다")
	void refusesOutOfRange() {
		// 버리면 개수만 줄고 아무도 못 알아챈다. 산출물이 이상하면 그 자리에서 멈춘다.
		assertThatThrownBy(() -> readAll(
				"{\"contentid\":\"1\",\"slopePercent\":140.0}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("140");

		assertThatThrownBy(() -> readAll(
				"{\"contentid\":\"1\",\"slopePercent\":-3.0}"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("🔴 열쇠 체계를 줄마다 들고 다닌다 — source_type 과 다른 것이다")
	void factCarriesKeySource() throws Exception {
		// 가격대는 source_type 이 RESEARCH_PRICEBAND 이면서 열쇠는 상가업소번호다. 둘은 서로
		// 독립이다 — 하나는 "값이 어디서 왔나", 하나는 "이 문자열을 무엇으로 읽나" 다.
		List<PlaceFeatureNdjsonReader.Fact> slopes = readAll(GAMCHEON);
		assertThat(slopes.get(0).keySource()).isEqualTo(TourApiPlaceLoader.SOURCE_TYPE);

		// 열쇠를 안 적은 기존 호출자는 상가업소번호로 본다 — 그 동작이 안 바뀌어야 한다.
		var legacy = new PlaceFeatureNdjsonReader.Fact("MA0101", "PRICE_LEVEL", "{}");
		assertThat(legacy.keySource()).isEqualTo(SbizPlaceLoader.SOURCE_TYPE);
	}

	@Test
	@DisplayName("장소 아이디는 관광공사 열쇠로 만든다 — 상가 열쇠로 만들면 딴 곳을 가리킨다")
	void placeIdComesFromTourApiKey() {
		// 두 원천은 같은 모양의 결정적 UUID 를 쓰고 앞에 붙는 말만 다르다. 섞이면 있는 장소를
		// "없어서 못 넣음" 으로 세고 끝난다 — 예외도 로그의 빨간 줄도 없다.
		assertThat(TourApiPlaceLoader.placeIdOf("1997221"))
			.isNotEqualTo(SbizPlaceLoader.placeIdOf("1997221"));

		// 같은 열쇠는 늘 같은 아이디여야 한다. 아니면 두 번 돌릴 때 같은 장소가 두 행이 된다.
		assertThat(TourApiPlaceLoader.placeIdOf("1997221"))
			.isEqualTo(TourApiPlaceLoader.placeIdOf("1997221"));
	}
}
