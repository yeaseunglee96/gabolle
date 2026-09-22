package com.gabolle.backend.feed.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.feed.domain.FeedBuild;
import com.gabolle.backend.feed.domain.FeedBuildStatus;
import com.gabolle.backend.feed.domain.FeedSurface;

public interface FeedBuildRepository extends JpaRepository<FeedBuild, UUID> {

	/**
	 * 지금 읽을 세대를 찾는다. {@code Optional} 인 것은 한 사용자·한 화면에 READY 세대가
	 * 최대 하나임을 DB 의 조건부 UNIQUE 색인({@code uq_feed_build_ready})이 보장하기 때문이다.
	 *
	 * <p>결과가 비어 있는 것은 오류가 아니다. 아직 아무것도 안 만든 사용자의 정상 상태다.
	 */
	Optional<FeedBuild> findByUserIdAndSurfaceAndStatus(UUID userId, FeedSurface surface, FeedBuildStatus status);
}
