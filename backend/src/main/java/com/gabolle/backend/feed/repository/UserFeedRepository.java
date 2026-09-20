package com.gabolle.backend.feed.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.feed.domain.FeedEntryId;
import com.gabolle.backend.feed.domain.UserFeedEntry;

public interface UserFeedRepository extends JpaRepository<UserFeedEntry, FeedEntryId> {

	/**
	 * 한 세대의 줄을 위치 순서대로 읽는다. 조인도 정렬 계산도 없다 — 기본 키가
	 * {@code (build_id, position)} 이라 색인을 앞에서부터 훑다가 {@code limit} 개를 채우면
	 * 멈추고, 그래서 사용자 수나 온톨로지 크기에 비용이 안 따라 늘어난다.
	 *
	 * <p>이어보기를 {@code OFFSET} 이 아니라 마지막으로 본 위치로 한다. {@code OFFSET} 은 앞의
	 * 줄을 실제로 읽고 버려서 뒤로 갈수록 느려지지만, 위치로 자르면 첫 장과 백 번째 장의
	 * 비용이 같다. 세대가 바뀌지 않는 한 위치가 안 변하므로 스크롤 도중에 같은 항목을 두 번
	 * 보거나 건너뛰는 일도 없다.
	 *
	 * @param afterPosition 이 위치 다음부터. 첫 장은 -1 을 준다
	 */
	@Query("select e from UserFeedEntry e "
			+ "where e.id.buildId = :buildId and e.id.position > :afterPosition "
			+ "order by e.id.position asc")
	List<UserFeedEntry> readPage(@Param("buildId") UUID buildId, @Param("afterPosition") int afterPosition,
			Pageable pageable);
}
