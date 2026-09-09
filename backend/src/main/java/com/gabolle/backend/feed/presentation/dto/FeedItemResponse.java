package com.gabolle.backend.feed.presentation.dto;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * 앱이 받는 피드 줄 하나.
 *
 * <h2>🔴 {@code payload} 가 {@link JsonNode} 인 이유</h2>
 *
 * 엔티티는 JSON 을 문자열로 들고 있다 — 이 저장소가 이미 그렇게 하고 있어서
 * ({@code RecommendationCandidate} 의 {@code featureValues} 등) 맞췄다. 그런데 문자열을
 * 그대로 응답에 실으면 <b>따옴표로 감싸진 문자열</b>로 나간다.
 *
 * <pre>
 *   "payload": "{\"name\":\"광안리\"}"     ← 앱이 두 번 파싱해야 한다
 *   "payload": { "name": "광안리" }         ← 이것이 맞다
 * </pre>
 *
 * <p>{@code @JsonRawValue} 로 한 번에 해결할 수도 있지만 <b>쓰지 않았다.</b>
 * 이 프로젝트에는 Jackson(<b>자바 객체와 JSON 을 서로 바꿔 주는 라이브러리</b>) 두 판이
 * 같이 올라와 있다 — 옛 판({@code com.fasterxml.jackson})과 새 판({@code tools.jackson}).
 * 애너테이션을 어느 판에서 가져오느냐에 따라 <b>조용히 무시될 수 있고</b>, 그러면 위의
 * 잘못된 모양이 아무 오류 없이 나간다. 응답을 눈으로 볼 때까지 아무도 모른다.
 *
 * <p>그래서 서버가 실제로 쓰는 판의 타입을 직접 쓴다. 대가는 줄마다 파싱 한 번이고,
 * 페이로드가 작은 객체라 무시할 만하다. 이게 문제가 되면 고칠 자리는 여기가 아니라
 * <b>엔티티가 문자열 대신 {@link JsonNode} 를 들게 하는 것</b>이다.
 *
 * @param position    저장된 자리. 이어보기 커서로 그대로 쓴다
 * @param itemType    {@code PLACE} · {@code ITINERARY} · {@code COURSE} · {@code POST}
 * @param itemId      가리키는 것의 번호
 * @param authorId    글쓴이. 커뮤니티에만 있고 홈은 {@code null}
 * @param score       순위를 매긴 점수. <b>앱이 이걸로 정렬하지 않는다</b> — 순서는 이미 정해져 있다
 * @param reasonCodes 왜 이것이 여기 있는가. 화면의 "이런 이유로 골랐어요" 가 이걸로 만들어진다
 * @param payload     카드를 그리는 데 필요한 값의 사본
 */
public record FeedItemResponse(int position, String itemType, UUID itemId, UUID authorId, Double score,
		List<String> reasonCodes, JsonNode payload) {
}
