package com.gabolle.backend.batch.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 배치 결과. "성공 N 건" 으로 뭉치지 않고 갈래를 나눠 돌려준다 — {@code rebuilt} 가 날마다
 * 사람 수만큼 나오면 접는 규칙이 흔들리는 것이고, {@code failed} 가 0 이 아니면 몇 명이 낡은
 * 채로 남은 것인데 뭉치면 둘 다 안 보인다.
 *
 * @param watermarkAdvanced 성분은 그대로고 표시만 옮긴 사람 수. 평소엔 이것이 가장 많다
 * @param unchanged 이미 이 구간까지 본 사람 수. 같은 구간을 다시 돌린 경우다
 * @param nothingToFold 설문도 행동도 없어 접을 것이 없던 사람 수. 빈 벡터를 만들지 않는다
 * @param failed 실패한 사람 수. 다음 실행에서 다시 걸린다
 * @param failures 실패 이유 요약. 스택은 서버 로그에 있다
 */
public record TasteVectorBatchResponse(OffsetDateTime asOf, int processed, int rebuilt, int watermarkAdvanced,
		int unchanged, int nothingToFold, int failed, List<UUID> failedUserIds, List<String> failures) {
}
