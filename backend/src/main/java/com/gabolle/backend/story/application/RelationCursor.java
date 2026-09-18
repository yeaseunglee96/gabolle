package com.gabolle.backend.story.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.gabolle.backend.story.presentation.dto.RelationItemResponse;
import com.gabolle.backend.story.presentation.dto.RelationListResponse;
import com.gabolle.backend.story.repository.RelationRow;

/**
 * 팔로워·팔로잉·차단 목록의 커서 — "마지막으로 본 관계의 (맺은 시각, 상대 식별자)".
 *
 * <p>모양과 이유는 {@link FeedCursor} 와 같다 — 그쪽은 기록을, 이쪽은 사람 관계를 가리킬 뿐이다.
 * 둘을 하나로 합치지 않는다. 이름이 {@code publishAt}·{@code storyId} 인 타입을 관계 목록에
 * 그대로 재사용하면, 읽는 사람이 "기록도 아닌데 왜 storyId 를 넣나" 를 매번 다시 풀어야 한다.
 */
public record RelationCursor(Instant relatedAt, UUID userId) {

	/** 커서가 없을 때 — "가장 최근 관계부터". timestamptz 가 담을 수 있는 먼 미래와 가장 큰 UUID 다. */
	public static final RelationCursor NONE = new RelationCursor(Instant.parse("9999-12-31T23:59:59Z"),
			UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));

	public String encode() {
		String raw = relatedAt.toString() + "|" + userId;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @param encoded 앱이 돌려준 문자열. {@code null}·빈 문자열이면 {@link #NONE}
	 * @throws InvalidCursorException 우리가 만든 모양이 아니다 — 400 으로 답할 자리다
	 */
	public static RelationCursor decode(String encoded) {
		if (encoded == null || encoded.isBlank()) {
			return NONE;
		}
		try {
			String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			int bar = raw.indexOf('|');
			if (bar < 0) {
				throw new InvalidCursorException(encoded);
			}
			return new RelationCursor(Instant.parse(raw.substring(0, bar)), UUID.fromString(raw.substring(bar + 1)));
		}
		catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
			throw new InvalidCursorException(encoded);
		}
	}

	/**
	 * 질의가 {@code size + 1} 개를 읽어 온 결과를 응답 모양으로 접는다 — {@code StoryFeedService.page}
	 * 와 같은 "한 개 더 읽기" 방식이다. 팔로워·팔로잉·차단 셋이 이 메서드 하나를 같이 쓴다.
	 *
	 * @param viewerFollows 지금 보는 사람(로그인한 사람)이 팔로우하는 사람들의 식별자 — 목록의
	 *                      각 줄에 "나는 이 사람을 팔로우하는가"(following)를 채우는 데 쓴다.
	 *                      S15P21E201-1179 계약: 목록 화면이 팔로우 버튼을 그리려면 필요하다.
	 *                      대상의 팔로우 여부가 아니라 <b>보는 사람</b> 기준이다 — 그래야 남의
	 *                      팔로워 목록을 볼 때도 내 버튼 상태가 맞게 나온다.
	 *
	 * <p>이 쪽으로 부르면 줄의 {@code storyCount} 는 {@code null} 이다 — <b>「0 개」가 아니라
	 * 「안 셌다」</b>. 숫자를 그리는 목록은 {@link #pageWithStoryCounts} 로 부른다.
	 */
	public static RelationListResponse page(List<RelationRow> rows, int size, Set<UUID> viewerFollows) {
		return page(rows, size, viewerFollows, null);
	}

	/**
	 * {@link #page} 와 같은데 줄마다 <b>기록 수</b>를 함께 싣는다 — S15P21E201-1317.
	 *
	 * @param storyCounts 사람 → 그 사람이 쓴 기록 중 보는 사람에게 보이는 것의 수.
	 *                    <b>이 표에 없는 사람은 0 이다</b> — 기록이 하나도 없는 사람은 세는
	 *                    질의의 결과에 아예 안 들어오기 때문이다. {@code null} 을 주면 세지
	 *                    않았다는 뜻이고, 그때는 응답의 {@code storyCount} 도 {@code null} 이다
	 */
	public static RelationListResponse pageWithStoryCounts(List<RelationRow> rows, int size, Set<UUID> viewerFollows,
			Map<UUID, Long> storyCounts) {
		return page(rows, size, viewerFollows, Objects.requireNonNull(storyCounts, "storyCounts"));
	}

	private static RelationListResponse page(List<RelationRow> rows, int size, Set<UUID> viewerFollows,
			Map<UUID, Long> storyCounts) {
		boolean hasMore = rows.size() > size;
		List<RelationRow> shown = hasMore ? rows.subList(0, size) : rows;
		List<RelationItemResponse> items = shown.stream()
				.map(row -> new RelationItemResponse(row.getUserId().toString(), row.getDisplayName(), row.getAvatarUrl(),
						viewerFollows.contains(row.getUserId()),
						storyCounts == null ? null : storyCounts.getOrDefault(row.getUserId(), 0L)))
				.toList();
		String next = null;
		if (hasMore) {
			RelationRow last = shown.get(shown.size() - 1);
			next = new RelationCursor(last.getRelatedAt(), last.getUserId()).encode();
		}
		return new RelationListResponse(items, next);
	}

	public static class InvalidCursorException extends RuntimeException {

		public InvalidCursorException(String cursor) {
			super("커서가 올바르지 않습니다. 이전 응답의 nextCursor 를 그대로 보내 주세요.");
		}
	}
}
