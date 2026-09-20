package com.gabolle.backend.feed.presentation.dto;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * 앱이 받는 피드 줄 하나.
 *
 * <p>{@code payload} 가 {@link JsonNode} 인 이유: 엔티티는 JSON 을 문자열로 들고 있는데,
 * 그대로 응답에 실으면 따옴표로 감싸진 문자열이 되어 앱이 두 번 파싱해야 한다.
 * {@code @JsonRawValue} 를 안 쓴 것은 이 프로젝트에 Jackson 두 판
 * ({@code com.fasterxml.jackson} · {@code tools.jackson})이 같이 올라와 있어서, 애너테이션을
 * 어느 판에서 가져오느냐에 따라 조용히 무시될 수 있기 때문이다. 대가는 줄마다 파싱 한 번이고,
 * 이게 문제가 되면 고칠 자리는 엔티티가 문자열 대신 {@link JsonNode} 를 들게 하는 쪽이다.
 *
 * @param position    저장된 자리. 이어보기 커서로 그대로 쓴다
 * @param authorId    커뮤니티에만 있고 홈은 {@code null}
 * @param score       순서는 이미 정해져 있다. 앱이 이 값으로 다시 정렬하지 않는다
 * @param reasonCodes 화면의 "이런 이유로 골랐어요" 가 이걸로 만들어진다
 */
public record FeedItemResponse(int position, String itemType, UUID itemId, UUID authorId, Double score,
		List<String> reasonCodes, JsonNode payload) {
}
