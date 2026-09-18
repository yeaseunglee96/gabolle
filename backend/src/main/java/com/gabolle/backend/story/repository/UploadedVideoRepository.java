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
 * 올라간 동영상 — S15P21E201-1275. {@code UploadedImageRepository} 와 같은 모양이다.
 *
 * <p>찾는 창구를 <b>주소로</b> 둔 것은 사진과 같은 이유다 — 기록을 만들 때 앱이 보내는 것이
 * 업로드 id 가 아니라 <b>돌려받은 주소</b>이기 때문이다.
 */
public interface UploadedVideoRepository extends JpaRepository<UploadedVideo, UUID> {

	Optional<UploadedVideo> findByVideoUrl(String videoUrl);

	Optional<UploadedVideo> findByStorageKey(String storageKey);

	/** 조립기가 한 번에 읽는다 — {@code UploadedImageRepository.findByUploadedImageIdIn} 과 같은 이유다. */
	List<UploadedVideo> findByUploadedVideoIdIn(Collection<UUID> ids);

	/**
	 * 어디에도 안 붙은 채 오래된 동영상 — S15P21E201-1284.
	 *
	 * <p>사진과 달리 붙는 자리가 <b>하나</b>다({@code story_video.uploadedVideoId}). 그래도 같은
	 * 모양으로 적어 두는 이유는, 나중에 자리가 늘면 <b>여기도 같이 늘려야 한다</b>는 것을 사진 쪽
	 * 주석과 나란히 보이게 하기 위해서다.
	 *
	 * <p>🔴 동영상은 한 개가 사진 수십 장이다. <b>이 청소가 실제로 지키는 것은 디스크</b>이고,
	 * 이 서버는 디스크를 쉽게 못 늘린다 — 차면 업로드가 아니라 서비스 전체가 멈춘다.
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
