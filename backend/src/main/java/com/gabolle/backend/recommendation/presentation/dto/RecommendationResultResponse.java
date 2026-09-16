package com.gabolle.backend.recommendation.presentation.dto;

import java.util.List;

import com.gabolle.backend.recommendation.domain.FallbackMode;

/**
 * 추천 결과 응답 — S15P21E201-604. 프론트가 이미 코드에 박아 놓고 부르는 모양을 그대로
 * 따른다({@code RecommendationResultController} 의 javadoc 참고).
 *
 * <p>🔴 2026-09-07 추가 — "필드를 추가·삭제하지 않는다"는 원칙을 여기서 깬다. 완료 여행표를
 * 만들던 진미리 님이 이 응답만으로는 방문지 수·예상 이동시간을 못 채운다고 알려왔고
 * ({@code placeCount}·{@code estimatedTravelMinutes} 필요), 그 값을 요청한 사람이 이
 * 응답의 실제 소비자라 이번엔 지어내는 추가가 아니다.
 */
public record RecommendationResultResponse(
		/** {@code COMPLETED} · {@code PARTIAL} · {@code FAILED}. */
		String status,
		List<Item> items,
		String itineraryId,
		FallbackMode fallbackMode,
		List<String> conflicts,
		String errorMessage,
		/** {@code items.size()} 와 같다 — 화면이 매번 배열 길이를 세지 않아도 되게 미리 준다. */
		int placeCount,
		/**
		 * 🔴 일정의 구간(leg)마다 있는 {@code duration_min} 합이다. 값이 없는 구간(모름)은
		 * 더하지 않고 건너뛴다 — 그래서 이 값은 "적어도 이만큼"이지 정확한 총합이 아닐 수
		 * 있다. 구간이 하나도 없거나(일정이 아직 없음) 전부 값이 없으면 {@code null}이다.
		 */
		Integer estimatedTravelMinutes,
		/**
		 * 🔴 이 결과를 만든 추천 요청의 정본 키 — 2026-09-07 추가 (S15P21E201-735).
		 *
		 * <p>이 화면에서 저장·제외를 누르면 앱이 행동 이벤트를 보내는데, 그 이벤트는
		 * {@code requestId} 로만 "무엇을 보여줬고 그중 무엇을 골랐나" 와 이어진다(-542 14장).
		 * 이 칸이 없던 동안 앱은 <b>이을 열쇠를 받을 방법이 없는 채로</b> 그 열쇠를 요구받았다.
		 *
		 * <p>결과가 실패({@code FAILED})여도 채운다 — 실패한 요청도 그 요청이다.
		 */
		String requestId,
		/**
		 * 🔴 이 추천이 어느 여행의 것인가 — 2026-09-16 추가 (S15P21E201-1084).
		 *
		 * <p>앱의 추천 화면 주소는 {@code /trips/{jobId}/recommendations?jobId={jobId}} 다. 경로의
		 * 칸에 들어 있는 것이 <b>작업 번호이지 여행 번호가 아니다</b>(생성 화면이 그렇게 보낸다).
		 * 그래서 앱은 담아두기·빼기를 보낼 주소
		 * ({@code /api/v1/trips/{tripId}/recommendation-actions}, S15P21E201-1013)를 알 수 없었고,
		 * 그 판단이 기기에만 남아 <b>동행자가 서로의 판단을 못 봤다</b>.
		 *
		 * <p>{@code recommendation_job.trip_id} 는 이미 있는 칸이다. 없던 값을 만든 것이 아니라
		 * <b>있던 값을 공개한 것</b>이다 — {@code requestId} 를 실을 때(-735)와 같은 이유다.
		 *
		 * <p>실패({@code FAILED}) 응답에서도 채운다. 실패한 요청도 그 여행의 요청이다.
		 * 여행에 매이지 않은 작업이면 {@code null} 이다 — 지어내지 않는다.
		 */
		String tripId) {

	public record Item(
			/** {@code place_id}. */
			String id,
			/** {@code place.name_ko}. */
			String title,
			/** 🔴 항상 {@code null} — {@code place} 표에 이미지 칸이 없다. */
			String imageUrl,
			List<String> reasonCodes,
			/** 🔴 항상 {@code null} — 비용 데이터가 없다. {@code 0} 을 넣으면 "공짜"로 읽힌다. */
			Integer estimatedCostKrw,
			/** {@code LOW} · {@code MEDIUM} · {@code HIGH}. 근거가 없으면 {@code null}. */
			String crowdLevel,
			/** 이동 관련 경고만. 없으면 {@code null}(빈 배열이 아니다). */
			List<String> mobilityWarnings,
			/** {@code VERIFIED} · {@code ESTIMATED} · {@code UNKNOWN}. */
			String dataStatus,
			FallbackMode fallbackMode,
			String itineraryId) {
	}
}
