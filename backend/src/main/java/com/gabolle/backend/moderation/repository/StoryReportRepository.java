package com.gabolle.backend.moderation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.moderation.domain.StoryReport;

public interface StoryReportRepository extends JpaRepository<StoryReport, UUID> {

	/** 같은 사람이 같은 기록을 이미 신고했는가. 표의 UNIQUE 와 짝이다. */
	Optional<StoryReport> findByStoryIdAndReporterUserId(UUID storyId, UUID reporterUserId);

	/**
	 * 미처리 신고를 <b>오래된 순</b>으로 (S15P21E201-267 완료 기준).
	 *
	 * <p>🔴 최신순이 아니다. 오래 기다린 신고가 먼저 처리돼야 하고, 최신순이면 신고가 몰리는
	 * 동안 옛 신고가 영원히 뒤로 밀린다.
	 */
	@Query("""
			SELECT r FROM StoryReport r
			WHERE r.resolvedAt IS NULL
			ORDER BY r.createdAt ASC, r.storyReportId ASC
			""")
	List<StoryReport> findPendingOldestFirst(Limit limit);

	/** 한 기록에 달린 미처리 신고 전부. 검토 화면이 사유를 모아 보여줄 때 쓴다. */
	@Query("""
			SELECT r FROM StoryReport r
			WHERE r.storyId = :storyId AND r.resolvedAt IS NULL
			ORDER BY r.createdAt ASC
			""")
	List<StoryReport> findPendingByStoryId(@Param("storyId") UUID storyId);

	/**
	 * 검토 목록이 다룰 기록들의 미처리 신고를 한 번에 읽는다.
	 *
	 * <p>🔴 기록마다 따로 부르면 목록 길이만큼 질의가 늘어난다(N+1). 목록을 만든 뒤 그
	 * {@code storyId} 를 모아 이것을 한 번 부른다.
	 */
	@Query("""
			SELECT r FROM StoryReport r
			WHERE r.storyId IN :storyIds AND r.resolvedAt IS NULL
			ORDER BY r.createdAt ASC
			""")
	List<StoryReport> findPendingByStoryIdIn(@Param("storyIds") List<UUID> storyIds);
}
