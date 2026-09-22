package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 하루 안에서 방문 순서를 바꾼다.
 * "이 항목을 3번째로" 가 아니라 그날의 전체 순서를 받는다. 한 항목만 옮기게 하면 같은 날을
 * 둘이 동시에 옮길 때 결과가 요청 순서에 달라진다 — A 를 3번으로, B 를 3번으로 옮기는 요청이
 * 겹치면 나중 것이 이기는 게 아니라 둘 다 반영된 이상한 순서가 남는다.
 * 전체 순서를 통째로 받으면 서버는 그것이 그날의 항목 전부와 정확히 일치하는지만 보면 된다.
 * 겹치는 편집은 {@code baseVersion} 이 이미 막는다.
 *
 * @param itemKeys 그날의 항목을 원하는 순서대로 전부. 하나라도 빠지거나 남으면 거부한다 —
 *        빠진 것을 조용히 뒤에 붙이면 사용자는 자기가 안 보낸 순서를 받는다
 * @param baseVersion 이 편집이 바탕으로 삼은 판. 그 사이 다른 편집이 있었으면 409
 */
public record ReorderDayRequest(
		@NotEmpty(message = "바꿀 순서가 비어 있습니다.") List<@NotNull String> itemKeys,
		@NotNull(message = "baseVersion 이 필요합니다.") Integer baseVersion) {
}
