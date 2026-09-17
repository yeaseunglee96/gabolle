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
 * 취향 답({@code preference_answer.value})의 값 모양을 읽는다 (S15P21E201-604 · -635).
 *
 * <h2>🔴 앱이 보내는 모양은 가정과 달랐다 — 그리고 조용히 0점이 됐다 (S15P21E201-635)</h2>
 *
 * 이 클래스는 태그형이 {@code {"codes": [...]}} 로, 점수형이 {@code {"score": 0.7}} 로 온다고
 * <b>가정</b>하고 있었다. 실제 앱({@code frontend/src/api/tripApi.ts})은 이렇게 보낸다:
 *
 * <table border="1">
 * <caption>앱이 실제로 보내는 값</caption>
 * <tr><th>차원</th><th>앱이 보내는 것</th><th>고치기 전 결과</th></tr>
 * <tr><td>{@code CATEGORY}·{@code ATMOSPHERE}·{@code FOOD_PREFERENCE}</td>
 *     <td>맨 배열 {@code ["SEA_BEACH","FOOD"]}</td>
 *     <td>{@code path("codes")} 가 비어 <b>언제나 빈 목록</b> → 태그 항이 통째로 빠졌다</td></tr>
 * <tr><td>{@code LOCALITY}·{@code QUIETNESS}·{@code TOURIST_PREFERENCE}</td>
 *     <td>맨 정수 <b>1~5</b> (화면의 5단계 슬라이더)</td>
 *     <td>채점기가 0~1 로 알고 {@code 1 - |장소값 - 3|} 을 계산 → clamp01 이 <b>전부 0</b> 으로 뭉갰다</td></tr>
 * </table>
 *
 * <p>둘 다 <b>오류가 나지 않는다</b>. 사용자는 분명히 취향을 골랐는데 점수에는 반영되지 않고,
 * 로그에도 아무것도 안 남는다. 그것이 이 결함이 오래 살아남은 이유다.
 *
 * <h2>🔴 어느 쪽 눈금으로 맞췄나 — 0~1 이다</h2>
 *
 * 앱을 못 고쳐서가 아니라(그쪽도 고칠 수는 있다) <b>맞은편이 못 움직이기 때문</b>이다.
 * 비교 대상인 장소 쪽 점수 피처({@code LOCALITY_SCORE}·{@code QUIETNESS_SCORE}·
 * {@code TOURIST_RATIO}·{@code SHADE_SCORE})가 0~1 이고, {@code SLOPE_PERCENT} 만 0~100
 * 이라 채점기가 100 으로 나눠 그 축에 맞춘다. 즉 <b>0~1 이 이미 이 계산의 기준 눈금</b>이고,
 * 1~5 로 옮기려면 장소 피처 14종과 그 CHECK 를 전부 바꿔야 한다. 그리고 앱은 배포된 판을
 * 즉시 못 고친다 — 서버가 받아 주는 쪽이 옳다({@code PreferenceDimensions} 가 차원 이름에서
 * 내린 것과 같은 결론이다, S15P21E201-665).
 *
 * <h2>🔴 2026-09-17 — 낱말로 오는 답 중 경사만 풀었다 (S15P21E201-1188)</h2>
 *
 * {@code SLOPE_PREFERENCE}·{@code SHADE_PREFERENCE} 는 앱이 숫자가 아니라 <b>낱말</b>을 보낸다
 * ({@code "AVOID"}/{@code "ALLOW"}, {@code "PREFER"}/{@code "NO_PREFERENCE"}). 눈금 문제가 아니라
 * <b>어휘</b> 문제라 앞선 판(-635)은 둘 다 {@code null} 로 두고 사람에게 넘겼다. 그 사이
 * <b>경사만 값이 붙었는데도 계속 안 쓰이고 있었다</b> — 장소 쪽은 운영에 2,682곳이 들어가
 * 있고 설문 실측 비중도 0.21 인데, 답을 못 읽어 축이 매번 통째로 빠졌다. 그래서 경사의
 * 어휘를 사람이 정했다({@link #wordScore}).
 *
 * <p><b>그늘은 그대로 둔다.</b> 앱에 그늘을 묻는 화면이 아직 없어 여기만 고쳐도 살지 않고,
 * {@code SHADE_SCORE} 가 큰 쪽이 "그늘이 많다" 인지 "볕이 잘 든다" 인지 어디에도 안 적혀 있다.
 * 반대로 짝지으면 <b>정반대로 학습되면서 아무 오류도 안 난다.</b> 비중 0.39 는 값이 구해진
 * 축들로 다시 정규화되며 자동으로 재분배되므로, 미뤄 두어도 남은 축이 손해를 보지 않는다.
 *
 * <p>🔴 <b>{@link #parseScore(String, ObjectMapper)}(차원 없는 판)은 일부러 그대로 뒀다.</b>
 * 취향 벡터를 접는 배치({@code TasteVectorFoldService})가 그것을 쓰는데, 거기까지 넓히면
 * 부탁받지 않은 행이 {@code user_taste_weight} 에 새로 쌓인다. 어휘는 <b>차원을 알 때만</b>
 * 푼다 — 빠뜨린 것이 아니라 여기까지가 이번에 정해진 것이다.
 *
 * <p>{@code BaselineCandidateTranslator}·{@code BaselineCandidateScorer} 가 함께 쓴다 —
 * 취향 답을 읽는 방법이 둘로 갈리면 "카테고리 필터에 쓴 코드" 와 "관심 태그 점수에 쓴 코드"
 * 가 조용히 달라질 수 있다.
 *
 * <h2>🔴 2026-09-14 — 셋째 사용자가 생겨서 자리를 옮겼다 (S15P21E201-787 후속)</h2>
 *
 * 원래 이 클래스는 {@code recommendation.adapter} 안의 package-private 이었다. 그래서
 * {@code TasteVectorFoldService}(설문·행동을 취향 벡터로 접는 배치)는 이것을 못 쓰고
 * <b>같은 파싱을 자기 안에 다시 썼다</b> — 그리고 그 사본은 위의 -635 수정을 못 받은
 * <b>고치기 전 판</b>이었다. 결과는 이 문서 맨 위 표와 똑같다: 태그형은 한 줄도 안 접히고,
 * 점수형은 1~5 를 0~1 로 알고 계산해 다섯 답이 <b>전부 같은 값</b>이 됐다. 아무 오류도 안 났다.
 *
 * <p>그래서 {@code preference} 아래로 옮겨 공개한다. 배치가 {@code recommendation.adapter} 를
 * 들여다보는 것은 방향이 반대이고, 무엇보다 <b>같은 규칙이 두 벌이면 한쪽만 고쳐진다</b> —
 * 이번이 그 증거다.
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
	 * 태그형 답의 코드 목록. 두 모양을 <b>둘 다</b> 받는다 (S15P21E201-635).
	 *
	 * <ul>
	 * <li>{@code ["SEA_BEACH","FOOD"]} — 앱이 실제로 보내는 모양</li>
	 * <li>{@code {"codes":["SEA_BEACH","FOOD"]}} — 이 클래스가 원래 기대하던 모양.
	 *     기존 테스트와 다른 클라이언트가 이 모양을 쓴다</li>
	 * </ul>
	 *
	 * <p>🔴 <b>한쪽을 버리지 않는 이유</b>는 {@code PreferenceDimensions} 와 같다 — 이미 저장된
	 * 답과 이미 배포된 앱이 둘 다 있고, 어느 쪽도 "지금 당장" 고칠 수 없다.
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

	/** 앱 화면의 5단계 슬라이더 최솟값·최댓값 ({@code taste.tsx} 의 1~5). */
	private static final int LIKERT_MIN = 1;

	private static final int LIKERT_MAX = 5;

	/**
	 * 점수형 답의 값을 <b>0~1 로</b> 돌려준다 (S15P21E201-635).
	 *
	 * <table border="1">
	 * <caption>받는 모양</caption>
	 * <tr><th>들어오는 값</th><th>무엇으로 읽나</th><th>나가는 값</th></tr>
	 * <tr><td>{@code 3} (정수 1~5)</td><td>앱의 5단계 슬라이더</td><td>{@code (3-1)/4 = 0.5}</td></tr>
	 * <tr><td>{@code 0.7} (소수)</td><td>이미 0~1</td><td>{@code 0.7}</td></tr>
	 * <tr><td>{@code {"score":0.7}}</td><td>이미 0~1</td><td>{@code 0.7}</td></tr>
	 * <tr><td>{@code "AVOID"} (경사)</td><td>어휘 — 차원을 알 때만 푼다</td><td>{@code 0.0} = 평지 선호</td></tr>
	 * <tr><td>{@code "PREFER"} 같은 그 밖의 낱말</td><td>어휘가 아직 안 정해졌다</td><td>{@code null}</td></tr>
	 * </table>
	 *
	 * <p>🔴 <b>정수인가 소수인가로 눈금을 가른다.</b> 앱은 이 세 축에 언제나 <b>정수 1~5</b> 를
	 * 보내고({@code localityLevel}·{@code quietLevel}·{@code touristLevel}), 0~1 눈금으로 쓰는
	 * 값은 {@code 0.7} 처럼 소수이거나 {@code {"score":...}} 로 감싸여 온다. 겹치는 자리는
	 * <b>정수 {@code 1} 하나뿐</b>이고, 그것은 앱의 "가장 낮음" 으로 읽는다 — 이 표에 값을 쓰는
	 * 곳이 지금은 여행 생성(앱 본문) 하나뿐이라 그렇게 읽는 것이 실제와 맞는다.
	 *
	 * <p>못 읽거나 안 골랐으면 {@code null} 이다 — <b>0 으로 채우지 않는다.</b> 0 은 "가장 낮게
	 * 답했다" 는 뜻이고 {@code null} 은 "이 축을 안 본다" 는 뜻이라, 채점기에서 전혀 다르게
	 * 동작한다(전자는 항이 들어가고 후자는 항이 빠진다).
	 */
	public static Double parseScore(String valueJson, ObjectMapper objectMapper) {
		return parseScore(valueJson, objectMapper, null);
	}

	/**
	 * 같은 것을 읽되 <b>어느 차원의 답인지를 알고</b> 읽는다 (S15P21E201-1188).
	 *
	 * <p>숫자로 오는 답은 차원을 몰라도 읽을 수 있지만 <b>낱말은 못 읽는다</b> —
	 * {@code "AVOID"} 가 무엇을 뜻하는지는 그 문항이 무엇을 물었나에 달려 있다. 그래서
	 * 어휘는 차원을 아는 이 판에서만 푼다.
	 *
	 * @param dimension {@code SLOPE_PREFERENCE} 등. {@code null} 이면 낱말을 안 푼다
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
		// 🔴 감싼 모양은 이 엔진이 정한 0~1 계약이라 눈금을 다시 바꾸지 않는다.
		return score.isNumber() ? clamp01(score.doubleValue()) : null;
	}

	/** 경사 문항의 답 — 앱의 {@code SlopeAnswer} 와 글자 그대로 같아야 한다. */
	private static final String SLOPE_AVOID = "AVOID";

	/** 경사 문항의 답 — 「상관없어요」. */
	private static final String SLOPE_ALLOW = "ALLOW";

	/**
	 * 낱말로 온 답을 0~1 로 옮긴다 (S15P21E201-1188).
	 *
	 * <h2>🔴 눈금은 0~1 이다. 0~100 이 아니다</h2>
	 *
	 * 경사만 <b>장소</b> 값이 0~100 퍼센트이고, 채점기가 그쪽을 100 으로 나눠 이 축에 맞춘다
	 * ({@code BaselineCandidateScorer} 의 {@code placeValueIsPercent}). 즉 <b>기준 눈금은
	 * 언제나 0~1</b> 이라 여기서 100 을 내놓으면 {@code clamp01} 이 1.0 으로 뭉갠다.
	 *
	 * <h2>🔴 「상관없어요」는 0 이 아니라 {@code null} 이다</h2>
	 *
	 * 0 은 "가장 낮게 답했다" = <b>평지를 가장 원한다</b> 는 뜻이라 「피하고 싶어요」와 같아진다.
	 * 「상관없어요」는 <b>이 축을 안 본다</b> 는 뜻이고, 그건 항이 빠지는 것으로만 표현된다.
	 * 반대쪽 끝(1.0 = 급경사를 가장 원한다)으로 읽지 않는 이유도 같다 — 안 따진다고 답한
	 * 사람에게 <b>가파른 곳을 골라서</b> 보여주게 되고, 그러면서 아무 오류도 안 난다.
	 *
	 * @param dimension 답이 속한 차원. {@code null} 이거나 어휘를 모르는 차원이면 안 푼다
	 * @param word 앱이 보낸 낱말
	 * @return 0~1 점수, 또는 안 보기로 한 축이면 {@code null}
	 */
	private static Double wordScore(String dimension, String word) {
		// 🔴 경사 말고는 어휘가 아직 안 정해졌다. 모르는 어휘를 지어내느니 축을 뺀다 —
		//    그늘이 그 자리다(클래스 머리말 참고). 여기서 경고를 내지 않는 것은,
		//    "아직 안 정했다" 는 결함이 아니라 결정이기 때문이다.
		if (!TasteDimension.SLOPE_PREFERENCE.name().equals(dimension) || word == null) {
			return null;
		}
		String normalized = word.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case SLOPE_AVOID -> 0.0;
			case SLOPE_ALLOW -> null;
			default -> {
				// 🔴 마지막 가지는 "나머지 전부" 가 아니라 "모르는 값" 이다. 앱이 새 낱말을
				//    보내기 시작하면 축이 조용히 사라지는데, 그게 이 티켓의 결함 그 자체였다.
				//    같은 일이 또 나면 이 줄이 알려 준다.
				log.warn("경사 취향 답의 낱말을 모른다 — 축을 뺀다. word={}", normalized);
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
