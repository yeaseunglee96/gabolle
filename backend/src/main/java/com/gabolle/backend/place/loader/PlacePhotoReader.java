package com.gabolle.backend.place.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.gabolle.backend.place.domain.Place.PhotoSubject;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 사진 수집본(NDJSON)을 읽는다.
 *
 * <p>수집기가 남기는 {@code matchedBy} 는 사진을 왜 붙였는지이지 무엇을 찍었는지가 아니다.
 * 이름이 겹쳐 붙은 행사장 사진이 그대로 「축제 사진」으로 나가므로, 여기서 사진 자체의 제목
 * ({@code galTitle})과 장소 이름을 직접 대조해 정한다. 수집기가 분류를 고쳐도 이 판정은 같은
 * 답을 낸다.
 */
final class PlacePhotoReader {

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
			String photoUrl = text(node, "photoUrl");
			if (contentId == null || photoUrl == null) {
				// 조용히 건너뛰지 않는다. 사진 한 장이 빠진 것이 "이 축제는 원래 사진이 없다"
				// 와 구분되지 않는다.
				throw new IllegalStateException(
						"사진 수집본 %d 번째 줄에 contentid 또는 photoUrl 이 없다: %s".formatted(lineNumber, line));
			}

			// 평문 http 는 앱·iOS·웹 어디에서도 안 보인다. 판정은 PhotoUrlScheme 한 곳에만
			// 둔다 — TourApiPlaceLoader 도 같은 것을 부른다.
			rows.add(new PlacePhotoRow(contentId, PhotoUrlScheme.secure(photoUrl),
					attributionOf(node), subjectOf(node)));
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
	 * 맞추면 행사장 사진이 다시 {@code SELF} 가 된다. 애매하면 {@code VENUE} 다. 축제 사진이 아닌
	 * 것을 축제 사진이라 말하는 쪽이 반대보다 나쁘다.
	 */
	private PhotoSubject subjectOf(JsonNode node) {
		String galTitle = text(node, "galTitle");
		String title = text(node, "title");
		if (galTitle == null || title == null) {
			return PhotoSubject.VENUE;
		}
		return normalize(galTitle).equals(normalize(title)) ? PhotoSubject.SELF : PhotoSubject.VENUE;
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
