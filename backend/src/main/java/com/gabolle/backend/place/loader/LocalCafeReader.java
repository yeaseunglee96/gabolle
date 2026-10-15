package com.gabolle.backend.place.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.gabolle.backend.place.domain.CertifiedRestaurant;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카페 NDJSON(<b>한 줄에 JSON 하나</b>인 글 파일)을 읽는다 (S15P21E201-2059). 한 줄 모양은
 *
 * <pre>
 * {"sourceId":"KTO2841481","name":"공극샌드커피","lat":35.1845252618,"lng":129.2131276086,
 *  "address":"부산광역시 기장군 기장읍 공수해안길 41","tel":null,
 *  "photoUrl":"http://tong.visitkorea.or.kr/cms/resource/76/2841476_image2_1.jpg","photoSource":"한국관광공사 공공누리 제3유형",
 *  "sources":["한국관광공사"],"existingPlaceId":null,"existingCategory":null,"gu":"기장군","dong":"기장읍"}
 * {"sourceId":"RT1192","name":"백구당", …, "photoUrl":null,"photoSource":null,"sources":["레드테이블(방송·수상)"],
 *  "intro":"…","existingPlaceId":"fe8f096a-…","existingCategory":"CAFE_HEALING","gu":"중구","dong":"중앙동4가"}
 * </pre>
 *
 * <p>{@link CertifiedRestaurantReader} 와 같은 생각이다 — <b>모르는 칸이 오면 멈추고</b>, 틀린 줄이 하나라도 있으면 줄 번호와
 * 함께 멈춘다. 파일 전체를 먼저 검사하므로 한 줄이라도 틀리면 한 행도 안 들어간다. 좌표 범위·구/동 열쇠 모양·주소의 구와
 * 좌표의 구가 다른 줄을 건너뛰는 규칙도 그쪽과 같은 것을 쓴다(그쪽 도우미를 그대로 부른다). 다만 번호가 겹치는 줄은 맛집 파일처럼
 * 「더 맞는 줄」을 고르지 않고 <b>멈춘다</b> — 카페 파일은 번호 하나에 줄 하나로 만든 파일이라 겹치면 만든 쪽의 실수다.
 *
 * <p>🔴 <b>레드테이블 사진은 읽는 순간 멈춘다.</b> 사진({@code photoUrl})은 관광공사 줄({@code KTO…})에 한국관광공사 출처 문구와
 * 짝으로만 올 수 있다. 레드테이블 사진은 다시 써도 되는지 이용 조건을 확인하지 못했고, 식당이 직접 찍은 사진일 수 있어
 * 조건을 모르고 쓰면 안 된다({@link RedtablePlaceLoader} 설명). 수집 파일은 이미 비워 두었지만 사람이 다시 만들다 섞을 수 있어
 * 여기서 한 번 더 막는다.
 */
