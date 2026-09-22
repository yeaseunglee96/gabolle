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

import com.gabolle.backend.story.domain.UploadedImage;

public interface UploadedImageRepository extends JpaRepository<UploadedImage, UUID> {

	Optional<UploadedImage> findByStorageKey(String storageKey);

	/** 기록 저장 요청이 준 주소들을 한 번에 찾는다. 없는 주소·남의 주소는 호출자가 걸러 400 으로 답한다. */
	List<UploadedImage> findByImageUrlIn(Collection<String> imageUrls);

	List<UploadedImage> findByUploadedImageIdIn(Collection<UUID> ids);

	/**
	 * 어디에도 안 붙은 채 오래된 사진.
	 *
	 * <p>사진이 붙는 자리는 넷이다 — {@code story_image}, 동영상 썸네일
	 * ({@code story_video.thumbnailUploadId}), 프로필 사진({@code app_user.avatar_url}),
	 * 커버 사진({@code app_user.cover_url}). 하나라도 빠뜨리면 붙어 있는 파일이 고아로 판정돼
	 * 조용히 지워지므로, 자리가 늘어나면 이 질의도 같이 늘려야 한다.
	 *
	 * <p>계정 쪽은 업로드 식별자가 아니라 주소 문자열을 들고 있어 주소로 잇는다. 청소가 하루 한
	 * 번 한 판 상한 안에서만 돌아 비용이 되지 않는다 — 계정이 크게 늘면 그때
	 * {@code avatar_url}·{@code cover_url} 에 인덱스를 둔다.
	 *
	 * <p>이미 지운 것({@code deletedAt} 이 찬 것)은 다시 보지 않는다.
	 *
	 * @param cutoff 이 시각보다 먼저 올라온 것만. 방금 올리고 아직 글을 쓰는 중인 것을 지우지
	 * 않기 위한 유예다
	 * @param limit 한 번에 가져올 상한. 이상한 일이 생겨도 피해가 한 판 크기로 묶인다
	 */
	@Query("""
			SELECT i FROM UploadedImage i
			 WHERE i.createdAt < :cutoff
			   AND i.deletedAt IS NULL
			   AND NOT EXISTS (SELECT 1 FROM StoryImage si WHERE si.uploadedImageId = i.uploadedImageId)
			   AND NOT EXISTS (SELECT 1 FROM StoryVideo sv WHERE sv.thumbnailUploadId = i.uploadedImageId)
			   AND NOT EXISTS (SELECT 1 FROM AppUser u
			                    WHERE u.avatarUrl = i.imageUrl OR u.coverUrl = i.imageUrl)
			 ORDER BY i.createdAt ASC
			""")
	List<UploadedImage> findOrphansOlderThan(@Param("cutoff") Instant cutoff, Pageable limit);
}
