package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 관광공사 수집본 판독 — S15P21E201-854.
 *
 * <p>DB 가 필요 없는 판정이라 파일만 만들어 읽힌다. 여기서 재는 것은 셋이다 — <b>음식을
 * 빼는가</b>, <b>깨진 줄에서 멈추지 않는가</b>, <b>버린 것을 세어 올리는가</b>.
 *
 * <p>🔴 실제 수집본은 이 저장소에 없다({@code bigData/dev} 에 있다). 그래서 응답 모양을 옮겨
 * 적는다 — 옮겨 적은 것이 실제와 다르면 이 검사가 통과하고 운영에서 0 건이 된다. 그 위험을
 * 줄이려고 <b>실제 파일에서 확인한 모양 그대로</b> 썼다: {@code raw} 가 객체가 아니라
 * <b>문자열</b>이고, 항목이 {@code response.body.items.item} 아래 있다.
 */
class TourApiPlaceReaderTest {

	@TempDir
	Path dir;

	@Test
	@DisplayName("🔴 음식(A05)은 안 넘기고 그 수를 세어 올린다 — 상가업소와 같은 가게인지 판정할 수 없다")
	void foodIsExcludedAndCounted() throws IOException {
		Path file = write(listLine(
				item("129156", "12", "A01", "A01010400", "금정산", "부산광역시 금정구", "129.0", "35.2"),
				item("200001", "39", "A05", "A05020100", "어느 국밥집", "부산광역시 중구", "129.03", "35.10")));

		TourApiPlaceReader.Loaded loaded = TourApiPlaceReader.read(file);

		assertThat(loaded.rows()).extracting(TourApiPlaceRow::contentId).containsExactly("129156");
		assertThat(loaded.counts().skippedFood()).isEqualTo(1);
		assertThat(loaded.counts().items()).isEqualTo(2);
		assertThat(loaded.counts().usable()).isEqualTo(1);
	}

	@Test
	@DisplayName("상세 단계 줄은 읽지 않는다 — 버린 것으로도 세지 않는다")
	void detailLinesAreIgnored() throws IOException {
		Path file = write(
				listLine(item("1", "12", "A01", "A01010400", "금정산", "부산 금정구", "129.0", "35.2")),
				"{\"stage\":\"detail\",\"contentid\":\"1\",\"raw\":\"{}\"}");

		TourApiPlaceReader.Loaded loaded = TourApiPlaceReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		assertThat(loaded.counts().totalLines()).isEqualTo(2);
		assertThat(loaded.counts().listLines()).isEqualTo(1);
		assertThat(loaded.counts().skippedBroken()).isZero();
	}

	@Test
	@DisplayName("🔴 좌표가 없는 항목은 버린다 — 0 으로 채우면 아프리카 앞바다에 장소가 생긴다")
	void itemWithoutCoordinatesIsSkipped() throws IOException {
		Path file = write(listLine(
				item("1", "12", "A01", "A01010400", "좌표 없는 곳", "부산 어딘가", null, null),
				item("2", "12", "A01", "A01010400", "금정산", "부산 금정구", "129.0", "35.2")));

		TourApiPlaceReader.Loaded loaded = TourApiPlaceReader.read(file);

		assertThat(loaded.rows()).extracting(TourApiPlaceRow::contentId).containsExactly("2");
		assertThat(loaded.counts().skippedBroken()).isEqualTo(1);
	}

	@Test
	@DisplayName("한 줄이 깨져도 나머지는 읽고, 버린 줄 수가 올라온다")
	void brokenLineDoesNotStopTheRest() throws IOException {
		Path file = write(
				"{이건 JSON 이 아니다",
				listLine(item("7", "14", "A02", "A02010100", "어느 박물관", "부산 동구", "129.05", "35.13")));

		TourApiPlaceReader.Loaded loaded = TourApiPlaceReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		assertThat(loaded.counts().skippedBroken()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 contentid 가 두 번 오면 먼저 본 것만 남기고 중복으로 센다 — 쪽 경계에서 실제로 생긴다")
	void duplicateContentIdIsCounted() throws IOException {
		Path file = write(
				listLine(item("9", "12", "A01", "A01010400", "금정산", "부산 금정구", "129.0", "35.2")),
				listLine(item("9", "12", "A01", "A01010400", "금정산", "부산 금정구", "129.0", "35.2")));

		TourApiPlaceReader.Loaded loaded = TourApiPlaceReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		assertThat(loaded.counts().skippedDuplicate()).isEqualTo(1);
	}

	@Test
	@DisplayName("항목이 하나면 배열이 아니라 객체로 온다 — 공공데이터 응답의 흔한 모양이다")
	void singleItemComesAsObject() throws IOException {
		String raw = "{\"response\":{\"body\":{\"items\":{\"item\":"
				+ item("3", "38", "A04", "A04010200", "국제시장", "부산 중구", "129.03", "35.10") + "}}}}";
		Path file = write("{\"stage\":\"list\",\"raw\":" + quote(raw) + "}");

		TourApiPlaceReader.Loaded loaded = TourApiPlaceReader.read(file);

		assertThat(loaded.rows()).extracting(TourApiPlaceRow::title).containsExactly("국제시장");
	}

	private Path write(String... lines) throws IOException {
		Path file = this.dir.resolve("tourapi.ndjson");
		Files.write(file, List.of(lines), StandardCharsets.UTF_8);
		return file;
	}

	/** 🔴 실제 파일과 같은 모양 — {@code raw} 가 <b>문자열</b>이다. */
	private static String listLine(String... items) {
		String raw = "{\"response\":{\"body\":{\"items\":{\"item\":[" + String.join(",", items) + "]}}}}";
		return "{\"stage\":\"list\",\"contentTypeId\":12,\"page\":1,\"raw\":" + quote(raw) + "}";
	}

	private static String item(String contentId, String contentTypeId, String cat1, String cat3,
			String title, String addr1, String mapx, String mapy) {
		StringBuilder out = new StringBuilder("{");
		out.append("\"contentid\":\"").append(contentId).append('"');
		out.append(",\"contenttypeid\":\"").append(contentTypeId).append('"');
		out.append(",\"cat1\":\"").append(cat1).append('"');
		out.append(",\"cat3\":\"").append(cat3).append('"');
		out.append(",\"title\":\"").append(title).append('"');
		out.append(",\"addr1\":\"").append(addr1).append('"');
		if (mapx != null) {
			out.append(",\"mapx\":\"").append(mapx).append('"');
		}
		if (mapy != null) {
			out.append(",\"mapy\":\"").append(mapy).append('"');
		}
		return out.append('}').toString();
	}

	/** JSON 문자열 안에 JSON 을 넣는다 — 수집기가 원문을 그대로 보관하는 방식 그대로다. */
	private static String quote(String value) {
		return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
	}
}
