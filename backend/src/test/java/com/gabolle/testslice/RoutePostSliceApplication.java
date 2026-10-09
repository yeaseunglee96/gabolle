package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 루트 포스트(-2027·-2028) 통합 시험이 띄우는 애플리케이션. 공통과 루트 포스트만 올린다 — 일정 조회는
 * 시험이 가짜 {@code RouteSnapshotSource} 로 바꿔 끼운다. 저장소는 JDBC 라 JPA 스캔이 필요 없다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common.config",
		"com.gabolle.backend.routepost"
})
public class RoutePostSliceApplication {
}
