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
 * 사진 수집본(NDJSON)을 읽는다 — S15P21E201-1006.
 *
 * <h2>🔴 파일에 적힌 분류를 믿지 않는다</h2>
 *
 * 수집기는 사진을 <b>왜 붙였는지</b>를 {@code matchedBy}(name·landmark)로 남긴다. 그것을
 * 그대로 "무엇을 찍었나" 로 쓰면 안 된다 — <b>둘은 다른 질문</b>이다.
 *
 * <p>2026-09-16 실측이 그 차이를 보여 준다. {@code matchedBy=name} 인 셋 중 <b>둘이 행사장
 * 사진</b>이었다.
 *
 * <pre>
 * 부산불꽃축제                 → 사진 "부산불꽃축제"      같음   ← 축제를 찍은 사진
 * 광안리 M(Marvelous) 드론쇼   → 사진 "광안리해수욕장"    다름   ← 행사장 사진
 * 해운대 빛축제                → 사진 "해운대해수욕장"    다름   ← 행사장 사진
 * </pre>
 *
 * 「광안리」가 축제 이름과 사진 이름 양쪽에 들어 있어서 {@code name} 으로 붙은 것이다.
 * 그대로 실었으면 <b>두 건에 「축제 사진」이라고 거짓 표시</b>가 나갔다.
 *
 * <p>그래서 여기서 <b>사진 자체의 제목({@code galTitle})과 장소 이름을 직접 대조</b>해 정한다.
 * 그것이 원천이 스스로 말하는 "이 사진이 무엇인가" 이고, 우리가 지어내는 값이 아니다.
 * 수집기가 분류를 고치더라도 이 판정은 <b>그것과 무관하게</b> 같은 답을 낸다.
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
				// 🔴 조용히 건너뛰지 않는다. 사진 한 장이 빠진 것을 아무도 모르는 상태가
				//    "이 축제는 원래 사진이 없다" 와 구분되지 않는다.
				throw new IllegalStateException(
						"사진 수집본 %d 번째 줄에 contentid 또는 photoUrl 이 없다: %s".formatted(lineNumber, line));
			}

			// 🔴 S15P21E201-1185 — 평문 http 는 앱·iOS·웹 어디에서도 안 보인다.
			//    판정은 PhotoUrlScheme 한 곳에만 둔다 — TourApiPlaceLoader 도 같은 것을 부른다.
			rows.add(new PlacePhotoRow(contentId, PhotoUrlScheme.secure(photoUrl),
					attributionOf(node), subjectOf(node)));
		}
		return rows;
	}

	/**
	 * 화면에 그대로 나갈 출처 표기 문구.
	 *
	 * <p>🔴 촬영자를 넣는다. 이 칸이 있는 이유가 <b>저작권 표기 없이 남의 사진을 쓰지 않기
	 * 위해</b>서다({@code place.photo_source} 주석). 출처만 적고 찍은 사람을 빼면 반쪽이다.
	 *
	 * <p>🔴 <b>「무엇을 찍었나」는 여기 안 적는다.</b> 그것은 {@link PhotoSubject} 가 값으로
	 * 갖는다 — 문장에 섞으면 화면이 그것을 읽어 판단할 수 없다.
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
	 * 사진 자체의 제목이 장소 이름과 같은가.
	 *
	 * <p>공백 차이만 무시한다. 그 이상으로 느슨하게 맞추면 — 예를 들어 「포함하면 같다」로
	 * 하면 — 위 javadoc 의 「광안리 드론쇼 ⊃ 광안리」 같은 것이 다시 {@code SELF} 가 된다.
     * <b>애매하면 {@code VENUE}</b> 다. 축제 사진이 아닌 것을 축제 사진이라 말하는 쪽이
	 * 반대보다 나쁘다.
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
