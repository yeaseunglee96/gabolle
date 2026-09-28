package com.gabolle.backend.place.loader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gabolle.backend.place.domain.Place.PhotoLicense;
import com.gabolle.backend.place.domain.Place.PhotoSubject;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 여기 쓰는 줄은 실제 수집본에서 그대로 가져온 것이다. 지어낸 예로 검사하면 수집기가 실제로
 * 내는 모양과 어긋나도 초록이 된다.
 *
 * <p>{@link #nameMatchedButDifferentTitleIsVenue} 가 쓰는 모양은 지금 수집본에 없다. 그래도
 * 지우지 않는다 — 우리 판정이 수집기의 분류에 기대지 않는다는 것이 그 검사의 요점이고,
 * 수집기가 다시 틀렸을 때 이쪽에서 걸려야 한다.
 */
class PlacePhotoReaderTest {

	@TempDir
	Path dir;

	private List<PlacePhotoRow> read(String... lines) throws Exception {
		Path file = this.dir.resolve("photos.ndjson");
		Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
		return new PlacePhotoReader(JsonMapper.builder().build()).read(file);
	}

	/** 사진 제목이 축제 이름과 같다 — 실제로 그 축제를 찍은 사진. */
	private static final String REAL_EVENT_PHOTO = """
			{"contentid":"2757010","title":"부산불꽃축제","photoUrl":"https://tong.visitkorea.or.kr/a.jpg",\
			"photoSource":"한국관광공사 관광사진갤러리","photographer":"IR 스튜디오","matchedBy":"name",\
			"galTitle":"부산불꽃축제"}""";

	/** matchedBy 는 name 인데 사진은 해수욕장이다. 「광안리」가 양쪽에 있어서 붙은 것이다. */
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
	 * 수집기의 {@code matchedBy=name} 을 그대로 믿으면 행사장 사진에 「축제 사진」이라는 거짓
	 * 표시가 나간다. 사진의 키워드는 「이 사진이 무엇이냐」가 아니라 「무엇과 관련 있냐」다.
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

	// ── 출처 열쇠 · 라이선스 · 항목 자신의 사진 (S15P21E201-1606) ─────────────────────────
	// 아래 줄은 bigData/research/photo-supply/out/ 의 수집본에서 가져왔다. 위키미디어 사진 주소 뒤의
	// 추적용 꼬리(?utm_…)와 부산항대교 줄의 되짚기용 칸 몇 개만 줄 길이 때문에 뺐다 — 적재기가 안 읽는 칸이다.

	/** OSM 장소에 이름이 같은 관광공사 항목의 대표사진을 붙인 줄 — contentid 가 없다. */
	private static final String OSM_WITH_TOURAPI_PHOTO = """
			{"sourceType":"OSM","sourceId":"12964466506","placeId":"2c730787-83d7-3d96-8400-2b09f20621be",\
			"title":"러브얼스","photoUrl":"http://tong.visitkorea.or.kr/cms/resource/15/2795715_image2_1.jpg",\
			"photoSource":"한국관광공사","category":"CAFE_HEALING","matchedBy":"tourapi","matchTier":2,\
			"sourceItemId":"2783646","sourceItemTitle":"러브얼스","distanceM":27,"cpyrhtDivCd":"Type3",\
			"evidence":"https://apis.data.go.kr/B551011/KorService2/areaBasedList2 (lDongRegnCd=26, contentid=2783646)",\
			"placeGu":"수영구","placeGuFrom":"nearby"}""";

	/** 관광공사 장소 자신의 추가사진. 운영에서 장소 번호가 계산 규칙과 다른 곳이다. */
	private static final String TOURAPI_EXTRA_IMAGE = """
			{"contentid":"2614712","title":"구상반려암 (부산 국가지질공원)",\
			"photoUrl":"https://tong.visitkorea.or.kr/cms/resource/11/2614711_image2_1.bmp","photoSource":"한국관광공사",\
			"category":"NATURE_WALK","matchedBy":"tourapi-detailImage2","matchTier":1,"cpyrhtDivCd":"Type3",\
			"imgname":"구상반려암","serialnum":"2614711_3",\
			"evidence":"https://apis.data.go.kr/B551011/KorService2/detailImage2 (contentId=2614712)"}""";

	private static final String WIKIMEDIA_CC_BY_SA = """
			{"sourceType":"OSM","sourceId":"368871416","placeId":"639e6f59-e97e-37a2-b978-aa6a8f42f654",\
			"title":"부산근대역사관","photoUrl":"https://upload.wikimedia.org/wikipedia/commons/8/83/Busan_Modern_History_Museum-01.jpg",\
			"photoSource":"Wikimedia Commons","photographer":"桂鷺淵 / Katsura Roen","category":"CULTURE_TEMPLE",\
			"matchedBy":"wikimedia","matchTier":3,"sourceItemId":"Q11246045","sourceItemTitle":"부산근대역사관",\
			"distanceM":28,"license":"CC BY-SA 3.0","licenseUrl":"https://creativecommons.org/licenses/by-sa/3.0",\
			"filePage":"https://commons.wikimedia.org/wiki/File:Busan_Modern_History_Museum-01.jpg",\
			"evidence":"https://commons.wikimedia.org/wiki/File:Busan_Modern_History_Museum-01.jpg",\
			"placeGu":"중구","placeGuFrom":"nearby","alsoMatched":["wikimedia:4","gallery:5"]}""";

	/** 퍼블릭 도메인은 라이선스 주소가 없다 — 수집본에 {@code null} 로 온다. */
	private static final String WIKIMEDIA_PUBLIC_DOMAIN = """
			{"sourceType":"OSM","sourceId":"7241587087","title":"부산항대교",\
			"photoUrl":"https://thumb.wikimedia.org/wikipedia/commons/thumb/7/73/Busan_Harbor_Bridge2.jpg/1280px-Busan_Harbor_Bridge2.jpg",\
			"photoSource":"Wikimedia Commons","photographer":"Glabb This photo was taken with DJI FC7203",\
			"matchedBy":"wikimedia","matchTier":3,"license":"Public domain","licenseUrl":null,\
			"filePage":"https://commons.wikimedia.org/wiki/File:Busan_Harbor_Bridge2.jpg"}""";

	@Test
	@DisplayName("🔴 contentid 가 없어도 sourceType+sourceId 로 읽는다 — 상가·OSM·카카오 장소의 사진")
	void sourceKeyWithoutContentId() throws Exception {
		PlacePhotoRow row = read(OSM_WITH_TOURAPI_PHOTO).get(0);

		assertThat(row.sourceType()).isEqualTo("OSM");
		assertThat(row.sourceId()).isEqualTo("12964466506");
		assertThat(row.photoUrl()).startsWith("https://");
		assertThat(row.attribution()).isEqualTo("한국관광공사");
		assertThat(row.license()).isNull();
	}

	@Test
	@DisplayName("contentid 가 있으면 관광공사 장소다")
	void contentIdMeansTourApi() throws Exception {
		PlacePhotoRow row = read(TOURAPI_EXTRA_IMAGE).get(0);

		assertThat(row.sourceType()).isEqualTo("TOURAPI");
		assertThat(row.sourceId()).isEqualTo("2614712");
	}

	/**
	 * 제목 칸이 없는 사진을 전부 {@code VENUE} 로 두면 식당·다리 사진에 「행사장 사진」 딱지가 붙는다.
	 * 이 셋은 짝지은 항목 <b>자신의</b> 대표·추가 사진이고 그 항목이 곧 이 장소다.
	 */
	@Test
	@DisplayName("🔴 항목 자신의 사진(관광공사 대표·추가, 위키미디어)은 이 장소를 찍은 사진이다")
	void ownPhotosAreSelf() throws Exception {
		assertThat(read(OSM_WITH_TOURAPI_PHOTO, TOURAPI_EXTRA_IMAGE, WIKIMEDIA_CC_BY_SA))
				.extracting(PlacePhotoRow::subject)
				.containsOnly(PhotoSubject.SELF);
	}

	@Test
	@DisplayName("🔴 사진 제목이 있으면 matchedBy 보다 제목 대조가 먼저다 — 갤러리 사진은 지금처럼 판정한다")
	void galTitleStillDecidesBeforeMatchedBy() throws Exception {
		String galleryVenue = """
				{"contentid":"1","title":"광안리어방축제","photoUrl":"https://x/y.jpg",\
				"photoSource":"한국관광공사 관광사진갤러리","matchedBy":"tourapi","galTitle":"광안리해수욕장"}""";

		assertThat(read(galleryVenue)).singleElement()
				.extracting(PlacePhotoRow::subject).isEqualTo(PhotoSubject.VENUE);
	}

	@Test
	@DisplayName("모르는 matchedBy 는 지금처럼 행사장 사진이다")
	void unknownMatchedByIsVenue() throws Exception {
		String unknown = """
				{"contentid":"1","title":"어떤축제","photoUrl":"https://x/y.jpg",\
				"photoSource":"한국관광공사","matchedBy":"name"}""";

		assertThat(read(unknown)).singleElement()
				.extracting(PlacePhotoRow::subject).isEqualTo(PhotoSubject.VENUE);
	}

	@Test
	@DisplayName("🔴 라이선스 이름·주소·원본 파일 페이지를 읽는다 — 출처 문구에는 촬영자만 붙는다")
	void licenseIsRead() throws Exception {
		PlacePhotoRow row = read(WIKIMEDIA_CC_BY_SA).get(0);

		assertThat(row.license()).isEqualTo(new PhotoLicense("CC BY-SA 3.0",
				"https://creativecommons.org/licenses/by-sa/3.0",
				"https://commons.wikimedia.org/wiki/File:Busan_Modern_History_Museum-01.jpg"));
		assertThat(row.attribution()).isEqualTo("Wikimedia Commons · 촬영 桂鷺淵 / Katsura Roen");
	}

	@Test
	@DisplayName("퍼블릭 도메인은 라이선스 주소 없이 들어간다")
	void publicDomainHasNoLicenseUrl() throws Exception {
		assertThat(read(WIKIMEDIA_PUBLIC_DOMAIN).get(0).license()).isEqualTo(new PhotoLicense("Public domain",
				null, "https://commons.wikimedia.org/wiki/File:Busan_Harbor_Bridge2.jpg"));
	}

	@Test
	@DisplayName("🔴 라이선스 이름 없이 주소만 있으면 멈춘다 — 무슨 라이선스인지 모르고 링크만 걸지 않는다")
	void licenseUrlWithoutNameStops() {
		String noName = """
				{"sourceType":"OSM","sourceId":"1","title":"다리","photoUrl":"https://x/y.jpg",\
				"photoSource":"Wikimedia Commons","licenseUrl":"https://creativecommons.org/licenses/by-sa/3.0"}""";

		assertThatThrownBy(() -> read(noName))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("license");
	}

	@Test
	@DisplayName("🔴 열쇠가 없는 줄은 멈춘다 — contentid 도 sourceType+sourceId 도 없다")
	void missingKeyStops() {
		String onlySourceId = """
				{"sourceId":"12964466506","title":"러브얼스","photoUrl":"https://x/y.jpg","photoSource":"한국관광공사"}""";

		assertThatThrownBy(() -> read(onlySourceId))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("열쇠");
	}
}
