package com.gabolle.backend.story.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
