package com.gabolle.backend.help.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.gabolle.backend.help.domain.HelpKind;
import com.gabolle.backend.help.domain.HelpPlace;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 부산 가까운 도움 자료 — 자원 파일({@value #RESOURCE})을 한 번 읽어 들고 있는다(S15P21E201-1893).
 *
 * <p>🔴 DB 표가 아니라 자원 파일인 이유: 4,500곳 남짓이라 메모리에 두고 전부 재도 가볍고, 기존 장소·추천 표와 섞이지
 * 않는다 — 병원·약국은 여행 후보가 아니다. 새 분기 자료로 바꿀 때는 {@code docs/help-places/} 의 스크립트로 파일만
 * 다시 만든다(분기마다 심평원이 갱신한다).
 *
 * <p>출처: 병원·의원·약국은 건강보험심사평가원 「전국 병의원 및 약국 현황」(공공누리 제1유형·출처표시),
 * 경찰은 © OpenStreetMap 기여자(ODbL). 자료의 {@code source} 와 {@code basedOn} 을 응답에 그대로 싣는다.
 */
@Component
public class HelpPlaceCatalog {

	static final String RESOURCE = "help/busan-help-places.json";

	private static final Map<String, DayOfWeek> DAY_KEYS = Map.of(
			"mon", DayOfWeek.MONDAY, "tue", DayOfWeek.TUESDAY, "wed", DayOfWeek.WEDNESDAY, "thu", DayOfWeek.THURSDAY,
			"fri", DayOfWeek.FRIDAY, "sat", DayOfWeek.SATURDAY, "sun", DayOfWeek.SUNDAY);

	private final Map<HelpKind, List<HelpPlace>> byKind;

	private final String source;

	private final String basedOn;

	public HelpPlaceCatalog() {
		this(RESOURCE);
	}

	HelpPlaceCatalog(String resource) {
		JsonNode root = read(resource);
		this.source = root.path("source").asString("");
		this.basedOn = root.path("basedOn").asString("");
		Map<HelpKind, List<HelpPlace>> places = new EnumMap<>(HelpKind.class);
		for (HelpKind kind : HelpKind.values()) {
			places.put(kind, new ArrayList<>());
		}
		for (JsonNode node : root.path("places")) {
			HelpPlace place = parse(node);
			if (place != null) {
				places.get(place.kind()).add(place);
			}
		}
		places.replaceAll((kind, list) -> Collections.unmodifiableList(list));
		this.byKind = Collections.unmodifiableMap(places);
	}

	public List<HelpPlace> places(HelpKind kind) {
		return this.byKind.get(kind);
	}

	public String source() {
		return this.source;
	}

	/** 자료 기준일(「2026-06-30」) */
	public String basedOn() {
		return this.basedOn;
	}

	private static HelpPlace parse(JsonNode node) {
		HelpKind kind;
		try {
			kind = HelpKind.valueOf(node.path("kind").asString(""));
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
		String name = text(node, "name");
		if (name == null || !node.path("lat").isNumber() || !node.path("lng").isNumber()) {
			return null;
		}
		Map<DayOfWeek, HelpPlace.DayHours> hours = new EnumMap<>(DayOfWeek.class);
		JsonNode hoursNode = node.path("hours");
		if (hoursNode.isObject()) {
			for (Map.Entry<String, DayOfWeek> day : DAY_KEYS.entrySet()) {
				JsonNode value = hoursNode.path(day.getKey());
				if (value.isString() && "closed".equals(value.asString())) {
					hours.put(day.getValue(), HelpPlace.DayHours.closedDay());
				}
				else if (value.isArray() && value.size() == 2) {
					hours.put(day.getValue(), new HelpPlace.DayHours(false, value.get(0).asInt(), value.get(1).asInt()));
				}
			}
		}
		return new HelpPlace(kind, text(node, "type"), name, text(node, "nameEn"), text(node, "address"),
				text(node, "phone"), node.path("lat").asDouble(), node.path("lng").asDouble(),
				node.path("emergency").asBoolean(false), Collections.unmodifiableMap(hours));
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isString()) {
			return null;
		}
		String text = value.asString().trim();
		return text.isEmpty() ? null : text;
	}

	private static JsonNode read(String resource) {
		try (InputStream in = HelpPlaceCatalog.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("가까운 도움 자원이 없다: " + resource);
			}
			return JsonMapper.builder().build().readTree(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("가까운 도움 자원을 못 읽었다: " + resource, ex);
		}
	}
}
