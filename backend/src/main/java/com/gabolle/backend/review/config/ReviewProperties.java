package com.gabolle.backend.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 방문 인증·리뷰가 쓰는 설정.
 *
 * 다른 설정 클래스와 달리 별도 {@code @Configuration} 없이 {@code @Component} 로 직접 등록한다.
 * 컴포넌트 스캔이 찾아 {@code @ConfigurationProperties} 바인딩을 그대로 적용한다.
 *
 * 기준 거리와 정확도 상한을 설정값으로 둔 이유는 실측으로 조정할 때 재배포 없이 바꾸기
 * 위해서다.
 */
@Component
@ConfigurationProperties(prefix = "gabolle.review")
public class ReviewProperties {

	/** 방문 인증 기준 거리(m). 경계 포함(이하)으로 비교한다 — 200m 정확히는 인증된다. */
	private int visitVerificationDistanceThresholdM = 200;

	/**
	 * 위치 정확도 상한(m). 기기가 알려준 정확도가 이 값보다 나쁘면(숫자가 크면) 거리 판정 자체를
	 * 하지 않는다.
	 */
	private int visitVerificationAccuracyLimitM = 100;

	/** 리뷰 목록 조회 한 번의 최대 개수. 페이지네이션 요구가 없어 단순 상한으로 둔다. */
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
