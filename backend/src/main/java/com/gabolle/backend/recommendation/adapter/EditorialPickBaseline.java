package com.gabolle.backend.recommendation.adapter;

import java.util.UUID;

/**
 * Editor's Pick 하나로 만든 기준선 (S15P21E201-555).
 *
 * <p>🔴 {@code pickId} 를 함께 들고 다니는 것이 요점이다. {@code editorial_pick} 은
 * (이름, 판) 마다 다른 행이므로 이 하나가 <b>어느 Pick 의 어느 판이었는지</b>를 가리킨다.
 * {@code recommendation_job.editorial_pick_id} 에 그대로 들어가고, 나중에 "그때 무엇을
 * 보여줬나" 를 되짚는 유일한 통로다.
 *
 * @param pickId 발행본 판의 ID
 * @param pickKey 사람이 부르는 이름 — 로그·지표에서 읽기 위한 것이고 정본은 {@code pickId} 다
 * @param contentVersion 그 이름의 몇 번째 판인가
 * @param batch 제약 판정까지 끝난 후보들. <b>편집자가 정한 순서</b>로 담겨 있다
 */
public record EditorialPickBaseline(
		UUID pickId,
		String pickKey,
		int contentVersion,
		EngineCandidateBatch batch) {
}
