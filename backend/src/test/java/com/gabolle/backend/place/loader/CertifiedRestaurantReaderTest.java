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

import com.gabolle.backend.place.domain.CertifiedRestaurant;
import com.gabolle.backend.place.domain.CertifiedRestaurant.Source;
import com.gabolle.backend.place.domain.ExtraInterestTagCode;
import com.gabolle.backend.place.loader.CertifiedRestaurantReader.Reason;

/**
 * 공공 인증 식당 NDJSON 읽기 (S15P21E201-2051).
 *
 * <p>{@code certified-restaurants-sample.ndjson} 은 2026-10-13 에 받은 실제 파일(666줄)에서 문제가 있었던 줄을 그대로 뽑은 것이다 —
 * 같은 번호가 다른 구·동으로 겹친 일곱 번호(15줄), 구가 「?」인 줄, 주소와 좌표의 구가 다른 두 줄, 그리고 평범한 줄 몇 개.
 * 지어낸 줄이 아니라서 「이 파일을 넣으면 무슨 일이 생기는가」를 그대로 보여 준다.
 */
class CertifiedRestaurantReaderTest {

	private static final Path SAMPLE = Path.of("src/test/resources/place/certified-restaurants-sample.ndjson");

	/** 가장 평범한 한 줄 — 시험이 칸 하나씩 바꿔 가며 쓴다. */
	private static final String BASE = "{\"sourceId\":\"T1\",\"name\":\"시험식당\",\"lat\":35.1,\"lng\":129.0,"
			+ "\"address\":\"해운대구 시험로 1\",\"tel\":null,\"gu\":\"해운대구\",\"dong\":\"우동\",\"sources\":[\"모범음식점\"],"
			+ "\"menu\":null,\"existingPlaceId\":null,\"photoUrl\":null,\"photoSource\":null}";

	@TempDir
	Path dir;

	// ── 실제 파일에서 뽑은 표본 ─────────────────────────────────────────────

	@Test
	@DisplayName("🔴 표본 스물세 줄이 넣을 열두 곳 · 구·동 모름 하나 · 같은 번호의 남는 줄 여덟 · 주소와 구가 안 맞는 둘로 갈린다")
	void sampleSplitsIntoRowsSkippedAndDropped() {
		CertifiedRestaurantReader.Result result = CertifiedRestaurantReader.read(SAMPLE);

		assertThat(result.linesRead()).isEqualTo(23);
		assertThat(result.rows()).hasSize(12);
		assertThat(result.skipped()).hasSize(3);
		assertThat(result.droppedDuplicates()).hasSize(8);
		// 줄은 어느 쪽으로든 정확히 한 번 센다 — 새는 줄이 없다.
		assertThat(result.rows().size() + result.skipped().size() + result.droppedDuplicates().size())
				.isEqualTo(result.linesRead());
	}

	@Test
	@DisplayName("🔴 구가 「?」인 줄은 멈추지 않고 건너뛴다 — 경도 칸에 위도가 들어 있어 좌표 검사에 걸릴 줄이다")
	void unknownAreaRowIsSkippedBeforeTheCoordinateCheck() {
		CertifiedRestaurantReader.Result result = CertifiedRestaurantReader.read(SAMPLE);

		List<CertifiedRestaurantReader.Skipped> unknown = result.skipped().stream()
				.filter(skipped -> skipped.reason() == Reason.UNKNOWN_AREA).toList();
		assertThat(unknown).hasSize(1);
		assertThat(unknown.get(0).sourceId()).isEqualTo("BF1630");
		assertThat(unknown.get(0).name()).isEqualTo("보느파티쓰 리");
		assertThat(unknown.get(0).line()).isEqualTo(8);
		assertThat(result.skippedCount(Reason.UNKNOWN_AREA)).isEqualTo(1);
		assertThat(result.rows()).extracting(CertifiedRestaurantRow::sourceId).doesNotContain("BF1630");
	}

