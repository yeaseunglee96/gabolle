package com.gabolle.backend.preference.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gabolle.backend.preference.domain.TasteDimension;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 취향 답({@code preference_answer.value})의 값 모양을 읽는다.
 *
 * <p>앱이 보내는 모양은 차원마다 다르다. 태그형은 맨 배열이거나 {@code {"codes":[...]}} 이고,
 * 점수형은 맨 정수 1~5(화면의 5단계 슬라이더)이거나 0~1 소수, 경사·그늘은 낱말이다.
 * 어느 쪽도 못 읽으면 오류 없이 항이 통째로 빠지므로 여기서 전부 받는다.
 *
 * <p>나가는 눈금은 0~1 이다. 비교 대상인 장소 쪽 점수 피처가 0~1 이고
 * {@code SLOPE_PERCENT} 만 0~100 이라 채점기가 그쪽을 100 으로 나눠 맞춘다. 1~5 로 옮기려면
 * 장소 피처 전부와 그 CHECK 를 바꿔야 한다.
 *
 * <p>취향 답을 읽는 방법이 둘로 갈리면 "카테고리 필터에 쓴 코드" 와 "관심 태그 점수에 쓴 코드"
 * 가 조용히 달라지므로, 채점기와 배치가 모두 이 클래스를 쓴다.
 */
public final class PreferenceJson {

	private static final Logger log = LoggerFactory.getLogger(PreferenceJson.class);

	private PreferenceJson() {
	}

