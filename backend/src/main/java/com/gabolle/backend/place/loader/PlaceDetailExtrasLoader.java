package com.gabolle.backend.place.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.repository.PlaceRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 장소 상세에 보여 줄 사실(메뉴·외국어 메뉴판·편의시설·입장료·가까운 곳·가기 좋은 때·영업시간·체크인)을
 * {@code place_feature} 에 넣는다 — S15P21E201-1886.
 *
 * <p>열쇠는 원천 번호가 아니라 <b>우리 장소 번호({@code place_id}) 그대로</b>다. 조사로 짝지은 장소에 OSM 장소가
 * 섞여 있는데, 상가·관광공사 번호로 장소를 찾는 {@link PlaceFeatureNdjsonReader} 는 그것을 못 찾는다.
 *
 * <p>한 줄 모양:
 *
 * <pre>
 * {"placeId":"uuid","featureType":"MENU_ITEMS","value":{…},"evidenceStatus":"VERIFIED"|"ESTIMATED",
 *  "sourceType":"…","sourceId":"…"|null,"sourceVersion":"…","observedAt":"2026-09-30T12:00:00+09:00"|null}
 * </pre>
 *
 * <p>🔴 모르는 낱말이 오면 멈춘다. 모르는 갈래·모르는 칸·모양이 틀린 값은 그 줄 번호와 함께 예외로 끝나고, 파일
 * 전체를 먼저 읽어 검사하므로 <b>한 줄이라도 틀리면 한 행도 안 들어간다.</b> 일부만 들어간 상태는 되짚기 어렵다.
 *
 * <p>🔴 알레르기는 받지 않는다. {@code ck_place_feature_safety_never_estimated} 가 추정 알레르기 행을 막는 데는 이유가
 * 있다(틀리면 사람이 다친다). 재료는 {@code MENU_ITEMS} 안에 글로만 둔다.
 *
 * <p>넣기만 한다. 같은 장소에 같은 갈래가 이미 있으면(출처가 달라도) 건너뛰고 센다 — 두 번 돌려도 행이 늘지 않는다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceDetailExtrasLoader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/** 메뉴는 이보다 많이 싣지 않는다. 화면이 다 못 보여 주고, 넘치면 산출물이 잘못 만들어진 것이다. */
	static final int MAX_MENU_ITEMS = 30;

	/** 이 적재기가 받는 갈래. 목록 밖이면 멈춘다. */
	static final Set<String> FEATURE_TYPES = Set.of("MENU_ITEMS", "FOREIGN_MENU", "AMENITIES", "ADMISSION_FEE",
			"NEARBY_LANDMARK", "BEST_TIME", OpeningHoursReader.TYPE_OPENING_HOURS, OpeningHoursReader.TYPE_CHECK_IN_OUT);

	private static final Set<String> LINE_FIELDS = Set.of("placeId", "featureType", "value", "evidenceStatus",
			"sourceType", "sourceId", "sourceVersion", "observedAt");

	/** {@code UNKNOWN} 은 값이 없어야 하는 등급이라({@code ck_place_feature_unknown_has_no_value}) 받지 않는다. */
	private static final Set<String> EVIDENCE = Set.of("VERIFIED", "ESTIMATED");

	/** {@link PlaceFeatureLoader#INSERT_IF_ABSENT} 와 같은 이유로 {@code ON CONFLICT} 에 대상을 적지 않는다. */
	private static final String INSERT_IF_ABSENT = """
			INSERT INTO place_feature
			    (place_feature_id, place_id, feature_type, feature_key, value, evidence_status,
			     source_type, source_id, observed_at, source_version, created_at)
			VALUES (?1, ?2, ?3, NULL, CAST(?4 AS jsonb), ?5, ?6, ?7, ?8, ?9, ?10)
			ON CONFLICT DO NOTHING
			""";

	private final PlaceRepository placeRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public PlaceDetailExtrasLoader(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/** 검사를 마친 한 줄. {@code value} 는 DB 에 그대로 들어갈 JSON 글이다. */
	public record Row(UUID placeId, String featureType, String value, String evidenceStatus, String sourceType,
			String sourceId, String sourceVersion, OffsetDateTime observedAt) {
	}

	/** 넣은 것 · 이미 있어 건너뛴 것 · 그 번호의 장소가 없어 못 넣은 것을 갈라 센다. */
	public record Result(int inserted, int skippedExisting, int noSuchPlace) {

		public Result plus(Result other) {
			return new Result(this.inserted + other.inserted, this.skippedExisting + other.skippedExisting,
					this.noSuchPlace + other.noSuchPlace);
		}

		@Override
		public String toString() {
			return "넣음 " + this.inserted + " · 이미 있어 건너뜀 " + this.skippedExisting + " · 장소가 없어 못 넣음 "
					+ this.noSuchPlace;
		}
	}

	/** 파일 전체를 읽고 검사한다. 빈 줄은 넘기고, 틀린 줄이 하나라도 있으면 줄 번호와 함께 멈춘다. */
	public static List<Row> readAll(Path file) {
		List<Row> rows = new ArrayList<>();
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			int lineNo = 0;
			while ((line = reader.readLine()) != null) {
				lineNo++;
				if (line.isBlank()) {
					continue;
				}
				try {
					rows.add(parse(line));
				}
				catch (IllegalArgumentException ex) {
					throw new IllegalArgumentException(file + " " + lineNo + "번째 줄: " + ex.getMessage(), ex);
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("장소 상세 산출물을 읽을 수 없다: " + file, ex);
		}
		return rows;
	}

	/** 한 줄을 읽는다. 틀리면 {@link IllegalArgumentException}. */
	public static Row parse(String line) {
		JsonNode node;
		try {
			node = MAPPER.readTree(line);
		}
		catch (JacksonException ex) {
			throw new IllegalArgumentException("JSON 이 아니다", ex);
		}
		requireObject(node, "줄");
		onlyFields(node, LINE_FIELDS, "줄");

		UUID placeId;
		try {
			placeId = UUID.fromString(requiredText(node, "placeId"));
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("placeId 가 UUID 가 아니다: " + node.get("placeId"), ex);
		}
		String featureType = requiredText(node, "featureType");
		if (!FEATURE_TYPES.contains(featureType)) {
			throw new IllegalArgumentException("모르는 featureType 이다: " + featureType + " — 받는 것은 " + FEATURE_TYPES);
		}
		String evidence = requiredText(node, "evidenceStatus");
		if (!EVIDENCE.contains(evidence)) {
			throw new IllegalArgumentException("evidenceStatus 는 VERIFIED 또는 ESTIMATED 여야 한다: " + evidence);
		}
		String sourceType = requiredText(node, "sourceType");
		String sourceId = optionalText(node, "sourceId");
		String sourceVersion = requiredText(node, "sourceVersion");
		String observedText = optionalText(node, "observedAt");
		OffsetDateTime observedAt = null;
		if (observedText != null) {
			try {
				observedAt = OffsetDateTime.parse(observedText);
			}
			catch (DateTimeParseException ex) {
				throw new IllegalArgumentException("observedAt 이 ISO-8601 시각(시간대 포함)이 아니다: " + observedText, ex);
			}
		}

		JsonNode value = node.get("value");
		requireObject(value, "value");
		validateValue(featureType, value);
		return new Row(placeId, featureType, value.toString(), evidence, sourceType, sourceId, sourceVersion,
				observedAt);
	}

	/** 갈래별 값 모양. 모르는 칸도 틀린 것으로 본다 — 오타 난 칸은 조용히 버려지기 쉽다. */
	static void validateValue(String featureType, JsonNode value) {
		switch (featureType) {
			case "MENU_ITEMS" -> {
				onlyFields(value, Set.of("items"), featureType);
				JsonNode items = value.get("items");
				if (items == null || !items.isArray() || items.isEmpty()) {
					throw new IllegalArgumentException("MENU_ITEMS.items 는 비지 않은 배열이어야 한다");
				}
				if (items.size() > MAX_MENU_ITEMS) {
					throw new IllegalArgumentException("MENU_ITEMS.items 는 " + MAX_MENU_ITEMS + "개까지다: " + items.size());
				}
				for (JsonNode item : items) {
					String where = "MENU_ITEMS.items[]";
					requireObject(item, where);
					onlyFields(item, Set.of("nameKo", "nameEn", "priceWon", "ingredientsKo", "ingredientsEn",
							"signature"), where);
					requiredText(item, "nameKo");
					optionalText(item, "nameEn");
					optionalNonNegativeInt(item, "priceWon");
					optionalText(item, "ingredientsKo");
					optionalText(item, "ingredientsEn");
					JsonNode signature = item.get("signature");
					if (signature == null || !signature.isBoolean()) {
						throw new IllegalArgumentException(where + ".signature 는 true/false 여야 한다");
					}
				}
			}
			case "FOREIGN_MENU" -> {
				onlyFields(value, Set.of("available"), featureType);
				JsonNode available = value.get("available");
				if (available == null || !available.isBoolean()) {
					throw new IllegalArgumentException("FOREIGN_MENU.available 은 true/false 여야 한다");
				}
			}
			case "AMENITIES" -> {
				onlyFields(value, Set.of("wifi", "parking", "restroom", "reservation", "homepage"), featureType);
				boolean any = false;
				for (String field : new String[] { "wifi", "parking", "restroom" }) {
					JsonNode flag = value.get(field);
					if (flag != null && !flag.isNull()) {
						if (!flag.isBoolean()) {
							throw new IllegalArgumentException("AMENITIES." + field + " 는 true/false/null 이어야 한다");
						}
						any = true;
					}
				}
				any |= optionalText(value, "reservation") != null;
				any |= optionalText(value, "homepage") != null;
				if (!any) {
					// 껍데기만 든 행을 넣으면 화면이 「확인했다」로 읽는다. 모름은 행이 없는 것이다.
					throw new IllegalArgumentException("AMENITIES 의 칸이 전부 비었다 — 모르면 줄을 만들지 않는다");
				}
			}
			case "ADMISSION_FEE" -> {
				onlyFields(value, Set.of("raw"), featureType);
				requiredText(value, "raw");
			}
			case "NEARBY_LANDMARK" -> {
				onlyFields(value, Set.of("name", "distanceM"), featureType);
				requiredText(value, "name");
				requiredNonNegativeInt(value, "distanceM");
			}
			case "BEST_TIME" -> {
				onlyFields(value, Set.of("day", "night", "any"), featureType);
				int total = requiredNonNegativeInt(value, "day") + requiredNonNegativeInt(value, "night")
						+ requiredNonNegativeInt(value, "any");
				if (total == 0) {
					throw new IllegalArgumentException("BEST_TIME 의 응답 수가 전부 0 이다 — 모르면 줄을 만들지 않는다");
				}
			}
			case OpeningHoursReader.TYPE_OPENING_HOURS -> {
				// OpeningHoursReader 가 쓰는 모양과 같다 — 판정기가 이 모양만 읽는다.
				onlyFields(value, Set.of("status", "byDay", "seasonal", "closedDays", "notes", "raw"), featureType);
				String status = requiredText(value, "status");
				if (!"PARSED".equals(status) && !"ALWAYS_OPEN".equals(status)) {
					throw new IllegalArgumentException("OPENING_HOURS.status 는 PARSED 또는 ALWAYS_OPEN 이어야 한다: " + status);
				}
				JsonNode byDay = value.get("byDay");
				if (byDay != null && !byDay.isNull() && !byDay.isObject()) {
					throw new IllegalArgumentException("OPENING_HOURS.byDay 는 객체여야 한다");
				}
			}
			case OpeningHoursReader.TYPE_CHECK_IN_OUT -> {
				onlyFields(value, Set.of("status", "checkIn", "checkOut", "notes", "raw"), featureType);
				if (!"LODGING".equals(requiredText(value, "status"))) {
					throw new IllegalArgumentException("CHECK_IN_OUT.status 는 LODGING 이어야 한다");
				}
				if (optionalText(value, "checkIn") == null && optionalText(value, "checkOut") == null) {
					throw new IllegalArgumentException("CHECK_IN_OUT 의 checkIn·checkOut 이 둘 다 비었다");
				}
			}
			default -> throw new IllegalArgumentException("모르는 featureType 이다: " + featureType);
		}
	}

	/**
	 * 한 덩어리를 넣는다. 장소는 번호로 한 번에 찾고, 없는 번호는 세기만 한다. 피처 아이디는
	 * {@link PlaceFeatureLoader#featureIdOf} 의 장소 번호 규칙을 그대로 쓴다.
	 */
	@Transactional
	public Result saveChunk(List<Row> rows, OffsetDateTime createdAt) {
		Set<UUID> known = new HashSet<>();
		this.placeRepository.findAllById(rows.stream().map(Row::placeId).distinct().toList())
				.forEach(place -> known.add(place.getPlaceId()));

		int inserted = 0;
		int skipped = 0;
		int missing = 0;
		for (Row row : rows) {
			if (!known.contains(row.placeId())) {
				missing++;
				continue;
			}
			UUID featureId = PlaceFeatureLoader.featureIdOf(PlaceFeatureLoader.PLACE_ID_KEY, row.placeId().toString(),
					row.featureType(), null);
			int affected = this.entityManager.createNativeQuery(INSERT_IF_ABSENT)
					.setParameter(1, featureId)
					.setParameter(2, row.placeId())
					.setParameter(3, row.featureType())
					.setParameter(4, row.value())
					.setParameter(5, row.evidenceStatus())
					.setParameter(6, row.sourceType())
					.setParameter(7, row.sourceId())
					.setParameter(8, row.observedAt())
					.setParameter(9, row.sourceVersion())
					.setParameter(10, createdAt)
					.executeUpdate();
			if (affected == 1) {
				inserted++;
			}
			else {
				skipped++;
			}
		}
		return new Result(inserted, skipped, missing);
	}

	private static void requireObject(JsonNode node, String where) {
		if (node == null || !node.isObject()) {
			throw new IllegalArgumentException(where + " 는 JSON 객체여야 한다");
		}
	}

	private static void onlyFields(JsonNode node, Set<String> allowed, String where) {
		for (Map.Entry<String, JsonNode> entry : node.properties()) {
			if (!allowed.contains(entry.getKey())) {
				throw new IllegalArgumentException(where + " 에 모르는 칸이 있다: " + entry.getKey() + " — 받는 것은 " + allowed);
			}
		}
	}

	private static String requiredText(JsonNode node, String field) {
		String text = optionalText(node, field);
		if (text == null) {
			throw new IllegalArgumentException(field + " 가 비었다");
		}
		return text;
	}

	private static String optionalText(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString()) {
			throw new IllegalArgumentException(field + " 는 글이어야 한다: " + value);
		}
		String text = value.asString().trim();
		return text.isEmpty() ? null : text;
	}

	private static int requiredNonNegativeInt(JsonNode node, String field) {
		Integer number = optionalNonNegativeInt(node, field);
		if (number == null) {
			throw new IllegalArgumentException(field + " 가 비었다");
		}
		return number;
	}

	private static Integer optionalNonNegativeInt(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
			throw new IllegalArgumentException(field + " 는 0 이상의 정수여야 한다: " + value);
		}
		return value.intValue();
	}

}
