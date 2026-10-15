package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gabolle.backend.place.domain.CertifiedRestaurant.Source;
import com.gabolle.backend.place.loader.CertifiedRestaurantReader.Reason;
import com.gabolle.backend.place.loader.LocalCafeRow.Origin;
import com.gabolle.backend.place.loader.LocalPickRow.CertMark;

/**
 * 카페 NDJSON 읽기 (S15P21E201-2059).
 *
 * <p>{@code local-cafes-sample.ndjson} 은 2026-10-15 에 받은 실제 파일(250줄)의 모양을 그대로 따른 열한 줄이다 — 관광공사 줄(사진 있음·없음),
 * 레드테이블 줄(일반·방송·수상), 두 목록이 겹친 줄, 이미 있는 장소를 가리키는 줄(음식·카페), 그리고 건너뛸 줄 둘. 번호는 시험용으로 바꿨다.
 */
class LocalCafeReaderTest {

	private static final Path SAMPLE = Path.of("src/test/resources/place/local-cafes-sample.ndjson");

	/** 가장 평범한 한 줄 — 시험이 칸 하나씩 바꿔 가며 쓴다. */
	private static final String BASE = "{\"sourceId\":\"KTO100\",\"name\":\"시험카페\",\"lat\":35.1,\"lng\":129.0,"
			+ "\"address\":\"해운대구 시험로 1\",\"tel\":null,\"photoUrl\":null,\"photoSource\":null,"
			+ "\"sources\":[\"한국관광공사\"],\"existingPlaceId\":null,\"existingCategory\":null,\"gu\":\"해운대구\",\"dong\":\"우동\"}";

	@TempDir
	Path dir;

	// ── 표본 ───────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 표본 열한 줄이 넣을 아홉 곳과 건너뛸 둘(구·동 모름 · 주소의 구와 좌표의 구가 다름)로 갈린다 — 새는 줄이 없다")
	void sampleSplitsIntoRowsAndSkipped() {
		LocalCafeReader.Result result = LocalCafeReader.read(SAMPLE);

		assertThat(result.linesRead()).isEqualTo(11);
		assertThat(result.rows()).hasSize(9);
		assertThat(result.skipped()).hasSize(2);
		assertThat(result.rows().size() + result.skipped().size()).isEqualTo(result.linesRead());
		assertThat(result.skippedCount(Reason.UNKNOWN_AREA)).isEqualTo(1);
		assertThat(result.skippedCount(Reason.ADDRESS_GU_MISMATCH)).isEqualTo(1);
		// 구·동을 모르는 줄은 좌표가 틀려 있어도(경도 칸에 위도) 좌표 검사에 걸려 멈추지 않고 건너뛴다.
		assertThat(result.skipped()).extracting(CertifiedRestaurantReader.Skipped::sourceId).containsExactly("RT9900008",
				"RT9900009");
		assertThat(result.rows()).extracting(LocalCafeRow::sourceId).doesNotContain("RT9900008", "RT9900009");
	}

	@Test
	@DisplayName("관광공사 줄은 사진과 출처 문구를 짝으로 읽고, 새 장소가 된다면 TOURAPI 의 콘텐츠 번호(숫자)로 만든다")
	void ktoRowCarriesItsPhotoAndBecomesATourApiPlace() {
		LocalCafeRow row = rowOf("KTO9900001");

		assertThat(row.name()).isEqualTo("시험샌드커피");
		assertThat(row.gu()).isEqualTo("기장군");
		assertThat(row.dong()).isEqualTo("기장읍");
		assertThat(row.photoUrl()).isEqualTo("http://tong.visitkorea.or.kr/cms/resource/76/9900001_image2_1.jpg");
		assertThat(row.photoSource()).isEqualTo("한국관광공사 공공누리 제3유형");
		assertThat(row.origins()).containsExactly(Origin.KTO);
		assertThat(row.certMarks()).containsExactly(new CertMark(Source.KTO_CAFE, false));

		LocalPickRow.NewPlace fresh = row.newPlace();
		assertThat(fresh.sourceType()).isEqualTo("TOURAPI");
		assertThat(fresh.sourceId()).isEqualTo("9900001");
		assertThat(fresh.placeId()).isEqualTo(TourApiPlaceLoader.placeIdOf("9900001"));
		assertThat(fresh.category()).isEqualTo("CAFE_HEALING");
	}