	@Test
	@DisplayName("🔴 같은 번호의 줄이 여럿이면 먼저 나온 줄이 아니라 주소와 맞는 줄을 고른다 — 실제 파일은 먼저 나온 줄이 일곱 번 모두 틀렸다")
	void duplicateSourceIdKeepsTheRowThatMatchesTheAddress() {
		CertifiedRestaurantReader.Result result = CertifiedRestaurantReader.read(SAMPLE);

		// 일곱 번호 모두 주소의 구·동과 맞는 줄이 남았다.
		assertAreaOf(result, "BF1599", "해운대구", "중동");
		assertAreaOf(result, "BF1591", "부산진구", "부전동");
		assertAreaOf(result, "MB서울깍두기@35.17207883", "해운대구", "좌동");
		assertAreaOf(result, "MB현대가든@35.1744556928639", "부산진구", "양정동");
		assertAreaOf(result, "MB배비장보쌈@35.1633188", "해운대구", "우동");
		assertAreaOf(result, "MB오륙도낙지@35.19620411", "동래구", "사직동");
		assertAreaOf(result, "MB이바돔감자탕@35.1635030908917", "부산진구", "전포동");

		// 같은 번호가 한 번만 남는다.
		assertThat(result.rows()).extracting(CertifiedRestaurantRow::sourceId).doesNotHaveDuplicates();

		// 남은 줄의 existingPlaceId 도 그 줄 것이다 — 팔레트는 두 줄이 서로 다른 기존 장소를 가리켰다.
		CertifiedRestaurantRow palette = rowOf(result, "BF1599");
		assertThat(palette.existingPlaceId()).isEqualTo(UUID.fromString("c5da327e-8216-3d45-934d-02bfaa52bf88"));
	}

	@Test
	@DisplayName("고르지 않은 줄은 몇 번째 줄이고 왜 버렸는지 남는다 — 서울깍두기는 세 줄 중 하나만 남는다")
	void droppedDuplicatesExplainThemselves() {
		CertifiedRestaurantReader.Result result = CertifiedRestaurantReader.read(SAMPLE);

		List<CertifiedRestaurantReader.DroppedDuplicate> kkakdugi = result.droppedDuplicates().stream()
				.filter(dropped -> dropped.sourceId().startsWith("MB서울깍두기")).toList();
		assertThat(kkakdugi).hasSize(2);
		assertThat(kkakdugi).extracting(CertifiedRestaurantReader.DroppedDuplicate::gu).containsExactlyInAnyOrder("금정구",
				"수영구");
		assertThat(kkakdugi.get(0).why()).contains("16번째 줄").contains("해운대구");
	}

	@Test
	@DisplayName("🔴 주소의 구와 좌표로 얻은 구가 다른 줄은 건너뛴다 — 큐제(연제구인데 금정구 좌표) · 초원복국(남구인데 해운대구 좌표)")
	void addressAndCoordinateDisagreementIsSkipped() {
		CertifiedRestaurantReader.Result result = CertifiedRestaurantReader.read(SAMPLE);

		List<CertifiedRestaurantReader.Skipped> mismatched = result.skipped().stream()
				.filter(skipped -> skipped.reason() == Reason.ADDRESS_GU_MISMATCH).toList();
		assertThat(mismatched).extracting(CertifiedRestaurantReader.Skipped::name).containsExactlyInAnyOrder("큐제",
				"초원복국");
		assertThat(mismatched.stream().filter(skipped -> skipped.name().equals("큐제")).findFirst().orElseThrow().detail())
				.contains("연제구").contains("금정구");
		assertThat(result.rows()).extracting(CertifiedRestaurantRow::name).doesNotContain("큐제", "초원복국");
	}

