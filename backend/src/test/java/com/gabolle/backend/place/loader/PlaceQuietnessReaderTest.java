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
 * <p>가덕도 등대와 연대봉은 대조군이다 — 같은 섬인데 90 대 10 으로 갈리는 것이 이 값이 쓸모
 * 있다는 근거다.
 */
class PlaceQuietnessReaderTest {

	/** 관광공사 열쇠({@code contentid})를 쓰는 줄. */
	private static final String LIGHTHOUSE = """
			{"contentid":"129156","title":"가덕도 등대","featureType":"QUIETNESS_SCORE",\
			"evidenceStatus":"ESTIMATED","quietnessScore":90,"noiseP90":0.1,"roads":12,\
			"roadLengthM":1098,"radiusM":200}""";

	/** 같은 섬인데 시끄러운 쪽. */
	private static final String PEAK = """
			{"contentid":"2726843","title":"가덕도 연대봉","featureType":"QUIETNESS_SCORE",\
			"evidenceStatus":"ESTIMATED","quietnessScore":10,"noiseP90":0.9,"roads":42,\
			"roadLengthM":5310,"radiusM":200}""";

	/** 상가 열쇠({@code sourceType}+{@code sourceId})를 쓰는 줄. */
	private static final String BURGER_KING = """
			{"sourceType":"SBIZ","sourceId":"MA0106202201A0010681","title":"버거킹부산센텀오일뱅크점",\
			"featureType":"QUIETNESS_SCORE","evidenceStatus":"ESTIMATED","quietnessScore":35,\
			"noiseP90":0.65,"roads":57,"roadLengthM":4310,"radiusM":200}""";

	/** 로컬성 — {@code noiseP90} 이 없어 검산을 건너뛰는 쪽이다. */
	private static final String LOCALITY = """
			{"sourceType":"SBIZ","sourceId":"MA0106202201A0010681","title":"버거킹부산센텀오일뱅크점",\
			"featureType":"LOCALITY_SCORE","evidenceStatus":"ESTIMATED","localityScore":66.2,\
			"shops":160,"radiusM":300}""";

	@TempDir
	Path dir;

	private List<PlaceFeatureNdjsonReader.Fact> readAll(String valueField, String featureType, String... lines)
			throws Exception {
		Path file = this.dir.resolve("scores.ndjson");
		Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		List<PlaceFeatureNdjsonReader.Fact> facts = new ArrayList<>();
		PlaceFeatureNdjsonReader.readPlaceScores(file, valueField, featureType, 100, facts::addAll);
		return facts;
	}

	@Test
	@DisplayName("🔴 0~100 산출물을 0~1 로 나눠 저장한다 — 채점기 눈금이 0~1 이다")
	void convertsPercentScaleToTheScorerScale() throws Exception {
		List<PlaceFeatureNdjsonReader.Fact> facts =
				readAll("quietnessScore", "QUIETNESS_SCORE", LIGHTHOUSE, PEAK);

		// 90 과 10 이 아니라 0.9 와 0.1 이어야 한다. 그대로 넣으면 채점기가
		// 1 - |장소값 - 선호값| 을 계산할 때 축을 통째로 0 으로 뭉갠다.
		assertThat(facts).extracting(PlaceFeatureNdjsonReader.Fact::value)
				.containsExactly(
						"{\"score\":0.9,\"radiusM\":200.0,\"noiseP90\":0.1,\"roads\":12.0,\"roadLengthM\":1098.0}",
						"{\"score\":0.1,\"radiusM\":200.0,\"noiseP90\":0.9,\"roads\":42.0,\"roadLengthM\":5310.0}");
	}