	@Test
	@DisplayName("레드테이블 줄은 사진이 없고, 새 장소가 된다면 REDTABLE 의 식당 번호(숫자)로 만든다 — 레드테이블 적재기와 같은 장소 번호다")
	void redtableRowBecomesARedtablePlace() {
		LocalCafeRow row = rowOf("RT9900003");

		assertThat(row.photoUrl()).isNull();
		assertThat(row.photoSource()).isNull();
		assertThat(row.intro()).contains("시험커피숍");
		assertThat(row.origins()).containsExactly(Origin.REDTABLE);
		assertThat(row.certMarks()).containsExactly(new CertMark(Source.REDTABLE_FEATURED, false));

		LocalPickRow.NewPlace fresh = row.newPlace();
		assertThat(fresh.sourceType()).isEqualTo("REDTABLE");
		assertThat(fresh.sourceId()).isEqualTo("9900003");
		assertThat(fresh.placeId()).isEqualTo(RedtablePlaceLoader.placeIdOf("9900003"));
	}

	@Test
	@DisplayName("🔴 레드테이블(방송·수상)은 같은 표식에 방송·수상 표시가 붙는다 — 관광공사와 함께 오면 표식이 둘이다")
	void broadcastOrAwardIsAFlagOnTheSameMark() {
		assertThat(rowOf("RT9900004").certMarks()).containsExactly(new CertMark(Source.REDTABLE_FEATURED, true));
		assertThat(rowOf("KTO9900005").certMarks()).containsExactly(new CertMark(Source.KTO_CAFE, false),
				new CertMark(Source.REDTABLE_FEATURED, true));
		// 번호 앞머리가 새 장소의 출처를 정한다 — 두 목록이 겹친 줄도 KTO 로 시작하면 관광공사 장소다.
		assertThat(rowOf("KTO9900005").newPlace().sourceType()).isEqualTo("TOURAPI");
	}

