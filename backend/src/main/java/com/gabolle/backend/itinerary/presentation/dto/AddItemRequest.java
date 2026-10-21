package com.gabolle.backend.itinerary.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장소를 일정에 더하는 요청.
 * {@code baseVersion} 은 화면이 보고 있던 판이다. 그 사이 다른 편집이 있었으면 409 다.
 * 세 칸 모두 감싼 타입이다. record 로 요청 본문을 받으면 JSON 에 없는 키가 생성자에
 * {@code null} 로 들어가는데, 그 자리가 원시형({@code int})이면 Jackson 이 거기서 실패하고
 * 응답이 "어느 항목이 빠졌다"(검증 오류)가 아니라 "요청 형식이 올바르지 않습니다"(역직렬화
 * 오류, {@code fields} 가 빈다)로 나가 화면이 무엇을 빠뜨렸는지 알 수 없다. 원시형은 필수를
 * 뜻하지 않고 빠뜨림을 진단 불가하게 만든다.
 *
 * @param placeId 더할 장소
 * @param dayIndex 며칠째에 넣을지. 0 이 첫날이다
 */
public record AddItemRequest(
		@NotNull(message = "더할 장소를 지정해 주세요.") String placeId,
		@NotNull(message = "며칠째에 넣을지 지정해 주세요.")
		@Min(value = 0, message = "며칠째인지는 0 이상이어야 합니다.") Integer dayIndex,
		@NotNull(message = "바탕 판 번호가 필요합니다.") Integer baseVersion,
		/**
		 * 그날 안의 몇 번째 자리에 넣을지(1부터, S15P21E201-2017). 없으면 예전처럼 맨 끝에 붙이고 시각을 비운다 — 화면이
		 * 재계산을 이어 부른다. 있으면 그 자리에 끼우고 서버가 구간·시각을 바로 다시 매긴다(재계산 Job 이 필요 없다).
		 */
		@Min(value = 1, message = "자리는 1 이상이어야 합니다.") Integer position) {

	/** 자리 없이 받던 예전 모양 — 맨 끝에 붙인다. */
	public AddItemRequest(String placeId, Integer dayIndex, Integer baseVersion) {
		this(placeId, dayIndex, baseVersion, null);
	}
}
