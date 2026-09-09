package com.gabolle.backend.feed.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.feed.domain.FeedBuild;
import com.gabolle.backend.feed.domain.FeedBuildStatus;
import com.gabolle.backend.feed.domain.FeedSurface;

public interface FeedBuildRepository extends JpaRepository<FeedBuild, UUID> {

	/**
	 * 지금 읽을 세대를 찾는다. <b>읽기 경로의 첫 번째이자 유일한 조회 대상 표</b>다.
	 *
	 * <p>🔴 {@code Optional} 인 것이 정확하다. 한 사용자·한 화면에 READY 세대가 최대
	 * 하나임을 DB 의 조건부 UNIQUE 색인({@code uq_feed_build_ready})이 보장하기 때문이다.
	 * 그 색인이 없으면 여기서 목록을 받아 "아무거나 하나" 를 고르는 코드가 됐을 것이고,
	 * 그건 사람마다 다른 피드를 보는 버그로 이어진다.
	 *
	 * <p>결과가 비어 있는 것은 <b>오류가 아니다.</b> 가입 직후 아직 아무것도 안 만든
	 * 사용자의 정상 상태다.
	 */
	Optional<FeedBuild> findByUserIdAndSurfaceAndStatus(UUID userId, FeedSurface surface, FeedBuildStatus status);
}
