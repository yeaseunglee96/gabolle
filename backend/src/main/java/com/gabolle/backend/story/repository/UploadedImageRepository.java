package com.gabolle.backend.story.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.story.domain.UploadedImage;

public interface UploadedImageRepository extends JpaRepository<UploadedImage, UUID> {

	Optional<UploadedImage> findByStorageKey(String storageKey);

	/** 기록 저장 요청이 준 주소들을 한 번에 찾는다. 없는 주소·남의 주소는 호출자가 걸러 400 으로 답한다. */
	List<UploadedImage> findByImageUrlIn(Collection<String> imageUrls);

	List<UploadedImage> findByUploadedImageIdIn(Collection<UUID> ids);
}
