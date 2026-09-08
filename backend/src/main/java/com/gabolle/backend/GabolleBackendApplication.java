package com.gabolle.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 🔴 {@code @EnableScheduling} — 이 저장소 최초의 {@code @Scheduled} 등록(S15P21E201-357 개인정보
 * 자동 정리 배치). 이 애노테이션이 없으면 {@code @Scheduled} 메서드가 조용히 한 번도 안 돈다 —
 * 컴파일도 기동도 막지 않아서 알아채기 어렵다.
 */
@SpringBootApplication
@EnableScheduling
public class GabolleBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GabolleBackendApplication.class, args);
	}

}
