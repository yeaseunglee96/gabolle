package com.gabolle.backend.place.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gabolle.backend.place.domain.Place.PhotoLicense;
import com.gabolle.backend.place.domain.Place.PhotoSubject;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 사진 수집본(NDJSON)을 읽는다.
 *
 * <p>열쇠 모양 둘을 다 읽는다 — {@code contentid} 가 있으면 관광공사, {@code sourceType}+{@code sourceId}
 * 가 있으면 그 출처다({@link PlaceFeatureNdjsonReader#readPlaceScores} 와 같다). 상가·OSM·카카오 장소의
 * 사진이 뒤에 붙는다(S15P21E201-1606).
 *
 * <p>옛 수집기가 남긴 {@code matchedBy}(name·landmark)는 사진을 왜 붙였는지이지 무엇을 찍었는지가
 * 아니다. 이름이 겹쳐 붙은 행사장 사진이 그대로 「축제 사진」으로 나가므로, 사진 제목({@code galTitle})이
 * 있으면 그것과 장소 이름을 직접 대조해 정한다. 제목이 없을 때만 {@link #OWN_PHOTO} 를 본다.
 */
final class PlacePhotoReader {

	/**
	 * {@code matchedBy} 중 「짝지은 항목 <b>자신의</b> 사진」을 뜻하는 값 — 관광공사 대표사진·추가사진,
	 * 위키데이터 항목의 사진. 그 항목이 곧 이 장소라서 {@code SELF} 다
	 * ({@code bigData/research/photo-supply/REPORT.md} 3.4절).
	 *
	 * <p>이것이 없으면 제목 칸이 없는 사진이 전부 {@code VENUE} 가 되어, 식당 사진에 「행사장 사진」
	 * 딱지가 붙는다. 모르는 값은 지금처럼 {@code VENUE} 다 — 옛 수집본의 {@code name}·{@code landmark}
	 * 는 여기 넣지 않는다.
	 */
	static final Set<String> OWN_PHOTO = Set.of("tourapi", "tourapi-detailImage2", "wikimedia");

	private final ObjectMapper objectMapper;

	PlacePhotoReader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	List<PlacePhotoRow> read(Path file) throws IOException {
		List<PlacePhotoRow> rows = new ArrayList<>();
		int lineNumber = 0;
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			lineNumber++;
			if (line.isBlank()) {
				continue;
			}
			JsonNode node = this.objectMapper.readTree(line);

			String contentId = text(node, "contentid");
			String sourceType = (contentId != null) ? TourApiPlaceLoader.SOURCE_TYPE : text(node, "sourceType");
			String sourceId = (contentId != null) ? contentId : text(node, "sourceId");
			String photoUrl = text(node, "photoUrl");
			if (sourceType == null || sourceId == null || photoUrl == null) {
				// 조용히 건너뛰지 않는다. 사진 한 장이 빠진 것이 "이 장소는 원래 사진이 없다"
				// 와 구분되지 않는다.
				throw new IllegalStateException(
						"사진 수집본 %d 번째 줄에 열쇠(contentid 또는 sourceType+sourceId) 또는 photoUrl 이 없다: %s"
								.formatted(lineNumber, line));
			}

			// 평문 http 는 앱·iOS·웹 어디에서도 안 보인다. 판정은 PhotoUrlScheme 한 곳에만
			// 둔다 — TourApiPlaceLoader 도 같은 것을 부른다.
			rows.add(new PlacePhotoRow(sourceType, sourceId, PhotoUrlScheme.secure(photoUrl),
					attributionOf(node), subjectOf(node), licenseOf(node)));
		}
		return rows;
	}

	/**
	 * 화면에 그대로 나갈 출처 표기 문구. 저작권 표기 없이 남의 사진을 쓰지 않으려는 칸이라
	 * 출처만 적고 촬영자를 빼면 반쪽이다. 「무엇을 찍었나」는 {@link PhotoSubject} 가 값으로
	 * 가지므로 여기 섞지 않는다 — 문장에 섞으면 화면이 읽어 판단할 수 없다.
	 */
	private String attributionOf(JsonNode node) {
		String source = text(node, "photoSource");
		if (source == null) {
			throw new IllegalStateException("photoSource 가 없다 — 출처 표기 없이 사진을 쓰지 않는다");
		}
		String photographer = text(node, "photographer");
		return (photographer == null) ? source : source + " · 촬영 " + photographer;
	}

	/**
	 * 사진 자체의 제목이 장소 이름과 같은가. 공백 차이만 무시한다 — 「포함하면 같다」처럼 느슨하게
	 * 맞추면 행사장 사진이 다시 {@code SELF} 가 된다. 제목이 없으면 {@link #OWN_PHOTO} 를 보고,
	 * 그것도 아니면 {@code VENUE} 다. 축제 사진이 아닌 것을 축제 사진이라 말하는 쪽이 반대보다 나쁘다.
	 */
	private PhotoSubject subjectOf(JsonNode node) {
		String galTitle = text(node, "galTitle");
		if (galTitle != null) {
			String title = text(node, "title");
			return (title != null && normalize(galTitle).equals(normalize(title))) ? PhotoSubject.SELF
					: PhotoSubject.VENUE;
		}
		String matchedBy = text(node, "matchedBy");
		return (matchedBy != null && OWN_PHOTO.contains(matchedBy)) ? PhotoSubject.SELF : PhotoSubject.VENUE;
	}

	/**
	 * 라이선스 이름·주소·원본 파일 페이지. 이름 없이 주소만 있는 줄은 멈춘다 — 무슨 라이선스인지
	 * 말하지 않고 링크만 거는 것은 표기가 아니다.
	 */
	private static PhotoLicense licenseOf(JsonNode node) {
		String name = text(node, "license");
		String url = text(node, "licenseUrl");
		String filePage = text(node, "filePage");
		if (name == null) {
			if (url != null || filePage != null) {
				throw new IllegalStateException("license 없이 licenseUrl·filePage 만 있다 — 라이선스 이름을 모른다");
			}
			return null;
		}
		return new PhotoLicense(name, url, filePage);
	}

	private static String normalize(String value) {
		return value.replaceAll("\\s+", "");
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.asString().trim();
		return text.isEmpty() ? null : text;
	}
}