	@Test
	@DisplayName("평범한 줄은 칸 그대로 읽는다 — 인증 목록은 낱말로, 사진은 출처 문구와 함께")
	void ordinaryRowsAreReadAsIs() {
		CertifiedRestaurantReader.Result result = CertifiedRestaurantReader.read(SAMPLE);

		CertifiedRestaurantRow withPhoto = rowOf(result, "BF70");
		assertThat(withPhoto.name()).isEqualTo("만드리곤드레밥");
		assertThat(withPhoto.lat()).isEqualTo(35.177387);
		assertThat(withPhoto.gu()).isEqualTo("강서구");
		assertThat(withPhoto.dong()).isEqualTo("대저2동");
		assertThat(withPhoto.sources()).containsExactly(Source.BUSAN_FOOD_INFO);
		assertThat(withPhoto.photoUrl()).startsWith("https://www.visitbusan.net/");
		assertThat(withPhoto.photoSource()).isEqualTo("출처 : 부산광역시 부산맛집정보");
		assertThat(withPhoto.existingPlaceId()).isNull();

		CertifiedRestaurantRow all = rowOf(result, "BF124");
		assertThat(all.sources()).containsExactly(Source.BUSAN_FOOD_INFO, Source.MODEL_RESTAURANT,
				Source.TAXI_DRIVER_PICK);
		assertThat(all.existingPlaceId()).isNotNull();
		assertThat(all.area().key()).isEqualTo("중구/신창동1가");

		CertifiedRestaurantRow kogl = rowOf(result, "TX오구");
		assertThat(kogl.photoSource()).isEqualTo("한국관광공사 공공누리 제3유형");
		assertThat(kogl.sources()).containsExactly(Source.TAXI_DRIVER_PICK);
	}

