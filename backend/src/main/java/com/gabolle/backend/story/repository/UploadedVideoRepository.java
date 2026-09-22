package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.story.domain.UploadedVideo;

/**
 * 올라간 동영상. 찾는 창구가 주소인 것은 기록을 만들 때 앱이 보내는 것이 업로드 id 가 아니라
 * 돌려받은 주소이기 때문이다.
 */
public interface UploadedVideoRepository extends JpaRepository<UploadedVideo, UUID> {

	Optional<UploadedVideo> findByVideoUrl(String videoUrl);

	Optional<UploadedVideo> findByStorageKey(String storageKey);

	/** 조립기가 한 번에 읽는다 — {@code UploadedImageRepository.findByUploadedImageIdIn} 과 같은 이유다. */
	List<UploadedVideo> findByUploadedVideoIdIn(Collection<UUID> ids);

	/**
	 * 어디에도 안 붙은 채 오래된 동영상. 붙는 자리는 지금
	 * {@code story_video.uploadedVideoId} 하나뿐이고, 늘어나면 이 질의도 같이 늘려야 한다.
	 */
	@Query("""
			SELECT v FROM UploadedVideo v
			 WHERE v.createdAt < :cutoff
			   AND v.deletedAt IS NULL
			   AND NOT EXISTS (SELECT 1 FROM StoryVideo sv WHERE sv.uploadedVideoId = v.uploadedVideoId)
			 ORDER BY v.createdAt ASC
			""")
	List<UploadedVideo> findOrphansOlderThan(@Param("cutoff") Instant cutoff, Pageable limit);
}
