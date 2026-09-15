package com.gabolle.backend.place.loader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gabolle.backend.place.domain.Place.PhotoSubject;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S15P21E201-1006 — 사진이 «무엇을 찍은 것인가» 를 옳게 정하는지.
 *
 * <p>🔴 여기 쓰는 줄은 <b>실제 수집본에서 그대로 가져온 것</b>이다
 * ({@code bigData/data/staged/festival-photos-busan.ndjson}). 지어낸 예로 검사하면
 * «수집기가 실제로 내는 모양» 과 어긋나도 초록이 된다.
 *
 * <h2>🔴 {@link #nameMatchedButDifferentTitleIsVenue} 는 지금 수집본에는 없는 모양이다</h2>
 *
 * 2026-09-16 오전 수집본에서는 {@code matchedBy=name} 인 셋 중 <b>둘</b>이 행사장 사진이었다.
 * 같은 날 오후 수집기가 고쳐져 지금은 <b>그런 줄이 0 건</b>이다(34 줄 · 축제 사진 1 ·
 * 행사장 사진 33, 직접 세어 확인).
 *
 * <p><b>그래도 그 검사를 지우지 않는다.</b> 우리 판정이 수집기의 분류에 기대지 않는다는 것이
 * 그 검사의 요점이고, 수집기가 다시 틀렸을 때 이쪽에서 걸려야 한다 — 실제로 오늘 그렇게
 * 걸렸다. 낡은 것은 <b>그 모양이 지금도 파일에 있다</b> 는 사실뿐이라, 지우는 대신 여기에
 * 적어 둔다.
 */
class PlacePhotoReaderTest {

	@TempDir
	Path dir;

	private List<PlacePhotoRow> read(String... lines) throws Exception {
		Path file = this.dir.resolve("photos.ndjson");
		Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
		return new PlacePhotoReader(JsonMapper.builder().build()).read(file);
	}

	// ── 실제 수집본에서 가져온 세 줄 ──────────────────────────────────────────

	/** 사진 제목이 축제 이름과 같다 — 실제로 그 축제를 찍은 사진. 34건 중 이것 하나뿐이다. */
	private static final String REAL_EVENT_PHOTO = """
			{"contentid":"2757010","title":"부산불꽃축제","photoUrl":"https://tong.visitkorea.or.kr/a.jpg",\
			"photoSource":"한국관광공사 관광사진갤러리","photographer":"IR 스튜디오","matchedBy":"name",\
			"galTitle":"부산불꽃축제"}""";

	/** 🔴 matchedBy 는 name 인데 사진은 해수욕장이다. 「광안리」가 양쪽에 있어서 붙은 것이다. */
	private static final String NAME_MATCHED_BUT_VENUE = """
			{"contentid":"2786391","title":"광안리 M(Marvelous) 드론 라이트쇼",\
			"photoUrl":"https://tong.visitkorea.or.kr/b.jpg","photoSource":"한국관광공사 관광사진갤러리",\
			"photographer":"디자인글꼴","matchedBy":"name","galTitle":"광안리해수욕장"}""";

	private static final String LANDMARK_MATCHED = """
			{"contentid":"506545","title":"광안리어방축제","photoUrl":"https://tong.visitkorea.or.kr/c.jpg",\
			"photoSource":"한국관광공사 관광사진갤러리","photographer":"라이브스튜디오","matchedBy":"landmark",\
			"landmark":"광안리","landmarkFrom":"광안해변로","galTitle":"광안리해수욕장"}""";

	@Test
	@DisplayName("사진 제목이 축제 이름과 같으면 그 축제를 찍은 사진이다")
	void sameTitleIsSelf() throws Exception {
		assertThat(read(REAL_EVENT_PHOTO)).singleElement()
				.extracting(PlacePhotoRow::subject).isEqualTo(PhotoSubject.SELF);
	}

	/**
	 * 🔴 이 검사가 이 작업의 핵심이다. 수집기가 붙인 {@code matchedBy=name} 을 그대로
	 * 믿었다면 <b>「이 축제가 이렇게 생겼구나」로 읽히는 거짓 표시</b>가 나갔다.
	 *
	 * <p>원인은 수집기가 사진의 <b>키워드</b>를 제목처럼 읽은 것이었다 — 광안리해수욕장
	 * 사진의 키워드에 「광안리 M 드론라이트쇼」가 들어 있었다. 키워드는 «이 사진이
	 * 무엇이냐» 가 아니라 «무엇과 관련 있냐» 다. 바다 사진에 축제 태그가 붙은 것은 거기서
	 * 그 행사가 열리기 때문이지 행사를 찍어서가 아니다.
	 *
	 * <p>수집기는 고쳐졌다(2026-09-16 오후). 이 검사는 <b>우리가 수집기를 안 믿는다</b> 는
	 * 것을 지키기 위해 남는다 — 클래스 javadoc 참고.
	 */
	@Test
	@DisplayName("🔴 matchedBy 가 name 이어도 사진 제목이 다르면 행사장 사진이다")
	void nameMatchedButDifferentTitleIsVenue() throws Exception {
		assertThat(read(NAME_MATCHED_BUT_VENUE)).singleElement()
				.extracting(PlacePhotoRow::subject).isEqualTo(PhotoSubject.VENUE);
	}

	@Test
	@DisplayName("landmark 로 붙은 것도 행사장 사진이다")
	void landmarkMatchedIsVenue() throws Exception {
		assertThat(read(LANDMARK_MATCHED)).singleElement()
				.extracting(PlacePhotoRow::subject).isEqualTo(PhotoSubject.VENUE);
	}

	@Test
	@DisplayName("🔴 사진 제목을 모르면 행사장 사진으로 본다 — 애매하면 축제 사진이라 하지 않는다")
	void unknownTitleIsVenue() throws Exception {
		String noGalTitle = """
				{"contentid":"1","title":"어떤축제","photoUrl":"https://x/y.jpg",\
				"photoSource":"한국관광공사 관광사진갤러리"}""";

		assertThat(read(noGalTitle)).singleElement()
				.extracting(PlacePhotoRow::subject).isEqualTo(PhotoSubject.VENUE);
	}

	// ── 출처 표기 ────────────────────────────────────────────────────────────

	@Test
	@DisplayName("출처에 촬영자를 함께 적는다 — 출처 표기 없이 남의 사진을 쓰지 않는다")
	void attributionCarriesPhotographer() throws Exception {
		assertThat(read(LANDMARK_MATCHED)).singleElement()
				.extracting(PlacePhotoRow::attribution)
				.isEqualTo("한국관광공사 관광사진갤러리 · 촬영 라이브스튜디오");
	}

	@Test
	@DisplayName("촬영자가 없으면 출처만 적는다")
	void attributionWithoutPhotographer() throws Exception {
		String noPhotographer = """
				{"contentid":"1","title":"축제","photoUrl":"https://x/y.jpg",\
				"photoSource":"한국관광공사 관광사진갤러리","galTitle":"축제"}""";

		assertThat(read(noPhotographer)).singleElement()
				.extracting(PlacePhotoRow::attribution).isEqualTo("한국관광공사 관광사진갤러리");
	}

	@Test
	@DisplayName("🔴 출처가 없으면 읽기를 멈춘다 — 출처 없는 사진을 넣지 않는다")
	void missingSourceIsRejected() {
		String noSource = """
				{"contentid":"1","title":"축제","photoUrl":"https://x/y.jpg","galTitle":"축제"}""";

		assertThatThrownBy(() -> read(noSource))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("photoSource");
	}

	/**
	 * 🔴 조용히 건너뛰면 «사진 한 장이 빠진 것»과 «이 축제는 원래 사진이 없다»가 구분되지
	 * 않는다. 줄이 깨졌으면 멈추고 사람이 본다.
	 */
	@Test
	@DisplayName("🔴 사진 주소가 없는 줄은 조용히 건너뛰지 않고 멈춘다")
	void missingPhotoUrlStops() {
		String noUrl = """
				{"contentid":"1","title":"축제","photoSource":"한국관광공사 관광사진갤러리"}""";

		assertThatThrownBy(() -> read(noUrl))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("photoUrl");
	}

	@Test
	@DisplayName("빈 줄은 건너뛴다")
	void blankLinesAreSkipped() throws Exception {
		assertThat(read(REAL_EVENT_PHOTO, "", LANDMARK_MATCHED)).hasSize(2);
	}
}
