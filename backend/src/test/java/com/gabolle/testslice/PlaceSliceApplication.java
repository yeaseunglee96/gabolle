package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 장소 통합 테스트가 띄우는 애플리케이션 — 장소와 공통만 올린다.
 *
 * <p>기존 {@code RecommendationSliceApplication} 에 place 를 얹지 않고 새로 만든 이유가 둘이다.
 * 하나는 그것이 다른 사람의 파일이라는 것이고, 다른 하나는 그 슬라이스에
 * {@code RecommendationEngineMissingIntegrationTest}(엔진 없이 컨텍스트가 뜨는지 보는 테스트)가
 * 붙어 있어서 스캔 범위를 넓히면 그 테스트가 무엇을 보는지 달라진다는 것이다.
 *
 * <p>{@code com.gabolle.backend} 밖에 두는 것이 필수다. 테스트 소스도 클래스패스에 올라가므로
 * 안에 두면 본 애플리케이션 스캔에 걸려 아래 {@code @EnableJpaRepositories} 가 {@code no-db}
 * 프로필에서도 켜지고, EntityManagerFactory 가 없어 {@code contextLoads} 가 죽는다. 같은 사고가
 * {@code RecommendationSliceApplication} 주석에 기록돼 있다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.place"
})
@EntityScan(basePackages = "com.gabolle.backend.place.domain")
@EnableJpaRepositories(basePackages = "com.gabolle.backend.place.repository")
public class PlaceSliceApplication {
}
