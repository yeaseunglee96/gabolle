package com.gabolle.backend.facility.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gabolle.backend.facility.domain.FacilityKind;
import com.gabolle.backend.facility.domain.ParkingFee;
import com.gabolle.backend.facility.domain.WeeklyHours;
import com.gabolle.backend.place.service.GeoDistance;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 근처 편의시설 NDJSON(<b>한 줄에 JSON 하나</b>인 글 파일) 넷을 읽고 다듬는다 (S15P21E201-2052).
 *
 * <p>🔴 <b>모르는 칸·틀린 모양이 하나라도 있으면 줄 번호와 함께 멈춘다.</b> 오타 난 칸은 조용히 버려지기 쉽고, 버려진 줄 모르고 넣으면
 * 빈 값이 사실처럼 남는다. 파일 전체를 먼저 검사하므로 한 줄이라도 틀리면 한 행도 안 들어간다.
 *
 * <p>🔴 <b>이상한 값은 멈추지 않고 비운다 — 모른다는 것을 0 이나 빈 글자로 지어내지 않는다.</b> 실제 파일에서 본 것들이다.
 * <ul>
 *   <li>주차장 요금이 0원인데 무료도 아니다 / 정수 최댓값(2147483647) / 4만 원(월정기권이 섞인 값) → 요금표를 비운다(아래 「요금」).
 *       추가요금만 0 이면 그 칸만 비운다</li>
 *   <li>시간이 {@code 00:00~00:00}(하루 종일인지 운영 안 하는지 모른다) · 시작·끝이 null · 시작보다 끝이 빠른 약국 시간 · 1분짜리 시간대 → 그날을
 *       「모른다」로 비운다({@link WeeklyHours} 설명)</li>
 *   <li>화장실 주소가 「부산광역시 연제구」뿐이고 좌표가 구 중심점인 줄 → 위치를 믿을 수 없어 넣지 않는다</li>
 *   <li>물품보관함 좌표가 역과 수 km 어긋남 → 역 자료의 좌표로 바로잡는다({@link SubwayStations}). 역을 못 찾으면 넣지 않는다</li>
 * </ul>
 *
 * <h2>종류별 {@code detail} 모양</h2>
 * <pre>
 * PARKING   {operator:PUBLIC|PRIVATE, free, spaces, fee:{baseMinutes,baseWon,addMinutes,addWon,dayMaxWon,freeMinutes}|null,
 *            hours:{Monday..Sunday,Holiday: {open,close}|null}|null, note}
 * PHARMACY  {tel, directions, note, hours:{…요일별…}|null}                      끝이 24:00 이상이면 다음 날
 * TOILET    {type:OPEN|PUBLIC|SIMPLE|MOBILE, openHours, alwaysOpen, accessible, diaper, diaperPlace:WOMEN|MEN|BOTH|null,
 *            emergencyBell, cctv, tel}
 * LOCKER    {station, stationEn, line, location, small, medium, large, xlarge, feeText, operator}
 * </pre>
 *
 * <h2>요금 (주차장)</h2>
 * 무료({@code free=true})면 요금표를 두지 않는다. 유료인데 요금표를 모르면({@code feeKnown=false}) {@code fee} 가 null — 예상요금은
 * 「요금 정보 없음」이고 순서도 요금을 아는 곳 뒤다. 🔴 {@code feeKnown=true} 로 와도 값이 이상하면({@link ParkingFee#normalize}) 요금을
 * 비운다 — 입력의 표시를 믿지 않고 값도 본다(실제 파일에서 {@code feeKnown=true} 인 60곳의 기본요금이 정수 최댓값이었다).
 */
public final class FacilityReader {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/** 부산을 넉넉히 덮는 상자. 위경도를 뒤바꿔 적은 줄을 여기서 잡는다. */
	private static final double LAT_MIN = 34.8;

	private static final double LAT_MAX = 35.5;

	private static final double LNG_MIN = 128.7;

	private static final double LNG_MAX = 129.4;

	static final int NAME_MAX = 200;

	static final int ADDRESS_MAX = 300;

	static final int TEXT_MAX = 300;

	/** 역에서 이만큼 넘게 옮긴 좌표는 로그에 따로 센다. */
	static final int FAR_METERS = 300;

	static final Set<String> PARKING_FIELDS = Set.of("sourceId", "name", "address", "lat", "lng", "operator", "free",
			"feeKnown", "spaces", "fee", "hours", "note");

	static final Set<String> PHARMACY_FIELDS = Set.of("sourceId", "name", "address", "tel", "lat", "lng", "directions",
			"note", "hours");

	static final Set<String> TOILET_FIELDS = Set.of("sourceId", "name", "address", "lat", "lng", "kind", "openHours",
			"accessible", "diaper", "diaperPlace", "emergencyBell", "cctv", "tel");

	static final Set<String> LOCKER_FIELDS = Set.of("sourceId", "station", "stationEn", "line", "location", "lat", "lng",
			"small", "medium", "large", "xlarge", "feeText", "operator");

	private static final Set<String> FEE_FIELDS = Set.of("baseMinutes", "baseWon", "addMinutes", "addWon", "dayMaxWon",
			"freeMinutes");

	private static final Map<String, String> TOILET_TYPES = Map.of("개방화장실", "OPEN", "공중화장실", "PUBLIC", "간이화장실",
			"SIMPLE", "이동화장실", "MOBILE");

	/** 「부산광역시 연제구」 처럼 구까지만 적힌 주소. 이런 줄의 좌표는 구 중심점이다. */
	private static final Pattern DISTRICT_ONLY = Pattern.compile("^부산광역시\\s+\\S+[구군]$");

	/** 지하철 환승주차장 번호 — 가운데 숫자가 역 자료의 정류장 번호다. */
	private static final Pattern METRO_ID = Pattern.compile("^METRO-(\\d+)-\\d+$");

	private static final Pattern ALWAYS_OPEN = Pattern.compile("^(상시|24시간|0?0:00\\s*[~-]\\s*24:00)$");

	private FacilityReader() {
	}

	/** 건너뛴 이유. */
	public enum Reason {

		/** 주소가 구까지만 있어 좌표가 구 중심점이다. */
		COARSE_ADDRESS,

		/** 역 자료에서 역을 못 찾아 위치를 믿을 수 없다. */
		STATION_NOT_FOUND
	}

	public record Skipped(int lineNumber, String sourceId, String name, Reason reason, String message) {

		@Override
		public String toString() {
			return lineNumber + "번째 줄 · " + sourceId + " · " + name + " · " + message;
		}
	}

	/**
	 * 한 파일을 읽은 결과.
	 *
	 * @param linesRead 빈 줄을 뺀 읽은 줄 수
	 * @param nulled 비운 값의 이유별 개수 (예: {@code fee.BASE_WON_IMPLAUSIBLE → 70})
	 * @param positionFixed 위치를 역 자료의 좌표로 바꾼 줄 수
	 * @param farMoved 그중 {@value #FAR_METERS}m 넘게 옮긴 줄 수
	 * @param maxMovedMeters 가장 멀리 옮긴 거리(m)
	 */
	public record Result(FacilityKind kind, int linesRead, List<FacilityRow> rows, List<Skipped> skipped,
			Map<String, Integer> nulled, int positionFixed, int farMoved, int maxMovedMeters) {

		public int skippedCount(Reason reason) {
			return (int) this.skipped.stream().filter(each -> each.reason() == reason).count();
		}
	}

	// ── 파일 넷 ─────────────────────────────────────────────────────────────

	/** @param stations 지하철 환승주차장(METRO-…)의 위치를 바로잡는 역 자료. 없으면 파일의 좌표를 그대로 쓴다 */
	public static Result readParking(Path file, SubwayStations stations) {
		return read(FacilityKind.PARKING, file, PARKING_FIELDS, stations, FacilityReader::parseParking);
	}

	public static Result readPharmacy(Path file) {
		return read(FacilityKind.PHARMACY, file, PHARMACY_FIELDS, null, FacilityReader::parsePharmacy);
	}

	public static Result readToilet(Path file) {
		return read(FacilityKind.TOILET, file, TOILET_FIELDS, null, FacilityReader::parseToilet);
	}

	/** @param stations 필수 — 물품보관함의 위치는 역 자료에서 가져온다 */
	public static Result readLocker(Path file, SubwayStations stations) {
		if (stations == null) {
			throw new IllegalArgumentException("물품보관함은 역 자료가 있어야 읽을 수 있다 — 파일의 좌표를 믿을 수 없다");
		}
		return read(FacilityKind.LOCKER, file, LOCKER_FIELDS, stations, FacilityReader::parseLocker);
	}

	// ── 줄 단위 읽기 ────────────────────────────────────────────────────────

	@FunctionalInterface
	private interface LineParser {

		Outcome parse(JsonNode node, Context context);
	}

	/** 한 줄의 결과 — 넣을 행이거나 건너뜀. */
	private record Outcome(FacilityRow row, Reason skipReason, String skipMessage, String skipName) {

		static Outcome of(FacilityRow row) {
			return new Outcome(row, null, null, null);
		}

		static Outcome skip(Reason reason, String name, String message) {
			return new Outcome(null, reason, message, name);
		}
	}

	/** 한 파일을 읽는 동안 쌓는 것. */
	private static final class Context {

		final SubwayStations stations;

		final Map<String, Integer> nulled = new TreeMap<>();

		int positionFixed;

		int farMoved;

		int maxMovedMeters;

		Context(SubwayStations stations) {
			this.stations = stations;
		}

		void count(String key) {
			this.nulled.merge(key, 1, Integer::sum);
		}

		/** 위치를 {@code meters} 만큼 옮겼다. */
		void moved(double meters) {
			this.positionFixed++;
			if (meters > FAR_METERS) {
				this.farMoved++;
			}
			this.maxMovedMeters = Math.max(this.maxMovedMeters, (int) Math.round(meters));
		}
	}

	private static Result read(FacilityKind kind, Path file, Set<String> fields, SubwayStations stations,
			LineParser parser) {
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		}
		catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
		Context context = new Context(stations);
		List<FacilityRow> rows = new ArrayList<>();
		List<Skipped> skipped = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int linesRead = 0;
		for (int i = 0; i < lines.size(); i++) {
			String line = i == 0 && lines.get(i).startsWith("﻿") ? lines.get(i).substring(1) : lines.get(i);
			if (line.isBlank()) {
				continue;
			}
			linesRead++;
			Outcome outcome;
			String sourceId = null;
			try {
				JsonNode node = MAPPER.readTree(line);
				if (!node.isObject()) {
					throw new IllegalArgumentException("줄이 JSON 객체가 아니다");
				}
				for (Map.Entry<String, JsonNode> entry : node.properties()) {
					if (!fields.contains(entry.getKey())) {
						throw new IllegalArgumentException("모르는 칸이 있다: " + entry.getKey() + " — 받는 것은 " + sorted(fields));
					}
				}
				sourceId = requiredText(node, "sourceId");
				if (!seen.add(sourceId)) {
					throw new IllegalArgumentException("같은 sourceId 가 또 나왔다 — " + sourceId);
				}
				outcome = parser.parse(node, context);
			}
			catch (RuntimeException failure) {
				throw new IllegalArgumentException((i + 1) + "번째 줄: " + failure.getMessage(), failure);
			}
			if (outcome.row() != null) {
				rows.add(outcome.row());
			}
			else {
				skipped.add(new Skipped(i + 1, sourceId, outcome.skipName(), outcome.skipReason(), outcome.skipMessage()));
			}
		}
		return new Result(kind, linesRead, List.copyOf(rows), List.copyOf(skipped), Map.copyOf(context.nulled),
				context.positionFixed, context.farMoved, context.maxMovedMeters);
	}

	private static List<String> sorted(Set<String> fields) {
		return fields.stream().sorted().toList();
	}

	// ── 주차장 ──────────────────────────────────────────────────────────────

	private static Outcome parseParking(JsonNode node, Context context) {
		String sourceId = requiredText(node, "sourceId");
		String name = FacilityText.cut(requiredClean(node, "name"), NAME_MAX);
		String address = FacilityText.cleanAddress(rawText(node, "address"), ADDRESS_MAX);
		double lat = number(node, "lat");
		double lng = number(node, "lng");
		requireInBusan(lat, lng);
		String operator = requiredText(node, "operator");
		if (!operator.equals("PUBLIC") && !operator.equals("PRIVATE")) {
			throw new IllegalArgumentException("operator 는 PUBLIC 이나 PRIVATE 여야 한다: " + operator);
		}
		boolean free = bool(node, "free");
		boolean feeKnown = bool(node, "feeKnown");
		Integer spaces = integerOrNull(node, "spaces");
		if (spaces != null && spaces <= 0) {
			spaces = null;
			context.count("spaces.0이하");
		}
		String note = FacilityText.cleanNote(rawText(node, "note"), TEXT_MAX);

		// 지하철 환승주차장 — 위치를 역 자료의 좌표로 둔다. 번호의 가운데 숫자가 역 자료의 정류장 번호이고, 이름의 역이 같아야 믿는다.
		Matcher metro = METRO_ID.matcher(sourceId);
		if (metro.matches() && context.stations != null) {
			Optional<SubwayStations.Station> station = context.stations.byStopId(metro.group(1));
			String lotStation = name.replaceFirst("\\s*환승주차장$", "");
			if (station.isPresent()
					&& SubwayStations.normalize(station.get().name()).equals(SubwayStations.normalize(lotStation))) {
				context.moved(GeoDistance.meters(lat, lng, station.get().lat(), station.get().lng()));
				lat = station.get().lat();
				lng = station.get().lng();
			}
			else {
				context.count("position.역을 못 찾아 파일 좌표 그대로");
			}
		}
		// 환승주차장은 주소 자리에 「2번 출구」 같은 위치 설명이 온다. 메모가 같은 말로 시작하면 주소가 아니라 위치 설명이다.
		if (address != null && note != null && note.startsWith(address)) {
			address = null;
			context.count("address.위치 설명이라 주소 아님");
		}

		Map<String, Object> feeDetail = null;
		if (!free) {
			JsonNode feeNode = node.path("fee");
			if (!feeKnown || feeNode.isMissingNode() || feeNode.isNull()) {
				context.count("fee.요금 모름");
			}
			else {
				feeDetail = normalizedFee(feeNode, context);
			}
		}

		Map<String, Object> hoursDetail = null;
		JsonNode hours = node.path("hours");
		if (hours.isObject()) {
			requireDayKeys(hours);
			Map<String, WeeklyHours.Window> days = new LinkedHashMap<>();
			for (String day : WeeklyHours.DAYS) {
				JsonNode pair = hours.path(day);
				if (pair.isMissingNode() || pair.isNull()) {
					// 주차장 자료는 여덟 칸을 다 적는다. 빠졌으면 쉬는 날이 아니라 모르는 날이다.
					days.put(day, null);
					context.count("hours.칸 없음");
					continue;
				}
				WeeklyHours.Parsed parsed = WeeklyHours.parkingWindow(clock(pair, "open"), clock(pair, "close"));
				if (parsed.dropped() != null) {
					context.count("hours." + parsed.dropped());
				}
				days.put(day, parsed.window());
			}
			WeeklyHours weekly = WeeklyHours.of(days);
			if (weekly.hasAnyWindow()) {
				hoursDetail = weekly.toDetail();
			}
			else {
				context.count("hours.시간대가 하나도 없음");
			}
		}
		else if (!hours.isNull() && !hours.isMissingNode()) {
			throw new IllegalArgumentException("hours 는 객체거나 null 이어야 한다: " + hours);
		}

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("operator", operator);
		detail.put("free", free);
		detail.put("spaces", spaces);
		detail.put("fee", feeDetail);
		detail.put("hours", hoursDetail);
		detail.put("note", note);
		return Outcome.of(new FacilityRow(FacilityKind.PARKING, sourceId, name, address, lat, lng, detail));
	}

	private static Map<String, Object> normalizedFee(JsonNode feeNode, Context context) {
		if (!feeNode.isObject()) {
			throw new IllegalArgumentException("fee 는 객체거나 null 이어야 한다: " + feeNode);
		}
		for (Map.Entry<String, JsonNode> entry : feeNode.properties()) {
			if (!FEE_FIELDS.contains(entry.getKey())) {
				throw new IllegalArgumentException("fee 에 모르는 칸이 있다: " + entry.getKey() + " — 받는 것은 " + sorted(FEE_FIELDS));
			}
		}
		ParkingFee.Normalized normalized = ParkingFee.normalize(integerOrNull(feeNode, "baseMinutes"),
				integerOrNull(feeNode, "baseWon"), integerOrNull(feeNode, "addMinutes"), integerOrNull(feeNode, "addWon"),
				integerOrNull(feeNode, "dayMaxWon"), integerOrNull(feeNode, "freeMinutes"));
		normalized.notes().forEach(each -> context.count("fee." + each));
		if (normalized.fee() == null) {
			context.count("fee.요금표를 못 씀");
			return null;
		}
		return normalized.fee().toDetail();
	}

	// ── 약국 ────────────────────────────────────────────────────────────────

	private static Outcome parsePharmacy(JsonNode node, Context context) {
		String sourceId = requiredText(node, "sourceId");
		String name = FacilityText.cut(requiredClean(node, "name"), NAME_MAX);
		String address = FacilityText.cleanAddress(rawText(node, "address"), ADDRESS_MAX);
		double lat = number(node, "lat");
		double lng = number(node, "lng");
		requireInBusan(lat, lng);
		String tel = FacilityText.cut(FacilityText.clean(rawText(node, "tel")), 50);
		String directions = FacilityText.cleanNote(rawText(node, "directions"), TEXT_MAX);
		String note = FacilityText.cleanNote(rawText(node, "note"), TEXT_MAX);

		Map<String, Object> hoursDetail = null;
		JsonNode hours = node.path("hours");
		if (hours.isObject()) {
			requireDayKeys(hours);
			Map<String, WeeklyHours.Window> days = new LinkedHashMap<>();
			// 약국 자료는 쉬는 날의 칸을 아예 안 적는다 — 없는 칸은 그대로 두어 「쉰다」로 읽힌다. 적혀 있는데 말이 안 되는 칸만 「모른다」.
			for (Map.Entry<String, JsonNode> entry : hours.properties()) {
				JsonNode pair = entry.getValue();
				if (!pair.isObject()) {
					throw new IllegalArgumentException("hours." + entry.getKey() + " 는 객체여야 한다: " + pair);
				}
				WeeklyHours.Parsed parsed = WeeklyHours.pharmacyWindow(clock(pair, "open"), clock(pair, "close"));
				if (parsed.dropped() != null) {
					context.count("hours." + parsed.dropped());
				}
				days.put(entry.getKey(), parsed.window());
			}
			WeeklyHours weekly = WeeklyHours.of(days);
			if (weekly.hasAnyWindow()) {
				hoursDetail = weekly.toDetail();
			}
			else {
				context.count("hours.시간대가 하나도 없음");
			}
		}
		else if (!hours.isNull() && !hours.isMissingNode()) {
			throw new IllegalArgumentException("hours 는 객체거나 null 이어야 한다: " + hours);
		}

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("tel", tel);
		detail.put("directions", directions);
		detail.put("note", note);
		detail.put("hours", hoursDetail);
		return Outcome.of(new FacilityRow(FacilityKind.PHARMACY, sourceId, name, address, lat, lng, detail));
	}

	// ── 공중화장실 ──────────────────────────────────────────────────────────

	private static Outcome parseToilet(JsonNode node, Context context) {
		String sourceId = requiredText(node, "sourceId");
		String name = FacilityText.cut(requiredClean(node, "name"), NAME_MAX);
		String address = FacilityText.cleanAddress(rawText(node, "address"), ADDRESS_MAX);
		double lat = number(node, "lat");
		double lng = number(node, "lng");
		requireInBusan(lat, lng);
		String kindLabel = requiredText(node, "kind");
		String type = TOILET_TYPES.get(kindLabel);
		if (type == null) {
			throw new IllegalArgumentException("모르는 화장실 종류다: " + kindLabel + " — 받는 것은 " + TOILET_TYPES.keySet());
		}
		boolean accessible = bool(node, "accessible");
		boolean diaper = bool(node, "diaper");
		boolean emergencyBell = bool(node, "emergencyBell");
		boolean cctv = bool(node, "cctv");

		// 🔴 주소가 구까지만 적힌 줄은 좌표가 구 중심점이다(실제 파일: 연제구 학교 25곳이 한 점을 나눠 가졌다). 엉뚱한 곳을 가리키는 화장실이
		//    「가까운 화장실」로 뜨면 안 되므로 넣지 않는다.
		if (address != null && DISTRICT_ONLY.matcher(address).matches()) {
			return Outcome.skip(Reason.COARSE_ADDRESS, name,
					"주소가 「" + address + "」뿐이라 좌표가 구 중심점일 가능성이 크다 — 위치를 믿을 수 없어 넣지 않는다");
		}

		OpenHours openHours = cleanOpenHours(rawText(node, "openHours"));
		if (openHours.text() == null) {
			context.count("openHours.내용 없음");
		}
		String diaperPlace = diaperPlaceCode(rawText(node, "diaperPlace"));
		if (!diaper && diaperPlace != null) {
			// 기저귀 교환대가 없다면서 위치가 적힌 줄(실제 파일 1줄) — 서로 안 맞으니 위치를 비운다.
			diaperPlace = null;
			context.count("diaperPlace.교환대 없음과 모순");
		}
		String tel = FacilityText.cut(FacilityText.clean(rawText(node, "tel")), 50);

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("type", type);
		detail.put("openHours", openHours.text());
		detail.put("alwaysOpen", openHours.alwaysOpen());
		detail.put("accessible", accessible);
		detail.put("diaper", diaper);
		detail.put("diaperPlace", diaperPlace);
		detail.put("emergencyBell", emergencyBell);
		detail.put("cctv", cctv);
		detail.put("tel", tel);
		return Outcome.of(new FacilityRow(FacilityKind.TOILET, sourceId, name, address, lat, lng, detail));
	}

	/** 개방시간 글과 「늘 연다」 여부. */
	record OpenHours(String text, boolean alwaysOpen) {
	}

	/**
	 * 개방시간 자유 글을 다듬는다. 앞에 붙은 분류말 「정시」는 떼고(「정시 09:00~18:00」→「09:00~18:00」) 분류말뿐인 글(「정시 정시」)은
	 * 내용이 없는 것이다. 「상시」「24시간」「00:00~24:00」은 늘 여는 곳이라 따로 표시한다 — 화면이 언어별 글로 바꿔 보여 줄 수 있다.
	 * 그 밖의 글(「불규칙 공연시」「09:00~18:00 주말및공휴일 미개방」)은 번역하지 않고 그대로 보여 준다.
	 */
	static OpenHours cleanOpenHours(String raw) {
		String text = FacilityText.clean(raw);
		if (text == null) {
			return new OpenHours(null, false);
		}
		text = text.replace('∼', '~').replace('～', '~');
		text = text.replaceFirst("^(?:정시\\s*)+", "").trim();
		Matcher wrapped = Pattern.compile("^\\((.*)\\)$").matcher(text);
		if (wrapped.matches()) {
			text = wrapped.group(1).trim();
		}
		if (text.isEmpty()) {
			return new OpenHours(null, false);
		}
		return new OpenHours(FacilityText.cut(text, TEXT_MAX), ALWAYS_OPEN.matcher(text).matches());
	}

	private static String diaperPlaceCode(String raw) {
		String text = FacilityText.clean(raw);
		if (text == null) {
			return null;
		}
		return switch (text) {
			case "여자화장실" -> "WOMEN";
			case "남자화장실" -> "MEN";
			case "남자화장실+여자화장실", "여자화장실+남자화장실" -> "BOTH";
			default -> throw new IllegalArgumentException("모르는 diaperPlace 다: " + text);
		};
	}

	// ── 지하철 물품보관함 ───────────────────────────────────────────────────

	private static Outcome parseLocker(JsonNode node, Context context) {
		String sourceId = requiredText(node, "sourceId");
		String station = requiredClean(node, "station");
		String stationEn = FacilityText.cut(FacilityText.clean(rawText(node, "stationEn")), 100);
		String line = requiredText(node, "line");
		String location = FacilityText.cleanNote(rawText(node, "location"), TEXT_MAX);
		double fileLat = number(node, "lat");
		double fileLng = number(node, "lng");
		requireInBusan(fileLat, fileLng);
		int small = nonNegativeInt(node, "small");
		int medium = nonNegativeInt(node, "medium");
		int large = nonNegativeInt(node, "large");
		int xlarge = nonNegativeInt(node, "xlarge");
		// 끝에 \r 이 붙은 줄이 있다(실제 파일 2줄) — 공백으로 보고 뗀다.
		String feeText = FacilityText.cleanNote(rawText(node, "feeText"), TEXT_MAX);
		String operator = FacilityText.cut(FacilityText.clean(rawText(node, "operator")), 100);
		String name = station.endsWith("역") ? station : station + "역";

		Optional<SubwayStations.Station> found = context.stations.find(station, line);
		if (found.isEmpty()) {
			return Outcome.skip(Reason.STATION_NOT_FOUND, name,
					"역 자료에서 「" + station + "」(" + line + "호선)을 찾지 못했다 — 파일의 좌표는 믿을 수 없어 넣지 않는다");
		}
		SubwayStations.Station at = found.get();
		context.moved(GeoDistance.meters(fileLat, fileLng, at.lat(), at.lng()));

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("station", station);
		detail.put("stationEn", stationEn);
		detail.put("line", line.trim());
		detail.put("location", location);
		detail.put("small", small);
		detail.put("medium", medium);
		detail.put("large", large);
		detail.put("xlarge", xlarge);
		detail.put("feeText", feeText);
		detail.put("operator", operator);
		// 주소 칸은 비운다 — 물품보관함에는 도로명 주소가 없고, 역 안의 위치는 location 이 말한다.
		return Outcome.of(new FacilityRow(FacilityKind.LOCKER, sourceId, name, null, at.lat(), at.lng(), detail));
	}

	// ── 칸 읽기 ─────────────────────────────────────────────────────────────

	private static void requireDayKeys(JsonNode hours) {
		for (Map.Entry<String, JsonNode> entry : hours.properties()) {
			if (!WeeklyHours.DAYS.contains(entry.getKey())) {
				throw new IllegalArgumentException("hours 에 모르는 요일 칸이 있다: " + entry.getKey() + " — 받는 것은 " + WeeklyHours.DAYS);
			}
		}
	}

	private static void requireInBusan(double lat, double lng) {
		if (lat < LAT_MIN || lat > LAT_MAX || lng < LNG_MIN || lng > LNG_MAX) {
			throw new IllegalArgumentException("좌표가 부산 밖이다 (위경도를 뒤바꿨나?): " + lat + ", " + lng);
		}
	}

	/** 글자 칸을 그대로(다듬지 않고) 읽는다. 없거나 null 이면 null. 글자가 아니면 던진다. */
	private static String rawText(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		if (!value.isString()) {
			throw new IllegalArgumentException(field + " 는 글자거나 null 이어야 한다: " + value);
		}
		return value.asString();
	}

	/** 비어 있지 않은 글자 칸 — 앞뒤 공백만 뗀다. */
	private static String requiredText(JsonNode node, String field) {
		String value = rawText(node, field);
		String trimmed = value == null ? null : value.trim();
		if (trimmed == null || trimmed.isEmpty()) {
			throw new IllegalArgumentException(field + " 가 비었다");
		}
		return trimmed;
	}

	/** 비어 있지 않은 글자 칸 — 겹공백·글자 코드까지 다듬는다. */
	private static String requiredClean(JsonNode node, String field) {
		String value = FacilityText.clean(rawText(node, field));
		if (value == null) {
			throw new IllegalArgumentException(field + " 가 비었다");
		}
		return value;
	}

	/** 「HH:MM」 글자 칸. 없거나 null 이면 null — 비어 있는 시각이다. 글자가 아니면 던진다. */
	private static String clock(JsonNode pair, String field) {
		if (!pair.isObject()) {
			throw new IllegalArgumentException("시간 칸은 {open, close} 객체여야 한다: " + pair);
		}
		String value = rawText(pair, field);
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static double number(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isNumber()) {
			throw new IllegalArgumentException(field + " 는 숫자여야 한다: " + value);
		}
		double number = value.asDouble();
		if (!Double.isFinite(number)) {
			throw new IllegalArgumentException(field + " 가 유한한 숫자가 아니다: " + value);
		}
		return number;
	}

	private static boolean bool(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isBoolean()) {
			throw new IllegalArgumentException(field + " 는 true 나 false 여야 한다: " + value);
		}
		return value.asBoolean();
	}

	/** 정수 칸. 없거나 null 이면 null. 정수가 아니거나 int 범위 밖이면 던진다. */
	private static Integer integerOrNull(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw new IllegalArgumentException(field + " 는 정수거나 null 이어야 한다: " + value);
		}
		return value.asInt();
	}

	private static int nonNegativeInt(JsonNode node, String field) {
		Integer value = integerOrNull(node, field);
		if (value == null || value < 0) {
			throw new IllegalArgumentException(field + " 는 0 이상 정수여야 한다: " + node.path(field));
		}
		return value;
	}
}
