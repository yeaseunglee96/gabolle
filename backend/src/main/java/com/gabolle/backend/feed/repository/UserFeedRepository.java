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
	 * 한 세대의 줄을 위치 순서대로 읽는다. <b>이 티켓 전체가 이 한 줄을 위해 있다.</b>
	 *
	 * <p>이 조회에는 조인이 없고, 정렬이 색인 순서 그대로이고, 계산이 없다. 기본 키가
	 * {@code (build_id, position)} 이라 PostgreSQL 은 그 색인을 앞에서부터 훑다가
	 * {@code limit} 개를 채우면 멈춘다 — 사용자가 몇 명이든, 온톨로지에 인스턴스가
	 * 몇 개든 이 비용은 안 변한다. 그게 미리 만들어 두는 이유다.
	 *
	 * <p>🔴 이어보기를 {@code OFFSET} 이 아니라 <b>마지막으로 본 위치</b>로 한다.
	 * {@code OFFSET 10000} 은 앞의 만 줄을 실제로 읽고 버리므로 뒤로 갈수록 느려진다.
	 * 위치로 자르면 색인이 바로 그 자리로 간다 — 첫 장과 백 번째 장의 비용이 같다.
	 * 이 방식을 커서 페이지네이션(<b>몇 번째부터가 아니라 "무엇 다음부터" 로 이어 읽는 것</b>)
	 * 이라고 한다.
	 *
	 * <p>덤으로 정확하기도 하다 — 세대가 바뀌지 않는 한 위치는 안 변하므로, 사람이
	 * 스크롤하는 도중에 줄이 밀려 같은 항목을 두 번 보거나 건너뛰는 일이 없다.
	 *
	 * @param afterPosition 이 위치 <b>다음</b>부터. 첫 장은 -1 을 준다
	 */
	@Query("select e from UserFeedEntry e "
			+ "where e.id.buildId = :buildId and e.id.position > :afterPosition "
			+ "order by e.id.position asc")
	List<UserFeedEntry> readPage(@Param("buildId") UUID buildId, @Param("afterPosition") int afterPosition,
			Pageable pageable);
}
