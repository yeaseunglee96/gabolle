package com.gabolle.backend.story.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 목록 한 쪽을 「무엇까지 보이는가」로 나누는 규칙 — S15P21E201-1317.
 *
 * <p>이 시험이 지키는 것은 <b>한 사람이 정확히 한 칸에만 들어가는가</b>다. 두 칸에 들어가면 그
 * 사람의 기록 수를 두 번 세어 <b>나중에 센 쪽으로 덮어쓰고</b>, 그 값은 화면에서 그럴듯해 보인다.
 */
class StoryCountBucketsTest {

	private final UUID viewer = UUID.randomUUID();

	private final UUID followed = UUID.randomUUID();

	private final UUID stranger = UUID.randomUUID();

	@Test
	@DisplayName("🔴 보는 사람·팔로우하는 사람·모르는 사람이 각각 다른 칸으로 간다")
	void splitsByWhatTheViewerCanSee() {
		StoryCountBuckets buckets = StoryCountBuckets.of(this.viewer,
				List.of(this.viewer, this.followed, this.stranger), Set.of(this.followed));

		assertThat(buckets.self()).containsExactly(this.viewer);
		assertThat(buckets.followed()).containsExactly(this.followed);
		assertThat(buckets.strangers()).containsExactly(this.stranger);
	}

	/**
	 * 🔴 나 자신은 <b>팔로우 여부보다 먼저</b> 갈린다. 자기 자신을 팔로우할 수는 없지만, 자기
	 * 자신이 든 집합이 실수로 들어와도 「나」 칸으로 가야 한다 — 그래야 내 비공개 기록이 목록에서
	 * 빠지지 않는다.
	 */
	@Test
	@DisplayName("🔴 나 자신은 팔로우 집합에 들어 있어도 「나」 칸으로 간다")
	void selfWinsOverFollowed() {
		StoryCountBuckets buckets = StoryCountBuckets.of(this.viewer, List.of(this.viewer), Set.of(this.viewer));

		assertThat(buckets.self()).containsExactly(this.viewer);
		assertThat(buckets.followed()).isEmpty();
	}

	@Test
	@DisplayName("🔴 빈 칸은 빈 칸으로 남는다 — 부르는 쪽이 이것을 보고 질의를 건너뛴다")
	void emptyBucketsStayEmpty() {
		StoryCountBuckets buckets = StoryCountBuckets.of(this.viewer, List.of(this.stranger), Set.of());

		assertThat(buckets.self()).isEmpty();
		assertThat(buckets.followed()).isEmpty();
		assertThat(buckets.strangers()).containsExactly(this.stranger);
	}

	@Test
	@DisplayName("아무도 없으면 세 칸 모두 빈다")
	void noRows() {
		StoryCountBuckets buckets = StoryCountBuckets.of(this.viewer, List.of(), Set.of());

		assertThat(buckets.self()).isEmpty();
		assertThat(buckets.followed()).isEmpty();
		assertThat(buckets.strangers()).isEmpty();
	}
}
