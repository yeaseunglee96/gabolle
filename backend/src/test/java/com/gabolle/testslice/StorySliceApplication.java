package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 여행 기록·사진·팔로우 통합 테스트가 띄우는 애플리케이션.
 *
 * <p>공통·기록·사용자·장소·여행만 올린다. 여행은 공개 시각 기본값("여행 종료 다음 날")을 계산할 때
 * {@code TripRepository} 가 필요해서, 장소는 기록에 연결한 장소 이름을 응답에 실을 때 필요해서 들어왔다.
 * 다른 슬라이스와 같은 이유로 {@code com.gabolle.testslice} 패키지에 둔다 — 본 애플리케이션의
 * 컴포넌트 스캔에 걸리지 않게.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.story",
		"com.gabolle.backend.user",
		"com.gabolle.backend.place",
		"com.gabolle.backend.trip",
		// 신고 접수·검토 목록 컨트롤러·서비스가 기록·사용자와 같은
		// 슬라이스에서 함께 떠야 한다(상세·피드가 신고 상태를 보고, 검토 목록이 기록·작성자
		// 이름을 읽는다).
		"com.gabolle.backend.moderation",
		// trip 패키지의 SpendProfileService가 EventIngestService를 물게
		// 되면서, trip만 띄우고 event를 안 띄우면 이 빈이 없어 이 슬라이스 전체(신고·팔로우
		// 포함, event와 무관한 테스트까지)가 컨텍스트 로딩부터 깨졌다.
		"com.gabolle.backend.event"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.story.domain",
		"com.gabolle.backend.user.domain",
		"com.gabolle.backend.place.domain",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.moderation.domain",
		"com.gabolle.backend.event.domain",
		// RecommendationJobRepository(아래 @EnableJpaRepositories)가
		// 관리하는 엔티티(RecommendationJob)의 표 매핑도 여기 없으면 "관리 대상 아님"으로
		// 빈 자체를 못 만든다.
		"com.gabolle.backend.recommendation.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.story.repository",
		"com.gabolle.backend.user.repository",
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.moderation.repository",
		"com.gabolle.backend.event.repository",
		// AnalyticsQueryService(event.application)가 생성자로
		// RecommendationJobRepository 를 요구한다. event 를 스캔하는 순간 그 빈도 같이
		// 요구된다(AuthSliceApplication 이 같은 이유로 먼저 겪었다).
		"com.gabolle.backend.recommendation.repository"
})
public class StorySliceApplication {
}
