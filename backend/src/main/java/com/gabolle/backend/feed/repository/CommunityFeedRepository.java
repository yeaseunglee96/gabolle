package com.gabolle.backend.feed.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.feed.domain.CommunityFeedEntry;
import com.gabolle.backend.feed.domain.FeedEntryId;

public interface CommunityFeedRepository extends JpaRepository<CommunityFeedEntry, FeedEntryId> {

	/** {@link UserFeedRepository#readPage} 와 같은 모양이고 같은 이유다. */
	@Query("select e from CommunityFeedEntry e "
			+ "where e.id.buildId = :buildId and e.id.position > :afterPosition "
			+ "order by e.id.position asc")
	List<CommunityFeedEntry> readPage(@Param("buildId") UUID buildId, @Param("afterPosition") int afterPosition,
			Pageable pageable);
}