	// ── 틀린 줄은 멈춘다 ──────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 모르는 칸이 있으면 줄 번호와 함께 멈춘다 — 오타 난 칸이 조용히 버려지지 않게")
	void unknownFieldStops() throws IOException {
		Path file = write(BASE, BASE.replace("\"T1\"", "\"T2\"").replace("\"tel\"", "\"phone\""));

		assertThatThrownBy(() -> CertifiedRestaurantReader.read(file)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("2번째 줄").hasMessageContaining("phone");
	}

	@Test
	@DisplayName("🔴 모르는 인증 목록 이름은 멈춘다 — 낱말을 지어내지 않는다")
	void unknownSourceStops() throws IOException {
		Path file = write(BASE.replace("\"모범음식점\"", "\"미슐랭\""));
		assertThatThrownBy(() -> CertifiedRestaurantReader.read(file)).hasMessageContaining("모르는 인증 목록")
				.hasMessageContaining("미슐랭");

		Path empty = write(BASE.replace("[\"모범음식점\"]", "[]"));
		assertThatThrownBy(() -> CertifiedRestaurantReader.read(empty)).hasMessageContaining("sources");
	}

	@Test
	@DisplayName("🔴 구·동을 아는 줄의 좌표가 부산 밖이면 멈춘다 — 위경도를 뒤바꾼 줄을 잡는다")
	void badCoordinatesStopWhenTheAreaIsKnown() throws IOException {
		Path swapped = write(BASE.replace("\"lat\":35.1,\"lng\":129.0", "\"lat\":129.0,\"lng\":35.1"));
		assertThatThrownBy(() -> CertifiedRestaurantReader.read(swapped)).hasMessageContaining("부산 밖");

		Path text = write(BASE.replace("\"lat\":35.1", "\"lat\":\"35.1\""));
		assertThatThrownBy(() -> CertifiedRestaurantReader.read(text)).hasMessageContaining("lat");
	}

	@Test
	@DisplayName("번호·이름·사진·기존 장소 번호가 틀리면 멈춘다 — 출처 없는 사진은 쓰지 않는다")
	void otherBadFieldsStop() throws IOException {
		assertThatThrownBy(() -> CertifiedRestaurantReader.read(write(BASE.replace("\"T1\"", "\"  \""))))
				.hasMessageContaining("sourceId");
		assertThatThrownBy(() -> CertifiedRestaurantReader.read(write(BASE.replace("\"시험식당\"", "null"))))
				.hasMessageContaining("name");
		assertThatThrownBy(() -> CertifiedRestaurantReader
				.read(write(BASE.replace("\"photoUrl\":null", "\"photoUrl\":\"https://example.invalid/a.jpg\""))))
				.hasMessageContaining("photoSource");
		assertThatThrownBy(() -> CertifiedRestaurantReader
				.read(write(BASE.replace("\"photoSource\":null", "\"photoSource\":\"출처\""))))
				.hasMessageContaining("photoUrl");
		assertThatThrownBy(() -> CertifiedRestaurantReader
				.read(write(BASE.replace("\"photoUrl\":null", "\"photoUrl\":\"ftp://x/a.jpg\"").replace(
						"\"photoSource\":null", "\"photoSource\":\"출처\""))))
				.hasMessageContaining("http");
		assertThatThrownBy(() -> CertifiedRestaurantReader
				.read(write(BASE.replace("\"existingPlaceId\":null", "\"existingPlaceId\":\"not-a-uuid\""))))
				.hasMessageContaining("UUID");
		assertThatThrownBy(() -> CertifiedRestaurantReader.read(write(BASE.replace("\"우동\"", "\"우/동\""))))
				.hasMessageContaining("'/'");
	}

	// ── 같은 번호 고르기의 세부 ───────────────────────────────────────────────

	@Test
	@DisplayName("점수가 같으면 파일에서 먼저 나온 줄이 남고, 남는 줄의 파일 순서는 그대로다")
	void tieKeepsTheFirstAndOrderFollowsTheFile() throws IOException {
		// 주소에 구가 없어 둘 다 판단 못 한다(같은 점수) — 먼저 나온 줄이 남는다.
		String a1 = BASE.replace("\"address\":\"해운대구 시험로 1\"", "\"address\":\"시험로 1\"");
		String a2 = a1.replace("\"우동\"", "\"중동\"");
		String other = BASE.replace("\"T1\"", "\"T9\"");
		CertifiedRestaurantReader.Result result = CertifiedRestaurantReader.read(write(a1, other, a2));

		assertThat(result.rows()).extracting(CertifiedRestaurantRow::sourceId).containsExactly("T1", "T9");
		assertThat(rowOf(result, "T1").dong()).isEqualTo("우동");
		assertThat(result.droppedDuplicates()).hasSize(1);
		assertThat(result.droppedDuplicates().get(0).line()).isEqualTo(3);
	}

	@Test
	@DisplayName("🔴 주소 첫머리에 구가 있을 때만 판단한다 — 길 이름 속의 「동구」·「중구」나 읍·면 주소는 판단하지 않는다")
	void addressGuReadsOnlyTheLeadingToken() {
		assertThat(CertifiedRestaurantReader.addressGu("부산 해운대구 달맞이길65번길 154 3층")).isEqualTo("해운대구");
		assertThat(CertifiedRestaurantReader.addressGu("부산광역시 부산진구 가야대로 772 (부전동)")).isEqualTo("부산진구");
		assertThat(CertifiedRestaurantReader.addressGu("부산시 기장군 기장읍 차성로 1")).isEqualTo("기장군");
		assertThat(CertifiedRestaurantReader.addressGu("강서구 공항앞길 85번길 13")).isEqualTo("강서구");
		assertThat(CertifiedRestaurantReader.addressGu("  남구   황령대로492번길 30 ")).isEqualTo("남구");

		assertThat(CertifiedRestaurantReader.addressGu("기장읍 차성로 322번길 40")).isNull();
		assertThat(CertifiedRestaurantReader.addressGu("부산광역시 두실로 17(구서동)")).isNull();
		assertThat(CertifiedRestaurantReader.addressGu("조방로 16번길 20")).isNull();
		assertThat(CertifiedRestaurantReader.addressGu("경남 양산시 동구로 1")).isNull();
		assertThat(CertifiedRestaurantReader.addressGu("")).isNull();
		assertThat(CertifiedRestaurantReader.addressGu(null)).isNull();
	}

	@Test
	@DisplayName("빈 줄은 건너뛰고 읽은 줄로 세지 않는다")
	void blankLinesAreIgnored() throws IOException {
		CertifiedRestaurantReader.Result result = CertifiedRestaurantReader.read(write(BASE, "", "   ",
				BASE.replace("\"T1\"", "\"T2\"")));

		assertThat(result.linesRead()).isEqualTo(2);
		assertThat(result.rows()).hasSize(2);
	}

	// ── 이름표 ────────────────────────────────────────────────────────────

	@Test
	@DisplayName("인증 목록 낱말 셋은 파일 이름과 짝이 있고, 탐색 갈래 낱말은 두 곳에 같게 적혀 있다")
	void vocabulary() {
		assertThat(Source.fromFileLabel("부산시 맛집정보")).contains(Source.BUSAN_FOOD_INFO);
		assertThat(Source.fromFileLabel(" 모범음식점 ")).contains(Source.MODEL_RESTAURANT);
		assertThat(Source.fromFileLabel("택슐랭")).contains(Source.TAXI_DRIVER_PICK);
		assertThat(Source.fromFileLabel("모르는 목록")).isEmpty();
		assertThat(Source.fromFileLabel(null)).isEmpty();
		assertThat(Source.fromCode("MODEL_RESTAURANT")).contains(Source.MODEL_RESTAURANT);
		assertThat(Source.fromCode("model_restaurant")).isEmpty();
		// 장소 상세의 공인 표식(RECOGNITION)이 이미 쓰는 낱말과 같다.
		assertThat(Source.MODEL_RESTAURANT.name()).isEqualTo("MODEL_RESTAURANT");
		assertThat(Source.TAXI_DRIVER_PICK.name()).isEqualTo("TAXI_DRIVER_PICK");

		assertThat(CertifiedRestaurant.EXPLORE_KEY).isEqualTo(ExtraInterestTagCode.LOCAL_RESTAURANT.name());
		assertThat(ExtraInterestTagCode.from("local_restaurant")).contains(ExtraInterestTagCode.LOCAL_RESTAURANT);
		assertThat(ExtraInterestTagCode.from("FESTIVAL")).isEmpty();
		assertThat(ExtraInterestTagCode.LOCAL_RESTAURANT.labelKo()).isEqualTo("맛집");
	}

	@Test
	@DisplayName("구·동 열쇠는 「구/동」이고, 모양이 틀린 열쇠는 되돌리지 않는다")
	void areaKeyRoundTrip() {
		CertifiedRestaurant.Area area = new CertifiedRestaurant.Area("해운대구", "우동");
		assertThat(area.key()).isEqualTo("해운대구/우동");
		assertThat(CertifiedRestaurant.Area.fromKey("해운대구/우동")).contains(area);
		assertThat(CertifiedRestaurant.Area.fromKey("해운대구/")).isEmpty();
		assertThat(CertifiedRestaurant.Area.fromKey("/우동")).isEmpty();
		assertThat(CertifiedRestaurant.Area.fromKey("해운대구")).isEmpty();
		assertThat(CertifiedRestaurant.Area.fromKey("해운대구/우동/또")).isEmpty();
		assertThat(CertifiedRestaurant.Area.fromKey(null)).isEmpty();
		assertThatThrownBy(() -> new CertifiedRestaurant.Area("해운대구", " ")).isInstanceOf(IllegalArgumentException.class);
	}

	// ── 도우미 ────────────────────────────────────────────────────────────

	private static void assertAreaOf(CertifiedRestaurantReader.Result result, String sourceId, String gu, String dong) {
		CertifiedRestaurantRow row = rowOf(result, sourceId);
		assertThat(row.gu()).as(sourceId + " 의 구").isEqualTo(gu);
		assertThat(row.dong()).as(sourceId + " 의 동").isEqualTo(dong);
	}

	private static CertifiedRestaurantRow rowOf(CertifiedRestaurantReader.Result result, String sourceId) {
		return result.rows().stream().filter(row -> row.sourceId().equals(sourceId)).findFirst()
				.orElseThrow(() -> new AssertionError("넣을 줄에 " + sourceId + " 가 없다"));
	}

	private Path write(String... lines) throws IOException {
		Path file = this.dir.resolve("certified.ndjson");
		Files.write(file, List.of(lines), StandardCharsets.UTF_8);
		return file;
	}
}
