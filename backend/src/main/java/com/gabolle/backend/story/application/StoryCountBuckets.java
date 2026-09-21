package com.gabolle.backend.story.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 팔로워·팔로잉 목록 한 쪽에 나온 사람들을 보는 사람에게 무엇까지 보이는가로 셋으로 나눈 것.
 * 기록 수를 한 명씩 세는 대신 같은 범위끼리 묶어 최대 세 번의 질의로 끝낸다.
 *
 * <p>비어 있는 칸은 부르는 쪽이 건너뛴다 — 네이티브 {@code IN ()} 은 값이 없으면 PostgreSQL
 * 구문 오류를 낸다.
 *
 * @param self 보는 사람 자신. 남의 팔로워 목록에 내가 들어 있을 수 있다
 * @param followed 보는 사람이 팔로우하는 사람들
 * @param strangers 그 밖의 사람들
 */
record StoryCountBuckets(List<UUID> self, List<UUID> followed, List<UUID> strangers) {

	static StoryCountBuckets of(UUID viewer, List<UUID> userIds, Set<UUID> viewerFollows) {
		List<UUID> self = new ArrayList<>();
		List<UUID> followed = new ArrayList<>();
		List<UUID> strangers = new ArrayList<>();
		for (UUID userId : userIds) {
			if (userId.equals(viewer)) {
				self.add(userId);
			}
			else if (viewerFollows.contains(userId)) {
				followed.add(userId);
			}
			else {
				strangers.add(userId);
			}
		}
		return new StoryCountBuckets(self, followed, strangers);
	}
}