	@Test
	@DisplayName("🔴 열쇠 모양 둘을 한 파일에서 다 읽는다 — 경사는 관광공사만 읽어 절반을 놓쳤다")
	void readsBothKeyShapes() throws Exception {
		List<PlaceFeatureNdjsonReader.Fact> facts =
				readAll("quietnessScore", "QUIETNESS_SCORE", LIGHTHOUSE, BURGER_KING);

		assertThat(facts).extracting(PlaceFeatureNdjsonReader.Fact::storeId,
				PlaceFeatureNdjsonReader.Fact::keySource)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple("129156", TourApiPlaceLoader.SOURCE_TYPE),
						org.assertj.core.groups.Tuple.tuple("MA0106202201A0010681", SbizPlaceLoader.SOURCE_TYPE));
	}

	@Test
	@DisplayName("🔴 눈금이 이미 0~1 이면 멈춘다 — 두 번 나누면 축이 또 전부 0 이 된다")
	void refusesWhenTheArtifactIsAlreadyOnTheScorerScale() throws Exception {
		// 산출물이 나중에 0~1 로 바뀌었다고 하자. 0.9 는 0~100 범위 검사를 통과하지만
		// 100 으로 또 나누면 0.009 가 되어 축이 다시 뭉개진다. noiseP90 검산이 그것을 잡는다.
		String alreadyScaled = """
				{"contentid":"129156","featureType":"QUIETNESS_SCORE","quietnessScore":0.9,\
				"noiseP90":0.1,"radiusM":200}""";

		assertThatThrownBy(() -> readAll("quietnessScore", "QUIETNESS_SCORE", alreadyScaled))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("눈금이 안 맞는다")
				.hasMessageContaining("0~1 로 바뀐 것은 아닌지");
	}

	@Test
	@DisplayName("🔴 모르는 sourceType 은 조용히 넘기지 않고 멈춘다")
	void refusesUnknownSourceType() throws Exception {
		// 오타를 그냥 두면 엉뚱한 장소 아이디를 계산해 "장소가 없어 못 넣음" 으로만 세어지고,
		// 그 오타를 알아챌 방법이 없다.
		String typo = BURGER_KING.replace("\"SBIZ\"", "\"SIBZ\"");

		assertThatThrownBy(() -> readAll("quietnessScore", "QUIETNESS_SCORE", typo))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("모르는 sourceType");
	}

	@Test
	@DisplayName("범위를 벗어난 값은 버리지 않고 멈춘다 — 조용히 버리면 개수만 줄고 아무도 못 알아챈다")
	void refusesOutOfRangeValue() throws Exception {
		String broken = LIGHTHOUSE.replace("\"quietnessScore\":90", "\"quietnessScore\":140");

		assertThatThrownBy(() -> readAll("quietnessScore", "QUIETNESS_SCORE", broken))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("범위를 벗어난 값");
	}

	@Test
	@DisplayName("로컬성도 같은 읽기로 처리된다 — noiseP90 이 없으면 검산을 건너뛴다")
	void readsLocalityWithoutTheCrossCheck() throws Exception {
		List<PlaceFeatureNdjsonReader.Fact> facts = readAll("localityScore", "LOCALITY_SCORE", LOCALITY);

		assertThat(facts).singleElement()
				.satisfies(fact -> {
					assertThat(fact.featureType()).isEqualTo("LOCALITY_SCORE");
					assertThat(fact.keySource()).isEqualTo(SbizPlaceLoader.SOURCE_TYPE);
					// 66.2 → 0.662. shops 는 되짚기용으로 남고 채점기는 score 만 읽는다.
					assertThat(fact.value()).contains("\"score\":0.662").contains("\"shops\":160.0");
				});
	}

	@Test
	@DisplayName("🔴 그늘도 같은 읽기로 처리된다 — 되짚기 값(treeDensity 등)이 함께 남는다")
	void readsShadeWithItsProvenanceFields() throws Exception {
		// noiseP90 같은 검산용 짝이 없는 쪽이다.
		String shade = """
				{"contentid":"2785289","title":"감지해변","featureType":"SHADE_SCORE",\
				"evidenceStatus":"ESTIMATED","shadeScore":21,"treeDensity":0.0398,"sections":1,\
				"plantedM":1400,"radiusM":500}""";

		List<PlaceFeatureNdjsonReader.Fact> facts = readAll("shadeScore", "SHADE_SCORE", shade);

		assertThat(facts.get(0).featureType()).isEqualTo("SHADE_SCORE");
		assertThat(facts.get(0).keySource()).isEqualTo(TourApiPlaceLoader.SOURCE_TYPE);
		// 21 → 0.21. treeDensity 는 shadeScore 와 일정한 비율이 아니라 검산에 쓸 수 없다.
		assertThat(facts.get(0).value())
				.contains("\"score\":0.21")
				.contains("\"treeDensity\":0.0398")
				.contains("\"plantedM\":1400.0");
	}

	@Test
	@DisplayName("🔴 검산할 짝이 없는 축도 눈금을 지킨다 — 파일 전체가 1 이하면 한 줄도 안 넣고 멈춘다")
	void refusesAnAlreadyScaledFileEvenWithoutACrossCheckPartner() throws Exception {
		// 그늘·로컬에는 noiseP90 같은 짝이 없다. 한 줄만 보면 0~100 의 작은 값인지 0~1 의 큰
		// 값인지 가를 수 없어 범위 검사로는 못 막지만, 파일 전체를 보면 갈린다.
		String[] alreadyScaled = new String[60];
		for (int i = 0; i < alreadyScaled.length; i++) {
			alreadyScaled[i] = """
					{"contentid":"%d","featureType":"SHADE_SCORE","shadeScore":0.%02d,"radiusM":500}"""
					.formatted(1000 + i, i);
		}

		assertThatThrownBy(() -> readAll("shadeScore", "SHADE_SCORE", alreadyScaled))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 0~1 눈금으로 보인다")
				.hasMessageContaining("100배 작아져");
	}

	@Test
	@DisplayName("줄이 몇 개뿐이면 눈금을 판정하지 않는다 — 우연히 전부 작을 수 있다")
	void doesNotJudgeScaleOnATinyFile() throws Exception {
		// 0~100 눈금인데 값이 우연히 작은 두 줄. 여기서 멈추면 정상 산출물을 막게 된다.
		String a = """
				{"contentid":"1","featureType":"SHADE_SCORE","shadeScore":0.4,"radiusM":500}""";
		String b = """
				{"contentid":"2","featureType":"SHADE_SCORE","shadeScore":0.9,"radiusM":500}""";

		assertThat(readAll("shadeScore", "SHADE_SCORE", a, b)).hasSize(2);
	}

	@Test
	@DisplayName("값이 없는 줄은 버린다 — 멈추지 않는다")
	void skipsLinesWithoutAValue() throws Exception {
		String noValue = """
				{"contentid":"129156","featureType":"QUIETNESS_SCORE","radiusM":200}""";

		assertThat(readAll("quietnessScore", "QUIETNESS_SCORE", noValue, LIGHTHOUSE)).hasSize(1);
	}
}
