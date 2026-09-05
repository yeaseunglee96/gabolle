package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.List;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 취향 답({@code preference_answer.value})의 두 가지 값 모양을 읽는다 (S15P21E201-604).
 *
 * <p>🔴 값 모양은 아직 온톨로지가 확정하지 않았다({@code PlaceFeature} 의 같은 문제와 같다).
 * 태그형(예: {@code CATEGORY})은 {@code {"codes": ["SEA", "ALLEY"]}} 로 확인됐다
 * ({@code PersonalizationInputSchemaTest} 실측). 점수형(예: {@code LOCALITY})은 장소 쪽
 * 점수 피처({@code LOCALITY_SCORE} 등)가 이미 쓰고 있는 {@code {"score": 0.7}} 모양을
 * 그대로 따른다고 <b>가정</b>한다 — 같은 축을 비교하는 값이니 같은 모양이 자연스럽고,
 * 실제 화면 계약이 확정되면 이 클래스 하나만 고치면 된다.
 *
 * <p>{@link BaselineCandidateTranslator}·{@link BaselineCandidateScorer} 가 함께 쓴다 —
 * 취향 답을 읽는 방법이 둘로 갈리면 "카테고리 필터에 쓴 코드" 와 "관심 태그 점수에 쓴 코드"
 * 가 조용히 달라질 수 있다.
 */
final class PreferenceJson {

	private PreferenceJson() {
	}

	/** 태그형 답의 코드 목록. 그 차원을 안 골랐으면(SELECTED 가 아니면) 빈 목록이다. */
	static List<String> codesFor(PreferenceSnapshot snapshot, String dimension, ObjectMapper objectMapper) {
		if (snapshot == null) {
			return List.of();
		}
		for (PreferenceSnapshot.PreferenceAnswer answer : snapshot.answers()) {
			if (dimension.equals(answer.dimension()) && answer.status() == PreferenceSnapshot.AnswerStatus.SELECTED) {
				return parseCodes(answer.valueJson(), objectMapper);
			}
		}
		return List.of();
	}

	/** 점수형 답의 값. 못 읽거나 안 골랐으면 {@code null} 이다 — 0 으로 채우지 않는다. */
	static Double scoreFor(PreferenceSnapshot snapshot, String dimension, ObjectMapper objectMapper) {
		if (snapshot == null) {
			return null;
		}
		for (PreferenceSnapshot.PreferenceAnswer answer : snapshot.answers()) {
			if (dimension.equals(answer.dimension()) && answer.status() == PreferenceSnapshot.AnswerStatus.SELECTED) {
				return parseScore(answer.valueJson(), objectMapper);
			}
		}
		return null;
	}

	private static List<String> parseCodes(String valueJson, ObjectMapper objectMapper) {
		JsonNode node = readTree(valueJson, objectMapper);
		if (node == null) {
			return List.of();
		}
		JsonNode codes = node.path("codes");
		List<String> result = new ArrayList<>();
		if (codes.isArray()) {
			for (JsonNode code : codes) {
				String text = code.asText(null);
				if (text != null && !text.isBlank()) {
					result.add(text);
				}
			}
		}
		return result;
	}

	private static Double parseScore(String valueJson, ObjectMapper objectMapper) {
		JsonNode node = readTree(valueJson, objectMapper);
		if (node == null) {
			return null;
		}
		if (node.isNumber()) {
			return node.asDouble();
		}
		JsonNode score = node.path("score");
		return score.isNumber() ? score.asDouble() : null;
	}

	private static JsonNode readTree(String valueJson, ObjectMapper objectMapper) {
		if (valueJson == null || valueJson.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(valueJson);
		}
		catch (JacksonException ex) {
			return null;
		}
	}
}
