package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 실태조사 가공본 읽기 — S15P21E201-1365.
 *
 * <p>여기서 세는 수가 적재 결과를 읽는 유일한 근거다. <b>세지 않으면 조용히 0 건이 되고
 * 아무 오류도 안 난다</b> — 그 모양을 이 저장소에서 오늘 네 번 겪었다.
 */
class FacilityAccessibilityReaderTest {

	@TempDir
	Path dir;

	private Path write(String... lines) throws IOException {
		Path file = this.dir.resolve("staged.ndjson");
		Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		return file;
	}

	private static String line(String sourceType, String sourceId, String placeId, String evalRaw) {
		return """
				{"sourceType":"%s","sourceId":"%s","placeId":"%s","wfcltId":"W-%s","evalRaw":"%s"}"""
				.formatted(sourceType, sourceId, placeId, sourceId, evalRaw);
	}

	@Test
	@DisplayName("두 출처를 다 읽고, 장소 id 를 각자의 계산식으로 만든다")
	void readsBothSources() throws IOException {
		UUID tour = TourApiPlaceLoader.placeIdOf("2544728");
		UUID sbiz = SbizPlaceLoader.placeIdOf("MA0101");
		Path file = write(
				line("TOURAPI", "2544728", tour.toString(), "주출입구 접근로"),
				line("SBIZ", "MA0101", sbiz.toString(), "주출입구 높이차이 제거"));

		FacilityAccessibilityReader.Loaded loaded = FacilityAccessibilityReader.read(file);

		assertThat(loaded.rows()).hasSize(2);
		assertThat(loaded.rows().get(0).placeId()).isEqualTo(tour);
		assertThat(loaded.rows().get(1).placeId()).isEqualTo(sbiz);
		assertThat(loaded.counts().idMismatch()).isZero();
	}

	@Test
	@DisplayName("🔴 보내온 장소 id 가 우리 계산과 다르면 버리고 센다 — 어느 쪽이 틀렸든 엉뚱한 곳에 붙는다")
	void dropsRowsWhenTheTwoSidesDisagree() throws IOException {
		Path file = write(line("TOURAPI", "2544728", UUID.randomUUID().toString(), "주출입구 접근로"));

		FacilityAccessibilityReader.Loaded loaded = FacilityAccessibilityReader.read(file);

		assertThat(loaded.rows()).isEmpty();
		assertThat(loaded.counts().idMismatch()).isEqualTo(1);
	}

	@Test
	@DisplayName("보내온 판정을 안 쓴다 — 문만 있으면 근거 없음으로 센다")
	void appliesOurOwnRuleNotTheirs() throws IOException {
		UUID tour = TourApiPlaceLoader.placeIdOf("111");
		Path file = write("""
				{"sourceType":"TOURAPI","sourceId":"111","placeId":"%s","wfcltId":"W-111",\
				"featureKey":"WHEELCHAIR","evalRaw":"주출입구(문)"}""".formatted(tour));

		FacilityAccessibilityReader.Loaded loaded = FacilityAccessibilityReader.read(file);

		assertThat(loaded.rows())
				.as("데이터 파트가 WHEELCHAIR 라고 보내도 문만으로는 안 붙인다")
				.isEmpty();
		assertThat(loaded.counts().noCode()).isEqualTo(1);
	}

	@Test
	@DisplayName("모르는 출처·깨진 줄·중복을 각각 따로 센다")
	void countsEachKindOfSkipSeparately() throws IOException {
		UUID tour = TourApiPlaceLoader.placeIdOf("222");
		Path file = write(
				line("KAKAO", "999", "", "주출입구 접근로"),
				"{\"sourceType\":\"TOURAPI\",\"sourceId\":\"\",\"wfcltId\":\"W-1\",\"evalRaw\":\"주출입구 접근로\"}",
				"이건 JSON 이 아니다",
				line("TOURAPI", "222", tour.toString(), "주출입구 접근로"),
				line("TOURAPI", "222", tour.toString(), "주출입구 접근로"));

		FacilityAccessibilityReader.Loaded loaded = FacilityAccessibilityReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		assertThat(loaded.counts().totalLines()).isEqualTo(5);
		assertThat(loaded.counts().unknownSource()).isEqualTo(1);
		assertThat(loaded.counts().broken()).isEqualTo(2);
		assertThat(loaded.counts().duplicate()).isEqualTo(1);
	}

	@Test
	@DisplayName("장소 id 를 안 보내도 읽는다 — 검산은 못 하지만 우리 계산으로 붙는다")
	void missingClaimedIdIsNotAnError() throws IOException {
		Path file = write(line("SBIZ", "MA0202", "", "주출입구 접근로"));

		FacilityAccessibilityReader.Loaded loaded = FacilityAccessibilityReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		assertThat(loaded.counts().idMismatch()).isZero();
	}
}