	/** 태그형 답의 코드 목록. 그 차원을 안 골랐으면(SELECTED 가 아니면) 빈 목록이다. 모양은 {@link #parseCodes}. */
	public static List<String> codesFor(PreferenceSnapshot snapshot, String dimension, ObjectMapper objectMapper) {
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

	/** 점수형 답의 값(0~1). 못 읽거나 안 골랐으면 {@code null} 이다 — 0 으로 채우지 않는다. {@link #parseScore}. */
	public static Double scoreFor(PreferenceSnapshot snapshot, String dimension, ObjectMapper objectMapper) {
		if (snapshot == null) {
			return null;
		}
		for (PreferenceSnapshot.PreferenceAnswer answer : snapshot.answers()) {
			if (dimension.equals(answer.dimension()) && answer.status() == PreferenceSnapshot.AnswerStatus.SELECTED) {
				return parseScore(answer.valueJson(), objectMapper, dimension);
			}
		}
		return null;
	}

	/**
	 * 태그형 답의 코드 목록. 맨 배열({@code ["SEA_BEACH","FOOD"]})과 감싼 모양
	 * ({@code {"codes":[...]}})을 둘 다 받는다 — 이미 저장된 답과 이미 배포된 앱이 둘 다 있고
	 * 어느 쪽도 지금 당장 고칠 수 없다.
	 */
	public static List<String> parseCodes(String valueJson, ObjectMapper objectMapper) {
		JsonNode node = readTree(valueJson, objectMapper);
		if (node == null) {
			return List.of();
		}
		JsonNode codes = node.isArray() ? node : node.path("codes");
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

	/** 앱 화면의 5단계 슬라이더 최솟값·최댓값. */
	private static final int LIKERT_MIN = 1;

	private static final int LIKERT_MAX = 5;

	/**
	 * 점수형 답의 값을 0~1 로 돌려준다.
	 *
	 * <p>정수인가 소수인가로 눈금을 가른다. 앱은 슬라이더 축에 언제나 정수 1~5 를 보내고,
	 * 0~1 눈금으로 쓰는 값은 소수이거나 {@code {"score":...}} 로 감싸여 온다. 겹치는 자리는
	 * 정수 {@code 1} 하나뿐이고 그것은 슬라이더의 "가장 낮음" 으로 읽는다.
	 *
	 * <p>못 읽거나 안 골랐으면 {@code null} 이다 — 0 으로 채우지 않는다. 0 은 "가장 낮게 답했다"
	 * 이고 {@code null} 은 "이 축을 안 본다" 라서, 전자는 항이 들어가고 후자는 항이 빠진다.
	 */
	public static Double parseScore(String valueJson, ObjectMapper objectMapper) {
		return parseScore(valueJson, objectMapper, null);
	}

	/**
	 * 같은 것을 읽되 어느 차원의 답인지를 알고 읽는다. 숫자는 차원을 몰라도 읽지만 낱말은
	 * 못 읽는다 — {@code "AVOID"} 의 뜻은 그 문항이 무엇을 물었나에 달려 있다.
	 *
	 * @param dimension {@code null} 이면 낱말을 안 푼다
	 */
	public static Double parseScore(String valueJson, ObjectMapper objectMapper, String dimension) {
		JsonNode node = readTree(valueJson, objectMapper);
		if (node == null) {
			return null;
		}
		if (node.isNumber()) {
			return normalizeScore(node);
		}
		if (node.isTextual()) {
			return wordScore(dimension, node.textValue());
		}
		JsonNode score = node.path("score");
		// 감싼 모양은 이미 0~1 계약이라 눈금을 다시 바꾸지 않는다.
		return score.isNumber() ? clamp01(score.doubleValue()) : null;
	}

	/** 경사 문항의 답 — 앱의 {@code SlopeAnswer} 와 글자 그대로 같아야 한다. */
	private static final String SLOPE_AVOID = "AVOID";

	/** 경사 문항의 답 — 「상관없어요」. */
	private static final String SLOPE_ALLOW = "ALLOW";

	/** 그늘 문항의 답 — 「그늘길 우선」을 켠 것. 앱의 문자열과 글자 그대로 같아야 한다. */
	private static final String SHADE_PREFER = "PREFER";

	/** 그늘 문항의 답 — 「상관없어요」(스위치를 끈 것). */
	private static final String SHADE_NO_PREFERENCE = "NO_PREFERENCE";

	/**
	 * 낱말로 온 답을 0~1 로 옮긴다. 0~100 이 아니다 — 경사만 장소 값이 퍼센트이고 채점기가
	 * 그쪽을 100 으로 나눠 맞추므로, 여기서 100 을 내놓으면 {@code clamp01} 이 1.0 으로 뭉갠다.
	 *
	 * <p>「상관없어요」는 0 이 아니라 {@code null} 이다. 0 은 "가장 낮게 답했다" 라서
	 * 「피하고 싶어요」와 같아지고, 반대쪽 끝으로 읽으면 안 따진다고 답한 사람에게 그 극단을
	 * 골라서 보여준다. 둘 다 아무 오류도 안 난다.
	 *
	 * @param dimension {@code null} 이거나 어휘를 모르는 차원이면 안 푼다
	 * @return 0~1 점수, 또는 안 보기로 한 축이면 {@code null}
	 */
	private static Double wordScore(String dimension, String word) {
		if (word == null) {
			return null;
		}
		String normalized = word.trim().toUpperCase(Locale.ROOT);
		if (TasteDimension.SLOPE_PREFERENCE.name().equals(dimension)) {
			return slopeWord(normalized);
		}
		if (TasteDimension.SHADE_PREFERENCE.name().equals(dimension)) {
			return shadeWord(normalized);
		}
		// 이 둘 말고는 어휘가 아직 안 정해졌다. 지어내느니 축을 뺀다. 경고를 안 내는 것은
		// "아직 안 정했다" 가 결함이 아니라 결정이기 때문이다.
		return null;
	}

	/** 경사 — 「피하고 싶어요」는 평지 선호(0.0), 「상관없어요」는 이 축을 안 본다. */
	private static Double slopeWord(String normalized) {
		return switch (normalized) {
			case SLOPE_AVOID -> 0.0;
			case SLOPE_ALLOW -> null;
			default -> {
				// 마지막 가지는 "나머지 전부" 가 아니라 "모르는 값" 이다. 앱이 새 낱말을 보내기
				// 시작하면 축이 조용히 사라지는데, 이 줄이 그것을 알려 준다.
				log.warn("경사 취향 답의 낱말을 모른다 — 축을 뺀다. word={}", normalized);
				yield null;
			}
		};
	}

	/**
	 * 그늘 — 「그늘길 우선」은 그늘이 가장 많은 곳(1.0), 「상관없어요」는 이 축을 안 본다.
	 *
	 * <p>{@code 1.0} 이 「그늘 많음」인 것은 장소 쪽 {@code SHADE_SCORE} 가 0~1 이고 클수록
	 * 그늘이 많기 때문이다. 경사와 달리 양쪽 다 0~1 이라 100 으로 나누는 자리가 없다.
	 */
	private static Double shadeWord(String normalized) {
		return switch (normalized) {
			case SHADE_PREFER -> 1.0;
			case SHADE_NO_PREFERENCE -> null;
			default -> {
				log.warn("그늘 취향 답의 낱말을 모른다 — 축을 뺀다. word={}", normalized);
				yield null;
			}
		};
	}

	private static Double normalizeScore(JsonNode node) {
		if (node.isIntegralNumber()) {
			int raw = node.intValue();
			if (raw >= LIKERT_MIN && raw <= LIKERT_MAX) {
				return (raw - (double) LIKERT_MIN) / (LIKERT_MAX - LIKERT_MIN);
			}
			// 1~5 밖의 정수는 어느 눈금인지 알 수 없다. 지어내지 않고 0~1 로 가둔다.
			return clamp01(raw);
		}
		return clamp01(node.doubleValue());
	}

	private static double clamp01(double value) {
		return Math.max(0.0, Math.min(1.0, value));
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
