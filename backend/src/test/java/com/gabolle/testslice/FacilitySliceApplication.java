package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 근처 편의시설 통합 테스트가 띄우는 애플리케이션 — 편의시설과 공통만 올린다 (S15P21E201-2052).
 *
 * <p>{@code PlaceSliceApplication} 에 편의시설을 얹지 않고 새로 만든 이유는 그것이 장소 쪽 사람의 파일이라는 것과, 스캔 범위를 넓히면
 * 장소 테스트가 무엇을 띄우는지 달라진다는 것이다. {@code com.gabolle.backend} 밖에 두는 것이 필수다 — 안에 두면 본 애플리케이션 스캔에
 * 걸려 아래 {@code @EnableJpaRepositories} 가 {@code no-db} 프로필에서도 켜진다({@code PlaceSliceApplication} 설명).
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.facility"
})
@EntityScan(basePackages = "com.gabolle.backend.facility.domain")
@EnableJpaRepositories(basePackages = "com.gabolle.backend.facility.repository")
public class FacilitySliceApplication {
}
