package com.gabolle.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} 을 빼면 {@code @Scheduled} 메서드가 조용히 한 번도 안 돈다 — 컴파일도 기동도 막지 않는다. */
@SpringBootApplication
@EnableScheduling
public class GabolleBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GabolleBackendApplication.class, args);
	}

}
