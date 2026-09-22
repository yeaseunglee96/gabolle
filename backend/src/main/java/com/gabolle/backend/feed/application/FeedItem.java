package com.gabolle.backend.feed.application;

import java.util.List;
import java.util.UUID;

/**
 * 피드 줄 하나. 홈과 커뮤니티가 표는 둘이지만 결과 모양은 이 하나로 통일한다.
 *
 * @param position    저장된 자리. 이어보기 커서로 그대로 쓴다
 * @param authorId    커뮤니티에만 있고 홈은 {@code null}
 * @param score       순서는 이미 정해져 있다. 이 값으로 다시 정렬하지 않는다
 * @param reasonCodes 비어 있을 수 없다 — DB 가 막는다
 * @param payload     카드를 그리는 데 필요한 값의 사본. JSON 문자열 그대로 지나간다
 */
public record FeedItem(int position, String itemType, UUID itemId, UUID authorId, Double score,
		List<String> reasonCodes, String payload) {
}
