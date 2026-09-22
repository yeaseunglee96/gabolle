package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 방문 인증·리뷰 통합 테스트가 띄우는 애플리케이션 — 공통·장소·리뷰만 올린다.
 *
 * <p>{@code PlaceSliceApplication} 과 같은 이유로 {@code com.gabolle.backend} 밖에 둔다 — 안에
 * 두면 본 애플리케이션의 컴포넌트 스캔에 걸려 {@code no-db} 프로필에서도
 * {@code @EnableJpaRepositories} 가 켜지고 {@code contextLoads} 가 깨진다.
 *
 * <p>{@code place} 를 더한 이유는 {@code VisitVerificationService}·{@code PlaceReviewService} 가
 * 장소 좌표·존재 확인을 {@code PlaceRepository} 로 읽기 때문이다({@code ItinerarySliceApplication}
 * 이 같은 이유로 place 를 더한 것과 같다).
 *
 * <p>{@code user.application} 을 더한 이유 — {@code VisitVerificationService} 가 좌표를 보기 전에 정밀 위치 동의를 확인하면서
 * {@code ConsentGuard} 를 필수로 요구하게 됐다. 그 빈이 없으면 컨텍스트가 통째로 못 뜨고
 * 이 슬라이스의 검사 열일곱 개가 한꺼번에 빨개진다 — 실제로 CI 가 그렇게 잡았다
 * ({@code NoSuchBeanDefinitionException: ConsentGuard}).
 *
 * <p>{@code user} 패키지를 통째로 스캔하지 않고 {@code user.application} 만 더한다. 통째로
 * 더하면 이 슬라이스가 사용자 도메인의 다른 빈들에까지 인질로 잡히고, 그건 {@code AuthSliceApplication}
 * 의 스캔 목록이 계속 길어지며 겪고 있는 문제다(그 파일의 "이 목록이 계속 길어지는 것 자체가
 * 신호다" 참고).
 *
 * <p>{@code user.repository} 도 함께 켠다. {@code ConsentGuard} 는 저장소를
 * {@link org.springframework.beans.factory.ObjectProvider} 로 늦게 찾으므로 없어도 빈은
 * 만들어지고, 실제로 동의를 물을 때 비로소 터진다. 즉 저장소를 빼면 컨텍스트는 뜨는데
 * 방문 인증 검사만 실행 시점에 죽는다 — 원인이 한 단계 멀어지는 실패다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.place",
		"com.gabolle.backend.review",
		"com.gabolle.backend.user.application"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.place.domain",
		"com.gabolle.backend.review.domain",
		"com.gabolle.backend.user.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.review.repository",
		"com.gabolle.backend.user.repository"
})
public class ReviewSliceApplication {
}
