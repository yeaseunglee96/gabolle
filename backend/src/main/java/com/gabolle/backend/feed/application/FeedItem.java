package com.gabolle.backend.feed.application;

import java.util.List;
import java.util.UUID;

/**
 * 피드 줄 하나 — 조회 결과의 원소.
 *
 * <p>홈과 커뮤니티가 <b>표는 둘인데 결과 모양은 하나</b>다. 저장할 때는 가리키는 것이
 * 달라서 나눠야 했지만(장소·일정 vs 글), 앱이 받는 것은 어느 쪽이든 "위치·무엇·왜·그릴 값"
 * 넷이라 같은 모양으로 낸다. 앱이 화면마다 다른 파서를 쓰지 않아도 된다.
 *
 * @param position    저장된 자리. 이어보기 커서로 그대로 쓴다
 * @param itemType    {@code PLACE} · {@code ITINERARY} · {@code COURSE} · {@code POST}
 * @param itemId      가리키는 것의 번호
 * @param authorId    글쓴이. 커뮤니티에만 있고 홈은 {@code null}
 * @param score       순위를 매긴 점수. 앱은 이걸로 정렬하지 않는다 — 순서는 이미 정해져 있다
 * @param reasonCodes 왜 이것이 여기 있는가. <b>비어 있을 수 없다</b> — DB 가 막는다
 * @param payload     카드를 그리는 데 필요한 값의 사본. JSON 문자열 그대로 지나간다
 */
public record FeedItem(int position, String itemType, UUID itemId, UUID authorId, Double score,
		List<String> reasonCodes, String payload) {
}
