package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 방문 인증·리뷰 통합 테스트가 띄우는 애플리케이션 — <b>공통·장소·리뷰만</b> 올린다.
 *
 * <p>{@code PlaceSliceApplication} 과 같은 이유로 {@code com.gabolle.backend} 밖에 둔다 — 안에
 * 두면 본 애플리케이션의 컴포넌트 스캔에 걸려 {@code no-db} 프로필에서도
 * {@code @EnableJpaRepositories} 가 켜지고 {@code contextLoads} 가 깨진다.
 *
 * <p>{@code place} 를 더한 이유는 {@code VisitVerificationService}·{@code PlaceReviewService} 가
 * 장소 좌표·존재 확인을 {@code PlaceRepository} 로 읽기 때문이다({@code ItinerarySliceApplication}
 * 이 같은 이유로 place 를 더한 것과 같다).
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.place",
		"com.gabolle.backend.review"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.place.domain",
		"com.gabolle.backend.review.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.review.repository"
})
public class ReviewSliceApplication {
}
