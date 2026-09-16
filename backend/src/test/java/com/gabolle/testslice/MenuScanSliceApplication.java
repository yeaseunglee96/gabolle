package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 메뉴판 한도 표를 진짜 PostgreSQL 위에서 보는 슬라이스 — S15P21E201-1038.
 *
 * <p>이 기능에서 표로 확인해야 하는 것은 「집계가 프로세스 밖에 남는가」다. 그건 메모리
 * 맵을 가짜로 끼워서는 확인할 수 없는 성질이라 진짜 DB 가 필요하다.
 *
 * <p>{@code menuscan} 만 훑는다 — 이 시험에 다른 도메인이 필요 없고, 남의 슬라이스에
 * 얹으면 그 슬라이스를 쓰는 시험들이 같이 무거워진다. 표는 Flyway 가 전부 만들므로
 * 여기서 안 훑는 {@code app_user} 도 생긴다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.menuscan"
})
@EntityScan(basePackages = "com.gabolle.backend.menuscan.domain")
@EnableJpaRepositories(basePackages = "com.gabolle.backend.menuscan.repository")
public class MenuScanSliceApplication {
}
