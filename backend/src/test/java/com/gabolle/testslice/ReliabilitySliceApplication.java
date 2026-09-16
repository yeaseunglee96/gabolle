package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 컬렉션·저장한 장소·추천 판단을 <b>한 컨텍스트</b>에 올리는 슬라이스 — S15P21E201-1037.
 *
 * <p>이 셋은 도메인이 다르지만 고쳐야 할 것이 같다 — 「같은 것을 두 번 넣으려 할 때
 * 500 이 아니라 성공을 준다」. 그 판정을 DB 가 하므로 <b>진짜 PostgreSQL 위에서</b>
 * 확인해야 하고, 그러려면 세 리포지토리가 한 컨텍스트에 있어야 한다.
 *
 * <p>슬라이스를 새로 만든 이유. {@link PlaceSliceApplication} 은 {@code place} 만 훑고,
 * 거기에 컬렉션과 추천을 더하면 그 슬라이스를 쓰는 기존 테스트들의 컨텍스트가 같이
 * 무거워진다. 남의 테스트가 왜 느려졌는지 아무도 못 찾는 종류의 변경이라 따로 둔다.
 *
 * <p>표는 Flyway 가 전부 만든다 — 여기서 훑지 않는 {@code app_user}·{@code trip} 도 생긴다.
 * 그래서 시험이 필요로 하는 부모 행은 JDBC 로 직접 넣는다.
 */
@SpringBootApplication(scanBasePackages = "com.gabolle.backend.common")
@EntityScan(basePackages = {
		"com.gabolle.backend.place.domain",
		"com.gabolle.backend.collection.domain",
		"com.gabolle.backend.recommendation.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.collection.repository",
		"com.gabolle.backend.recommendation.repository"
})
public class ReliabilitySliceApplication {
}
