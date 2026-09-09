package com.gabolle.backend.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 방문 인증·리뷰(S15P21E201-279 · -287 · -408)가 쓰는 설정.
 *
 * <h2>🔴 왜 이 클래스 자체에 {@code @Component} 를 붙였나</h2>
 *
 * 이 저장소의 다른 설정 클래스(예: {@code AuthProperties}·{@code PlaceProperties})는 전용
 * {@code @Configuration} 클래스에 {@code @EnableConfigurationProperties} 를 붙여 등록한다. 이
 * 작업은 만들 파일 목록이 {@code review} 패키지 아래로 정해져 있어 새 {@code @Configuration}
 * 클래스를 추가하지 않는다({@code common.security.SecurityAlertProperties} 와 같은 판단).
 * {@code @Component} 를 직접 붙이는 것도 스프링 부트가 지원하는 동등한 방법이다 — 컴포넌트
 * 스캔이 이 클래스를 찾아 {@code @ConfigurationProperties} 바인딩을 그대로 적용한다.
 *
 * <h2>기준값을 여기 두는 이유</h2>
 *
 * 완료 기준 거리(200m)와 정확도 상한(100m)을 자바 코드에 하드코딩하면, 실측으로 값을 조정할
 * 때마다 배포를 다시 해야 한다. 설정값으로 빼 두면 값만 바꿔 재기동할 수 있다.
 */
@Component
@ConfigurationProperties(prefix = "gabolle.review")
public class ReviewProperties {

	/**
	 * 방문 인증 기준 거리(m). 장소 좌표와 이 거리 안이면 인증된다.
	 *
	 * <p>티켓 완료 기준의 경계값 테스트(199m 인증·201m 거절)에 맞춰 <b>이하(&le;)</b> 로
	 * 비교한다 — {@link com.gabolle.backend.review.application.VisitVerificationService} 참고.
	 */
	private int visitVerificationDistanceThresholdM = 200;

	/**
	 * 위치 정확도 상한(m). 기기가 알려준 정확도가 이 값보다 나쁘면(숫자가 크면) 거리 판정
	 * 자체를 하지 않는다 — 그 좌표로는 "가까이 있었다" 를 말할 수 없기 때문이다.
	 */
	private int visitVerificationAccuracyLimitM = 100;

	/**
	 * 리뷰 목록 조회 한 번에 돌려주는 최대 개수. 아직 커서 방식 페이지네이션 요구가 없어
	 * 단순 상한으로 둔다 — {@code PlaceReviewRepository.findByPlaceId} 가 {@code Limit} 을
	 * 요구해서 어딘가는 값을 정해야 한다.
	 */
	private int reviewListMaxSize = 100;

	public int getVisitVerificationDistanceThresholdM() {
		return this.visitVerificationDistanceThresholdM;
	}

	public void setVisitVerificationDistanceThresholdM(int visitVerificationDistanceThresholdM) {
		this.visitVerificationDistanceThresholdM = visitVerificationDistanceThresholdM;
	}

	public int getVisitVerificationAccuracyLimitM() {
		return this.visitVerificationAccuracyLimitM;
	}

	public void setVisitVerificationAccuracyLimitM(int visitVerificationAccuracyLimitM) {
		this.visitVerificationAccuracyLimitM = visitVerificationAccuracyLimitM;
	}

	public int getReviewListMaxSize() {
		return this.reviewListMaxSize;
	}

	public void setReviewListMaxSize(int reviewListMaxSize) {
		this.reviewListMaxSize = reviewListMaxSize;
	}
}
