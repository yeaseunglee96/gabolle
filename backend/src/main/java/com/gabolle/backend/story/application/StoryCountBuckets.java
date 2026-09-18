package com.gabolle.backend.story.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 팔로워·팔로잉 목록 한 쪽에 나온 사람들을 <b>「나에게 무엇까지 보이는가」로</b> 셋으로 나눈 것
 * — S15P21E201-1317.
 *
 * <h2>🔴 왜 나누나</h2>
 * 목록의 각 줄에 그 사람이 쓴 기록 수를 적어야 하는데, <b>세는 범위가 사람마다 다르다.</b> 내가
 * 팔로우하는 사람은 「팔로워 공개」 글까지 보이고, 모르는 사람은 전체 공개 글만 보인다. 나 자신이
 * 목록에 있으면 비공개 글까지 보인다.
 *
 * <p>사람마다 다르다고 한 명씩 세면 줄 수만큼 질의가 나간다(목록 20줄이면 질의 20개). 그런데
 * <b>범위는 셋뿐</b>이라, 같은 범위끼리 묶으면 쪽 크기와 무관하게 <b>최대 세 번</b>이면 끝난다.
 *
 * <p>비어 있는 칸은 부르는 쪽이 건너뛴다 — 네이티브 {@code IN ()} 은 값이 없으면 PostgreSQL
 * 구문 오류를 낸다.
 *
 * @param self 보는 사람 자신. 남의 팔로워 목록에 내가 들어 있을 수 있어서 생기는 칸이다
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
