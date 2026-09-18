package com.gabolle.backend.batch.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 접기 요청 — MLOps Phase 1.
 *
 * @param userIds 접을 사람. 비어 있으면 400 이다 — 빈 요청은 실수이지 "전부" 가 아니다
 * @param asOf 이 시각까지 도착한 것만 본다
 */
public record RebuildTasteVectorsRequest(

		@NotEmpty(message = "userIds 가 비어 있다. 빈 목록은 '전부' 가 아니라 실수다") List<UUID> userIds,

		/*
		 * 🔴 기본값을 두지 않는다. 서버가 "지금" 으로 채우면 8월 1일을 채우는 backfill 이
		 * 9월 데이터를 먹는데, 값이 있기는 하므로 DB 도 검사도 그것을 못 잡는다. 없으면
		 * 400 으로 시끄럽게 멈추는 편이 낫다.
		 */
		@NotNull(message = "asOf 가 없다. 배치의 시각은 부르는 쪽이 정한다") OffsetDateTime asOf) {
}
