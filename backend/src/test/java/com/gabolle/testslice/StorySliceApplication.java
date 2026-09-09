package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * S15P21E201-207 계열 — 여행 기록·사진·팔로우 통합 테스트가 띄우는 애플리케이션.
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
		// S15P21E201-254 · -267 — 신고 접수·검토 목록 컨트롤러·서비스가 기록·사용자와 같은
		// 슬라이스에서 함께 떠야 한다(상세·피드가 신고 상태를 보고, 검토 목록이 기록·작성자
		// 이름을 읽는다).
		"com.gabolle.backend.moderation"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.story.domain",
		"com.gabolle.backend.user.domain",
		"com.gabolle.backend.place.domain",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.moderation.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.story.repository",
		"com.gabolle.backend.user.repository",
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.moderation.repository"
})
public class StorySliceApplication {
}