public final class LocalCafeReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	static final Set<String> FIELDS = Set.of("sourceId", "name", "lat", "lng", "address", "tel", "photoUrl", "photoSource",
			"sources", "intro", "existingPlaceId", "existingCategory", "gu", "dong");

	/** 번호 모양 — {@code KTO} 또는 {@code RT} 뒤에 숫자. 새 장소의 출처와 번호를 이 모양에서 가른다. */
	private static final Pattern SOURCE_ID = Pattern.compile("^(KTO|RT)[0-9]+$");

	/** 사진 출처 문구가 이것으로 시작해야 한다 — 관광공사 사진만 쓴다. */
	private static final String KTO_PHOTO_SOURCE_PREFIX = "한국관광공사";

	/** {@code place.category} 칸 길이. */
	private static final int CATEGORY_MAX = 50;

	private LocalCafeReader() {
	}

	/**
	 * 읽은 결과. {@code rows} 는 넣을 줄(파일 순서)이고, {@code skipped} 는 읽고도 안 넣은 줄이다.
	 *
	 * @param linesRead 빈 줄을 뺀 읽은 줄 수 — {@code rows + skipped} 와 같다
	 */
	public record Result(List<LocalCafeRow> rows, int linesRead, List<CertifiedRestaurantReader.Skipped> skipped) {

		public Result {
			rows = List.copyOf(rows);
			skipped = List.copyOf(skipped);
		}

		public long skippedCount(CertifiedRestaurantReader.Reason reason) {
			return this.skipped.stream().filter(s -> s.reason() == reason).count();
		}
	}

	public static Result read(Path file) {
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		List<LocalCafeRow> rows = new ArrayList<>();
		List<CertifiedRestaurantReader.Skipped> skipped = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int linesRead = 0;
		for (int i = 0; i < lines.size(); i++) {
			String text = lines.get(i);
			if (text.isBlank()) {
				continue;
			}
			linesRead++;
			int number = i + 1;
			try {
				JsonNode node = MAPPER.readTree(text);
				checkShape(node);
				String sourceId = CertifiedRestaurantReader.required(node, "sourceId");
				if (!seen.add(sourceId)) {
					throw new IllegalArgumentException("같은 sourceId 가 또 나왔다 — " + sourceId);
				}
				CertifiedRestaurantReader.Skipped unknownArea = unknownAreaOf(number, node, sourceId);
				if (unknownArea != null) {
					skipped.add(unknownArea);
					continue;
				}
				LocalCafeRow row = parse(node);
				String addressGu = CertifiedRestaurantReader.addressGu(row.address());
				if (addressGu != null && !addressGu.equals(row.gu())) {
					skipped.add(new CertifiedRestaurantReader.Skipped(number, row.sourceId(), row.name(),
							CertifiedRestaurantReader.Reason.ADDRESS_GU_MISMATCH,
							"주소는 「%s」인데 좌표로 얻은 구는 「%s %s」다 — 좌표가 어긋난 것으로 보고 안 넣는다".formatted(addressGu,
									row.gu(), row.dong())));
					continue;
				}
				rows.add(row);
			}
			catch (RuntimeException exception) {
				throw new IllegalArgumentException(number + "번째 줄: " + exception.getMessage(), exception);
			}
		}
		return new Result(rows, linesRead, skipped);
	}

	private static void checkShape(JsonNode node) {
		if (!node.isObject()) {
			throw new IllegalArgumentException("줄이 JSON 객체가 아니다");
		}
		for (Map.Entry<String, JsonNode> entry : node.properties()) {
			if (!FIELDS.contains(entry.getKey())) {
				throw new IllegalArgumentException("모르는 칸이 있다: " + entry.getKey() + " — 받는 것은 " + FIELDS);
			}
		}
	}

	/**
	 * 구·동을 모르는 줄이면 건너뛴 줄 정보를, 아니면 {@code null}. 좌표 검사보다 먼저 부른다 — 구를 모르는 줄은 좌표를 역지오코딩하지
	 * 못한 줄이라 좌표가 틀려 있을 수 있고, 검사하면 건너뛸 줄에서 멈춰 버린다.
	 */
	private static CertifiedRestaurantReader.Skipped unknownAreaOf(int lineNumber, JsonNode node, String sourceId) {
		String name = CertifiedRestaurantReader.required(node, "name");
		String gu = CertifiedRestaurantReader.optional(node, "gu");
		String dong = CertifiedRestaurantReader.optional(node, "dong");
		boolean known = gu != null && !CertifiedRestaurantReader.UNKNOWN_AREA.equals(gu) && dong != null
				&& !CertifiedRestaurantReader.UNKNOWN_AREA.equals(dong);
		return known ? null
				: new CertifiedRestaurantReader.Skipped(lineNumber, sourceId, name,
						CertifiedRestaurantReader.Reason.UNKNOWN_AREA, "구·동을 모른다(gu=%s, dong=%s)".formatted(gu, dong));
	}

	/** 한 줄을 읽는다. 구·동을 아는 줄만 온다. */
	static LocalCafeRow parse(JsonNode node) {
		checkShape(node);
		String sourceId = CertifiedRestaurantReader.required(node, "sourceId");
		if (!SOURCE_ID.matcher(sourceId).matches()) {
			throw new IllegalArgumentException(
					"sourceId 는 KTO 또는 RT 로 시작하고 숫자가 이어져야 한다(출처를 이 앞머리에서 가른다): " + sourceId);
		}
		String name = CertifiedRestaurantReader.required(node, "name");
		double lat = CertifiedRestaurantReader.number(node, "lat");
		double lng = CertifiedRestaurantReader.number(node, "lng");
		if (lat < CertifiedRestaurantReader.LAT_MIN || lat > CertifiedRestaurantReader.LAT_MAX
				|| lng < CertifiedRestaurantReader.LNG_MIN || lng > CertifiedRestaurantReader.LNG_MAX) {
			throw new IllegalArgumentException("좌표가 부산 밖이다 (위경도를 뒤바꿨나?): " + lat + ", " + lng);
		}
		String gu = CertifiedRestaurantReader.required(node, "gu");
		String dong = CertifiedRestaurantReader.required(node, "dong");
		if (CertifiedRestaurant.areaKey(gu, dong).length() > CertifiedRestaurant.KEY_MAX) {
			throw new IllegalArgumentException("구/동 열쇠가 너무 길다(" + CertifiedRestaurant.KEY_MAX + "자까지): " + gu + "/" + dong);
		}
		if (gu.indexOf('/') >= 0 || dong.indexOf('/') >= 0) {
			throw new IllegalArgumentException("구·동 이름에 '/' 가 들어 있다 — 열쇠가 구와 동을 '/' 로 나눈다: " + gu + ", " + dong);
		}

		Set<LocalCafeRow.Origin> origins = parseOrigins(node, sourceId);
		UUID existing = parseExisting(node);
		String existingCategory = CertifiedRestaurantReader.optional(node, "existingCategory");
		if (existingCategory != null && existing == null) {
			throw new IllegalArgumentException("existingCategory 가 있는데 existingPlaceId 가 없다 — 이미 있는 장소의 갈래를 말하는 칸이다");
		}
		if (existingCategory != null && existingCategory.length() > CATEGORY_MAX) {
			throw new IllegalArgumentException("existingCategory 가 너무 길다(" + CATEGORY_MAX + "자까지): " + existingCategory);
		}

		String photoUrl = CertifiedRestaurantReader.optional(node, "photoUrl");
		String photoSource = CertifiedRestaurantReader.optional(node, "photoSource");
		checkPhoto(sourceId, photoUrl, photoSource);

		return new LocalCafeRow(sourceId, name, lat, lng, CertifiedRestaurantReader.optional(node, "address"),
				CertifiedRestaurantReader.optional(node, "tel"), gu, dong, new ArrayList<>(origins),
				CertifiedRestaurantReader.optional(node, "intro"), existing, existingCategory, photoUrl, photoSource);
	}

	/** {@code sources} 를 읽고, 번호 앞머리가 말하는 출처가 그 안에 있는지 본다. */
	private static Set<LocalCafeRow.Origin> parseOrigins(JsonNode node, String sourceId) {
		JsonNode sourcesNode = node.path("sources");
		if (!sourcesNode.isArray() || sourcesNode.isEmpty()) {
			throw new IllegalArgumentException("sources 는 비지 않은 배열이어야 한다: " + sourcesNode);
		}
		Set<LocalCafeRow.Origin> origins = new LinkedHashSet<>();
		for (JsonNode element : sourcesNode) {
			if (!element.isString()) {
				throw new IllegalArgumentException("sources 의 원소는 글자여야 한다: " + element);
			}
			origins.add(LocalCafeRow.Origin.fromFileLabel(element.asString()).orElseThrow(
					() -> new IllegalArgumentException("모르는 목록이다: " + element.asString() + " — 아는 것은 "
							+ Arrays.stream(LocalCafeRow.Origin.values()).map(LocalCafeRow.Origin::fileLabel).toList())));
		}
		// 번호의 앞머리가 새 장소의 출처를 정한다 — 소개한 목록과 어긋나면 엉뚱한 출처로 장소가 만들어진다.
		boolean kto = sourceId.startsWith(LocalCafeRow.KTO_PREFIX);
		boolean consistent = kto ? origins.contains(LocalCafeRow.Origin.KTO)
				: origins.stream().anyMatch(LocalCafeRow.Origin::isRedtable);
		if (!consistent) {
			throw new IllegalArgumentException("번호 " + sourceId + " 의 출처(" + (kto ? "한국관광공사" : "레드테이블")
					+ ")가 sources 에 없다: " + origins.stream().map(LocalCafeRow.Origin::fileLabel).toList());
		}
		return origins;
	}

	private static UUID parseExisting(JsonNode node) {
		String existingText = CertifiedRestaurantReader.optional(node, "existingPlaceId");
		if (existingText == null) {
			return null;
		}
		try {
			return UUID.fromString(existingText);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("existingPlaceId 가 UUID 가 아니다: " + existingText);
		}
	}

	/** 사진은 관광공사 줄에 관광공사 출처 문구와 짝으로만 온다. */
	private static void checkPhoto(String sourceId, String photoUrl, String photoSource) {
		if (photoUrl == null) {
			if (photoSource != null) {
				throw new IllegalArgumentException("photoSource 가 있는데 photoUrl 이 없다");
			}
			return;
		}
		if (!photoUrl.startsWith("http://") && !photoUrl.startsWith("https://")) {
			throw new IllegalArgumentException("photoUrl 은 http(s) 주소여야 한다: " + photoUrl);
		}
		if (photoSource == null) {
			// 출처 표기가 이용 조건이다 — 출처 없는 사진은 넣지 않는다(Place.attachPhoto 와 같은 규칙).
			throw new IllegalArgumentException("photoUrl 이 있는데 photoSource 가 없다 — 출처 없이 사진을 쓰지 않는다");
		}
		if (photoSource.length() > CertifiedRestaurantReader.PHOTO_SOURCE_MAX) {
			throw new IllegalArgumentException(
					"photoSource 가 너무 길다(" + CertifiedRestaurantReader.PHOTO_SOURCE_MAX + "자까지): " + photoSource);
		}
		if (!sourceId.startsWith(LocalCafeRow.KTO_PREFIX) || !photoSource.startsWith(KTO_PHOTO_SOURCE_PREFIX)) {
			throw new IllegalArgumentException("사진은 한국관광공사 줄(KTO…)에 한국관광공사 출처 문구와 함께만 쓴다 — 레드테이블 사진은 이용 조건을 "
					+ "확인하지 못해 쓰지 않는다. photoUrl·photoSource 를 비워라: " + sourceId + " " + photoSource);
		}
	}
}
