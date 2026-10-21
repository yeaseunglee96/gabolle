package com.gabolle.backend.recommendation.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 「어느 조건이 후보를 다 걷어냈나」를 후보 행에서 되읽는다 — S15P21E201-1468 의 ㄷ.
 *
 * <p><b>왜 필요한가.</b> 꼭 지켜야 하는 조건(알레르기·식단·이동)이 후보를 전부 걷어내면
 * 작업은 {@code RECOMMENDATION_NO_FEASIBLE_RESULT} 로 실패하는데, 화면은 「조건을 조정해
 * 주세요」라고만 말했다. 사용자는 <b>어느 조건인지 모른 채</b> 날짜나 예산을 넓혀 보고 또
 * 실패한다 — 그 둘은 이 실패와 아무 상관이 없다.
 *
 * <p>🟢 <b>새 칸을 만들지 않는다.</b> 답은 이미 저장돼 있다. 실패 경로가 후보 행을 전부
 * 남기고({@code RecommendationService} — <i>"후보 행은 전부 저장한다 — 왜 빈손이었나는 그
 * 행들에만 적혀 있다"</i>), 그 행의 {@code unknown_facts}·{@code violations} 에 무엇이
 * 걸렸는지가 그대로 들어 있다. 2026-09-22 운영 실측으로, 땅콩 알레르기로 실패한 작업의
 * 후보 200건이 전부 {@code ALLERGEN_UNVERIFIED / PEANUT / REQUIRED} 였다.
 *
 * <p>🔴 <b>「맞는 곳이 없다」와 「확인하지 못했다」를 가른다.</b> 지금 운영에
 * {@code ALLERGEN_TAG} 가 0건이라, 서버는 <b>위험한 곳을 골라낸 것이 아니라 안전한 곳인지
 * 확인할 수 없어</b> 전부 뺀 것이다. 둘을 같은 말로 적으면 사용자는 부산에 자기가 먹을 것이
 * 없다고 읽는다. 그래서 {@code reason} 을 따로 싣는다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(RecommendationJobRunner.class)
public class BlockingConstraintAnalyzer {

	/** 「확인하지 못했다」 — {@code unknown_facts} 에서 온다. */
	public static final String REASON_UNVERIFIED = "UNVERIFIED";

	/** 「확인했고 안 된다」 — {@code violations} 에서 온다. */
	public static final String REASON_VIOLATED = "VIOLATED";

	/**
	 * 걸린 코드가 무엇을 뜻하는가 — 어느 갈래이고, 확인 못 한 것인가 확인된 위반인가.
	 *
	 * <p>🔴 <b>표를 둘로 나누지 않는다.</b> 갈래 표와 「위반인 코드」 목록을 따로 두면, 새
	 * 위반 코드를 한쪽에만 적는 날 그 코드가 <b>확인된 위반인데 「확인 못 함」으로</b>
	 * 나간다 — 이 클래스가 지키려는 구분이 바로 그것이라, 그 어긋남은 기능을 조용히
	 * 뒤집는다. 한 줄에 둘 다 적어 두면 빠뜨릴 수가 없다.
	 *
	 * <p>코드가 갈래를 이름에 담고 있지만 접두어로 자르지 않는다 —
	 * {@code ACCESS_VERIFIED_UNAVAILABLE} 와 {@code ACCESSIBILITY_MAPPING_MISSING} 처럼 같은
	 * 갈래인데 접두어가 다른 것이 이미 있어서, 규칙으로 자르면 그 둘이 갈라진다.
	 */
	private static final Map<String, Meaning> MEANING_BY_CODE = Map.of(
			"ALLERGEN_UNVERIFIED", new Meaning("ALLERGY", REASON_UNVERIFIED),
			"ALLERGEN_MAPPING_MISSING", new Meaning("ALLERGY", REASON_UNVERIFIED),
			"ALLERGEN_PRESENT", new Meaning("ALLERGY", REASON_VIOLATED),
			"DIET_SUPPORT_UNVERIFIED", new Meaning("DIET", REASON_UNVERIFIED),
			"DIET_MAPPING_MISSING", new Meaning("DIET", REASON_UNVERIFIED),
			"DIET_NOT_SUPPORTED", new Meaning("DIET", REASON_VIOLATED),
			"ACCESSIBILITY_MAPPING_MISSING", new Meaning("MOBILITY", REASON_UNVERIFIED),
			"ACCESS_VERIFIED_UNAVAILABLE", new Meaning("MOBILITY", REASON_VIOLATED),
			// 경사가 상한(휠체어 경사로 기준 1:12 = 8.33%)을 넘은 곳. 둘레 길에서 짐작한 값이지만
			// 「경사를 모른다」가 아니라 「재어 보니 가파르다」라서 확인된 위반 쪽이다. 이 줄이 빠져
			// 있던 동안 경사만으로 다 막힌 여행은 갈래 없는 코드로 나가, 화면이 이동 조건 탓인 줄 몰랐다.
			// (Map.of 는 짝 10개까지다 — 하나 더 늘면 Map.ofEntries 로 바꾼다.)
			"SLOPE_OVER_LIMIT", new Meaning("MOBILITY", REASON_VIOLATED));

	/**
	 * 코드 하나의 뜻.
	 *
	 * @param constraintType 조건 갈래. 모르는 코드면 이 기록 자체가 없다
	 * @param reason 확인 못 함인가 확인된 위반인가
	 */
	private record Meaning(String constraintType, String reason) {
	}

	/** 이 단계·이 코드로 실패했을 때만 답이 있다. 그 밖에는 질의조차 하지 않는다. */
	private static final Set<JobStage> EXPLAINABLE_STAGES = Set.of(JobStage.CONSTRAINT_EVALUATION);

	private final RecommendationCandidateRepository candidateRepository;

	private final ObjectMapper objectMapper;

	public BlockingConstraintAnalyzer(RecommendationCandidateRepository candidateRepository,
			ObjectMapper objectMapper) {
		this.candidateRepository = candidateRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * 이 작업을 막은 조건들. 막은 후보가 많은 것부터 온다.
	 *
	 * <p>설명할 수 없는 작업이면 <b>빈 목록</b>이다 — 성공했거나, 다른 코드로 실패했거나,
	 * 제약 판정 단계가 아닌 곳에서 멈췄다. 그때 지어내지 않는다.
	 *
	 * @return 막은 조건 목록. 비어 있으면 화면은 예전처럼 갈래를 뭉뚱그려 말한다
	 */
	@Transactional(readOnly = true)
	public List<Blocking> analyze(RecommendationJob job) {
		if (!explainable(job)) {
			return List.of();
		}

		List<RecommendationCandidate> candidates = this.candidateRepository
				.findByRequestIdOrderByFinalRankAscPlaceIdAsc(job.getRequestId());
		if (candidates.isEmpty()) {
			return List.of();
		}

		// 같은 조건이 한 후보 안에서 두 번 적혀 있어도 한 번만 센다. 안 그러면 "막은 후보 수"
		// 가 실제 후보 수를 넘어서, 화면이 "200곳 중 240곳이 걸렸다" 같은 말을 하게 된다.
		Map<Key, Integer> counts = new LinkedHashMap<>();
		for (RecommendationCandidate candidate : candidates) {
			Set<Key> keysOfThisCandidate = new LinkedHashSet<>();
			collect(candidate.getUnknownFacts(), "fact", keysOfThisCandidate);
			collect(candidate.getViolations(), "code", keysOfThisCandidate);
			for (Key key : keysOfThisCandidate) {
				counts.merge(key, 1, Integer::sum);
			}
		}

		List<Blocking> blocking = new ArrayList<>(counts.size());
		counts.forEach((key, count) -> {
			// 모르는 코드는 갈래도 사유도 지어내지 않는다. 사유를 임의로 채우면 확인된 위반이
			// "확인 못 함" 으로 둔갑할 수 있다 — 그 구분이 이 기능의 전부다.
			Meaning meaning = MEANING_BY_CODE.get(key.code());
			blocking.add(new Blocking(
					(meaning == null) ? null : meaning.constraintType(), key.constraintKey(),
					(meaning == null) ? null : meaning.reason(), key.code(), count));
		});

		// 많이 막은 것이 먼저다. 같으면 코드 이름으로 — 순서가 실행마다 흔들리면 화면 문구가
		// 새로고침할 때마다 바뀐다.
		blocking.sort(Comparator.comparingInt(Blocking::blockedCandidates).reversed()
				.thenComparing(Blocking::code)
				.thenComparing(block -> String.valueOf(block.constraintKey())));
		return List.copyOf(blocking);
	}

	/** 이 작업이 「어느 조건 때문인가」에 답할 수 있는 모양인가. */
	private static boolean explainable(RecommendationJob job) {
		return job != null
				&& job.getJobStatus() == JobStatus.FAILED
				&& RecommendationCodes.ERROR_NO_FEASIBLE_RESULT.equals(job.getErrorCode())
				&& EXPLAINABLE_STAGES.contains(job.getFailureStage());
	}

	/**
	 * {@code [{"fact":"ALLERGEN_UNVERIFIED","featureKey":"PEANUT",…}]} 모양의 JSON 에서
	 * (코드, 열쇠) 짝을 꺼낸다.
	 *
	 * <p>값이 깨져 있으면 그 후보 하나만 건너뛰고 설명 전체를 포기하지 않는다 — 설명이
	 * 없는 것보다 일부라도 있는 편이 낫고, 이 경로는 이미 실패한 작업을 읽는 자리다.
	 *
	 * @param codeField 코드가 든 칸 이름. 미확인 사실은 {@code fact}, 위반은 {@code code} 다
	 */
	private void collect(String json, String codeField, Set<Key> into) {
		if (json == null || json.isBlank()) {
			return;
		}
		JsonNode root;
		try {
			root = this.objectMapper.readTree(json);
		}
		catch (JacksonException malformed) {
			return;
		}
		if (!root.isArray()) {
			return;
		}
		for (JsonNode entry : root) {
			String code = entry.path(codeField).asString(null);
			if (code == null || code.isBlank()) {
				continue;
			}
			// 열쇠가 없는 코드도 있다(갈래 전체가 막힌 경우). 그때는 null 로 두고 갈래만 말한다.
			String constraintKey = entry.path("featureKey").asString(null);
			into.add(new Key(code, (constraintKey == null || constraintKey.isBlank()) ? null : constraintKey));
		}
	}

	/** 세는 동안만 쓰는 열쇠. 갈래와 사유는 {@link #MEANING_BY_CODE} 가 정하므로 담지 않는다. */
	private record Key(String code, String constraintKey) {
	}

	/**
	 * 후보를 막은 조건 하나.
	 *
	 * @param constraintType {@code ALLERGY}·{@code DIET}·{@code MOBILITY}. 모르는 코드면
	 *     {@code null} 이다 — 지어내지 않는다. 그때도 {@code code} 는 그대로 실려 나가므로
	 *     화면이 최소한 무엇이 걸렸는지는 말할 수 있다
	 * @param constraintKey 사용자가 고른 값. 예: {@code PEANUT}. 갈래 전체가 막힌 경우 {@code null}
	 * @param reason {@link #REASON_UNVERIFIED} 확인 못 함 · {@link #REASON_VIOLATED} 확인했고 안 됨.
	 *     <b>둘을 같은 말로 쓰면 안 된다</b> — 지금 대부분은 앞쪽이다. 모르는 코드면
	 *     {@code null} 이고, 그때는 화면이 사유를 말하지 않는다
	 * @param code 걸린 원래 코드. 갈래를 모르는 값이 와도 잃지 않게 그대로 싣는다
	 * @param blockedCandidates 이 조건이 막은 후보 수
	 */
	public record Blocking(String constraintType, String constraintKey, String reason, String code,
			int blockedCandidates) {
	}
}
