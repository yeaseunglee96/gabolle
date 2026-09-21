package com.gabolle.backend.recommendation.adapter;

import java.util.UUID;

/**
 * Editor's Pick 하나로 만든 기준선.
 *
 * {@code editorial_pick} 은 (이름, 판) 마다 다른 행이라 정본 키는 {@code pickId} 이고
 * {@code pickKey} 는 로그에서 읽기 위한 이름이다. {@code batch} 의 후보는 편집자가 정한
 * 순서로 담겨 있다.
 */
public record EditorialPickBaseline(
		UUID pickId,
		String pickKey,
		int contentVersion,
		EngineCandidateBatch batch) {
}
