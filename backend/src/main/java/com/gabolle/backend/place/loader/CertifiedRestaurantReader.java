package com.gabolle.backend.place.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gabolle.backend.place.domain.CertifiedRestaurant;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공공기관 인증 식당 NDJSON(<b>한 줄에 JSON 하나</b>인 글 파일)을 읽는다 (S15P21E201-2051). 한 줄 모양은
 *
 * <pre>
 * {"sourceId":"BF70","name":"만드리곤드레밥","lat":35.177387,"lng":128.95245,"address":"강서구 공항앞길 85번길 13",
 *  "tel":"051-941-3669","gu":"강서구","dong":"대저2동","sources":["부산시 맛집정보"],"menu":"돌솥곤드레정식, 단호박오리훈제",
 *  "existingPlaceId":null,"photoUrl":"https://www.visitbusan.net/…","photoSource":"출처 : 부산광역시 부산맛집정보"}
 * </pre>
 *
 * <p>{@link RedtablePlaceReader} 와 같은 생각이다 — <b>모르는 칸이 오면 멈추고</b>, 틀린 줄이 하나라도 있으면 줄 번호와
 * 함께 멈춘다. 파일 전체를 먼저 검사하므로 한 줄이라도 틀리면 한 행도 안 들어간다. 다만 아래 셋은 멈추지 않고 <b>세어서
 * 돌려준다</b> — 데이터의 알려진 흠이라 파일을 고쳐 다시 받을 때까지 나머지를 막을 이유가 없다.
 *
 * <ol>
 *   <li><b>구·동을 모르는 줄</b> — {@code gu} 가 {@code "?"} 다. 좌표가 부산 안이 아니어서 역지오코딩이 실패한 줄이고
 *       (2026-10-13 파일의 「보느파티쓰 리」는 경도 칸에 위도가 한 번 더 적혀 있었다) 구를 모르면 「맛집」 화면에서 갈 곳이
 *       없다. 좌표 검사보다 <b>먼저</b> 건너뛴다 — 좌표가 틀린 줄이니 검사하면 멈춰 버린다</li>
 *   <li><b>같은 번호가 여러 줄</b> — 번호({@code sourceId}) 하나에 장소 하나다. 그런데 2026-10-13 파일은 같은 번호의
 *       줄이 <b>서로 다른 좌표·구·동</b>으로 겹쳤다(7개 번호, 15줄). 같은 이름의 다른 지점으로 짝지어졌기 때문이고,
 *       그 파일에서는 <b>먼저 나온 줄이 7번 모두 틀렸다</b>. 그래서 「첫 줄」이 아니라 「주소 글과 가장 잘 맞는 줄」을 고른다
 *       ({@link #consistency})</li>
 *   <li><b>주소의 구와 좌표의 구가 다른 줄</b> — 주소가 「연제구 …」인데 좌표로 얻은 구가 금정구다(큐제·초원복국).
 *       좌표가 몇 km 어긋난 것이라 그대로 넣으면 지도의 핀이 엉뚱한 곳에 찍힌다. 틀린 장소를 만드는 것보다 안 넣고 알리는
 *       편이 싸다. 주소의 첫머리에 구가 적혀 있을 때만 판단한다({@link #addressGu})</li>
 * </ol>
 */
public final class CertifiedRestaurantReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	static final Set<String> FIELDS = Set.of("sourceId", "name", "lat", "lng", "address", "tel", "gu", "dong", "sources",
			"menu", "existingPlaceId", "photoUrl", "photoSource");

	/** 수집 쪽이 구·동을 모를 때 적는 글자. */
	static final String UNKNOWN_AREA = "?";

	/** 부산을 넉넉히 덮는 상자. 위경도를 뒤바꿔 적은 줄을 여기서 잡는다. */
	static final double LAT_MIN = 34.8;

	static final double LAT_MAX = 35.5;

	static final double LNG_MIN = 128.7;

	static final double LNG_MAX = 129.4;

	/** {@code place.source_id} 와 {@code place.photo_source} 의 칸 길이. */
	static final int SOURCE_ID_MAX = 200;

	static final int PHOTO_SOURCE_MAX = 100;

	/** 부산의 열여섯 구·군 — 주소 첫머리가 이 중 하나면 그 주소의 구다. */
	private static final Set<String> BUSAN_GU = Set.of("중구", "서구", "동구", "영도구", "부산진구", "동래구", "남구", "북구",
			"해운대구", "사하구", "금정구", "강서구", "연제구", "수영구", "사상구", "기장군");

	private static final Set<String> CITY_PREFIXES = Set.of("부산광역시", "부산시", "부산");

	private CertifiedRestaurantReader() {
	}

	/** 건너뛴 이유. */
	public enum Reason {

		/** 구·동을 모른다 — 좌표를 역지오코딩하지 못한 줄. */
		UNKNOWN_AREA,

		/** 주소의 구와 좌표로 얻은 구가 다르다 — 좌표가 어긋난 것으로 본다. */
		ADDRESS_GU_MISMATCH
	}

	/** 건너뛴 줄 하나. 로그와 보고에 그대로 쓰므로 어느 줄인지 알 수 있게 담는다. */
	public record Skipped(int line, String sourceId, String name, Reason reason, String detail) {

		@Override
		public String toString() {
			return "%d번째 줄 %s(%s) — %s".formatted(this.line, this.name, this.sourceId, this.detail);
		}
	}

	/** 같은 번호의 줄 가운데 고르지 않은 줄. */
	public record DroppedDuplicate(int line, String sourceId, String name, String gu, String dong, String why) {

		@Override
		public String toString() {
			return "%d번째 줄 %s(%s) %s %s — %s".formatted(this.line, this.name, this.sourceId, this.gu, this.dong,
					this.why);
		}
	}

	/**
	 * 읽은 결과. {@code rows} 는 넣을 줄(파일 순서)이고, 나머지는 읽고도 안 넣은 줄이다.
	 *
	 * @param linesRead 빈 줄을 뺀 읽은 줄 수 — {@code rows + skipped + droppedDuplicates} 와 같다
	 */
	public record Result(List<CertifiedRestaurantRow> rows, int linesRead, List<Skipped> skipped,
			List<DroppedDuplicate> droppedDuplicates) {

		public Result {
			rows = List.copyOf(rows);
			skipped = List.copyOf(skipped);
			droppedDuplicates = List.copyOf(droppedDuplicates);
		}

		public long skippedCount(Reason reason) {
			return this.skipped.stream().filter(s -> s.reason() == reason).count();
		}
	}

	/** 줄 번호를 단 한 줄 — 같은 번호의 줄 가운데 어느 것이 먼저인지 알아야 해서 번호를 들고 다닌다. */
	private record Line(int number, CertifiedRestaurantRow row) {
	}

	public static Result read(Path file) {
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		List<Line> known = new ArrayList<>();
		List<Skipped> skipped = new ArrayList<>();
		int linesRead = 0;
		for (int i = 0; i < lines.size(); i++) {
			String text = lines.get(i);
			if (text.isBlank()) {
				continue;
			}
			linesRead++;
			try {
				JsonNode node = MAPPER.readTree(text);
				Skipped unknownArea = unknownAreaOf(i + 1, node);
				if (unknownArea != null) {
					skipped.add(unknownArea);
					continue;
				}
				known.add(new Line(i + 1, parse(node)));
			}
			catch (RuntimeException exception) {
				throw new IllegalArgumentException((i + 1) + "번째 줄: " + exception.getMessage(), exception);
			}
		}

		List<DroppedDuplicate> dropped = new ArrayList<>();
		List<Line> unique = pickOnePerSourceId(known, dropped);

		List<CertifiedRestaurantRow> rows = new ArrayList<>(unique.size());
		for (Line line : unique) {
			CertifiedRestaurantRow row = line.row();
			String addressGu = addressGu(row.address());
			if (addressGu != null && !addressGu.equals(row.gu())) {
				skipped.add(new Skipped(line.number(), row.sourceId(), row.name(), Reason.ADDRESS_GU_MISMATCH,
						"주소는 「%s」인데 좌표로 얻은 구는 「%s %s」다 — 좌표가 어긋난 것으로 보고 안 넣는다".formatted(addressGu,
								row.gu(), row.dong())));
				continue;
			}
			rows.add(row);
		}
		return new Result(rows, linesRead, skipped, dropped);
	}

	/**
	 * 구·동을 모르는 줄이면 건너뛴 줄 정보를, 아니면 {@code null}. 좌표 검사보다 먼저 부른다. 번호·이름이 비었으면 이
	 * 줄은 건너뛰는 것이 아니라 틀린 줄이라 예외를 던진다.
	 */
	private static Skipped unknownAreaOf(int lineNumber, JsonNode node) {
		checkShape(node);
		String sourceId = required(node, "sourceId");
		String name = required(node, "name");
		String gu = optional(node, "gu");
		String dong = optional(node, "dong");
		if (gu != null && !UNKNOWN_AREA.equals(gu) && dong != null && !UNKNOWN_AREA.equals(dong)) {
			return null;
		}
		return new Skipped(lineNumber, sourceId, name, Reason.UNKNOWN_AREA,
				"구·동을 모른다(gu=%s, dong=%s)".formatted(gu, dong));
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

	/** 한 줄을 읽는다. 구·동을 아는 줄만 온다({@link #unknownAreaOf} 가 먼저 거른다). */
	static CertifiedRestaurantRow parse(JsonNode node) {
		checkShape(node);
		String sourceId = required(node, "sourceId");
		if (sourceId.length() > SOURCE_ID_MAX) {
			throw new IllegalArgumentException("sourceId 가 너무 길다(" + SOURCE_ID_MAX + "자까지): " + sourceId);
		}
		String name = required(node, "name");
		double lat = number(node, "lat");
		double lng = number(node, "lng");
		if (lat < LAT_MIN || lat > LAT_MAX || lng < LNG_MIN || lng > LNG_MAX) {
			throw new IllegalArgumentException("좌표가 부산 밖이다 (위경도를 뒤바꿨나?): " + lat + ", " + lng);
		}
		String gu = required(node, "gu");
		String dong = required(node, "dong");
		if (CertifiedRestaurant.areaKey(gu, dong).length() > CertifiedRestaurant.KEY_MAX) {
			throw new IllegalArgumentException("구/동 열쇠가 너무 길다(" + CertifiedRestaurant.KEY_MAX + "자까지): " + gu + "/" + dong);
		}
		if (gu.indexOf('/') >= 0 || dong.indexOf('/') >= 0) {
			throw new IllegalArgumentException("구·동 이름에 '/' 가 들어 있다 — 열쇠가 구와 동을 '/' 로 나눈다: " + gu + ", " + dong);
		}

		JsonNode sourcesNode = node.path("sources");
		if (!sourcesNode.isArray() || sourcesNode.isEmpty()) {
			throw new IllegalArgumentException("sources 는 비지 않은 배열이어야 한다: " + sourcesNode);
		}
		Set<CertifiedRestaurant.Source> sources = new LinkedHashSet<>();
		for (JsonNode element : sourcesNode) {
			if (!element.isString()) {
				throw new IllegalArgumentException("sources 의 원소는 글자여야 한다: " + element);
			}
			sources.add(CertifiedRestaurant.Source.fromFileLabel(element.asString()).orElseThrow(
					() -> new IllegalArgumentException("모르는 인증 목록이다: " + element.asString() + " — 아는 것은 "
							+ Arrays.stream(CertifiedRestaurant.Source.values())
									.map(CertifiedRestaurant.Source::fileLabel).toList())));
		}

		UUID existing = null;
		String existingText = optional(node, "existingPlaceId");
		if (existingText != null) {
			try {
				existing = UUID.fromString(existingText);
			}
			catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("existingPlaceId 가 UUID 가 아니다: " + existingText);
			}
		}

		String photoUrl = optional(node, "photoUrl");
		String photoSource = optional(node, "photoSource");
		if (photoUrl != null) {
			if (!photoUrl.startsWith("http://") && !photoUrl.startsWith("https://")) {
				throw new IllegalArgumentException("photoUrl 은 http(s) 주소여야 한다: " + photoUrl);
			}
			if (photoSource == null) {
				// 출처 표기가 이용 조건이다 — 출처 없는 사진은 넣지 않는다(Place.attachPhoto 와 같은 규칙).
				throw new IllegalArgumentException("photoUrl 이 있는데 photoSource 가 없다 — 출처 없이 사진을 쓰지 않는다");
			}
			if (photoSource.length() > PHOTO_SOURCE_MAX) {
				throw new IllegalArgumentException("photoSource 가 너무 길다(" + PHOTO_SOURCE_MAX + "자까지): " + photoSource);
			}
		}
		else if (photoSource != null) {
			throw new IllegalArgumentException("photoSource 가 있는데 photoUrl 이 없다");
		}

		return new CertifiedRestaurantRow(sourceId, name, lat, lng, optional(node, "address"), optional(node, "tel"), gu,
				dong, new ArrayList<>(sources), optional(node, "menu"), existing, photoUrl, photoSource);
	}

	/**
	 * 번호 하나에 한 줄만 남긴다. 줄이 하나뿐이면 그대로이고, 여럿이면 {@link #consistency} 가 가장 큰 줄을 고른다. 점수가
	 * 같으면 파일에서 먼저 나온 줄이다. 남기는 줄은 <b>그 줄의 파일 순서</b>를 지킨다.
	 */
	private static List<Line> pickOnePerSourceId(List<Line> lines, List<DroppedDuplicate> dropped) {
		Map<String, List<Line>> bySourceId = new LinkedHashMap<>();
		for (Line line : lines) {
			bySourceId.computeIfAbsent(line.row().sourceId(), key -> new ArrayList<>()).add(line);
		}
		List<Line> chosen = new ArrayList<>(bySourceId.size());
		for (List<Line> group : bySourceId.values()) {
			if (group.size() == 1) {
				chosen.add(group.get(0));
				continue;
			}
			Line best = group.get(0);
			for (Line candidate : group) {
				if (consistency(candidate.row()) > consistency(best.row())) {
					best = candidate;
				}
			}
			for (Line line : group) {
				if (line != best) {
					dropped.add(new DroppedDuplicate(line.number(), line.row().sourceId(), line.row().name(),
							line.row().gu(), line.row().dong(), "같은 번호의 줄이 %d개라 %d번째 줄(%s %s)을 골랐다 — 주소와 맞는 쪽".formatted(
									group.size(), best.number(), best.row().gu(), best.row().dong())));
				}
			}
			chosen.add(best);
		}
		chosen.sort((a, b) -> Integer.compare(a.number(), b.number()));
		return chosen;
	}

	/**
	 * 이 줄의 구·동이 주소 글과 얼마나 맞는가. 클수록 맞는다.
	 *
	 * <ul>
	 *   <li>주소의 구와 같다 +4 · 주소에 구가 안 적혀 있어 판단 못 한다 +2 · 다르다 0</li>
	 *   <li>주소 글에 동 이름이 들어 있다 +1 (예: 「…세실로69번길 19 (좌동, …)」)</li>
	 *   <li>번호 끝에 {@code @위도} 가 붙은 줄(모범음식점)은 그 위도가 이 줄의 위도와 같다 +1 — 번호를 만들 때 쓴 원래 좌표다</li>
	 * </ul>
	 */
	static int consistency(CertifiedRestaurantRow row) {
		int score = 0;
		String addressGu = addressGu(row.address());
		if (addressGu == null) {
			score += 2;
		}
		else if (addressGu.equals(row.gu())) {
			score += 4;
		}
		if (row.address() != null && row.address().contains(row.dong())) {
			score += 1;
		}
		int at = row.sourceId().lastIndexOf('@');
		if (at >= 0) {
			try {
				if (Math.abs(Double.parseDouble(row.sourceId().substring(at + 1)) - row.lat()) < 1e-9) {
					score += 1;
				}
			}
			catch (NumberFormatException ignored) {
				// 번호 끝이 위도가 아니면 이 근거는 안 쓴다.
			}
		}
		return score;
	}

	/**
	 * 주소 첫머리의 구·군. 「부산 해운대구 …」「부산광역시 해운대구 …」「해운대구 …」는 해운대구이고, 「기장읍 …」「부산광역시
	 * 두실로 17(구서동)」처럼 구가 안 적혀 있으면 {@code null} — 모르는 것을 맞다고도 틀리다고도 하지 않는다. 주소 한가운데의
	 * 「동구」「중구」 같은 두 글자는 길 이름 속에서도 나오므로 첫머리만 본다.
	 */
	static String addressGu(String address) {
		if (address == null || address.isBlank()) {
			return null;
		}
		String[] tokens = address.trim().split("\\s+");
		int at = CITY_PREFIXES.contains(tokens[0]) ? 1 : 0;
		return (at < tokens.length && BUSAN_GU.contains(tokens[at])) ? tokens[at] : null;
	}

	static String required(JsonNode node, String field) {
		String value = optional(node, field);
		if (value == null) {
			throw new IllegalArgumentException(field + " 가 비었다");
		}
		return value;
	}

	static String optional(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		if (!value.isString()) {
			throw new IllegalArgumentException(field + " 는 글자거나 null 이어야 한다: " + value);
		}
		String text = value.asString().trim();
		return text.isEmpty() ? null : text;
	}

	static double number(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isNumber()) {
			throw new IllegalArgumentException(field + " 는 숫자여야 한다: " + value);
		}
		return value.asDouble();
	}
}