	@Test
	@DisplayName("이미 있는 장소를 가리키는 줄은 그 번호와 갈래를 그대로 읽는다 — 갈래는 음식일 수도 카페일 수도 있다")
	void existingPlacesKeepTheirCategory() {
		LocalCafeRow food = rowOf("KTO9900006");
		assertThat(food.existingPlaceId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000009906"));
		assertThat(food.existingCategory()).isEqualTo("FOOD");

		LocalCafeRow cafe = rowOf("RT9900007");
		assertThat(cafe.existingPlaceId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000009907"));
		assertThat(cafe.existingCategory()).isEqualTo("CAFE_HEALING");

		assertThat(rowOf("KTO9900001").existingPlaceId()).isNull();
		assertThat(rowOf("KTO9900001").existingCategory()).isNull();
	}

	// ── 멈추는 경우 ─────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 레드테이블 줄에 사진이 있으면 멈춘다 — 이용 조건을 확인하지 못한 사진이다")
	void aRedtablePhotoStopsTheRead() {
		String line = BASE.replace("\"KTO100\"", "\"RT100\"").replace("[\"한국관광공사\"]", "[\"레드테이블\"]")
				.replace("\"photoUrl\":null", "\"photoUrl\":\"https://example.invalid/a.jpg\"")
				.replace("\"photoSource\":null", "\"photoSource\":\"출처 : 레드테이블\"");

		assertThatThrownBy(() -> readOne(line)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1번째 줄")
				.hasMessageContaining("레드테이블 사진");
	}

	@Test
	@DisplayName("🔴 관광공사 줄이어도 출처 문구가 한국관광공사가 아니면 멈춘다 — 사진은 출처와 짝이고 출처가 이용 조건이다")
	void aKtoPhotoNeedsTheKtoCredit() {
		String other = BASE.replace("\"photoUrl\":null", "\"photoUrl\":\"https://example.invalid/a.jpg\"")
				.replace("\"photoSource\":null", "\"photoSource\":\"출처 : 어딘가\"");
		assertThatThrownBy(() -> readOne(other)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("한국관광공사");

		String noCredit = BASE.replace("\"photoUrl\":null", "\"photoUrl\":\"https://example.invalid/a.jpg\"");
		assertThatThrownBy(() -> readOne(noCredit)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("photoSource");

		String creditOnly = BASE.replace("\"photoSource\":null", "\"photoSource\":\"한국관광공사 공공누리 제3유형\"");
		assertThatThrownBy(() -> readOne(creditOnly)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("photoUrl");

		String plain = BASE.replace("\"photoUrl\":null", "\"photoUrl\":\"ftp://example.invalid/a.jpg\"")
				.replace("\"photoSource\":null", "\"photoSource\":\"한국관광공사 공공누리 제3유형\"");
		assertThatThrownBy(() -> readOne(plain)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("http");
	}

	@Test
	@DisplayName("🔴 번호 모양·출처가 어긋나면 멈춘다 — 앞머리가 새 장소의 출처를 정하므로 소개한 목록과 맞아야 한다")
	void sourceIdAndSourcesMustAgree() {
		assertThatThrownBy(() -> readOne(BASE.replace("\"KTO100\"", "\"XX100\""))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("KTO 또는 RT");
		assertThatThrownBy(() -> readOne(BASE.replace("\"KTO100\"", "\"KTO\""))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> readOne(BASE.replace("\"KTO100\"", "\"KTO1a\""))).isInstanceOf(IllegalArgumentException.class);
		// 관광공사 번호인데 레드테이블이 소개했다고만 적혔다.
		assertThatThrownBy(() -> readOne(BASE.replace("[\"한국관광공사\"]", "[\"레드테이블\"]"))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("한국관광공사");
		// 레드테이블 번호인데 관광공사가 소개했다고만 적혔다.
		assertThatThrownBy(() -> readOne(BASE.replace("\"KTO100\"", "\"RT100\""))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("레드테이블");
		assertThatThrownBy(() -> readOne(BASE.replace("[\"한국관광공사\"]", "[\"모범음식점\"]"))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("모르는 목록");
		assertThatThrownBy(() -> readOne(BASE.replace("[\"한국관광공사\"]", "[]"))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("sources");
	}

	@Test
	@DisplayName("모르는 칸 · 숫자가 아닌 좌표 · 부산 밖 좌표 · 같은 번호 · 어긋난 existing 칸 · '/' 가 든 구·동은 멈춘다")
	void malformedRowsStopTheRead() {
		assertThatThrownBy(() -> readOne(BASE.replace("\"tel\":null", "\"tel\":null,\"menu\":\"x\""))).isInstanceOf(
				IllegalArgumentException.class).hasMessageContaining("모르는 칸").hasMessageContaining("menu");
		assertThatThrownBy(() -> readOne(BASE.replace("\"lat\":35.1", "\"lat\":\"35.1\""))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("lat");
		assertThatThrownBy(() -> readOne(BASE.replace("\"lat\":35.1,\"lng\":129.0", "\"lat\":129.0,\"lng\":35.1")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("부산 밖");
		assertThatThrownBy(() -> readOne(BASE, BASE)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2번째 줄")
				.hasMessageContaining("KTO100");
		assertThatThrownBy(() -> readOne(BASE.replace("\"existingCategory\":null", "\"existingCategory\":\"FOOD\"")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("existingCategory");
		assertThatThrownBy(() -> readOne(BASE.replace("\"existingPlaceId\":null", "\"existingPlaceId\":\"nope\"")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UUID");
		assertThatThrownBy(() -> readOne(BASE.replace("\"dong\":\"우동\"", "\"dong\":\"우/동\""))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("/");
		assertThatThrownBy(() -> readOne("[1,2]")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON 객체");
	}

	@Test
	@DisplayName("빈 줄은 건너뛰고 줄 수에 안 센다 · 한 줄만 있어도 읽는다")
	void blankLinesAreIgnored() throws IOException {
		LocalCafeReader.Result result = LocalCafeReader.read(write(BASE, "", "   ", BASE.replace("KTO100", "KTO101")));

		assertThat(result.linesRead()).isEqualTo(2);
		assertThat(result.rows()).hasSize(2);
	}

	@Test
	@DisplayName("주소의 구와 좌표로 얻은 구가 같으면 넣고, 주소에 구가 안 적혀 있으면 믿는다")
	void agreeingOrUnknownAddressGuIsKept() {
		assertThat(readOne(BASE).rows()).hasSize(1);
		assertThat(readOne(BASE.replace("해운대구 시험로 1", "우동 시험로 1")).rows()).hasSize(1);
		assertThat(readOne(BASE.replace("\"address\":\"해운대구 시험로 1\"", "\"address\":null")).rows()).hasSize(1);
		LocalCafeReader.Result mismatch = readOne(BASE.replace("해운대구 시험로 1", "연제구 시험로 1"));
		assertThat(mismatch.rows()).isEmpty();
		assertThat(mismatch.skipped()).singleElement().satisfies(skipped -> {
			assertThat(skipped.reason()).isEqualTo(Reason.ADDRESS_GU_MISMATCH);
			assertThat(skipped.detail()).contains("연제구").contains("해운대구");
		});
	}

	// ── 도우미 ────────────────────────────────────────────────────────────

	private static LocalCafeRow rowOf(String sourceId) {
		return LocalCafeReader.read(SAMPLE).rows().stream().filter(row -> row.sourceId().equals(sourceId)).findFirst()
				.orElseThrow(() -> new AssertionError(sourceId + " 가 넣을 줄에 없다"));
	}

	private LocalCafeReader.Result readOne(String... lines) {
		try {
			return LocalCafeReader.read(write(lines));
		}
		catch (IOException exception) {
			throw new AssertionError(exception);
		}
	}

	private Path write(String... lines) throws IOException {
		return Files.write(this.dir.resolve("cafes-" + System.nanoTime() + ".ndjson"), List.of(lines), StandardCharsets.UTF_8);
	}
}
